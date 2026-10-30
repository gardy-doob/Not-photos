#!/bin/bash
# Test de fumée Aura : installe l'APK, ouvre l'app, crée un souvenir, ouvre la visionneuse et ses infos, parcourt les onglets.
# Les résultats sont publiés en annotations (::notice:: / ::error::).
mkdir -p shots
APK=$(ls apk/*.apk | head -1)
adb install -r -g "$APK" || { echo "::error::Installation impossible"; exit 1; }
PKG=$(adb shell pm list packages | grep notphotos | head -1 | cut -d: -f2 | tr -d '\r')
[ -z "$PKG" ] && { echo "::error::Paquet introuvable"; exit 1; }
adb shell mkdir -p /sdcard/DCIM/Camera
for i in $(seq 1 14); do
  adb shell screencap -p "/sdcard/DCIM/Camera/IMG_202610$(printf %02d $((i % 28 + 1)))_1200$(printf %02d $i).png"
done
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 3
shot() { adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "shots/$1.png" >/dev/null 2>&1; }
top() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | sed 's/.*{//; s/}.*//' | tr ' ' '\n' | grep '/' | sed 's/.*[./]//' | tr -d '\r'; }
STATES=""
step() { STATES="$STATES | $1: $(top)"; }
# centre de la N-ième vue portant cet identifiant (uiautomator)
center() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  adb pull /sdcard/u.xml u.xml >/dev/null 2>&1
  python3 - "$1" "${2:-1}" <<'PY'
import re,sys
xml=open('u.xml',encoding='utf-8',errors='ignore').read()
rid,n=sys.argv[1],int(sys.argv[2])
hits=re.findall(r'resource-id="[^"]*:id/'+rid+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml)
if len(hits)>=n:
    a,b,c,d=map(int,hits[n-1]); print((a+c)//2,(b+d)//2)
PY
}
MISSING=""
tapid() { local p; p=$(center "$1" "${2:-1}"); [ -n "$p" ] && adb shell input tap $p && return 0; MISSING="$MISSING $1#${2:-1}"; return 1; }
dtapid() { local p; p=$(center "$1" "${2:-1}"); if [ -n "$p" ]; then adb shell input tap $p; adb shell input tap $p; else MISSING="$MISSING dtap:$1#${2:-1}"; fi; }

adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell settings put secure immersive_mode_confirmations confirmed || true
sleep 2
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 20; shot 1-galerie; step lancement
# boîte « ne répond pas » : laquelle, puis on la ferme (« Attendre ») avant de tester
for k in 1 2 3; do
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml u0.xml >/dev/null 2>&1
  grep -q "aerr_" u0.xml || break
  WHO=$(adb shell dumpsys window | grep -m2 -E "Application Not Responding|mCurrentFocus" | tr -d '\r' | tr '\n' ' ')
  echo "::warning::Boîte « ne répond pas » ($k) : $WHO"
  p=$(python3 -c "import re;x=open('u0.xml').read();m=re.search(r'aerr_close\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')")
  [ -n "$p" ] && adb shell input tap $p
  sleep 4
done
step apres-dialogue
dtapid medium_thumbnail 4; sleep 3; shot 2-souvenir; step souvenir
tapid medium_thumbnail 8; sleep 5; shot 4-visionneuse; step visionneuse
adb shell input swipe 540 1500 540 500 180; sleep 3; shot 5-infos; step infos
adb shell input keyevent KEYCODE_BACK; sleep 2; step apres-infos
if [ "$(top)" = "ViewPagerActivity" ]; then
  adb shell input tap 540 1100; sleep 2; shot 6-plein-ecran
  adb shell input keyevent KEYCODE_BACK; sleep 3
fi
step retour-galerie
for t in np_video_filter np_video_filter np_nav_jalons np_nav_gallery np_nav_all np_nav_folders np_nav_gallery np_nav_toggle np_nav_toggle; do tapid $t; sleep 4; done
shot 7-onglets; step onglets
tapid np_burger; sleep 2; shot 8-burger; adb shell input keyevent KEYCODE_BACK; sleep 1
adb logcat -d > logcat.txt
# blocage (ANR) : quel processus, et où le fil principal est bloqué
if grep -q "ANR in" logcat.txt; then
  echo "::warning::$(grep -m3 -A6 'ANR in' logcat.txt | tr '\n' '|' | cut -c1-900)"
  adb root >/dev/null 2>&1; sleep 3
  mkdir -p anr; adb pull /data/anr anr >/dev/null 2>&1
  F=$(grep -rl "$PKG" anr 2>/dev/null | head -1)
  if [ -n "$F" ]; then
    STACK=$(awk '/^"main"/{f=1} f{print} f&&/^$/{exit}' "$F" | head -45 | tr '\n' '|' | sed 's/%/%25/g' | cut -c1-3500)
    echo "::error::BLOCAGE fil principal : $STACK"
  fi
fi
echo "::notice::Activités$STATES"
[ -n "$MISSING" ] && echo "::warning::Introuvables :$MISSING (vues : $(grep -o 'resource-id="[^"]*:id/[a-z_]*' u.xml | sed 's/.*:id\///' | sort | uniq -c | sort -rn | head -12 | tr '\n' ' '))"
if grep -q "FATAL EXCEPTION" logcat.txt; then
  CRASH=$(grep -E "FATAL EXCEPTION" -A25 logcat.txt | sed 's/^.*AndroidRuntime: //' | head -25 | tr '\n' '|' | sed 's/%/%25/g')
  echo "::error::PLANTAGE $CRASH"
  exit 1
fi
echo "$STATES" | grep -qE "lancement: (Media|Gallery)Activity" || { echo "::error::L'app ne s'est pas ouverte sur la Galerie"; exit 1; }
echo "$STATES" | grep -q "visionneuse: ViewPagerActivity" || echo "::warning::La visionneuse ne s'est pas ouverte"
echo "::notice::Aucun plantage"
