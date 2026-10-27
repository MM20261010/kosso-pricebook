package org.privateprice.pricebook;
import java.util.*;
/** Fictional OCR corruption/positions, not a transcription of a real receipt. */
public final class ReceiptExtractionTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static OcrDocument.Region box(String s,float x,float y,float w){return new OcrDocument.Region(s,x,y,x+w,y,x+w,y+24,x,y+24);}
    static final String[] NAMES={"架空もやし200g","架空パン6枚入","架空コーヒー500g","架空豆腐300g","架空洗剤CV-20","架空スポンジ"};
    static final long[] PRICES={6,158,1460,490,399,359};
    static OcrDocument fixture(int variant){
        List<OcrDocument.Region> a=new ArrayList<>();
        a.add(box("FICTIVE MARKET",100,20,500));a.add(box("2026/10/2719:00",70,62,400));a.add(box("全品税抜表示",70,104,300));
        // Sequential fake identifiers replace all real store/register/transaction information.
        a.add(box("架空受付店舗No00レジNo00担当者No00取引No12,345",30,146,900));
        float y=210;
        for(int i=0;i<6;i++){
            String code=i<3?"11":"22";
            if(i==0||i==3||variant==1){a.add(box(code,30,y,36));a.add(box(NAMES[i],150,y,500));}
            else if(variant==3&&i==2){a.add(box(code,30,y,36));y+=32;a.add(box(NAMES[i],150,y,500));}
            else a.add(box(code+(variant==2?" ":"")+NAMES[i],30,y,620));
            a.add(box(PRICES[i]+(i<3?"C":"V"),850,y+4,140));
            if(i==1||i==3){a.add(box(i==1?"@79×2":"@245×2",150,y+40,240));y+=40;}
            y+=48;
        }
        // A misplaced/repeated detail must never give the 359 yen row a quantity of two.
        a.add(box("@245×2",150,y,240));y+=48;
        for(String s:new String[]{variant==1?"小計8点 2872":variant==2?"小訐8点2872":"小汁8点 2872","8%消費税77","10%消費税191","税額合計268","合計3140","不明な支払案内999","カード支払3140"}){a.add(box(s,70,y,900));y+=44;}
        Collections.shuffle(a,new Random(8+variant));return new OcrDocument("synthetic corrupted rows only",1200,1600,a);
    }
    static ReceiptParser.Receipt parsed(int variant){return ReceiptParser.parseDocument(fixture(variant));}
    static void core(ReceiptParser.Receipt r){
        check(r.items.size()==6,"exactly six, no administration or subtotal candidate");
        for(int i=0;i<6;i++){check(r.items.get(i).name.equals(NAMES[i]),"code-free name on correct row");check(r.items.get(i).price==PRICES[i],"correct line amount");check(PriceMath.key(r.items.get(i).name).equals(PriceMath.key(NAMES[i])),"clean shopping memo key");}
        check(r.items.get(1).quantity==2&&r.items.get(1).unitPrice==79,"79 x 2 = 158");
        check(r.items.get(3).quantity==2&&r.items.get(3).unitPrice==245,"245 x 2 = 490");
        check(r.items.get(5).quantity==null,"359 never borrows quantity two");
        check(r.itemSum()==2872&&Long.valueOf(2872).equals(r.subtotal)&&Long.valueOf(268).equals(r.taxAmount)&&Long.valueOf(3140).equals(r.total),"line sum and aggregates");
        check(r.printedItemCount==8&&r.knownQuantitySum()==4&&r.unknownQuantityCount()==4,"whole receipt count does not fill individual quantities");
        check(r.items.stream().allMatch(c->c.taxRate==null),"C/V never imply tax rates");
        check(r.lines.stream().anyMatch(l->l.kind==ReceiptParser.Kind.MANAGEMENT&&l.raw.contains("12,345")),"management retained in details");
    }
    public static void main(String[] args){
        for(int variant=0;variant<4;variant++)core(parsed(variant));
        for(String name:new String[]{"小計ノート","小計8点セット","商品計量スプーン","合計ノート","小汁うどん","小豆","小麦","内臓","総菜","16茶","21世紀ブレンド","3Mスポンジ","2in1洗剤","CV-20洗剤","No.1珈琲","レジ袋","取引ノート","取引No.1ガイド","担当者のおすすめ茶"}){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt(name+" 399");
            check(r.items.size()==1&&r.items.get(0).name.equals(name),"inverse legitimate name: "+name);
        }
        for(String heading:new String[]{"小計8点","小汁8点","小訐8点","小 言十 8 点"}){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt("架空豆腐 490\n@245×2\n架空スポンジ359\n@245×2\n"+heading+" 849\n税額合計68\n合計917\n謎のフッター999");
            check(r.items.size()==2&&r.subtotal==849&&r.printedItemCount==8,"joined subtotal heading with count");
            check(r.items.get(0).quantity==2&&r.items.get(1).quantity==null,"mismatched follow-on quantity stays unknown");
        }
        check(ReceiptParser.parseReceipt("小汁200").items.size()==1,"bare soup name not a subtotal without context");
        for(String row:new String[]{"架空店舗No00レジNo00取引No12,345","架空店レジ00取引No12345","担当者No00レジNo00取引No12345","取引No12345","レジNo00","No.12345","架空店レジN000取引N012345","架空受付取引No12345","会計案内レジNo00"})check(ReceiptParser.parseReceipt(row).items.isEmpty(),"concatenated labels never products");
        ReceiptParser.Receipt r=parsed(0);ReceiptReview review=new ReceiptReview(r);
        check(review.items.stream().allMatch(i->!i.selected&&i.grossUnit==null),"all unselected / no gross assumption");
        check(review.items.stream().anyMatch(i->!i.codeConfirmed),"unlabelled code columns need confirmation");
        ReceiptReview.Item named=review.items.get(1);named.grossUnit=86L;check(!named.ready(),"code proposal cannot silently save");named.codeConfirmed=true;check(named.ready(),"explicit code/name confirmation permits otherwise reviewed item");
        check(r.items.get(1).raw.contains("11"),"glued original survives in details");
        for(String name:new String[]{"16茶","21世紀ブレンド","11世紀ブレンド","11茶飲料","500gコーヒー","3Mスポンジ","2in1洗剤","CV-20洗剤"}){
            List<OcrDocument.Region> a=Arrays.asList(box("11",30,30,36),box("架空りんご",150,30,500),box("100",850,34,140),box("22",30,90,36),box("架空石けん",150,90,500),box("200",850,94,140),box(name,30,150,620),box("399V",850,154,140));
            r=ReceiptParser.parseDocument(new OcrDocument("",1200,1600,a));check(r.items.size()==3&&r.items.get(2).name.equals(name),"capacity/model/brand inside product band preserved");
        }
        // No repeated column anchors: an isolated numeric prefix might be a brand/model.
        r=ReceiptParser.parseDocument(new OcrDocument("",1200,1600,Arrays.asList(box("11",30,30,36),box("世紀ブレンド",150,30,500),box("399",850,34,140))));
        check(r.items.get(0).name.equals("11 世紀ブレンド"),"isolated numeric model retained");
        // Text-only codes remain unmodified when no geometric evidence is available.
        r=ReceiptParser.parseReceipt("11架空パン158\n@79×2");check(r.items.get(0).name.equals("11架空パン")&&r.items.get(0).quantity==2,"text-only prefix not deleted blindly");
        r=ReceiptParser.parseReceipt("架空豆腐490\n@245×2\n架空スポンジ 単価359 359");check(r.items.get(1).quantity==null&&r.items.get(1).unitPrice==359,"unit equal to line amount does not silently set quantity one");
        r=ReceiptParser.parseReceipt("架空豆腐490\n@245×2\n架空スポンジ359\n2点");check(r.items.get(1).quantity==null,"bare count not borrowed without matching printed unit");
        r=ReceiptParser.parseReceipt("架空豆腐490\n@245×2\n架空スポンジ 単価359 359\n2点");check(r.items.get(1).quantity==null&&r.items.get(1).unitPrice==359,"bad count does not erase independent matching printed unit");
        r=ReceiptParser.parseReceipt("架空豆腐490\n小汁1008点490\n税額合計39\n合計529");check(r.items.size()==1&&r.subtotal==490&&r.printedItemCount==null,"oversized summary count never becomes an item or a truncated eight");
        System.out.println("PASS ReceiptExtraction "+checks+" checks (fictional corrupted OCR only)");
    }
}
