package org.privateprice.pricebook;
import java.util.*;
import java.util.function.UnaryOperator;
import org.privateprice.pricebook.ReceiptParser.*;
import org.privateprice.pricebook.ReceiptDiagnostics.*;

/** Fictional different-value reproductions and equally weighted rejection paths. No private receipt data. */
public final class ReceiptFiscalRescueTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Receipt parse(OcrDocument doc){return ReceiptParser.parseDocument(doc);}
    static TaxBreakdown band(Receipt r,int rate){return r.taxBreakdowns.stream().filter(b->b.rate==rate).findFirst().orElseThrow();}
    static boolean total(Receipt r,long value){return r.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL&&h.item.price==value);}
    static OcrDocument change(OcrDocument source,UnaryOperator<String> rewrite){List<OcrDocument.Region> a=new ArrayList<>();for(OcrDocument.Region r:source.regions){String text=rewrite.apply(r.text);if(text==null)continue;float[] corners=new float[8];for(int n=0;n<4;n++){corners[n*2]=r.x(n);corners[n*2+1]=r.y(n);}a.add(new OcrDocument.Region(text,corners));}return new OcrDocument("fictional fiscal rescue variation",source.width,source.height,a);}
    static void anonymous(Receipt r){int n=ReceiptPipelineTest.checks;String report=ReceiptDiagnostics.anonymousFiscal(r);ReceiptPipelineTest.noData(report);checks+=ReceiptPipelineTest.checks-n;check(report.length()<=7030,"fiscal-only diagnostics stay bounded");}
    public static int run(){
        checks=0;
        for(int mode:new int[]{0,1,2,4,5,6,7}){
            Receipt r=parse(ReceiptFiscalRescueScenario.fixture(mode));long expected=mode==7?3000:3264;
            check(r.items.size()==6&&r.itemSum()==3000,"six fictional products survive the compound fiscal damage");
            check(r.items.get(1).quantity==2&&r.items.get(1).unitPrice==91&&r.items.get(3).quantity==2&&r.items.get(3).unitPrice==315,"new fixture pairs remain printed evidence");
            check(total(r,expected)&&r.total==null,"restored final total is a review candidate, never an observed confirmed amount");
            check(band(r,8).base==(mode==7?2160:1800)&&band(r,10).base==(mode==7?840:1200),"base amounts come from observed numeric tokens, not inverted taxes");
            check(band(r,8).baseSource==ValueSource.PROPOSED&&band(r,10).baseSource==ValueSource.PROPOSED&&band(r,8).taxSource==ValueSource.PRINTED,"role proposals stay distinct from the separately printed tax tokens");
            check(r.amountBasis==AmountBasis.UNKNOWN&&r.basisProposal==(mode==7?AmountBasis.INCLUSIVE:AmountBasis.EXCLUSIVE)&&r.basisSource==ValueSource.PROPOSED,"basis is only proposed from independent base/tax/subtotal observations");
            ReceiptReview model=new ReceiptReview(r);
            check(model.items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4&&model.items.stream().noneMatch(i->i.selected||i.grossUnit!=null),"four provisional ones and unconfirmed paid units are untouched");
            if(mode!=5)check(model.fiscalStatus().contains("候補値の金額関係は一致")&&model.fiscalStatus().contains("候補・要確認"),"candidate arithmetic may match while confirmation remains pending");
            if(mode==5)check(total(r,3000)&&model.fiscalStatus().contains("合計候補"),"competing coherent interpretations are both visible without automatic choice");
            anonymous(r);
        }
        OcrDocument original=ReceiptFiscalRescueScenario.fixture(0);
        for(String wrong:new String[]{"白言十3.274","白言十1.234","白言十3.26","白言十03.264","白言十-3.264","白言十3.264kg","カード白言十3.264","現金3.264","ポイント支払3.264","預り金3.264","お釣り3.264"}){
            Receipt r=parse(change(original,s->s.equals("白言十3.264")?wrong:s));check(r.total==null&&r.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"mismatch, decimals, weights and payment amounts cannot supply a corrected total");
        }
        Receipt noSubtotal=parse(change(original,s->s.equals("小未読8点")||s.equals("3000")?null:s));check(noSubtotal.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"coincidental item-plus-tax equality alone is insufficient");
        Receipt noPayment=parse(change(original,s->s.contains("支払")||s.startsWith("預り")||s.startsWith("お釣り")?null:s));check(noPayment.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"correction requires independent following payment context");
        List<OcrDocument.Region> basisConflict=new ArrayList<>(original.regions);basisConflict.add(ReceiptAuditScenario.box("税抜表示",70,30,300));basisConflict.add(ReceiptAuditScenario.box("税込表示",70,65,300));Receipt conflict=parse(new OcrDocument("fictional conflicting basis declarations",1200,1600,basisConflict));check(conflict.basisConflict&&conflict.basisProposal==AmountBasis.UNKNOWN,"arithmetic cannot erase explicit conflicting basis declarations");
        Receipt onlyPayment=parse(change(original,s->s.equals("白言十3.264")?"白言十":s));check(onlyPayment.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"payment money is not borrowed when total money was not observed");
        OcrDocument split=ReceiptFiscalRescueScenario.fixture(1);float amountBottom=split.regions.stream().filter(r->r.text.equals("3.264")).findFirst().orElseThrow().y(2);List<OcrDocument.Region> touching=new ArrayList<>();
        for(OcrDocument.Region r:split.regions)touching.add(r.text.equals("カード支払2000")?ReceiptAuditScenario.box(r.text,70,amountBottom,920):r);
        check(total(parse(new OcrDocument("fictional touching footer bounds",1200,1600,touching)),3264),"merely touching the following payment box is not a same-line competing heading");
        check(ReceiptFiscalNotation.amount("1.000.000").value==1_000_000&&ReceiptFiscalNotation.amount("1.000.001")==null,"tentative grouping never bypasses monetary bounds");
        Receipt absentBase=parse(change(original,s->s.equals("8%5点1800")||s.equals("10%3点1200")?null:s));check(band(absentBase,8).base==null&&band(absentBase,10).base==null&&absentBase.basisProposal==AmountBasis.UNKNOWN,"taxes are never inverted to invent bases or a global basis");
        Receipt wrongBase=parse(change(original,s->s.equals("8%5点1800")?"8%5点9800":s));check(band(wrongBase,8).base==null&&wrongBase.basisProposal==AmountBasis.UNKNOWN,"arithmetically unsupported base is not forced to fit");
        ReceiptReview pointConflict=new ReceiptReview(parse(change(original,s->s.equals("8%5点1800")?"8%6点1800":s)));check(pointConflict.items.stream().noneMatch(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE),"uncertain base roles do not hide an observed target-count contradiction or permit guessed ones");
        List<OcrDocument.Region> alternative=new ArrayList<>(original.regions);OcrDocument.Region anchor=original.regions.stream().filter(r->r.text.equals("8%5点1800")).findFirst().orElseThrow();alternative.add(ReceiptAuditScenario.box("8%点計5点1801",70,anchor.y(0)+17,920));
        Receipt ambiguous=parse(new OcrDocument("fictional competing target bases",1200,1600,alternative));check(band(ambiguous,8).base==null&&band(ambiguous,8).baseAlternatives.containsAll(List.of(1800L,1801L)),"two observed bases are retained instead of selecting the one that best fits the subtotal");
        for(String name:new String[]{"白言十ノート","日計帳1.234L","対像額ノート","内計メモ","点計シール"}){
            Receipt r=ReceiptParser.parseReceipt("税込表示\n"+name+" 200\n架空文具300\n小計500\nカード支払500");check(r.items.size()==2&&r.items.get(0).name.equals(name),"fiscal-like product names, model numbers and decimals remain faithful");
        }
        Receipt daily=parse(change(original,s->s.equals("白言十3.264")?"営業日報":s));check(daily.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"daily sales reports remain outside purchase totals");
        Receipt rounding=ReceiptParser.parseReceipt("税抜表示\n架空小物10\n小計10\n8%対象3点10税2\n合計12");check(!rounding.taxConflict&&band(rounding,8).uncertain&&rounding.taxDetailsPending,"unknown per-item rounding is reviewable rather than a forced strict mismatch");
        Receipt impossible=ReceiptParser.parseReceipt("税抜表示\n架空小物200\n小計200\n8%対象1点200税99\n合計299");check(impossible.taxConflict,"a contradiction beyond the observed count's rounding envelope remains a conflict");
        ReceiptReview confirm=new ReceiptReview(parse(original));ReceiptReview.Batch batch=confirm.batch();check(batch.summaryBasis==AmountBasis.EXCLUSIVE&&confirm.aggregate().amountBasis==AmountBasis.UNKNOWN,"batch prefill is only a basis proposal");
        batch.acknowledged=true;batch.confirmSummary=true;for(ReceiptReview.Draft d:batch.rows){if(d.held!=null)d.choice=ReceiptReview.Choice.SUBTOTAL;else d.defer=true;}confirm.apply(batch);
        check(confirm.aggregate().basisSource==ValueSource.USER_CONFIRMED&&confirm.aggregate().amountBasis==AmountBasis.EXCLUSIVE&&confirm.items.stream().noneMatch(i->i.selected||i.grossUnit!=null),"explicit aggregate confirmation does not confirm or save item paid prices");
        check(confirm.batch().summaryBasis==AmountBasis.EXCLUSIVE&&!confirm.batch().confirmSummary,"confirmed aggregate basis is retained without silently requesting the same confirmation again");
        ReceiptReview restored=new ReceiptReview(parse(original));ReceiptReview.Batch restore=restored.batch();restore.acknowledged=true;restore.acceptMismatch=true;
        for(ReceiptReview.Draft d:restore.rows){if(d.held==null)d.defer=true;else if(d.held.source.aggregateKind==Kind.SUBTOTAL){d.choice=ReceiptReview.Choice.ITEM;d.quantity="1";d.gross="3000";d.exceptionChecked=true;}else d.choice=ReceiptReview.Choice.DEFER;}restored.apply(restore);
        check(restored.items.size()==7&&restored.aggregate().basisProposal==AmountBasis.UNKNOWN,"restoring a held real product invalidates the old arithmetic basis proposal");
        check(restored.items.stream().limit(6).allMatch(i->i.grossUnit==null&&!i.selected),"restoration does not confirm the other products' paid units");
        ReceiptReview changedBasis=new ReceiptReview(parse(original));changedBasis.items.get(0).basis=AmountBasis.INCLUSIVE;check(changedBasis.aggregate().basisProposal==AmountBasis.UNKNOWN,"a human item-basis correction invalidates an inconsistent old basis proposal");
        Receipt noisy=parse(original);
        for(int n=0;n<20;n++){FiscalAudit f=new FiscalAudit();f.tax=TaxStop.values()[n%TaxStop.values().length];f.geometry=Geometry.values()[n%Geometry.values().length];RowAudit a=new RowAudit(Token.NONE_OBSERVED,Token.NONE_OBSERVED,Layout.AMBIGUOUS,Extraction.NONE,Association.NOT_ATTEMPTED,null,null,f);noisy.decisions.add(0,new Decision(900+n,Kind.TAX,"LABEL_TAX","",null,"","","",List.of(),a));}
        String compact=ReceiptDiagnostics.anonymousFiscal(noisy);
        check(compact.contains("REPORT=TRUNCATED")&&compact.length()<=7030&&compact.split("\\[ROW\\]",-1).length<=13,"diagnostic pressure preserves the explicit bounded-report contract");
        check(compact.contains("TOTAL_STOP=LAYOUT_CANDIDATE")&&compact.contains("BASE_STOP=CANDIDATE")&&compact.indexOf("FISCAL_RECOVERY=TOTAL_FIELDS_PROPOSED")<compact.indexOf("FISCAL_RECOVERY=ORIGINAL"),"successful recovery stages precede noisy original failures even when the report is capped");
        System.out.println("PASS ReceiptFiscalRescue "+checks+" checks (fictional compound damage and rejection paths)");return checks;
    }
    public static void main(String[] args){run();}
}
