#!/bin/bash
# Test de fumée Aura : installe l'APK, ouvre l'app, crée un jalon, ouvre la visionneuse, parcourt les onglets.
set -x
mkdir -p shots
APK=$(ls apk/*.apk | head -1)
adb install -r -g "$APK"
PKG=$(adb shell pm list packages | grep notphotos | head -1 | cut -d: -f2 | tr -d '\r')
echo "Paquet : $PKG"
adb shell mkdir -p /sdcard/DCIM/Camera
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb shell screencap -p "/sdcard/DCIM/Camera/IMG_202610$(printf %02d $i)_120000.png"
done
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary || true
sleep 3
shot() { adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "shots/$1.png" >/dev/null; }
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 20
shot 1-galerie
adb shell input tap 110 560; adb shell input tap 110 560; sleep 3
shot 2-jalon
adb shell input tap 330 560; sleep 5
shot 3-visionneuse
adb shell input swipe 540 1500 540 600 200; sleep 3
shot 4-infos
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap 540 1200; sleep 2
shot 5-plein-ecran
adb shell input keyevent KEYCODE_BACK; sleep 3
for x in 240 110 370 500 630 110 980 980; do adb shell input tap $x 2230; sleep 4; done
shot 6-fin
adb logcat -d > logcat.txt
echo "===== Plantages ====="
grep -E "FATAL EXCEPTION" -A40 logcat.txt | head -200
if grep -q "FATAL EXCEPTION" logcat.txt; then echo "PLANTAGE DETECTE"; exit 1; fi
echo "Aucun plantage"
