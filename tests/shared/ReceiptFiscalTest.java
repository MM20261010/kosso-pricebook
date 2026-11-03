package org.privateprice.pricebook;

import java.util.ArrayList;
import java.util.List;
import org.privateprice.pricebook.ReceiptParser.AmountBasis;
import org.privateprice.pricebook.ReceiptParser.Kind;
import org.privateprice.pricebook.ReceiptParser.Receipt;
import org.privateprice.pricebook.ReceiptParser.TaxBreakdown;

/** Fictional text and coordinates. Not a transcription of any personal receipt. */
public final class ReceiptFiscalTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void rejects(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    static Receipt text(String s){return ReceiptParser.parseReceipt(s);}
    static TaxBreakdown band(Receipt r,int rate){return r.taxBreakdowns.stream().filter(b->b.rate==rate).findFirst().orElseThrow();}
    static OcrDocument.Region box(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    public static OcrDocument fixture(boolean split,boolean bad){
        List<OcrDocument.Region> rows=new ArrayList<>();rows.add(box("FICTIVE FOODS",70,20,500));rows.add(box("2026/11/03 19:00",70,58,500));rows.add(box("税抜表示",70,96,400));
        String[] names={"架空もやし200g","架空パン6枚入","架空珈琲500g","架空豆腐300g","架空洗剤CV-20","架空スポンジ"};long[] amounts={6,158,1460,490,399,359};float y=145;
        for(int n=0;n<6;n++){rows.add(box(names[n],70,y,550));rows.add(box(amounts[n]+"C",850,y+4,140));if(n==1||n==3){rows.add(box(n==1?"@79×2":"@245×2",120,y+38,280));y+=38;}y+=46;}
        rows.add(box("小未読8点",70,y,500));rows.add(box("2872",850,y+4,140));y+=46;
        rows.add(box("税率 点数 税抜対象額 消費税額",70,y,920));y+=40;
        for(String[] cells:new String[][]{{"8%","5点","960","77"},{"10%","3点","1912",bad?"991":"191"}}){
            float[] xs={70,310,600,850};for(int n=0;n<4;n++)rows.add(box(cells[n],xs[n],y,130));y+=40;
        }
        rows.add(box("日計"+(split?"":"3140"),70,y,split?200:920));
        if(split){y+=35;rows.add(box("3140",850,y,140));}y+=40;
        for(String s:new String[]{"クレジット支払2000","現金支払1000","ポイント支払140","預り金1500","お釣り500"}){rows.add(box(s,70,y,920));y+=40;}
        return new OcrDocument("fictional fiscal layout only",1200,1600,rows);
    }
    static ReceiptReview.Batch confirm(ReceiptReview m){
        ReceiptReview.Batch b=m.batch();b.acknowledged=true;b.acceptOneEstimates=true;b.mode=ReceiptReview.PriceMode.NET_PRINTED;b.commonTax="8";b.rounding=ReceiptReview.Rounding.DOWN;
        for(ReceiptReview.Draft d:b.rows)if(d.held!=null)d.choice=ReceiptReview.Choice.SUBTOTAL;
        return b;
    }
    public static int run(){
        checks=0;
        for(boolean split:new boolean[]{false,true}){
            Receipt r=ReceiptParser.parseDocument(fixture(split,false));
            check(r.items.size()==6&&r.heldItems.size()==2&&r.itemSum()==2872,"six goods, recoverable subtotal and total; no payment products");
            check(r.total==null&&r.heldItems.get(1).aggregateKind==Kind.TOTAL&&r.heldItems.get(1).item.price==3140,"observed daily heading is a total candidate, never payment-derived total");
            check(band(r,8).base==960&&band(r,8).tax==77&&band(r,8).itemCount==5&&band(r,10).base==1912&&band(r,10).tax==191&&band(r,10).itemCount==3,"header-ordered separate cells retain rate/count/base/tax");
            check(r.taxAmount==268&&r.taxAmountFromBreakdown&&r.taxAmountUncertain,"sum of observed taxes is labeled derived, not printed total");
            check(band(r,8).amountBasis==AmountBasis.EXCLUSIVE&&!band(r,8).uncertain&&!band(r,10).uncertain,"explicit net context and arithmetic validate tax roles without a rounding policy");
            ReceiptReview model=new ReceiptReview(r);
            check(model.items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4&&model.items.get(1).quantity==2&&model.items.get(3).quantity==2,"previous conditional one proposals and both true pairs preserved");
            check(model.fiscalStatus().contains("税込の最終合計：3140円（要確認）")&&model.fiscalStatus().contains("内訳合算・要確認")&&model.fiscalStatus().contains("照合：未確定"),"top summary distinguishes observed candidate, derived tax and pending match");
            String anonymous=ReceiptDiagnostics.anonymousReceipt(r);
            check(anonymous.contains("TOTAL_CANDIDATE_REVIEW")||anonymous.contains("TOTAL_CONTEXT_MATCHED_REVIEW"),"total candidate remains visible in closed diagnostic codes");
            ReceiptReview.Batch b=confirm(model);b.confirmSummary=true;b.commonTax="";
            // Independently confirmed paid unit inputs, not tax allocation from the footer.
            long[] paid={6,86,1606,270,431,385};for(int n=0;n<6;n++)b.rows.get(n).gross=Long.toString(paid[n]);model.apply(b);
            check(model.aggregate().total==3140&&model.aggregate().subtotal==2872&&model.aggregate().taxAmount==268&&!model.aggregate().taxAmountUncertain,"only explicit review adopts printed aggregate values");
            check(model.fiscalStatus().contains("照合：一致")&&model.items.size()==6&&model.items.get(1).printedUnit==79&&model.items.get(3).printedUnit==245,"net item-to-final reconciliation without changing original pairs");
            ReceiptReview.Batch dirty=model.batch();dirty.total="3130";rejects(model::validateSave,"unapplied fiscal changes block save");dirty.acknowledged=true;rejects(()->model.apply(dirty),"aggregate corrections need their own acknowledgement");
            dirty.confirmSummary=true;rejects(()->model.apply(dirty),"contradictory confirmed final total requires mismatch review");
            check(model.aggregate().total==3140,"failed summary application leaves previous total unchanged");
            dirty.acceptMismatch=true;model.apply(dirty);check(model.aggregate().total==3130&&model.fiscalStatus().contains("不一致"),"human can correct totals while retaining visible contradiction");
        }
        for(String heading:new String[]{"日計","お会計","会計","買上合計","日汁","日言十"}){
            Receipt r=text("税込表示\n架空茶100\n架空パン200\n小計300\n"+heading+"300\n現金1000\nお釣り700");
            check(r.items.size()==2&&r.total==null&&r.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL&&h.item.price==300),"extended labels require context and human classification");
        }
        for(String product:new String[]{"日計ノート","日計表ノート","日汁うどん","会計ノート","計100ノート","合計金額ノート","消費税額ノート","8%配合架空茶","対象額メモ"}){
            Receipt r=text("税込表示\n"+product+"100\n架空パン200\n合計300");
            check(r.items.size()==2&&r.items.get(0).name.equals(product)&&r.total==300,"genuine similarly named goods retained");
        }
        Receipt ordinary=text("税込表示\n架空茶100\n架空パン200\n日計50\n架空米400\n合計750");
        check(ordinary.items.size()==4&&ordinary.items.get(2).name.equals("日計"),"an exact-looking item with no fiscal context remains an item");
        for(String report:new String[]{"レジ日計","日計表","営業日報","本日売上"}){
            Receipt r=text(report+"\n税込表示\n架空茶100\n架空パン200\n小計300\n日計300\n合計300\n現金300");
            check(r.total==null,"daily business report must not supply a receipt purchase total");
        }
        for(String tender:new String[]{"支払金額300","お支払金額300","クレジット支払300","現金支払300","ポイント支払300","預り金300","お釣り300"}){
            Receipt r=text("税込表示\n架空茶100\n架空パン200\n小計300\n"+tender);
            check(r.total==null&&r.items.size()==2,"tender/change/deposit even equal to goods is not a purchase total");
        }
        Receipt split=text("税抜表示\n架空パン200\n小計\n200\n消費税額\n16\n合計\n216\n現金1000");
        check(split.total==216&&split.subtotal==200&&split.taxAmount==16&&split.items.size()==1,"adjacent standalone amounts bind only to fiscal headings");
        Receipt separated=text("税抜表示\n架空食品960\n架空洗剤1912\n小計2872\n税抜対象額\n8%5点960\n10%3点1912\n消費税額\n8%77\n10%191\n合計3140");
        check(band(separated,8).base==960&&band(separated,8).tax==77&&band(separated,8).itemCount==5&&band(separated,10).tax==191,"separate target and tax sections join by explicit percent only");
        check(separated.taxAmount==268&&separated.taxAmountUncertain,"separate section tax sum stays derived");
        Receipt headerBasis=text("架空茶200\n税抜対象額\n8%200\n消費税額\n8%16");
        check(band(headerBasis,8).amountBasis==AmountBasis.EXCLUSIVE&&band(headerBasis,8).base==200&&band(headerBasis,8).tax==16,"tax section explicitly supplies its basis without inventing global item basis");
        Receipt bareCount=text("税抜表示\n架空茶960\n架空洗剤1912\n税率 点数 対象額 消費税額\n8%5 960 77\n10%3 1912 191");
        check(band(bareCount,8).itemCount==5&&band(bareCount,10).itemCount==3&&band(bareCount,10).base==1912,"unmarked point values use explicit header columns, never arbitrary digit splitting");
        Receipt splitRate=text("税抜表示\n架空茶200\n小計200\n消費税額\n8%\n16\n合計216");
        check(band(splitRate,8).tax==16&&splitRate.items.size()==1&&splitRate.total==216,"rate-only row binds its next amount only inside an explicit fiscal section");
        Receipt gap=text("税抜表示\n架空茶200\n消費税額\n8%\n\n16");
        check(band(gap,8).tax==null&&gap.taxDetailsPending,"split fiscal values cannot jump blank lines");
        Receipt repeated=text("税込表示\n架空茶200\n合計200\n合計300");
        check(repeated.total==null&&new ReceiptReview(repeated).fiscalStatus().contains("合計候補"),"conflicting observed totals remain candidates rather than a fabricated or missing value");
        ReceiptReview recovered=new ReceiptReview(text("税込表示\n架空商品A100\n架空商品B200\n小未読2点300\n日計600\n架空商品C50"));
        ReceiptReview.Batch restore=confirm(recovered);restore.mode=ReceiptReview.PriceMode.GROSS_PRINTED;
        for(ReceiptReview.Draft d:restore.rows){d.quantity="1";d.gross=Long.toString(d.item.lineAmount);if(d.held!=null)d.choice=d.held.source.aggregateKind==Kind.TOTAL?ReceiptReview.Choice.ITEM:ReceiptReview.Choice.DEFER;}
        recovered.apply(restore);check(recovered.items.size()==4&&recovered.items.stream().anyMatch(i->i.name.equals("日計"))&&recovered.items.stream().anyMatch(i->i.name.equals("架空商品C"))&&recovered.aggregate().total==null,"recovering a possible true total-named item retains its name and following goods");
        ReceiptReview reversible=new ReceiptReview(text("税込表示\n架空商品A100\n架空商品B200\n小未読2点300\n日計300"));ReceiptReview.Batch first=confirm(reversible);first.mode=ReceiptReview.PriceMode.GROSS_PRINTED;first.confirmSummary=true;reversible.apply(first);
        check(reversible.aggregate().total==300&&reversible.aggregate().subtotal==300,"explicit aggregate values adopted before a later reclassification");
        ReceiptReview.Batch change=reversible.batch();change.acknowledged=true;for(ReceiptReview.Draft d:change.rows){d.choice=d.held.source.aggregateKind==Kind.TOTAL?ReceiptReview.Choice.ITEM:ReceiptReview.Choice.DEFER;d.quantity="1";d.gross="300";}reversible.apply(change);
        check(reversible.items.size()==3&&reversible.aggregate().total==null&&reversible.aggregate().subtotal==null,"reclassifying confirmed aggregates as goods/deferred clears stale confirmed monetary snapshots");
        ReceiptReview.Batch back=reversible.batch();back.acknowledged=true;for(ReceiptReview.Draft d:back.rows)if(d.held.source.aggregateKind==Kind.TOTAL)d.choice=ReceiptReview.Choice.SUBTOTAL;reversible.apply(back);
        check(reversible.items.size()==2&&reversible.aggregate().total==300,"a total candidate can be adopted again without stale edits or duplicate goods");
        Receipt reversed=text("税抜表示\n架空茶200\n税率 消費税額 対象額\n8%16 200\n合計216");
        check(band(reversed,8).base==200&&band(reversed,8).tax==16&&!band(reversed,8).rolesInferred,"header column order can be tax-before-base");
        Receipt inferred=text("税抜表示\n架空茶200\n8%200 16\n合計216");
        check(band(inferred,8).rolesInferred&&band(inferred,8).uncertain&&inferred.taxAmount==null,"arithmetically plausible unlabeled columns are proposals, not certain roles");
        Receipt unknown=text("架空茶200\n8%200 16\n合計216");
        check(band(unknown,8).base==null&&band(unknown,8).tax==null&&unknown.taxDetailsPending,"unknown gross/net never determines numeric roles");
        Receipt gross=text("税込表示\n架空茶108\n小計108\n8%対象額108 内税8\n税額合計8\n合計108");
        check(band(gross,8).base==108&&band(gross,8).tax==8&&band(gross,8).amountBasis==AmountBasis.INCLUSIVE&&!gross.taxConflict,"inclusive tax uses gross target base, not exclusive formula");
        check(new ReceiptReview(gross).fiscalStatus().contains("照合：一致")&&new ReceiptReview(gross).fiscalStatus().contains("二重加算"),"inclusive final amount does not add tax again");
        ReceiptReview mixedBasis=new ReceiptReview(text("税抜表示\n架空茶(税込)200\n税額合計16\n合計216"));
        check(mixedBasis.fiscalStatus().contains("照合：不一致"),"arithmetic equality cannot hide a conflicting per-item gross/net basis");
        check(mixedBasis.items.get(0).quantityState!=ReceiptReview.QuantityState.PROVISIONAL_ONE,"mixed bases also block ordinary one proposals");
        for(String bad:new String[]{"8%対象額200 税99","8%対象額200 税16\n8%対象額300 税24","税額合計16\n税額合計20","8%対象額200 税16\n税額合計20"}){
            Receipt r=text("税抜表示\n架空茶200\n小計200\n"+bad+"\n合計216");
            check(r.taxConflict||r.taxBreakdowns.stream().anyMatch(b->b.uncertain),"conflicting tax observations never become trusted totals");
        }
        for(String bad:new String[]{"8%1912191","8%対象額1,46O 税77","7%対象額200税14","8%対象額-200税-16","8%対象額200税16 10%対象額100税10","標準税率対象額200税16"}){
            Receipt r=text("税抜表示\n架空茶200\n小計200\n"+bad);
            check(r.total==null&&r.taxAmount==null&&r.items.size()==1,"broken numbers, unsupported/missing rate and mixed-rate rows cannot yield a final amount");
        }
        Receipt noFinal=text("税抜表示\n架空茶200\n小計200\n税額合計16\n現金216");
        check(noFinal.total==null&&new ReceiptReview(noFinal).fiscalStatus().contains("税込の最終合計：未取得"),"subtotal plus tax does not fabricate an OCR final total");
        for(String adjustment:new String[]{"商品値引-20","返品-20","ポイント利用20"}){
            Receipt r=text("税込表示\n架空茶200\n小計200\n"+adjustment+"\n合計180");
            check(r.hasDiscount&&new ReceiptReview(r).fiscalStatus().contains("照合：未確定"),"discount/return/point allocation requires review, no silent allocation");
        }
        ReceiptReview badModel=new ReceiptReview(ReceiptParser.parseDocument(fixture(false,true)));
        check(badModel.receipt.taxConflict&&badModel.items.stream().noneMatch(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE),"proven tax arithmetic contradiction blocks ordinary one proposals");
        Receipt countConflict=text("税抜表示\n架空茶200\n架空米300\n税率 点数 対象額 消費税額\n8%2 200 16\n10%2 300 30");
        check(new ReceiptReview(countConflict).items.stream().noneMatch(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE),"band count suggesting lost quantities blocks one proposals without inventing individual counts");
        List<OcrDocument.Region> offset=new ArrayList<>();for(OcrDocument.Region r:fixture(false,false).regions){if(r.text.equals("小未読8点"))offset.add(box("小未読8点2872",70,r.y(0),420));else if(!r.text.equals("2872"))offset.add(r);}
        Receipt offsetReceipt=ReceiptParser.parseDocument(new OcrDocument("fictional left fiscal column",1200,1600,offset));
        check(offsetReceipt.items.size()==6&&offsetReceipt.heldItems.size()==2,"a subtotal in a different price column uses following tax structure and stays recoverable");
        for(int n:new int[]{20,50}){Receipt r=ReceiptParser.parseDocument(ReceiptPairingTest.longReceipt(n,false));check(r.items.size()==n&&r.total!=null&&r.taxAmount==80,"long fictional coordinates retain fiscal footer and goods");}
        System.out.println("PASS ReceiptFiscal "+checks+" checks (fictional text and coordinates only)");return checks;
    }
    public static void main(String[] args){run();}
}
