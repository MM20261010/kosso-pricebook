#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
javac -encoding UTF-8 -d "$TEMP" app/src/main/java/org/privateprice/pricebook/PriceMath.java app/src/main/java/org/privateprice/pricebook/OcrDocument.java app/src/main/java/org/privateprice/pricebook/ReceiptLayout.java app/src/main/java/org/privateprice/pricebook/ReceiptParser.java app/src/main/java/org/privateprice/pricebook/ReceiptFiscal.java app/src/main/java/org/privateprice/pricebook/ReceiptFiscalRecovery.java app/src/main/java/org/privateprice/pricebook/ReceiptFiscalNotation.java app/src/main/java/org/privateprice/pricebook/ReceiptDiagnostics.java tests/ReceiptParserTest.java tests/ReceiptLayoutTest.java app/src/main/java/org/privateprice/pricebook/ReceiptReview.java tests/ReceiptUnitTest.java tests/shared/ReceiptExtractionTest.java tests/shared/ReceiptAuditScenario.java tests/shared/ReceiptClassificationProbe.java tests/shared/ReceiptClassificationAuditTest.java tests/shared/ReceiptPairingTest.java tests/shared/ReceiptPipelineTest.java tests/shared/ReceiptConfirmationTest.java tests/shared/ReceiptFiscalTest.java tests/shared/ReceiptFiscalStagesTest.java tests/shared/ReceiptFiscalRecoveryScenario.java tests/shared/ReceiptFiscalRecoveryTest.java tests/shared/ReceiptFiscalRescueScenario.java tests/shared/ReceiptFiscalRescueTest.java
java -cp "$TEMP" org.privateprice.pricebook.ReceiptParserTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptLayoutTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptUnitTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptExtractionTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptClassificationAuditTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptPairingTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptPipelineTest
java -cp "$TEMP" org.privateprice.pricebook.ReceiptConfirmationTest

java -cp "$TEMP" org.privateprice.pricebook.ReceiptFiscalTest

java -cp "$TEMP" org.privateprice.pricebook.ReceiptFiscalStagesTest

java -cp "$TEMP" org.privateprice.pricebook.ReceiptFiscalRecoveryTest

java -cp "$TEMP" org.privateprice.pricebook.ReceiptFiscalRescueTest
