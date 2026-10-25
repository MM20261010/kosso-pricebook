package org.privateprice.pricebook;
import java.util.List;
public final class ReceiptParserTest {
  public static void main(String[] args) {
    List<ReceiptParser.Candidate> found=ReceiptParser.parse("野良スーパー\n牛乳 1L 218\n卵 10個 258\n小計 476\n消費税 35\n合計 511\n値引 20\n豆腐 88円\n");
    if(found.size()!=3)throw new AssertionError("商品3件を期待: "+found.size());
    if(!found.get(0).name.equals("牛乳 1L")||found.get(0).price!=218)throw new AssertionError("牛乳");
    if(!found.get(2).name.equals("豆腐")||found.get(2).price!=88)throw new AssertionError("豆腐");
    if(!ReceiptParser.parse("現金 1,000\n合計 1,000\n").isEmpty())throw new AssertionError("合計を除外");
    System.out.println("PASS ReceiptParser 4 checks");
  }
}
