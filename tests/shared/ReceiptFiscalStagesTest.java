package org.privateprice.pricebook;

import java.util.*;
import org.privateprice.pricebook.ReceiptParser.*;
import org.privateprice.pricebook.ReceiptDiagnostics.*;

/** One-factor fictional failures, not an assertion that unseen phone OCR equals these inputs. */
public final class ReceiptFiscalStagesTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Receipt text(String s){return ReceiptParser.parseReceipt(s);}
    static TaxBreakdown band(Receipt r,int rate){return r.taxBreakdowns.stream().filter(b->b.rate==rate).findFirst().orElseThrow();}
    static boolean total(Receipt r){return r.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL);}
    static final String PRE="税抜表示\n架空商品A100\n架空商品B200\n小未読2点300\n";
    static void anonymous(Receipt r){int before=ReceiptPipelineTest.checks;ReceiptPipelineTest.noData(ReceiptDiagnostics.anonymousReceipt(r));checks+=ReceiptPipelineTest.checks-before;}
    static OcrDocument.Region box(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    public static OcrDocument fixture(int variant){
        List<OcrDocument.Region> a=new ArrayList<>();float top=0;
        for(OcrDocument.Region r:ReceiptFiscalTest.fixture(false,false).regions){if(r.text.startsWith("税率")){top=r.y(0);break;}}
        for(OcrDocument.Region r:ReceiptFiscalTest.fixture(false,false).regions)if(r.y(0)<top)a.add(r);
        float y=top;
        String[] fiscal=variant==1?new String[]{"税率","対象点数","税抜対象額","消費税額","8%5点 960 77","10%3点 1912 191"}:new String[]{"税抜対象額","8%対象5点960","10%対象3点1912","消費税額 8%77 10%191"};
        for(String s:fiscal){a.add(box(s,70,y,920));y+=36;}
        if(variant==2){a.add(box("合計3140",70,y,920));a.add(box("合計3140",72,y+3,920));y+=40;}
        else if(variant==3){a.add(box("目計",70,y,180));a.add(box("3140",850,y+4,140));y+=40;}
        else {a.add(box("日計(税込)",70,y,400));y+=34;a.add(box("¥ 3,140",850,y,180));y+=40;}
        for(String s:new String[]{"カード支払2000","現金支払1000","ポイント支払140","預り金1500","お釣り500"}){a.add(box(s,70,y,920));y+=36;}
        return new OcrDocument("fictional split fiscal pipeline",1200,1800,a);
    }
    public static int run(){
        checks=0;
        Receipt multi=text(PRE+"税抜対象額\n8%200\n10%100\n消費税額 8%16 10%10\n日計326\nカード支払326");
        check(multi.items.size()==2&&multi.taxAmount==26&&multi.taxAmountFromBreakdown&&multi.taxAmountUncertain,"two supported rate groups use the explicit tax section, still a derived proposal");
        check(band(multi,8).base==200&&band(multi,8).tax==16&&band(multi,10).base==100&&band(multi,10).tax==10,"multi-rate row retains amounts by printed rate");
        check(multi.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.MULTI_RATE_FIELDS&&d.audit.fiscal.rates==Rates.MULTIPLE_SUPPORTED),"actual multi-rate branch recorded");anonymous(multi);
        Receipt wrapped=text(PRE+"税率\n対象点数\n税抜対象額\n消費税額\n8%1点200 16\n10%1点100 10\n日計326\nカード支払326");
        check(wrapped.taxAmount==26&&!wrapped.taxDetailsPending&&band(wrapped,8).itemCount==1&&band(wrapped,10).itemCount==1,"contiguous column headings accumulate before data rather than overwriting roles");
        check(wrapped.decisions.stream().anyMatch(d->d.audit.fiscal.role==FiscalRole.COLUMNS&&d.audit.fiscal.basis==AmountBasis.EXCLUSIVE),"header role and basis recorded separately");
        Receipt bareCount=text(PRE+"税率\n対象点数\n税抜対象額\n消費税額\n8%1 200 16\n10%1 100 10");
        check(band(bareCount,8).itemCount==1&&band(bareCount,8).base==200&&bareCount.taxAmount==26,"target-count header is not mistaken for the target-money column");
        Receipt sections=text(PRE+"税抜対象額\n8%200\n10%100\n消費税額\n8%16\n10%10");
        check(sections.taxAmount==26&&band(sections,8).tax==16,"a new header after data starts a new section, not a combined-column header");
        for(String heading:new String[]{"日計(税込)","日 計（税抜）"}){
            Receipt split=text(PRE+"8%消費税24\n"+heading+"\n¥ 324\nカード支払324");
            check(total(split)&&split.total==null&&split.items.size()==2,"separate observed currency amount binds to annotated known heading as a proposal");
            check(split.decisions.stream().anyMatch(d->d.audit.fiscal.binding==Binding.NEXT_ROW&&d.audit.fiscal.total==TotalStop.CANDIDATE),"binding and candidate gate have actual enum evidence");anonymous(split);
        }
        Receipt shape=text(PRE+"8%消費税24\n目計324\nカード支払324");
        check(total(shape)&&shape.total==null&&shape.items.size()==2,"unknown short heading can be held only with footer, matching observed amount and payment evidence");
        check(shape.decisions.stream().anyMatch(d->d.reason.equals("TOTAL_STRUCTURE_REVIEW")&&d.audit.fiscal.sum==Gate.PASS&&d.audit.fiscal.payment==Gate.PASS),"structural candidate gates, not a glyph alias, recorded");
        ReceiptReview model=new ReceiptReview(shape);check(model.fiscalStatus().contains("324円（要確認）")&&model.aggregate().total==null,"structural amount is visible without automatic adoption");
        for(String tail:new String[]{"目計324","目計999\nカード支払324","目計324\n預り金500"}){
            Receipt r=text(PRE+"8%消費税24\n"+tail);
            if(tail.endsWith("500"))check(total(r),"a recognized payment/deposit supplies context, not a copied total value");
            else check(!total(r)&&r.total==null,"unsupported short heading without arithmetic or payment evidence is not promoted");
            anonymous(r);
        }
        for(String word:new String[]{"目計ノート","時計","消費税等ノート","8%対象額メモ"}){
            Receipt r=text("税込表示\n"+word+"100\n架空商品B200\n合計300");check(r.items.size()==2&&r.items.get(0).name.equals(word),"real products with fiscal-like names remain products");
        }
        ReceiptReview inverse=new ReceiptReview(text("税込表示\n架空商品A100\n架空商品B200\n小未読2点300\n時計300\nカード支払600"));
        check(total(inverse.receipt),"coincidental structural match remains recoverable, not deleted");
        ReceiptReview.Batch b=inverse.batch();b.acknowledged=true;b.acceptOneEstimates=true;b.mode=ReceiptReview.PriceMode.GROSS_PRINTED;
        for(ReceiptReview.Draft d:b.rows){d.quantity="1";d.gross=Long.toString(d.item.lineAmount);if(d.held!=null)d.choice=d.held.source.aggregateKind==Kind.TOTAL?ReceiptReview.Choice.ITEM:ReceiptReview.Choice.DEFER;}inverse.apply(b);
        check(inverse.items.size()==3&&inverse.items.stream().anyMatch(i->i.name.equals("時計"))&&inverse.aggregate().total==null,"human restoration retains true name and does not create a receipt total");
        for(int v=0;v<4;v++){
            Receipt r=ReceiptParser.parseDocument(fixture(v));check(r.items.size()==6&&total(r)&&r.itemSum()==2872,"coordinate ablation retains six goods and total proposal");
            check(r.items.get(1).quantity==2&&r.items.get(1).unitPrice==79&&r.items.get(3).quantity==2&&r.items.get(3).unitPrice==245,"both protected pairs survive every fiscal layout");
            check(r.taxAmount==268&&band(r,8).base==960&&band(r,8).tax==77&&band(r,10).base==1912&&band(r,10).tax==191,"coordinate multi-rate/column cases structure printed values, no allocation");
            check(new ReceiptReview(r).items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4,"ordinary four one proposals remain unconfirmed");
            if(v==2)check(r.total==null&&r.decisions.stream().anyMatch(d->d.audit.fiscal.geometry==Geometry.OVERLAP&&d.audit.fiscal.total==TotalStop.LAYOUT_CANDIDATE),"genuinely overlapping total is retained for review, not silently accepted");
            anonymous(r);
        }
        for(String wrong:new String[]{"カード支払324","現金支払324","ポイント支払324","預り金324","お釣り324"}){Receipt r=text(PRE+"8%消費税24\n"+wrong);check(!total(r)&&r.total==null,"a tender amount equal to the expected total is still not a total");}
        Receipt malformed=text(PRE+"消費税額10%1918%77\n日計324\nカード支払324");
        check(malformed.taxAmount==null&&malformed.taxDetailsPending&&malformed.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.RATE_REJECTED),"joined rate/value digits are not guessed or split");
        for(String taxRow:new String[]{"消費税額8%1610%10","消費税額10%108%16"}){
            Receipt proposed=text(PRE+"税抜対象額\n8%200\n10%100\n"+taxRow);
            check(proposed.taxAmount==26&&proposed.taxAmountUncertain&&proposed.taxDetailsPending&&band(proposed,8).rolesInferred,"uniquely matching observed bases allow only a joined-boundary tax proposal");
            check(proposed.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.JOINED_RATE_PROPOSAL&&d.audit.fiscal.rates==Rates.JOINED_PROPOSAL),"joined-boundary inference remains explicit in diagnostic enums");anonymous(proposed);
        }
        Receipt wrongJoin=text(PRE+"税抜対象額\n8%200\n10%100\n消費税額8%9910%10");check(wrongJoin.taxAmount==null&&wrongJoin.taxDetailsPending,"a rate-shaped substring without arithmetic support is never split into accepted taxes");
        Receipt grossJoin=text("税込表示\n架空商品A216\n架空商品B110\n小未読2点326\n税込対象額\n8%216\n10%110\n消費税額8%1610%10");
        check(grossJoin.taxAmount==26&&grossJoin.taxAmountUncertain&&band(grossJoin,8).amountBasis==AmountBasis.INCLUSIVE,"joined tax proposal validates inclusive tax, without adding tax to item amounts");
        for(String rejected:new String[]{"税抜対象額\n8%200\n10%100\n消費税額8%1611%10","対象額\n8%200\n10%100\n消費税額8%1610%10","税抜対象額\n8%200\n10%100\n消費税額8%100000110%10"}){
            Receipt r=text("架空商品A100\n架空商品B200\n小未読2点300\n"+rejected);
            check(r.taxAmount==null&&r.taxDetailsPending,"unsupported rate, unknown basis or oversized amount cannot support a joined proposal");
        }
        Receipt conflictJoin=text(PRE+"税抜対象額\n8%200\n10%100\n消費税額27\n消費税額8%1610%10");
        check(conflictJoin.taxConflict&&conflictJoin.taxAmountUncertain,"independent total tax disagreement cannot become a confirmed joined proposal");
        Receipt mixedBasis=text(PRE+"税抜対象額\n税込対象額\n8%200 16");check(mixedBasis.taxConflict&&mixedBasis.taxDetailsPending,"contradictory wrapped basis headings are held");
        List<OcrDocument.Region> distant=new ArrayList<>();distant.add(box("架空商品A100",70,10,920));distant.add(box("架空商品B200",70,50,920));distant.add(box("小計300",70,90,920));distant.add(box("日計(税込)",70,130,350));distant.add(box("324",850,500,140));distant.add(box("カード支払324",70,550,920));
        Receipt far=ReceiptParser.parseDocument(new OcrDocument("fictional fiscal gap",1200,900,distant));
        check(!total(far)&&far.decisions.stream().anyMatch(d->d.audit.fiscal.binding==Binding.GAP_REJECTED),"a standalone amount beyond the heading gap is not borrowed");
        Receipt overlappedHeader=ReceiptParser.parseDocument(new OcrDocument("fictional overlapping tax header",1200,800,List.of(box("架空商品A100",70,10,920),box("架空商品B200",70,50,920),box("小計300",70,90,920),box("税抜対象額 消費税額",70,130,920),box("税抜対象額 消費税額",72,133,920),box("8%200 16",70,170,920),box("10%100 10",70,210,920))));
        check(overlappedHeader.taxAmount==26&&overlappedHeader.taxAmountUncertain&&band(overlappedHeader,8).uncertain&&band(overlappedHeader,10).uncertain,"duplicate-only heading view can supply a review proposal, never a confirmed tax interpretation");
        Receipt grammar=text(PRE+"8%対象額2OO税16\n合計324");
        check(grammar.items.size()==2&&grammar.taxDetailsPending&&grammar.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.GRAMMAR_REJECTED),"corrupt financial number stays out of goods, grammar is not relaxed into a number");anonymous(grammar);
        Receipt missingRole=text(PRE+"8%200 16");check(missingRole.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.INFERRED_ROLES),"arithmetic-only role proposal remains explicitly inferred");
        Receipt splitRate=text(PRE+"消費税額\n8%\n¥ 24");check(splitRate.taxAmount==24&&splitRate.decisions.stream().anyMatch(d->d.audit.fiscal.rates==Rates.INHERITED_SUPPORTED&&d.audit.fiscal.binding==Binding.NEXT_ROW),"bounded rate-to-next-value association is diagnosable without values");
        Receipt unknown=text(PRE+"8%消費税24\n未知の合算表示324\nカード支払324");
        String report=ReceiptDiagnostics.anonymousReceipt(unknown);check(!total(unknown)&&report.contains("TOTAL_STOP=HEADING_REJECTED")&&report.contains("REASON=AFTER_SUMMARY_HELD"),"an unrecognized footer is included instead of disappearing from anonymous copy");anonymous(unknown);
        Receipt missing=text(PRE+"8%消費税24\n日計(税込)\nカード支払324");check(missing.decisions.stream().anyMatch(d->d.audit.fiscal.total==TotalStop.AMOUNT_MISSING&&d.audit.fiscal.binding==Binding.NO_AMOUNT),"missing OCR amount is distinct from rejected heading");
        Receipt absent=text(PRE+"8%消費税24\nカード支払324");check(absent.totalResult==FiscalResult.NOT_OBTAINED&&absent.total==null,"no total invented from subtotal/tax/payment arithmetic");
        for(String adjustment:new String[]{"返品-20","商品値引-20","ポイント利用20"}){Receipt r=text(PRE+adjustment+"\n8%消費税24\n目計324\nカード支払324");check(r.hasDiscount&&!total(r),"unallocated adjustment disables structural total arithmetic");}
        Receipt unsafe=text("営業日報\n"+PRE+"8%消費税24\n日計324\nカード支払324");check(unsafe.total==null&&!total(unsafe),"daily report cannot enter total proposals");
        Receipt huge=text(("未知の見出し123\n").repeat(300));String bounded=ReceiptDiagnostics.anonymousReceipt(huge);check(bounded.length()<=60000&&bounded.endsWith("REPORT=TRUNCATED\n"),"complete unknown-row diagnostics are still bounded");anonymous(huge);
        System.out.println("PASS ReceiptFiscalStages "+checks+" checks (fictional failure-path ablations)");return checks;
    }
    public static void main(String[] args){run();}
}
