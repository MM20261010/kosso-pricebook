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
        for(int candidateNumber=1;candidateNumber<=r.items.size();candidateNumber++){
            final int number=candidateNumber;
            List<ReceiptParser.Decision> related=ReceiptParser.decisionsForCandidate(r,number);
            check(!related.isEmpty()&&related.stream().allMatch(d->d.candidates.contains(number)),"candidate lookup only includes truly linked decisions");
            check(related.stream().anyMatch(d->d.kind==ReceiptParser.Kind.ITEM&&d.reason.equals("ITEM_POSITIONED_AMOUNT")),"positioned item adoption reason is reachable from candidate number");
        }
        check(ReceiptParser.decisionsForCandidate(r,0).isEmpty()&&ReceiptParser.decisionsForCandidate(r,r.items.size()+1).isEmpty()&&ReceiptParser.decisionsForCandidate(null,1).isEmpty(),"invalid candidate cannot expose unrelated rows");

        String copied=ReceiptParser.anonymizedCandidateAudit(r,1);
        check(copied.contains("candidate=1")&&copied.contains("reason=ITEM_POSITIONED_AMOUNT")&&copied.contains("mode=POSITIONED"),"candidate clipboard report gives direct adoption reason");
        check(copied.contains("adminSignals=")&&copied.contains("labelKind="),"only named structural diagnostic fields appear");
        check(!copied.contains("架空")&&!copied.contains("79")&&!copied.contains("2872")&&!copied.contains("regions="),"clipboard excludes names, amounts and region mapping");
        check(ReceiptParser.anonymizedCandidateAudit(r,0).isEmpty(),"cannot copy invalid candidate");
        r.decisions.add(new ReceiptParser.Decision(99,ReceiptParser.Kind.ITEM,"ITEM_POSITIONED_AMOUNT",
                "dateAnchor=private-123; freeText=SECRET123; adminSignals=1; identifierShape=true; labelKind=OTHER",
                null,"SECRET-REGION","SECRET-RAW","SECRET-NORMALIZED",java.util.List.of(1)));
        String guarded=ReceiptParser.anonymizedCandidateAudit(r,1);
        check(guarded.contains("adminSignals=1")&&!guarded.contains("SECRET")&&!guarded.contains("private-123")&&!guarded.contains("freeText"),"extra and malformed metadata cannot leak into clipboard");
        r.decisions.remove(r.decisions.size()-1);
        check(r.items.size()==6&&r.itemSum()==2872,"combined perturbation excludes both non-products");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("PRE_DATE_HEADER_PRICE_HELD")&&d.candidates.isEmpty()),"pre-date reason excludes ID regardless of label accuracy");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("SUBTOTAL_COLUMN_COUNT_SUM_FOOTER")&&d.kind==ReceiptParser.Kind.SUBTOTAL&&d.candidates.isEmpty()),"subtotal decision uses column/count/sum/footer");
        check(r.decisions.stream().anyMatch(d->d.reason.equals("PRE_DATE_HEADER_PRICE_HELD")&&d.evidence.contains("aboveDate=true")&&!d.evidence.contains("12,345")),"failed/passed gate evidence can be reported without original identifiers");
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
        // Independent fictional follow-up to the user's 8-candidate device diagnosis.
        // Date precedes a damaged two-cue administrative row; taxes/footers are missing after the subtotal.
        List<OcrDocument.Region> orphan=new ArrayList<>();
        for(OcrDocument.Region part:ReceiptExtractionTest.fixture(0).regions){
            if(part.text.contains("架空受付")){
                orphan.add(ReceiptAuditScenario.box("店:012V:34取引N0:",30,part.y(0),700));
                orphan.add(ReceiptAuditScenario.box("98765",850,part.y(0)+4,140));
            }else if(part.text.startsWith("小汁")){
                orphan.add(ReceiptAuditScenario.box("小鈄8占",70,part.y(0),300));
                orphan.add(ReceiptAuditScenario.box("2872",850,part.y(0)+4,140));
            }else if(part.text.contains("消費税")||part.text.contains("税額合計")
                    ||part.text.startsWith("合計")||part.text.contains("支払案内")||part.text.contains("カード支払")){
                continue;
            }else orphan.add(part);
        }
        ReceiptParser.Receipt deviceLike=ReceiptParser.parseDocument(new OcrDocument("fictional broken-footer case",1200,1600,orphan));
        check(deviceLike.analysisMode.equals("POSITIONED")&&deviceLike.items.size()==6&&deviceLike.itemSum()==2872,
                "after-date damaged admin and missing-footer subtotal do not become products");
        check(deviceLike.decisions.stream().anyMatch(x->x.raw.contains("店:012")
                &&(x.kind==ReceiptParser.Kind.MANAGEMENT||x.kind==ReceiptParser.Kind.HELD)
                &&x.candidates.isEmpty()),
                "broken after-date administration row is excluded, regardless of which valid exclusion reason applies");
        check(deviceLike.decisions.stream().anyMatch(x->x.reason.equals("SUMMARY_LIKE_NO_FOOTER_HELD")&&x.candidates.isEmpty()),
                "strong compact subtotal with aligned amount is held even without footer");
        check(deviceLike.subtotal==null,"without footer, guessed subtotal amount must remain unset");
        check(deviceLike.items.stream().anyMatch(x->x.price==158&&Integer.valueOf(2).equals(x.quantity)&&Long.valueOf(79).equals(x.unitPrice))
                &&deviceLike.items.stream().anyMatch(x->x.price==490&&Integer.valueOf(2).equals(x.quantity)&&Long.valueOf(245).equals(x.unitPrice)),
                "legitimate 79x2 and 245x2 are unchanged");
        List<OcrDocument.Region> safeProducts=Arrays.asList(
                ReceiptAuditScenario.box("2026/10/27",70,30,400),
                ReceiptAuditScenario.box("レジ袋",70,100,380),ReceiptAuditScenario.box("100",850,104,140),
                ReceiptAuditScenario.box("架空麦",70,170,380),ReceiptAuditScenario.box("200",850,174,140),
                ReceiptAuditScenario.box("小豆8点",70,240,380),ReceiptAuditScenario.box("399",850,244,140),
                ReceiptAuditScenario.box("小計8点セット",70,310,380),ReceiptAuditScenario.box("499",850,314,140));
        ReceiptParser.Receipt safe=ReceiptParser.parseDocument(new OcrDocument("fictional real products",1200,1000,safeProducts));
        check(safe.items.size()==4&&safe.items.stream().anyMatch(x->x.name.equals("レジ袋"))
                &&safe.items.stream().anyMatch(x->x.name.equals("小豆8点"))
                &&safe.items.stream().anyMatch(x->x.name.equals("小計8点セット")),
                "legitimate first product and small/count-labelled products remain items");
        // Bounded lookahead: a far-off tax/total pair must not be lost after >5 OCR rows.
        // Only synthetic labels; original user receipt text is neither known nor used.
        OcrDocument src=ReceiptExtractionTest.fixture(0);
        float subtotalY=0;for(OcrDocument.Region p:src.regions)
            if(p.text.startsWith("小汁"))subtotalY=p.y(0);
        List<OcrDocument.Region> late=new ArrayList<>();
        for(OcrDocument.Region p:src.regions){
            if(p.text.startsWith("小汁")){
                late.add(ReceiptAuditScenario.box("小鈄8占: 2872",70,p.y(0),900));
            }else if(p.y(0)>subtotalY){
                float[] moved=new float[8];
                for(int n=0;n<4;n++){moved[2*n]=p.x(n);moved[2*n+1]=p.y(n)+6*44;}
                late.add(new OcrDocument.Region(p.text,moved));
            }else late.add(p);
        }
        for(int n=1;n<=6;n++)late.add(ReceiptAuditScenario.box("架空の補助行",70,subtotalY+n*44,330));
        ReceiptParser.Receipt distant=ReceiptParser.parseDocument(new OcrDocument("synthetic distant fiscal footer",1200,1800,late));
        check(distant.items.size()==6&&distant.itemSum()==2872&&Long.valueOf(2872).equals(distant.subtotal),
                "subtotal with punctuation OCR artifact and fiscal footer more than five rows later");
        check(distant.decisions.stream().anyMatch(x->x.kind==ReceiptParser.Kind.SUBTOTAL&&x.candidates.isEmpty()),
                "distant genuine tax/total footer supports a separated summary");
        check(distant.items.stream().anyMatch(x->x.price==158&&Integer.valueOf(2).equals(x.quantity)&&Long.valueOf(79).equals(x.unitPrice))
                &&distant.items.stream().anyMatch(x->x.price==490&&Integer.valueOf(2).equals(x.quantity)&&Long.valueOf(245).equals(x.unitPrice)),
                "distant footer does not change known paired quantities");
        List<OcrDocument.Region> genuine=Arrays.asList(
                ReceiptAuditScenario.box("2026/10/27",70,30,400),
                ReceiptAuditScenario.box("架空牛乳",70,100,380),ReceiptAuditScenario.box("100",850,104,140),
                ReceiptAuditScenario.box("架空パン",70,170,380),ReceiptAuditScenario.box("200",850,174,140),
                ReceiptAuditScenario.box("小豆8点",70,240,380),ReceiptAuditScenario.box("300",850,244,140),
                ReceiptAuditScenario.box("税額",70,310,200),ReceiptAuditScenario.box("10",850,314,140),
                ReceiptAuditScenario.box("合計610",70,375,900));
        ReceiptParser.Receipt inverse=ReceiptParser.parseDocument(new OcrDocument("synthetic product with coincidental sum",1200,1000,genuine));
        check(inverse.items.size()==3&&inverse.items.get(2).name.equals("小豆8点"),
                "genuine small-name product retained even when price equals prior-item sum near footer");
        check(ReceiptParser.visibleText("取\u200b引").equals("取〈U+200B〉引"),"local text display makes invisible formatting explicit");
        System.out.println("PASS ReceiptClassificationAudit "+checks+" targeted checks");return checks;
    }
    public static void main(String[] args){run();ReceiptClassificationProbe.main(new String[]{"current"});}
}
