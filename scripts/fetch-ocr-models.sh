#!/bin/sh
# Build-time only. Runtime uses these verified assets without network access.
set -eu
cd "$(dirname "$0")/.."
OUT="app/src/main/assets/tessdata"
REV=87416418657359cb625c412a48b6e1d6d41c29bd
mkdir -p "$OUT"
for lang in jpn eng; do
  case "$lang" in
    jpn) checksum=1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d ;;
    eng) checksum=7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2 ;;
  esac
  dest="$OUT/$lang.traineddata"
  if ! test -f "$dest" || ! echo "$checksum  $dest" | sha256sum -c --status; then
    echo "Fetching pinned $lang model"
    curl --fail --location --retry 3 --silent --show-error \
      "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$REV/$lang.traineddata" -o "$dest.part"
    if ! echo "$checksum  $dest.part" | sha256sum -c; then rm -f "$dest.part"; exit 1; fi
    mv "$dest.part" "$dest"
  fi
  echo "$checksum  $dest" | sha256sum -c
done
