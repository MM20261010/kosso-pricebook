package org.privateprice.pricebook;
import java.util.*;
/** Stage ablations use fictional text/positions. Missing OCR tokens never prove missing paper print. */
public final class ReceiptPipelineTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static OcrDocument.Region b(String s,float x,float y,float width){return ReceiptAuditScenario.box(s,x,y,width);}
    static ReceiptParser.Receipt positioned(OcrDocument.Region... regions){return ReceiptParser.parseDocument(new OcrDocument("fictional only",1200,1600,Arrays.asList(regions)));}
    static ReceiptParser.Decision row(ReceiptParser.Receipt r,String raw){for(ReceiptParser.Decision d:r.decisions)if(d.raw.equals(raw))return d;throw new AssertionError("fictional row missing");}
    static void allow(Set<String> allowed,String key,Enum<?>[] values){for(Enum<?> value:values)allowed.add(key+"="+value.name());}
    static void noData(String report){
        check(!report.matches("(?s).*[0-9]{2,}.*")&&!report.contains("架空")&&!report.contains("SECRET")&&!report.contains("2026")&&!report.contains("candidates="),"copy contains no variable digits, identity, raw or free-form data");
        Set<String> allowed=new HashSet<>(List.of("SCHEMA=OCR_PIPELINE_V1","[CANDIDATE]","[ROW]","REPORT=TRUNCATED"));
        allow(allowed,"ROUTE",ReceiptDiagnostics.Route.values());allow(allowed,"UNIT",ReceiptDiagnostics.Known.values());allow(allowed,"QUANTITY",ReceiptDiagnostics.Known.values());
        allow(allowed,"RELATION",ReceiptDiagnostics.Relation.values());allow(allowed,"KIND",ReceiptParser.Kind.values());allow(allowed,"REASON",ReceiptDiagnostics.Reason.values());
        for(String key:new String[]{"REGION_TOKEN","ROW_TOKEN"})allow(allowed,key,ReceiptDiagnostics.Token.values());
        allow(allowed,"LAYOUT",ReceiptDiagnostics.Layout.values());allow(allowed,"EXTRACTION",ReceiptDiagnostics.Extraction.values());allow(allowed,"ASSOCIATION",ReceiptDiagnostics.Association.values());
        allow(allowed,"SUMMARY_STOP",ReceiptDiagnostics.SummaryStop.values());allow(allowed,"COUNT_CHECK",ReceiptDiagnostics.CountCheck.values());
        for(String key:new String[]{"KNOWN_STEM","STRONG_HEADING","PLAUSIBLE_HEADING","COUNT_HINT","AMOUNT_COLUMN","SUM_MATCH","FUTURE_FOOTER"})allow(allowed,key,ReceiptDiagnostics.Gate.values());
        check(Arrays.stream(report.split("\\n")).allMatch(allowed::contains),"every copied field and value belongs to a closed enum vocabulary");
    }
    static ReceiptParser.Receipt summary(String heading,String price){
        List<OcrDocument.Region> a=new ArrayList<>();
        for(OcrDocument.Region p:ReceiptAuditScenario.fixture(4).regions){
            if(p.text.equals("小鈄8占"))a.add(b(heading,70,p.y(0),300));
            else if(p.text.equals("2872"))a.add(b(price,850,p.y(0),140));
            else if(p.text.contains("税")&&!p.text.equals("全品税抜表示")||p.text.startsWith("合計")||p.text.contains("支払"))continue;
            else a.add(p);
        }
        return ReceiptParser.parseDocument(new OcrDocument("fictional summary ablation",1200,1600,a));
    }
    public static int run(){
        checks=0;
        for(String text:new String[]{"架空スポンジ359\n単価359円","架空パン158\n単価79円"}){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt(text);ReceiptParser.Candidate c=r.items.get(0);
            check(c.unitPrice!=null&&c.quantity==null,"standalone explicit unit retained without invented count");
            check(r.decisions.get(1).audit.extraction==ReceiptDiagnostics.Extraction.UNIT_ONLY&&r.decisions.get(1).audit.association==ReceiptDiagnostics.Association.BOUND,"unit extraction and actual binding independently traced");
        }
        for(String text:new String[]{"架空パン158\n数量2点","架空パン158\n個数2","架空パン158\n数量 2"}){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt(text);check(r.items.size()==1&&Integer.valueOf(2).equals(r.items.get(0).quantity)&&r.items.get(0).unitPrice==null,"labelled count is explicit; unit not derived from division");
        }
        for(String text:new String[]{"架空パン158\n単価79円\n数量2点","架空パン158\n数量2点\n単価79円"}){
            ReceiptParser.Receipt r=ReceiptParser.parseReceipt(text);check(r.items.get(0).quantity==2&&r.items.get(0).unitPrice==79,"complementary explicit separate rows attach only to own item");
        }
        ReceiptParser.Receipt r=positioned(b("架空パン",70,60,250),b("単価 79",380,60,140),b("数量 2",580,60,140),b("158C",850,64,140));
        check(r.items.size()==1&&r.items.get(0).name.equals("架空パン")&&r.items.get(0).unitPrice==79&&r.items.get(0).quantity==2,"separate labelled columns do not become overlapping products");
        ReceiptDiagnostics.RowAudit a=r.decisions.get(0).audit;
        check(a.recognizedRegions==ReceiptDiagnostics.Token.COMPLEMENTARY_FIELDS&&a.layout==ReceiptDiagnostics.Layout.MERGED_REGIONS&&a.extraction==ReceiptDiagnostics.Extraction.PAIR&&a.association==ReceiptDiagnostics.Association.INLINE_BOUND,"raw/row/extraction/binding traced separately for split columns");
        r=positioned(b("架空パン158",70,60,600),b("@79×2",70,300,260));
        check(r.items.get(0).quantity==null,"far detail remains unbound");
        ReceiptParser.Decision distant=row(r,"@79×2");
        check(distant.audit.recognizedRegions==ReceiptDiagnostics.Token.PAIR&&distant.audit.extraction==ReceiptDiagnostics.Extraction.PAIR&&distant.audit.association==ReceiptDiagnostics.Association.GEOMETRY_REJECTED,"recognized and extracted pair can fail geometry independently");
        check(distant.candidates.isEmpty()&&ReceiptDiagnostics.attemptsForCandidate(r,1).contains(distant),"failed association is a separate attempt, never a successful link");
        String report=ReceiptDiagnostics.anonymousReceipt(r);check(report.contains("ASSOCIATION=GEOMETRY_REJECTED")&&report.contains("RELATION=ATTEMPTED_NOT_LINKED"),"single copy includes rows previously absent from candidate reports");noData(report);
        r=positioned(b("架空パン158",70,60,600),b("@79",70,110,160),b("×2",300,170,150));
        check(r.items.get(0).quantity==null,"split incomplete OCR regions not speculatively stitched");
        check(row(r,"@79").audit.recognizedRegions==ReceiptDiagnostics.Token.PARTIAL_MARKER&&row(r,"@79").audit.extraction==ReceiptDiagnostics.Extraction.NONE,"partial region is distinguished from full extracted pair");
        r=ReceiptParser.parseReceipt("架空パン158\n@79×3");
        check(r.decisions.get(1).audit.extraction==ReceiptDiagnostics.Extraction.PAIR&&r.decisions.get(1).audit.association==ReceiptDiagnostics.Association.AMOUNT_MISMATCH&&r.items.get(0).quantity==null,"arithmetic rejection is not OCR absence");
        r=ReceiptParser.parseReceipt("架空パン158\n2点");check(r.items.get(0).quantity==null&&r.decisions.get(1).audit.association==ReceiptDiagnostics.Association.BARE_COUNT_UNPROVEN,"bare count remains unproven");
        r=summary("小辻8点","2872");
        check(r.items.size()==6&&r.subtotal==null,"known no-footer summary is held without confirming amounts");
        check(r.decisions.stream().anyMatch(d->d.audit.summary!=null&&d.audit.summary.stop==ReceiptDiagnostics.SummaryStop.NO_FOOTER_HELD&&d.audit.summary.strongHeading==ReceiptDiagnostics.Gate.PASS),"actual hold gate and known heading exported");
        for(String heading:new String[]{"小X8点","小辻8点:","小辻8点セット","小豆8点"}){
            r=summary(heading,"2872");
            check(r.items.size()==7,"unproven heading is not deleted on sum coincidence");
            ReceiptParser.Decision d=r.decisions.stream().filter(x->x.kind==ReceiptParser.Kind.ITEM&&x.audit.summary!=null&&x.audit.summary.stop==(heading.endsWith("セット")?ReceiptDiagnostics.SummaryStop.NO_COUNT_HINT:ReceiptDiagnostics.SummaryStop.HEADING_NOT_ALLOWED)).findFirst().orElseThrow();
            check(d.audit.summary.strongHeading==ReceiptDiagnostics.Gate.FAIL&&d.audit.summary.knownStem==(heading.equals("小X8点")||heading.equals("小豆8点")?ReceiptDiagnostics.Gate.FAIL:ReceiptDiagnostics.Gate.PASS),"unknown label versus known-label formatting is distinguished without copying text");
            check(r.items.get(1).unitPrice==79&&r.items.get(1).quantity==2&&r.items.get(3).unitPrice==245&&r.items.get(3).quantity==2,"both protected pairs survive heading ablations");
        }
        r=summary("小辻2点","2872");check(r.items.size()==7&&r.decisions.stream().anyMatch(d->d.audit.summary!=null&&d.audit.summary.stop==ReceiptDiagnostics.SummaryStop.COUNT_CONTRADICTION),"count rejection has its own fixed stop code");
        r=summary("小辻8点","2873");check(r.items.size()==7&&r.decisions.stream().anyMatch(d->d.audit.summary!=null&&d.audit.summary.stop==ReceiptDiagnostics.SummaryStop.SUM_CONTRADICTION),"sum rejection has its own fixed stop code");
        for(int size:new int[]{6,20,50}){
            List<OcrDocument.Region> fields=new ArrayList<>();
            for(OcrDocument.Region p:ReceiptPairingTest.longReceipt(size,false).regions){
                boolean replaced=false;
                for(int i=4;i<Math.min(size,8);i++){
                    String name="架空商品"+(char)('A'+i/26)+(char)('A'+i%26);
                    if(p.text.startsWith(name+" @")){
                        fields.add(b(name,70,p.y(0),280));fields.add(b("単価 "+(100+i),400,p.y(0),150));
                        fields.add(b("数量 1",600,p.y(0),140));fields.add(b(Integer.toString(100+i),850,p.y(0)+4,140));replaced=true;break;
                    }
                }
                if(!replaced)fields.add(p);
            }
            Collections.shuffle(fields,new Random(size));
            ReceiptParser.Receipt longResult=ReceiptParser.parseDocument(new OcrDocument("fictional labelled long receipt",1200,ReceiptPairingTest.longReceipt(size,false).height,fields));
            check(longResult.items.size()==size,"varied labelled long receipt keeps exact product count");
            check(longResult.items.get(0).unitPrice==79&&longResult.items.get(0).quantity==2&&longResult.items.get(1).unitPrice==245&&longResult.items.get(1).quantity==2,"protected pairs survive mixed long layout");
            for(int i=4;i<Math.min(size,8);i++)check(longResult.items.get(i).unitPrice==100+i&&longResult.items.get(i).quantity==1,"explicit printed unit/count from independent labelled columns");
            check(longResult.reconciliation().contains("金額一致"),"mixed long columns reconcile without inferring tax per item");
            check(ReceiptDiagnostics.anonymousReceipt(longResult).length()<61000,"long receipt diagnostic stays bounded");
        }
        r=ReceiptExtractionTest.parsed(0);
        check(r.unknownQuantityCount()==4,"four missing printed measures remain unknown in baseline synthetic case");
        check(r.decisions.stream().filter(d->d.kind==ReceiptParser.Kind.ITEM).filter(d->d.audit.extraction==ReceiptDiagnostics.Extraction.NONE).count()==6,"item row itself need not carry below-row unit pair");
        report=ReceiptDiagnostics.anonymousReceipt(r);noData(report);
        check(!report.contains("NOT_PRINTED")&&!report.contains("OCR_FAILED"),"no token observed cannot decide paper absence versus OCR failure");
        r.decisions.add(new ReceiptParser.Decision(991,ReceiptParser.Kind.ITEM,"SECRET_UPPERCASE_IDENTIFIER","dateAnchor=2026; sumMatches=SECRET",null,"SECRET_REGIONS","SECRET_RAW 123456","SECRET_NORMALIZED",List.of(1)));
        report=ReceiptDiagnostics.anonymousReceipt(r);check(report.contains("REASON=UNAVAILABLE")&&!report.contains("SECRET"),"even uppercase invented reason and evidence cannot enter copy");noData(report);
        check(ReceiptDiagnostics.anonymousCandidate(r,0).isEmpty()&&ReceiptDiagnostics.anonymousReceipt(null).isEmpty(),"invalid diagnostic requests do not expose other records");
        for(int i=0;i<500;i++)r.decisions.add(new ReceiptParser.Decision(i,ReceiptParser.Kind.ITEM,"ITEM_TEXT_AMOUNT","SECRET",null,"SECRET","SECRET","SECRET",List.of(1)));
        report=ReceiptDiagnostics.anonymousReceipt(r);check(report.length()<61000&&report.contains("REPORT=TRUNCATED"),"large clipboard report bounded by records and bytes");
        System.out.println("PASS ReceiptPipeline "+checks+" checks (fictional stage ablations only)");return checks;
    }
    public static void main(String[] args){run();}
}
