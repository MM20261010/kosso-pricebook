package org.privateprice.pricebook;
import java.util.*;
public final class ReceiptUnitTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void rejects(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException e){checks++;}}
    static OcrDocument.Region box(String s,float x,float y,float w){return new OcrDocument.Region(s,x,y,x+w,y,x+w,y+24,x,y+24);}
    static OcrDocument fixture(){
        List<OcrDocument.Region> a=new ArrayList<>();
        a.add(box("FICTIVE MARKET",70,20,500));a.add(box("2026/10/2719:00",70,62,500));a.add(box("全品税抜表示",70,104,300));
        String[] names={"架空もやし 200g","架空パン 6枚入","架空コーヒー 500g","架空豆腐 300g","架空洗剤 CV-20","架空ビタミンC"};
        int[] lines={6,158,1460,490,399,359},units={6,79,1460,245,399,359},q={1,2,1,2,1,1};
        a.add(box("商品コード",20,140,140));a.add(box("商品名",190,140,200));a.add(box("行金額",850,140,140));
        float y=190;
        for(int i=0;i<6;i++){
            a.add(box("00000"+(i+1),20,y,140));a.add(box(names[i],190,y,420));a.add(box(lines[i]+(i<3?"C":"V"),850,y+5,140));
            a.add(box("@"+units[i]+"×"+q[i],190,y+40,300));y+=80;
        }
        for(String s:new String[]{"小訐 2872","8%消費税 77","10%消費税 191","消費税額 268","台計 3140","購入点数 8点","取引番号 00000000","レジ番号 00","担当者番号 00","カード支払 3140"}){a.add(box(s,70,y,800));y+=40;}
        return new OcrDocument("fictional positioned data only",1200,1600,a);
    }
    public static void main(String[] args){
        ReceiptParser.Receipt r=ReceiptParser.parseDocument(fixture());
        check(r.items.size()==6,"six products");check(r.itemSum()==2872,"line sum");check(r.knownQuantitySum()==8,"eight units");
        check(r.subtotal==2872&&r.taxAmount==268&&r.total==3140,"aggregates");
        long[] lines={6,158,1460,490,399,359},units={6,79,1460,245,399,359};int[] quantities={1,2,1,2,1,1};
        for(int i=0;i<6;i++){
            ReceiptParser.Candidate c=r.items.get(i);check(c.price==lines[i]&&c.unitPrice==units[i]&&c.quantity==quantities[i],"matching own unit and line");
            check(!c.name.contains("00000")&&c.raw.contains("00000"),"geometric code separation retains original in details");
            check(c.taxRate==null,"markers never assign rates");
        }
        check(r.items.get(1).name.equals("架空パン 6枚入")&&r.items.get(1).specification.equals("6枚入"),"specification not purchase quantity");
        check(r.items.get(4).name.equals("架空洗剤 CV-20")&&r.items.get(5).name.equals("架空ビタミンC"),"model numbers and vitamin C preserved");
        check(r.lines.stream().filter(l->l.kind==ReceiptParser.Kind.MANAGEMENT).count()>=9,"management classified separately");
        ReceiptReview review=new ReceiptReview(r);check(review.items.stream().noneMatch(i->i.selected||i.grossUnit!=null||i.taxRate!=null),"initially unselected and unconfirmed");
        ReceiptReview.Item two=review.items.get(1);
        check(two.equation().contains("79円 × 2点 ＝ 158円"),"printed unit and line differ");
        check(ReceiptReview.printedUnit(two)==79,"unit not line total");
        rejects(()->ReceiptReview.confirmedUnit(two,false,null,null),"net not accepted as gross");
        rejects(()->ReceiptReview.confirmedUnit(two,true,8d,null),"rounding required");
        rejects(()->ReceiptReview.confirmedUnit(two,true,null,ReceiptReview.Rounding.DOWN),"rate required");
        check(ReceiptReview.confirmedUnit(two,true,8d,ReceiptReview.Rounding.DOWN)==85,"explicit unit floor");
        check(ReceiptReview.confirmedUnit(two,true,8d,ReceiptReview.Rounding.UP)==86,"explicit unit ceiling");
        check(ReceiptReview.confirmedUnit(review.items.get(3),true,10d,ReceiptReview.Rounding.NEAREST)==270,"decimal half up exact");
        check(two.grossUnit==null&&!two.selected,"calculation does not mutate or save");
        two.grossUnit=85L;check(two.ready()&&two.taxRate==null,"confirmed gross with unknown tax");
        ReceiptReview unknown=new ReceiptReview(ReceiptParser.parseReceipt("牛乳 158\n@79×2"));ReceiptReview.Item item=unknown.items.get(0);
        check(ReceiptReview.confirmedUnit(item,false,null,null)==79,"explicit gross confirmation with unknown tax");
        item.quantity=null;rejects(()->ReceiptReview.printedUnit(item),"no implicit quantity one");
        item.quantity=3;rejects(()->ReceiptReview.printedUnit(item),"unit and line mismatch");
        ReceiptReview.Item fractional=new ReceiptReview(ReceiptParser.parseReceipt("牛乳 159\n2点")).items.get(0);
        rejects(()->ReceiptReview.printedUnit(fractional),"fractional unit not rounded silently");
        check(ReceiptParser.parseReceipt("小計200\n未知商品300\n取引番号123456").items.isEmpty(),"summary boundary blocks footer goods");
        check(ReceiptParser.parseReceipt("商品コード123456\nJANコード000000\n担当者番号123456\n取引番号123456").items.isEmpty(),"administrative labels not products");
        ReceiptParser.Receipt text=ReceiptParser.parseReceipt("000001 架空洗剤 CV-20 399V");check(text.items.get(0).name.startsWith("000001"),"text-only numeric prefix retained when geometry unavailable");
        List<OcrDocument.Region> regions=Arrays.asList(box("架空洗剤",20,20,200),box("CV-20",300,20,140),box("399V",850,20,140));
        check(ReceiptParser.parseDocument(new OcrDocument("",1200,1600,regions)).items.get(0).name.equals("架空洗剤 CV-20"),"separate useful model region preserved");
        item.quantity=2;item.basis=ReceiptParser.AmountBasis.INCLUSIVE;rejects(()->ReceiptReview.confirmedUnit(item,true,8d,ReceiptReview.Rounding.DOWN),"inclusive not double taxed");
        rejects(()->ReceiptReview.confirmedUnit(two,true,Double.NaN,ReceiptReview.Rounding.DOWN),"nonfinite tax rejected");
        ReceiptParser.Receipt variant=ReceiptParser.parseReceipt("消費稅額 268\n牛乳200");check(variant.items.size()==1&&variant.taxAmount==268,"tax glyph variant classified even before summary");
        ReceiptParser.Receipt numericModel=ReceiptParser.parseDocument(new OcrDocument("",1200,1600,Arrays.asList(box("123456",20,20,140),box("架空工具",190,20,300),box("399V",850,20,140))));
        check(numericModel.items.get(0).name.startsWith("123456"),"separate numeric model is retained without explicit code column evidence");
        System.out.println("PASS ReceiptUnit "+checks+" checks (synthetic only)");
    }
}
