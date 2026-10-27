#!/bin/bash
# CI uses an empty AOSP emulator and only the synthetic assets in the instrumentation APK.
set -euo pipefail
app_apk="$1"
test_apk="$2"
emulator_bin="${ANDROID_HOME}/emulator/emulator"
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  sudo setfacl -m "u:$(id -un):rw" /dev/kvm
fi
printf 'no\n' | avdmanager create avd --force --name local_ocr_test --package 'system-images;android-35;default;x86_64' --device pixel_2
"$emulator_bin" -avd local_ocr_test -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -camera-back none -camera-front none -memory 2048 -cores 2 > "${RUNNER_TEMP:-/tmp}/local-ocr-emulator.log" 2>&1 &
emulator_pid=$!
trap 'adb emu kill >/dev/null 2>&1 || true; kill "$emulator_pid" >/dev/null 2>&1 || true' EXIT
timeout 360 adb wait-for-device
booted=false
for attempt in $(seq 1 150); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; then booted=true; break; fi
  sleep 2
done
[[ "$booted" == true ]]
adb shell input keyevent 82
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell am instrument -w org.privateprice.pricebook.test/org.privateprice.pricebook.OfflineOcrInstrumentation > "${RUNNER_TEMP:-/tmp}/local-ocr-instrumentation.txt"
cat "${RUNNER_TEMP:-/tmp}/local-ocr-instrumentation.txt"
python3 - "${RUNNER_TEMP:-/tmp}/local-ocr-instrumentation.txt" <<'PY'
from pathlib import Path
import sys,re
s=Path(sys.argv[1]).read_text()
assert 'INSTRUMENTATION_CODE: -1' in s, 'Android instrumentation did not finish successfully'
assert 'PASS synthetic-only offline OCR and JSON roundtrip' in s, 'Android synthetic checks failed'
m=re.search(r'INSTRUMENTATION_RESULT: checks=(\d+)',s)
assert m and int(m.group(1))>=321, 'Expected synthetic checks did not execute'
print('PASS Android emulator: both OCRs used the same pixels; models, date/parser and isolated JSON roundtrip checked')
PY
