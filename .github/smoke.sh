#!/bin/bash
# Test de fumée Aura : installe l'APK, ouvre l'app, crée un jalon, ouvre la visionneuse, parcourt les onglets.
# Les résultats sont publiés en annotations (::notice:: / ::error::).
mkdir -p shots
APK=$(ls apk/*.apk | head -1)
adb install -r -g "$APK" || { echo "::error::Installation impossible"; exit 1; }
PKG=$(adb shell pm list packages | grep notphotos | head -1 | cut -d: -f2 | tr -d '\r')
[ -z "$PKG" ] && { echo "::error::Paquet introuvable"; exit 1; }
adb shell mkdir -p /sdcard/DCIM/Camera
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb shell screencap -p "/sdcard/DCIM/Camera/IMG_202610$(printf %02d $i)_120000.png"
done
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 3
shot() { adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "shots/$1.png" >/dev/null 2>&1; }
top() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | sed 's/.*{//; s/}.*//' | tr -d '\r'; }
STATES=""
step() { STATES="$STATES | $1: $(top | tr ' ' '\n' | grep '/' | sed 's/.*[./]//')"; }
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 20; shot 1-galerie; step lancement
adb shell input tap 110 520; adb shell input tap 110 520; sleep 2; shot 2-debut; step debut
adb shell input tap 470 520; sleep 3; shot 2-souvenir; step souvenir
adb shell input tap 540 1500; sleep 5; shot 3-visionneuse; step visionneuse
adb shell input swipe 540 1500 540 600 200; sleep 3; shot 4-infos; step infos
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap 540 1200; sleep 2; shot 5-plein-ecran
adb shell input keyevent KEYCODE_BACK; sleep 3; step retour
for x in 240 110 370 500 630 110 980 980; do adb shell input tap $x 2230; sleep 4; done
shot 6-fin; step onglets
adb shell input tap 980 160; sleep 2; shot 7-burger; adb shell input keyevent KEYCODE_BACK; sleep 1
adb logcat -d > logcat.txt
echo "::notice::Activités$STATES"
if grep -q "FATAL EXCEPTION" logcat.txt; then
  CRASH=$(grep -E "FATAL EXCEPTION" -A25 logcat.txt | sed 's/^.*AndroidRuntime: //' | head -25 | tr '\n' '|' | sed 's/%/%25/g')
  echo "::error::PLANTAGE $CRASH"
  exit 1
fi
if ! echo "$STATES" | grep -q "lancement: .*Activity"; then
  echo "::error::L'app ne semble pas ouverte au lancement"
  exit 1
fi
echo "::notice::Aucun plantage"
