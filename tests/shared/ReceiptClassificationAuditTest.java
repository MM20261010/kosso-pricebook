package org.privateprice.pricebook;
import java.util.*;
/** Targeted investigation of decisions and ablations, not a claim of device accuracy. */
public final class ReceiptClassificationAuditTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static int run(){
        checks=0;
        ReceiptParser.Receipt r=ReceiptParser.parseDocument(ReceiptAuditScenario.fixture(4));
        check(r.analysisMode.equals("POSITIONED"),"positioned route exposed");
        check(r.items.size()==6&&r.itemSum()==2872,"combined perturbation excludes both non-products");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("PRE_DATE_HEADER_PRICE_HELD")&&d.candidates.isEmpty()),"pre-date reason excludes ID regardless of label accuracy");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("SUBTOTAL_COLUMN_COUNT_SUM_FOOTER")&&d.kind==ReceiptParser.Kind.SUBTOTAL&&d.candidates.isEmpty()),"subtotal decision uses column/count/sum/footer");
        check(r.subtotal==2872&&r.subtotalUncertain&&r.printedItemCount==8&&r.taxAmount==268&&r.total==3140,"structural subtotal remains unconfirmed");
        check(r.decisions.stream().anyMatch(d->d.raw.equals("@79×2")&&d.candidates.contains(2)),"79 pair trace leads to its item");
        check(r.decisions.stream().anyMatch(d->d.raw.equals("@245×2")&&d.candidates.contains(4)),"245 pair trace leads to its item");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("UNIT_QUANTITY_AMOUNT_MISMATCH")&&d.candidates.isEmpty()),"rejected misplaced detail is visible");
        check(r.decisions.stream().allMatch(d->!d.regions.isEmpty()&&Double.isFinite(d.top)),"region IDs and coordinates retained locally");
        List<OcrDocument.Region> a=new ArrayList<>();for(OcrDocument.Region p:ReceiptAuditScenario.fixture(4).regions)a.add(p.text.equals("2872")?ReceiptAuditScenario.box("2873",850,p.y(0),140):p);
        r=ReceiptParser.parseDocument(new OcrDocument("fictional mismatch",1200,1600,a));
        check(r.items.size()==6&&r.subtotal==null&&r.decisions.stream().anyMatch(d->d.reason.equals("SUMMARY_LIKE_AMOUNT_MISMATCH_HELD")),"mismatching summary held without making up subtotal");
        r=ReceiptParser.parseReceipt("FICTIVE MARKET\n2026/10/27\n架空パン158\n@79×2");
        check(r.analysisMode.equals("TEXT")&&r.decisions.stream().allMatch(d->Double.isNaN(d.left)),"text route cannot claim geometry");
        r=ReceiptParser.parseReceipt("架空パン158\n@79×2\n消費税12\n不明な管理案内12345\n合計170");
        check(r.items.size()==1&&r.decisions.stream().anyMatch(d->d.reason.equals("AFTER_SUMMARY_HELD")),"monetary tax marks footer");
        for(String name:new String[]{"小豆8個","小計ノート","レジ袋","16茶","取引No.1ガイド"}){
            r=ReceiptParser.parseDocument(new OcrDocument("fictional inverse",1200,1600,Arrays.asList(ReceiptAuditScenario.box("2026/10/27",70,30,400),ReceiptAuditScenario.box(name,70,100,400),ReceiptAuditScenario.box("399",850,104,140))));
            check(r.items.size()==1&&r.items.get(0).name.equals(name),"legitimate product name retained");
        }
        List<OcrDocument.Region> longHeader=new ArrayList<>();
        for(OcrDocument.Region p:ReceiptAuditScenario.fixture(4).regions){if(p.y(0)>=106){float[] xy=new float[8];for(int n=0;n<4;n++){xy[n*2]=p.x(n);xy[n*2+1]=p.y(n)+250;}longHeader.add(new OcrDocument.Region(p.text,xy));}else longHeader.add(p);}
        for(int i=0;i<7;i++)longHeader.add(ReceiptAuditScenario.box("架空のご案内",70,100+i*32,400));
        r=ReceiptParser.parseDocument(new OcrDocument("fictional long header",1200,1800,longHeader));check(r.items.size()==6&&r.decisions.stream().anyMatch(d->d.reason.equals("PRE_DATE_HEADER_PRICE_HELD")),"header date is not restricted to first six OCR rows");
        List<OcrDocument.Region> footerDate=new ArrayList<>();String[] goods={"架空牛乳","架空パン","レジ袋"};for(int i=0;i<3;i++){footerDate.add(ReceiptAuditScenario.box(goods[i],70,30+i*50,400));footerDate.add(ReceiptAuditScenario.box("200",850,34+i*50,140));}footerDate.add(ReceiptAuditScenario.box("2026/10/27",70,220,400));
        r=ReceiptParser.parseDocument(new OcrDocument("fictional footer date",1200,1600,footerDate));check(r.items.size()==3,"a footer date does not quarantine an established product block");
        r=ReceiptParser.parseReceipt(ReceiptAuditScenario.text(4));check(r.items.size()==6&&r.analysisMode.equals("TEXT")&&r.subtotal==null&&r.decisions.stream().anyMatch(d->d.reason.equals("SUMMARY_LIKE_NO_POSITION_HELD")),"same content after geometry loss is held instead of a summary item");
        check(ReceiptParser.visibleText("取\u200b引").equals("取〈U+200B〉引"),"local text display makes invisible formatting explicit");
        System.out.println("PASS ReceiptClassificationAudit "+checks+" targeted checks");return checks;
    }
    public static void main(String[] args){run();ReceiptClassificationProbe.main(new String[]{"current"});}
}
