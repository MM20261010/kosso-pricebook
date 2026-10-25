The APK build must include jpn.traineddata and eng.traineddata from
https://github.com/tesseract-ocr/tessdata_fast (Apache-2.0).
Run scripts/fetch-ocr-models.sh before building. Android runtime never downloads data.
This source ZIP intentionally omits traineddata binaries: network fetch is build-only.
For F-Droid submission, pin to a specific revision and checksum before public release.
