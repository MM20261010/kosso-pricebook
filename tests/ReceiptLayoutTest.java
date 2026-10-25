package org.privateprice.pricebook;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
public final class ReceiptLayoutTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static OcrDocument.Region box(String s,float x,float y,float w,float h){return new OcrDocument.Region(s,x,y,x+w,y,x+w,y+h,x,y+h);}
    static ReceiptParser.Receipt text(String s){return ReceiptParser.parseReceipt(s);}
    static List<OcrDocument.Region> focus(boolean allQuantities){
        List<OcrDocument.Region> a=new ArrayList<>();
        a.add(box("FICTIVE MARKET",100,20,500,36));a.add(box("2026/10/2518:30",60,80,500,24));a.add(box("全品税抜表示",60,122,250,24));
        String[] names={"架空りんご ※","架空食パン C","架空洗濯洗剤","架空台所洗剤","20枚入","架空スポンジ"};
        int[] prices={480,480,700,398,428,386},units={240,480,350,398,428,386},qty={2,1,2,1,1,1};
        float y=180;
        for(int i=0;i<6;i++){
            if(i==4){a.add(box("架空ごみ袋",60,y,220,24));y+=42;}
            a.add(box(names[i],60,y,360,24));a.add(box(prices[i]+(i<2?"C":"V"),800,y+8,120,24));
            if(allQuantities||qty[i]==2)a.add(box("@"+units[i]+"×"+qty[i],85,y+42,250,24));y+=84;
        }
        for(String s:new String[]{"小訐 2872","8%対象額 960 税77","10%対象額 1912 税191","税額合計 268","台計 3140","購入点数 8点","カード支払 3140","店番号 0000 レジ番号 00"}){a.add(box(s,60,y,850,24));y+=42;}
        return a;
    }
    static OcrDocument document(List<OcrDocument.Region> a){return new OcrDocument("flat OCR text is not used for positioned parsing",1200,1600,a);}
    static ReceiptParser.Receipt parse(List<OcrDocument.Region> a){return ReceiptParser.parseDocument(document(a));}
    static byte[] encoded(){
        byte[] text="牛乳 200".getBytes(StandardCharsets.UTF_8);ByteBuffer b=ByteBuffer.allocate(20+text.length+36+text.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x3642504b).putInt(1000).putInt(1000).putInt(text.length).put(text).putInt(1);
        for(float v:new float[]{20,20,200,20,200,50,20,50})b.putFloat(v);b.putInt(text.length).put(text);return b.array();
    }
    static void rejects(Runnable f,String name){try{f.run();throw new AssertionError(name);}catch(IllegalArgumentException e){checks++;}}
    public static void main(String[] args){
        List<OcrDocument.Region> regions=focus(true);Collections.shuffle(regions,new Random(6));ReceiptParser.Receipt r=parse(regions);
        check(r.items.size()==6,"six products, no subtotal/payment candidates");
        check(r.itemSum()==2872,"line total 2872");check(r.knownQuantitySum()==8&&r.unknownQuantityCount()==0,"explicit eight units");
        int[] expectedQuantity={2,1,2,1,1,1};long[] expectedUnit={240,480,350,398,428,386};
        for(int i=0;i<6;i++)check(Integer.valueOf(expectedQuantity[i]).equals(r.items.get(i).quantity)&&Long.valueOf(expectedUnit[i]).equals(r.items.get(i).unitPrice),"quantity/unit attached to its own product");
        check(r.items.stream().filter(c->Integer.valueOf(2).equals(c.quantity)).count()==2,"two distinct double purchases");
        check(r.items.stream().allMatch(c->c.taxRate==null),"C/V never imply a tax rate");
        check(r.items.get(0).name.equals("架空りんご")&&r.items.get(1).name.equals("架空食パン"),"separate name markers");
        check(r.items.get(4).name.equals("架空ごみ袋 20枚入")&&r.items.get(4).quantity==1,"wrapped specification is not quantity");
        check(r.items.get(0).unitPrice==240&&r.items.get(2).unitPrice==350,"below-item unit rows attached geometrically");
        check(r.items.stream().allMatch(c->c.amountBasis==ReceiptParser.AmountBasis.EXCLUSIVE),"net line amount basis");
        check(r.subtotal==2872&&r.total==3140&&r.taxAmount==268,"printed aggregates distinct");
        check(r.taxBreakdowns.size()==2&&r.taxBreakdowns.get(0).base==960&&r.taxBreakdowns.get(1).base==1912,"mixed tax bases");
        check(r.taxBreakdowns.get(0).tax==77&&r.taxBreakdowns.get(1).tax==191,"mixed printed tax amounts, no rounding inference");
        check(r.subtotalUncertain&&r.totalUncertain,"OCR heading variants remain unconfirmed");
        check(r.reconciliation().contains("金額一致")&&r.reconciliation().contains("要確認"),"arithmetic equality does not confirm fuzzy headings");
        check(r.store.equals("FICTIVE MARKET")&&r.date.equals("2026-10-25"),"English store and concatenated date");
        List<OcrDocument.Region> rotated=new ArrayList<>();double angle=Math.toRadians(3);
        for(OcrDocument.Region region:regions){float[] p=new float[8];for(int i=0;i<4;i++){p[2*i]=(float)(region.x(i)*Math.cos(angle)-region.y(i)*Math.sin(angle)+100);p[2*i+1]=(float)(region.x(i)*Math.sin(angle)+region.y(i)*Math.cos(angle));}rotated.add(new OcrDocument.Region(region.text,p));}
        r=parse(rotated);check(r.items.size()==6&&r.itemSum()==2872&&r.knownQuantitySum()==8,"deskewed geometry preserves row attachment");
        r=parse(focus(false));check(r.printedItemCount==8&&r.unknownQuantityCount()==4&&r.knownQuantitySum()==4,"printed total must not fill four missing quantities");
        check(r.reconciliation().contains("補完しません"),"unknown quantity warning");
        r=text("STORE: FICTIVE FOODS\n税抜表示\n牛乳 200C\n小計 200\n消費税額 16\n合計 216");
        check(r.store.equals("FICTIVE FOODS")&&r.itemSum()==200&&r.items.get(0).quantity==null,"explicit English store; no implicit quantity one");
        check(r.reconciliation().contains("金額一致")&&r.items.get(0).amountBasis==ReceiptParser.AmountBasis.EXCLUSIVE,"exclusive reconciliation");
        check(text("FICTIVE FOODS\n2026/10/25\n牛乳 200").store.equals("FICTIVE FOODS"),"English header anchored before date");
        check(text("APPLE\n200").store.isEmpty(),"plain English product is not a store");
        r=text("税込表示\nビタミンC 200V\n合計 200\n内消費税 14");check(r.items.get(0).name.equals("ビタミンC")&&r.reconciliation().contains("重ねて加算しません"),"attached product letter preserved and inclusive tax not added twice");
        r=text("牛乳 200 C 8%\n合計 216");check(r.items.get(0).taxRate==8&&r.items.get(0).amountBasis==ReceiptParser.AmountBasis.UNKNOWN,"only explicit percent gives tax candidate");
        r=text("税抜表示\n牛乳 200C\n小計 300\n消費税額 16\n合計 216");check(r.reconciliation().contains("明細計と小計が不一致")&&r.reconciliation().contains("小計＋税額"),"subtotal discrepancy detected even if line sum plus tax equals total");
        r=text("税抜表示\n牛乳 200\n8%対象額 200 税16\n税額合計 20\n合計 220");check(r.reconciliation().contains("税率別税額"),"tax subtotal mismatch");
        r=text("牛乳 200\n購入点数 3点");check(r.printedItemCount==3&&r.items.get(0).quantity==null,"count summary separated");
        check(text("店番号 123\n店舗No. 456\nSTORE NO 0000\nREG: 00\nレジ番号 123\nカード支払 3140\n小言十2872\n台計3140").items.isEmpty(),"metadata and visual heading variants excluded");
        check(text("牛乳 100000000000001\n牛乳 12345678").items.isEmpty(),"do not truncate oversized money into a plausible suffix");
        check(text("購入点数 1008点").printedItemCount==null,"oversized count not truncated");
        r=parse(Arrays.asList(box("牛乳",50,50,200,24),box("200C",800,250,120,24)));check(r.items.isEmpty(),"distant price not attached");
        r=parse(Arrays.asList(box("牛乳 200",50,50,200,24),box("@100×2",85,250,200,24)));check(r.items.get(0).quantity==null,"distant quantity not attached");
        r=parse(Arrays.asList(box("牛乳 200",50,50,200,24),box("牛乳 200",52,51,200,24)));check(r.items.isEmpty()&&!r.warnings.isEmpty(),"overlapping detections are not duplicated");
        r=text("税抜表示\n牛乳 (税込)200\n消費税 16\n合計216");check(r.reconciliation().contains("混在")&&!r.reconciliation().contains("金額一致"),"mixed line bases are not reconciled as net");
        r=text("牛乳 200C\n@100×2\n@200×1\n@100×2");check(r.items.get(0).quantity==null&&r.items.get(0).unitPrice==null,"conflicting detail cannot be silently reconfirmed");
        r=text("ビタミンC 200C");check(r.items.get(0).name.equals("ビタミンC")&&r.items.get(0).nameSuffixUncertain,"glued marker remains a name uncertainty");
        r=parse(Arrays.asList(box("牛乳 200",50,50,300,24),box("洗剤 300",600,50,300,24)));check(r.items.isEmpty(),"independent priced columns not collapsed into one product");
        check(text("REG NO. 03\nPOS NUMBER 01\n店番 0000\n電子支払 3140").items.isEmpty(),"additional administrative prefixes");
        check(text("OK\n2026/10/25\n牛乳200").store.equals("OK"),"short English store header");
        r=text("牛乳200\n商品值引 -20\n合計180");check(r.hasDiscount&&r.reconciliation().contains("未確定"),"OCR discount variant does not imply a tax allocation");
        r=text("牛乳200\n調整 -20\n合計180");check(r.hasDiscount&&r.reconciliation().contains("未確定"),"unclassified negative adjustment keeps reconciliation pending");
        byte[] many=encoded();ByteBuffer.wrap(many).order(ByteOrder.LITTLE_ENDIAN).putInt(16+"牛乳 200".getBytes(StandardCharsets.UTF_8).length,81);rejects(()->OcrDocument.decode(many),"wire region count bounded");
        rejects(()->OcrDocument.decode(new byte[262145]),"wire size bounded");
        r=text("牛乳200 8% 10%");check(r.items.get(0).taxRate==null&&!r.warnings.isEmpty(),"conflicting printed rates stay unknown");
        r=text("牛乳\n@100×2\n@150×2 300C");check(r.items.get(0).quantity==null&&r.items.get(0).unitPrice==null,"pending and inline detail conflicts stay unknown");
        check(text("牛乳100.50").items.isEmpty(),"decimal price not misread as integer suffix");
        r=text("小豆 200\n小麦 300\n内臓 400\n総菜 500\n合計1400");check(r.items.size()==4&&r.itemSum()==1400,"short product names resembling aggregates are preserved");
        OcrDocument decoded=OcrDocument.decode(encoded());check(decoded.regions.size()==1&&ReceiptParser.parseDocument(decoded).itemSum()==200,"binary document decoding reaches parser");
        byte[] bad=encoded();rejects(()->OcrDocument.decode(Arrays.copyOf(bad,25)),"truncated payload rejected");
        byte[] nan=encoded();ByteBuffer.wrap(nan).order(ByteOrder.LITTLE_ENDIAN).putFloat(20+"牛乳 200".getBytes(StandardCharsets.UTF_8).length,Float.NaN);rejects(()->OcrDocument.decode(nan),"nonfinite coordinates rejected");
        byte[] extra=Arrays.copyOf(encoded(),encoded().length+1);rejects(()->OcrDocument.decode(extra),"trailing payload rejected");
        byte[] utf=encoded();utf[16]=(byte)0xff;rejects(()->OcrDocument.decode(utf),"malformed UTF8 rejected");
        rejects(()->new OcrDocument("",1200,1600,Collections.nCopies(81,box("牛乳",1,1,20,20))),"region count bounded");
        rejects(()->document(Arrays.asList(box("牛乳",-1,2,20,20))),"coordinate bounds checked");
        System.out.println("PASS ReceiptLayout "+checks+" checks (synthetic only)");
    }
}
