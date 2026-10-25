#!/usr/bin/env python3
"""Check the built/downloaded v0.4 debug APK, without receipt data or network access."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/apk/debug/app-debug.apk')
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('ANDROID_HOME or ANDROID_SDK_ROOT is required')
tools = Path(sdk) / 'build-tools/35.0.0'

def run(tool, *args):
    return subprocess.run([str(tools / tool), *map(str, args)], check=True,
                          capture_output=True, text=True).stdout

run('apksigner', 'verify', apk)
print('PASS APK signature')
badging = run('aapt', 'dump', 'badging', apk)
if "package: name='org.privateprice.pricebook' versionCode='4' versionName='0.4.0-dev'" not in badging:
    raise SystemExit('Unexpected APK package/version')
permissions = run('aapt', 'dump', 'permissions', apk)
actual = re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions, re.MULTILINE)
if actual != ['org.privateprice.pricebook.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION']:
    raise SystemExit('Unexpected APK permission set')
manifest = run('aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
for flag in ('allowBackup', 'fullBackupContent', 'usesCleartextTraffic'):
    if not re.search(r'android:' + flag + r'\(0x[0-9a-f]+\)=\(type 0x12\)0x0\s*$', manifest, re.MULTILINE):
        raise SystemExit('APK must disable ' + flag)
print('PASS v0.4 package, permissions, backup and cleartext settings')
with zipfile.ZipFile(apk) as archive:
    for lang, expected in (
        ('eng', '7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2'),
        ('jpn', '1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d'),
    ):
        actual = hashlib.sha256(archive.read('assets/tessdata/' + lang + '.traineddata')).hexdigest()
        if actual != expected:
            raise SystemExit('Bundled OCR model checksum mismatch: ' + lang)
        print('PASS bundled ' + lang + ' model SHA-256')
print('APK SHA-256: ' + hashlib.sha256(apk.read_bytes()).hexdigest())
