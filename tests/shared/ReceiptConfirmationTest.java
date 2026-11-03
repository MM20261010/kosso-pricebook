package org.privateprice.pricebook;
import java.util.*;
/** Fictional receipt decisions and explicit user actions. No real OCR or purchase data. */
public final class ReceiptConfirmationTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void rejects(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    static OcrDocument.Region b(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    public static ReceiptParser.Receipt fixture(String summary,int count){
        List<OcrDocument.Region> a=new ArrayList<>();a.add(b("FICTIVE MARKET",70,20,500));a.add(b("2026/11/03 18:00",70,58,500));a.add(b("税抜表示",70,96,400));
        String[] names={"架空もやし200g","架空パン6枚入","架空珈琲500g","架空豆腐300g","架空洗剤CV-20","架空スポンジ"};long[] prices={6,158,1460,490,399,359};
        float y=145;
        for(int i=0;i<6;i++){
            a.add(b(names[i],70,y,550));a.add(b(prices[i]+"C",850,y+4,140));
            if(i==1||i==3){a.add(b(i==1?"@79×2":"@245×2",120,y+38,280));y+=38;}y+=46;
        }
        a.add(b(summary,70,y,200));a.add(b(count+"点",330,y,130));a.add(b("2872",850,y+4,140));y+=46;
        for(String s:new String[]{"8%消費税77","10%消費税191","税額合計268"}){a.add(b(s,70,y,920));y+=40;}
        return ReceiptParser.parseDocument(new OcrDocument("fictional structural review only",1200,1600,a));
    }
    static ReceiptReview.Batch prepared(ReceiptReview m){
        ReceiptReview.Batch b=m.batch();b.acknowledged=true;b.acceptOneEstimates=true;b.acceptCodes=true;b.mode=ReceiptReview.PriceMode.NET_PRINTED;b.commonTax="8";b.rounding=ReceiptReview.Rounding.DOWN;
        for(ReceiptReview.Draft d:b.rows)if(d.held!=null)d.choice=ReceiptReview.Choice.SUBTOTAL;
        return b;
    }
    public static int run(){
        checks=0;
        ReceiptParser.Receipt r=fixture("小未読",8);
        check(r.items.size()==6&&r.heldItems.size()==1&&r.itemSum()==2872,"unknown subtotal shape becomes review row, not seventh product");
        ReceiptParser.Decision summary=r.decisions.stream().filter(d->d.reason.equals("STRUCTURAL_SUMMARY_REVIEW")).findFirst().orElseThrow();
        check(summary.audit.summary.knownStem==ReceiptDiagnostics.Gate.FAIL&&summary.audit.summary.sumMatches==ReceiptDiagnostics.Gate.PASS&&summary.audit.summary.futureFooter==ReceiptDiagnostics.Gate.FAIL&&summary.audit.summary.count==ReceiptDiagnostics.CountCheck.POSSIBLE_WITH_UNKNOWN,"same anonymous gate combination as device, before human choice");
        check(r.subtotal==null&&r.printedItemCount==null,"held row does not assert an aggregate");
        ReceiptParser.Receipt textOnly=ReceiptParser.parseReceipt("架空商品A100\n架空商品B200\n小未読3点300");
        check(textOnly.items.size()==2&&textOnly.heldItems.size()==1,"text-only ambiguous summary is also recoverable, not an ordinary one-estimate product");
        ReceiptReview m=new ReceiptReview(r);
        check(m.items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4,"four normal unknown quantities propose one");
        check(m.items.get(1).quantity==2&&m.items.get(1).printedUnit==79&&m.items.get(3).quantity==2&&m.items.get(3).printedUnit==245,"two printed pairs remain exact");
        check(m.items.get(0).printedUnit==null&&m.items.get(0).proposedUnit()==6&&m.items.get(0).grossUnit==null,"proposal is not a printed or gross price");
        check(m.items.get(0).equation().contains("推定")&&m.items.get(0).equation().contains("1点"),"provisional state visible");
        m.items.get(0).selected=true;rejects(m::validateSave,"estimated one cannot be saved even if selected");
        ReceiptReview.Batch b=prepared(m);b.acknowledged=false;rejects(()->m.apply(b),"batch requires human acknowledgement");
        b.acknowledged=true;b.acceptOneEstimates=false;rejects(()->m.apply(b),"single-point estimates require explicit acceptance");
        check(m.held.get(0).choice==ReceiptReview.Choice.UNRESOLVED&&m.items.get(0).grossUnit==null,"failed batch mutates neither hold choice nor prices");
        b.acceptOneEstimates=true;b.rows.get(4).gross="1000001";rejects(()->m.apply(b),"one invalid amount aborts whole batch");
        check(m.items.get(0).quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE&&m.items.get(1).grossUnit==null,"no earlier row partially confirmed on later failure");
        b.rows.get(4).gross="";m.apply(b);
        check(m.items.size()==6&&m.items.stream().allMatch(i->i.ready()&&i.selected&&i.quantityState==ReceiptReview.QuantityState.USER_CONFIRMED),"one explicit batch confirms fields and selects without storing");
        check(m.items.stream().mapToInt(i->i.quantity).sum()==8&&m.items.stream().mapToLong(i->i.lineAmount).sum()==2872,"confirmed count eight and printed line sum 2872");
        check(m.aggregate().subtotal==2872&&m.aggregate().printedItemCount==8&&m.aggregate().subtotalUncertain,"accepted subtotal stays an OCR-derived aggregate with caution");
        m.validateSave();checks++;
        check(m.items.get(0).printedUnit==null&&m.items.get(1).printedUnit==79,"derived single price never contaminates printed-unit backup field");
        ReceiptReview cancel=new ReceiptReview(fixture("小未読",8));ReceiptReview.Item single=cancel.items.get(0);cancel.rejectEstimate(single);
        check(single.quantity==null&&single.quantityState==ReceiptReview.QuantityState.UNKNOWN&&single.estimationRejected&&!single.selected,"estimate may be rejected to unknown");
        ReceiptReview.Batch cancelled=prepared(cancel);rejects(()->cancel.apply(cancelled),"unknown rejected estimate must not be silently restored");
        cancelled.rows.get(0).defer=true;cancel.apply(cancelled);check(!single.selected&&single.deferred&&single.quantity==null,"explicit defer allows confirmed subset without storing rejected row");
        for(String name:new String[]{"小豆","小辻"}){
            ReceiptReview inverse=new ReceiptReview(fixture(name,8));check(inverse.held.size()==1&&inverse.held.get(0).item.name.startsWith(name),"possible real small-name product preserved");
            ReceiptReview.Batch restore=prepared(inverse);
            for(ReceiptReview.Draft d:restore.rows)if(d.held!=null){d.choice=ReceiptReview.Choice.ITEM;d.quantity="1";d.gross="2872";}
            restore.acceptMismatch=true;inverse.apply(restore);
            check(inverse.items.size()==7&&inverse.items.get(6).name.startsWith(name)&&inverse.items.get(6).ready(),"explicit human item choice restores held original name and price");
        }
        ReceiptReview nameOnly=new ReceiptReview(ReceiptParser.parseReceipt("架空パン200"));nameOnly.items.get(0).name="架空パン別名";nameOnly.items.get(0).grossUnit=200L;nameOnly.edited(nameOnly.items.get(0),false);
        check(nameOnly.items.get(0).quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE&&!nameOnly.items.get(0).ready(),"optional name/price edit alone cannot confirm estimated quantity");
        ReceiptReview missingTwo=new ReceiptReview(fixture("小未読",9));
        check(missingTwo.items.stream().noneMatch(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE),"printed count suggesting lost two blocks ordinary one proposals");
        ReceiptReview.Batch two=prepared(missingTwo);
        for(int i=0;i<two.rows.size();i++)if(two.rows.get(i).held==null){two.rows.get(i).quantity=Integer.toString(i==0?2:missingTwo.items.get(i).source.quantity==null?1:missingTwo.items.get(i).source.quantity);two.rows.get(i).gross="100";}
        two.acceptMismatch=true;missingTwo.apply(two);check(missingTwo.items.stream().mapToInt(i->i.quantity).sum()==9,"human can confirm two after OCR omission, not force all to one");
        ReceiptReview invisibleTwo=new ReceiptReview(ReceiptParser.parseReceipt("架空パン200"));
        check(invisibleTwo.items.get(0).quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE,"indistinguishable fully missing OCR count is only a proposal");
        ReceiptReview.Batch correction=prepared(invisibleTwo);correction.mode=ReceiptReview.PriceMode.GROSS_PRINTED;correction.rows.get(0).quantity="2";correction.rows.get(0).gross="100";invisibleTwo.apply(correction);
        check(invisibleTwo.items.get(0).quantity==2&&invisibleTwo.items.get(0).quantityState==ReceiptReview.QuantityState.USER_CONFIRMED,"human correction distinguishes missing two from unprinted one");
        for(String text:new String[]{"架空牛肉399\n100g単価198円 購入重量201.5g","量り売り架空肉399","架空パン399\n商品値引-20","架空まとめ買い2個で399 399","架空パン399\n@199×2"}){
            ReceiptReview risky=new ReceiptReview(ReceiptParser.parseReceipt(text));
            check(risky.items.size()==1&&risky.items.get(0).quantityState!=ReceiptReview.QuantityState.PROVISIONAL_ONE,"weight/discount/partial mismatching measurement is not normal one inference");
            ReceiptReview.Batch review=prepared(risky);review.rows.get(0).quantity="1";rejects(()->risky.apply(review),"special uncertainty requires direct paid price and exception confirmation");
            review.rows.get(0).gross="399";review.rows.get(0).exceptionChecked=true;review.rows.get(0).weight="201.5";review.acceptMismatch=true;risky.apply(review);
            check(risky.items.get(0).ready()&&risky.items.get(0).grossUnit==399,"human can confirm paid per-item value for special case");
        }
        ReceiptReview weight=new ReceiptReview(ReceiptParser.parseReceipt("架空牛肉399\n100g単価198円 購入重量201.5g"));
        check(weight.items.size()==1&&weight.items.get(0).risks.contains(ReceiptReview.Risk.WEIGHT)&&weight.items.get(0).printedUnit==null,"weight-rate metadata is not a second product or item unit");
        ReceiptReview.Batch weighed=prepared(weight);weighed.rows.get(0).quantity="1";weighed.rows.get(0).gross="399";weighed.rows.get(0).exceptionChecked=true;weighed.rows.get(0).weight="NaN";
        rejects(()->weight.apply(weighed),"nonfinite purchased mass rejected");
        weighed.rows.get(0).weight="201.5";weight.apply(weighed);check(weight.items.get(0).weightGrams==201.5,"purchased weight retained independently of count and printed rate");
        ReceiptReview net=new ReceiptReview(ReceiptParser.parseReceipt("税抜表示\n架空パン200"));ReceiptReview.Batch basis=prepared(net);basis.mode=ReceiptReview.PriceMode.GROSS_PRINTED;
        rejects(()->net.apply(basis),"printed net never silently becomes gross");
        basis.mode=ReceiptReview.PriceMode.NET_PRINTED;basis.rounding=null;rejects(()->net.apply(basis),"net conversion requires unit rounding evidence");
        basis.rounding=ReceiptReview.Rounding.DOWN;basis.commonTax="NaN";rejects(()->net.apply(basis),"invalid tax rejected");
        basis.commonTax="8";net.apply(basis);check(net.items.get(0).grossUnit==216&&net.items.get(0).printedUnit==null,"explicit tax/rounding produces confirmed purchase unit without inventing printed unit");
        ReceiptReview pending=new ReceiptReview(fixture("小未読",8));ReceiptReview.Batch later=prepared(pending);
        for(ReceiptReview.Draft d:later.rows)if(d.held!=null)d.choice=ReceiptReview.Choice.DEFER;
        pending.apply(later);check(pending.held.get(0).choice==ReceiptReview.Choice.DEFER&&pending.aggregate().subtotal==null,"aggregate may be explicitly deferred without invented subtotal");
        ReceiptReview userWeight=new ReceiptReview(ReceiptParser.parseReceipt("架空肉200"));ReceiptReview.Batch declared=prepared(userWeight);declared.acceptOneEstimates=false;
        declared.rows.get(0).declaredRisks.add(ReceiptReview.Risk.WEIGHT);declared.rows.get(0).quantity="1";declared.rows.get(0).gross="200";declared.rows.get(0).weight="150";declared.rows.get(0).exceptionChecked=true;
        userWeight.apply(declared);check(userWeight.items.get(0).risks.contains(ReceiptReview.Risk.WEIGHT)&&userWeight.items.get(0).quantityState==ReceiptReview.QuantityState.USER_CONFIRMED,"user can identify weight omitted by OCR and explicitly confirm piece quantity");
        ReceiptReview badPaid=new ReceiptReview(ReceiptParser.parseReceipt("税込表示\n架空パン200"));ReceiptReview.Batch bad=prepared(badPaid);bad.rows.get(0).gross="100";
        rejects(()->badPaid.apply(bad),"manual gross/printed inclusive mismatch cannot silently commit");
        bad.acceptMismatch=true;badPaid.apply(bad);check(badPaid.aggregate().subtotalUncertain,"acknowledged OCR/aggregate mismatch stays marked uncertain in existing receipt schema");
        for(int n:new int[]{20,50}){
            ReceiptReview longReview=new ReceiptReview(ReceiptParser.parseDocument(ReceiptPairingTest.longReceipt(n,false)));
            ReceiptReview.Batch batch=prepared(longReview);batch.acceptMismatch=true;longReview.apply(batch);check(longReview.items.size()==n&&longReview.items.stream().allMatch(Item->Item.ready()),"long synthetic layout can use one batch without per-item dialogs");
        }
        System.out.println("PASS ReceiptConfirmation "+checks+" checks (fictional proposals and user decisions)");return checks;
    }
    public static void main(String[] args){run();}
}
