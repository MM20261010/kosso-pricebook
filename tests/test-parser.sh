#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
javac -encoding UTF-8 -d "$TEMP" app/src/main/java/org/privateprice/pricebook/PriceMath.java app/src/main/java/org/privateprice/pricebook/OcrDocument.java app/src/main/java/org/privateprice/pricebook/ReceiptLayout.java app/src/main/java/org/privateprice/pricebook/ReceiptParser.java tests/ReceiptParserTest.java tests/ReceiptLayoutTest.java app/src/main/java/org/privateprice/pricebook/ReceiptReview.java tests/ReceiptUnitTest.java tests/shared/ReceiptExtractionTest.java tests/shared/ReceiptAuditScenario.java tests/shared/ReceiptClassificationProbe.java tests/shared/ReceiptClassificationAuditTest.java
java -cp "$TEMP" org.privateprice.pricebook.ReceiptParserTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptLayoutTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptUnitTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptExtractionTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptClassificationAuditTest
