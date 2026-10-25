#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
manifest=app/src/main/AndroidManifest.xml
if grep -Eqi '<uses-permission' "$manifest"; then echo 'FAIL: permission present'; exit 1; fi
grep -q 'android:allowBackup="false"' "$manifest"
grep -q 'android:usesCleartextTraffic="false"' "$manifest"
grep -q 'android:dataExtractionRules=' "$manifest"
if grep -ERqi '(firebase|google-analytics|play-services|admob|crashlytics)' app/src/main/java app/build.gradle; then echo 'FAIL: tracking SDK mention'; exit 1; fi
echo 'PASS basic static privacy checks (not a full audit)'
