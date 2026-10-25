package org.privateprice.pricebook;
import java.util.List;
public final class ReceiptParserTest {
  static int checks=0;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static ReceiptParser.Receipt receipt(String s){return ReceiptParser.parseReceipt(s);}
  public static void main(String[] args) {
    ReceiptParser.Receipt r=receipt("架空スーパー\n2026年10月25日 18:30\n税込表示\n牛乳 1L 218\n卵 10個 258\n豆腐 88円\n小計 564\n内消費税 41\n合計 564\n現金 1000\nお釣り 436");
    check(r.items.size()==3,"商品3件");check(r.items.get(0).name.equals("牛乳 1L"),"規格保持");
    check(r.items.get(1).name.equals("卵 10個"),"10個パックを購入数量にしない");
    check(r.items.get(1).quantity==null&&r.items.get(0).unitPrice==null,"単価と数量は未確定");
    check(r.store.equals("架空スーパー")&&r.date.equals("2026-10-25"),"店名と日付");
    check(r.total==564&&r.subtotal==564&&r.taxAmount==41,"集計を別保持");
    check(r.reconciliation().contains("一致"),"税込行金額の照合");
    r=receipt("架空マート\nりんご\n@100×2 200※\n人参\n3点 300 軽\n洗剤 330 10%\n合計 830");
    check(r.items.size()==3,"複数行と税記号");check(r.items.get(0).quantity==2&&r.items.get(0).unitPrice==100,"明記単価と数量");
    check(r.items.get(1).quantity==3&&r.items.get(1).unitPrice==null,"行合計から単価を推測しない");
    check(r.items.get(0).taxRate==null&&r.items.get(2).taxRate==10,"記号から税率を推測しない");
    check(receipt("税 額 123\n合 計 999\nお買上点数 4\n現金 1000\n内税 70\n8%対象 800\nうち消費税 59\n支払金額 999\n獲得ポイント 9\nレジ番号 12\n電話 000-0000-0000").items.isEmpty(),"非商品行の除外");
    check(receipt("牛乳 200\n合計 200\n謎の決済情報 999").items.size()==1,"合計後の支払フッターを商品にしない");
    check(receipt("人参\n電話 123\n2点 200").items.isEmpty(),"集計を挟んで結合しない");
    r=receipt("税込表示\n牛乳 200\n合計 999");check(r.reconciliation().contains("不一致"),"総額矛盾");
    r=receipt("牛乳 200\n値引 -20\n合計 180");check(r.hasDiscount&&r.items.size()==1&&r.reconciliation().contains("未確定"),"割引の分離");
    check(receipt("2026/02/30\n牛乳 200").date.isEmpty(),"不正日付");
    check(receipt("2026/10/25\n2026/10/24\n2026/10/25").date.isEmpty(),"日付競合を再確定しない");
    check(receipt("架空A店\n架空B店\n架空A店").store.isEmpty(),"店名競合");
    check(receipt("牛乳 200").store.isEmpty()&&receipt("牛乳 200").date.isEmpty(),"未認識メタデータは空");
    check(receipt("牛乳 1000001\n返品 -200\n12345\n商品 99999999999999999999").items.isEmpty(),"数値上限・負数・数字のみ");
    check(receipt("x".repeat(32001)).items.isEmpty(),"全文上限");
    check(receipt("牛乳 200\n".repeat(501)).items.isEmpty(),"行数上限");
    check(receipt("x".repeat(241)+" 200").items.isEmpty(),"行長上限");
    check(receipt(null).items.isEmpty(),"null");
    check(receipt("数量 3\n個数 2\n購入点数 5\n税金 30\n内 計 500\n本体価格合計 400").items.isEmpty(),"追加の数量・税・集計見出し");
    check(receipt("税込合計 432").total==432,"税込総額");
    check(receipt("消貴税 123\n台計 999\n税頭 12").items.isEmpty(),"集計見出しの一文字OCR誤り");
    r=receipt("牛乳\n\n@109x2 218※");check(r.items.size()==1&&r.items.get(0).quantity==2&&r.items.get(0).unitPrice==109,"OCRの空行を挟む明示数量行");
    check(receipt("牛乳\n\n200").items.isEmpty(),"空行越しの金額だけの誤結合を防ぐ");
    check(receipt("牛乳 - 200\n豆腐 △ 80").items.isEmpty(),"空白付き負金額");
    r=receipt("りんご\n数量2点 200※");check(r.items.size()==1&&r.items.get(0).quantity==2,"数量見出し付き継続行");
    System.out.println("PASS ReceiptParser "+checks+" checks (synthetic receipts only)");
  }
}
