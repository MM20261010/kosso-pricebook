package org.privateprice.pricebook;

import java.util.*;

/** Fictional amount/quantity/layout cases. Never fed user OCR, images or history. */
public final class ReceiptPairingTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static OcrDocument.Region box(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    private static boolean pair(ReceiptParser.Receipt r,long price,long unit,int qty){
        return r.items.size()==1&&r.items.get(0).name.equals("架空パン6枚入")&&r.items.get(0).price==price
            &&Long.valueOf(unit).equals(r.items.get(0).unitPrice)&&Integer.valueOf(qty).equals(r.items.get(0).quantity);
    }
    private static final String[] FORMATS={
        "架空パン6枚入 158C\n@ 79 × 2点",
        "架空パン6枚入 79円×2点 158C",
        "架空パン6枚入 79円 × 2点 = 158C",
        "架空パン6枚入 158C\n79円×2点=158円",
        "架空パン6枚入 158C\n単価 79円 数量 2点",
        "架空パン6枚入 158C\n数量 2点 単価 79円",
        "架空パン6枚入 158C\n単価79円数量2点",
        "架空パン6枚入 158C\n数量2点単価79円",
        "架空パン6枚入\n79円×2点=158円",
        "架空パン6枚入\n79×2=158円"
    };
    public static void main(String[] args){
        if(args.length>0&&args[0].equals("survey")){
            for(int i=0;i<FORMATS.length;i++)System.out.println("PAIRING fictional-format-"+i+" matched="+pair(ReceiptParser.parseReceipt(FORMATS[i]),158,79,2));
        }else run();
    }
    public static int run(){
        checks=0;
        for(String text:FORMATS){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt(text);
            check(pair(r,158,79,2),"explicit unit/count format attaches without changing printed name");
            ReceiptReview.Item item=new ReceiptReview(r).items.get(0);
            check(!item.selected&&item.grossUnit==null,"a recognized pair never confirms tax or saves");
        }
        // Separately detected middle column is strong evidence for an unprefixed unit expression.
        List<OcrDocument.Region> regions=Arrays.asList(box("架空パン6枚入",70,60,350),box("79×2点",500,60,180),box("158C",850,64,140));
        ReceiptParser.Receipt r=ReceiptParser.parseDocument(new OcrDocument("",1200,1000,regions));
        check(pair(r,158,79,2),"independent unit column attached to same product, not part of name");
        check(ReceiptParser.anonymizedCandidateAudit(r,1).contains("UNIT=KNOWN\nQUANTITY=KNOWN"),"anonymous audit exposes only pair-known booleans");
        check(r.items.get(0).raw.contains("79×2点"),"original detail stays local in raw");
        r=ReceiptParser.parseReceipt("架空パン6枚入 158C\n@ 79×2\n架空豆腐300g 490V\n@ 245×2\n架空スポンジ359V\n@ 245×2");
        check(r.items.size()==3&&r.items.get(0).unitPrice==79&&r.items.get(0).quantity==2&&r.items.get(1).unitPrice==245&&r.items.get(1).quantity==2,"protected pairs stay with their own lines");
        check(r.items.get(2).quantity==null&&r.items.get(2).unitPrice==null,"mismatching previous pair never flows to 359");
        for(String detail:new String[]{"79円×2点=159円","79円×3点=158円","単価79円 数量3点","79円×2点=1000001円"}){
            r=ReceiptParser.parseReceipt("架空パン6枚入 158C\n"+detail);
            check(r.items.size()==1&&r.items.get(0).quantity==null&&r.items.get(0).unitPrice==null,"conflicting explicit equation stays unconfirmed");
        }
        // A naked count, a capacity or an equal unit/line price is not a purchase-quantity proof.
        for(String text:new String[]{"架空パン6枚入 158C","架空パン6枚入 単価158 158C","架空パン6枚入 158C\n2点","架空パン6枚入 158C\n購入点数2点"}){
            r=ReceiptParser.parseReceipt(text);check(r.items.size()==1&&r.items.get(0).quantity==null,"no quantity inferred from package/count total/equality");
        }
        for(String name:new String[]{"RX79x2","79円ノート","小豆8点","小計8点セット","16茶500ml","小辻8点"}){
            r=ReceiptParser.parseReceipt(name+" 399");check(r.items.size()==1&&r.items.get(0).name.equals(name),"product spelling and numeric models remain intact");
        }
        // All quantities known: a higher point count is a contradiction, not a subtotal proof.
        List<OcrDocument.Region> countConflict=Arrays.asList(box("架空パン @79×2 158C",70,30,920),box("架空豆腐 @245×2 490V",70,90,920),box("小辻8点 648",70,150,920));
        r=ReceiptParser.parseDocument(new OcrDocument("",1200,500,countConflict));
        check(r.items.size()==3&&r.items.get(2).name.equals("小辻8点"),"known count contradiction preserves possible real product");
        for(int size:new int[]{6,20,50}){
            long start=System.nanoTime();r=ReceiptParser.parseDocument(longReceipt(size,false));long elapsed=System.nanoTime()-start;
            check(r.items.size()==size,"long receipt contains exactly intended products");
            long sum=0;
            for(int i=0;i<size;i++){
                long unit=i==0?79:i==1?245:100+i;int qty=i<2?2:1;long line=unit*qty;sum+=line;
                ReceiptParser.Candidate c=r.items.get(i);
                String expected="架空商品"+(char)('A'+i/26)+(char)('A'+i%26)+(i==3?" 500g":"");
                check(c.name.equals(expected),"long receipt name/order/wrapped specification stays faithful");
                check(c.price==line&&Long.valueOf(unit).equals(c.unitPrice)&&Integer.valueOf(qty).equals(c.quantity),"long receipt each product owns its explicit pair");
            }
            check(r.itemSum()==sum&&Long.valueOf(sum).equals(r.subtotal)&&r.total==sum+80&&r.taxAmount==80,"long receipt aggregates are separate even with discount");
            check(!r.hasDiscount&&r.reconciliation().contains("金額一致"),"long net receipt reconciles without inventing item tax");
            check(r.items.stream().noneMatch(c->c.name.contains("取引")||c.name.contains("小計")),"long footer/admin never becomes item");
            check(new ReceiptReview(r).items.stream().noneMatch(i->i.selected||i.grossUnit!=null),"long candidates require explicit confirmation");
            System.out.println("PERF synthetic positioned-parser products="+size+" regions="+longReceipt(size,false).regions.size()+" sample_us="+elapsed/1000+" (not OCR or phone performance)");
        }
        r=ReceiptParser.parseDocument(longReceipt(20,false,true));
        check(r.items.size()==20&&r.hasDiscount&&r.reconciliation().contains("未確定"),"discount allocation remains unconfirmed on long receipt");
        r=ReceiptParser.parseDocument(longReceipt(20,true));
        check(r.items.size()==19,"missing product text cannot steal neighbouring quantity or make guessed item");
        check(r.items.stream().noneMatch(c->c.name.contains("??")),"unreadable row stays outside products");
        check(r.reconciliation().contains("不一致"),"missing row is caught by printed subtotal");
        List<OcrDocument.Region> partial=Arrays.asList(box("架空商品欠損",70,30,350),box("架空パン6枚入 @79×2 158C",70,72,920),box("小計258",70,114,920));
        r=ReceiptParser.parseDocument(new OcrDocument("",1200,500,partial));
        check(pair(r,158,79,2),"a missing earlier price does not change a complete following product name");
        check(r.reconciliation().contains("不一致"),"partially read unpriced product remains detectable through subtotal");
        List<OcrDocument.Region> tooMany=new ArrayList<>();for(int i=0;i<81;i++)tooMany.add(box("架空行",70,i*30,100));
        try{new OcrDocument("",1200,2600,tooMany);throw new AssertionError("region limit was bypassed");}catch(IllegalArgumentException expected){checks++;}
        // Baseline v0.8.6 missing-footer case must continue to hold rather than confirm a subtotal.
        List<OcrDocument.Region> shortReceipt=new ArrayList<>();
        for(OcrDocument.Region p:ReceiptAuditScenario.fixture(4).regions){
            if(p.text.equals("小鈄8占"))shortReceipt.add(box("小辻8点",70,p.y(0),300));
            else if(p.text.contains("税")&&!p.text.equals("全品税抜表示")||p.text.startsWith("合計")||p.text.contains("支払"))continue;
            else shortReceipt.add(p);
        }
        r=ReceiptParser.parseDocument(new OcrDocument("",1200,1600,shortReceipt));
        check(r.items.size()==6&&r.subtotal==null,"no-footer subtotal is held, not promoted to certain amount");
        System.out.println("PASS ReceiptPairing "+checks+" checks (fictional only)");return checks;
    }
    static OcrDocument longReceipt(int count,boolean missing){return longReceipt(count,missing,false);}
    private static OcrDocument longReceipt(int count,boolean missing,boolean discount){
        List<OcrDocument.Region> a=new ArrayList<>();
        a.add(box("FICTIVE MARKET",70,10,500));a.add(box("2026/10/30 18:00",70,48,500));a.add(box("全品税抜表示",70,86,350));
        a.add(box("店No00レジNo00取引No12345",70,124,800));
        long sum=0;float y=170;
        for(int i=0;i<count;i++){
            long unit=i==0?79:i==1?245:100+i;int qty=i<2?2:1;long line=unit*qty;sum+=line;
            String name="架空商品"+(char)('A'+i/26)+(char)('A'+i%26);
            if(missing&&i==8){a.add(box("???",70,y,350));y+=42;continue;}
            if(i==3){a.add(box(name,70,y,350));y+=38;name="500g";}
            if(i<2){a.add(box(name,70,y,360));a.add(box(line+"C",850,y+4,140));y+=38;a.add(box("@ "+unit+" × "+qty+"点",150,y,300));}
            else a.add(box(name+" @ "+unit+"×"+qty+" "+line+(i%2==0?"C":"V"),70,y,920));
            y+=42;
        }
        for(String footer:new String[]{"小計"+(count+2)+"点 "+sum,"商品値引 -10","8%消費税40","10%消費税40","税額合計80","合計"+(sum+(discount?70:80)),"カード支払"+(sum+(discount?70:80)),"レジNo00取引No12345"}){if(footer.startsWith("商品値引")&&!discount)continue;a.add(box(footer,70,y,920));y+=38;}
        Collections.shuffle(a,new Random(count));return new OcrDocument("fictional receipt only",1200,(int)y+40,a);
    }
}
