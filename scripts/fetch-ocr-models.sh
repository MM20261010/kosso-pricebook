#!/bin/sh
# Build-time download only: the Android app itself has no network permission.
set -eu
cd "$(dirname "$0")/.."
OUT="app/src/main/assets/tessdata"
mkdir -p "$OUT"
for lang in jpn eng; do
  dest="$OUT/$lang.traineddata"
  if [ ! -s "$dest" ]; then
    echo "Fetching $lang model into APK asset staging directory"
    curl --fail --location --retry 3 --silent --show-error \
      "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$lang.traineddata" -o "$dest.part"
    test "$(wc -c < "$dest.part")" -gt 100000 || { echo 'Model too short'; exit 1; }
    mv "$dest.part" "$dest"
  fi
done
sha256sum "$OUT"/*.traineddata
