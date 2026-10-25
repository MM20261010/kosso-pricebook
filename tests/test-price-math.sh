#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p tests/.classes
javac -d tests/.classes app/src/main/java/org/privateprice/pricebook/PriceMath.java tests/PriceMathTest.java
java -cp tests/.classes PriceMathTest
rm -rf tests/.classes
