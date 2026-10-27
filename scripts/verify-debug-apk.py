#!/usr/bin/env python3
"""Check the built/downloaded v0.7 debug APK, without receipt data or network access."""
import hashlib
import os
import json
import struct
import tempfile
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
if "package: name='org.privateprice.pricebook' versionCode='7' versionName='0.7.0-debug-unit-review'" not in badging:
    raise SystemExit('Unexpected APK package/version')
permissions = run('aapt', 'dump', 'permissions', apk)
actual = re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions, re.MULTILINE)
if actual != ['org.privateprice.pricebook.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION']:
    raise SystemExit('Unexpected APK permission set')
manifest = run('aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
for flag in ('allowBackup', 'fullBackupContent', 'usesCleartextTraffic'):
    if not re.search(r'android:' + flag + r'\(0x[0-9a-f]+\)=\(type 0x12\)0x0\s*$', manifest, re.MULTILINE):
        raise SystemExit('APK must disable ' + flag)
print('PASS v0.7 package, permissions, backup and cleartext settings')
with zipfile.ZipFile(apk) as archive:
    for lang, expected in (
        ('eng', '7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2'),
        ('jpn', '1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d'),
    ):
        actual = hashlib.sha256(archive.read('assets/tessdata/' + lang + '.traineddata')).hexdigest()
        if actual != expected:
            raise SystemExit('Bundled OCR model checksum mismatch: ' + lang)
        print('PASS bundled ' + lang + ' model SHA-256')
    model_manifest = json.loads(archive.read('assets/paddle/manifest.json'))
    lock = Path(__file__).resolve().with_name('paddle-models.json')
    if model_manifest['source_lock_sha256'] != hashlib.sha256(lock.read_bytes()).hexdigest():
        raise SystemExit('Unexpected Paddle source lock')
    if model_manifest['precision'] != 'FP32' or model_manifest.get('conversion_network_denied') is not True:
        raise SystemExit('Expected offline FP32 conversion')
    expected_names = {'det.param', 'det.bin', 'rec.param', 'rec.bin', 'keys.bin'}
    if set(model_manifest['files']) != expected_names:
        raise SystemExit('Unexpected Paddle files')
    for name, entry in model_manifest['files'].items():
        data = archive.read('assets/paddle/' + name)
        if len(data) != entry['bytes'] or hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise SystemExit('Bundled Paddle hash mismatch: ' + name)
    print('PASS five Paddle assets, FP32/offline conversion and pinned source provenance')
    for name in ('ncnn-LICENSE.txt', 'OpenCV-LICENSE.txt', 'OpenCV-THIRD-PARTY-NOTICES.txt', 'Clipper-LICENSE.txt',
                 'Boost-LICENSE.txt', 'PaddleOCR-LICENSE.txt',
                 'PP-OCRv5_mobile_det-model-card.md', 'PP-OCRv5_mobile_rec-model-card.md'):
        if not archive.read('assets/licenses/' + name):
            raise SystemExit('Missing license: ' + name)
    expected_abis = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
    libs = [n for n in archive.namelist() if n.endswith('/liblocal_paddle.so')]
    if {n.split('/')[1] for n in libs} != expected_abis:
        raise SystemExit('Unexpected Paddle ABI set')
    for name in libs:
        data = archive.read(name)
        readelf = Path(sdk) / 'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
        with tempfile.TemporaryDirectory() as temp:
            lib = Path(temp) / 'liblocal_paddle.so'
            lib.write_bytes(data)
            symbols = subprocess.run([str(readelf), '--dyn-syms', '--wide', str(lib)],
                                     check=True, capture_output=True, text=True).stdout
            network_api = r'\bUND\s+(?:socket|connect|send|sendto|sendmsg|recv|recvfrom|recvmsg|getaddrinfo|execve|system)(?:@|\s|$)'
            if re.search(network_api, symbols):
                raise SystemExit('Unexpected network/process API import: ' + name)
        if data[:4] != b'\x7fELF' or data[5] != 1:
            raise SystemExit('Expected little endian ELF')
        bits = data[4]
        if bits == 2:
            phoff = struct.unpack_from('<Q', data, 32)[0]
            entsize, count = struct.unpack_from('<HH', data, 54)
        else:
            phoff = struct.unpack_from('<I', data, 28)[0]
            entsize, count = struct.unpack_from('<HH', data, 42)
        stack = relro = False
        for i in range(count):
            offset = phoff + i * entsize
            if bits == 2:
                kind, flags, _, _, _, _, _, align = struct.unpack_from('<IIQQQQQQ', data, offset)
            else:
                kind, _, _, _, _, _, flags, align = struct.unpack_from('<IIIIIIII', data, offset)
            if kind == 1:
                if flags & 3 == 3 or align < 16384:
                    raise SystemExit('Unsafe load segment or missing 16K alignment: ' + name)
            if kind == 0x6474e551:
                stack = not (flags & 1)
            if kind == 0x6474e552:
                relro = True
        if not stack or not relro:
            raise SystemExit('Missing NX stack/RELRO: ' + name)
    print('PASS Paddle has no imported network/process launch APIs')
    print('PASS Paddle ABIs, ELF NX stack/RELRO, no writable executable segments and 16K alignment')
print('APK bytes: ' + str(apk.stat().st_size))
print('APK SHA-256: ' + hashlib.sha256(apk.read_bytes()).hexdigest())
