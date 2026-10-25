#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
javac -encoding UTF-8 -d "$TEMP" app/src/main/java/org/privateprice/pricebook/ReceiptImage.java tests/ReceiptImageTest.java
java -cp "$TEMP" org.privateprice.pricebook.ReceiptImageTest
