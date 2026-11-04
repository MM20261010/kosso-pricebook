package org.privateprice.pricebook;
import java.util.*;
import org.privateprice.pricebook.ReceiptParser.*;
import org.privateprice.pricebook.ReceiptDiagnostics.*;

/** Fictional failure paths and inverse cases. Does not assert an unseen phone image is reproduced. */
public final class ReceiptFiscalRecoveryTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static OcrDocument.Region b(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    static Receipt parse(OcrDocument d){return ReceiptParser.parseDocument(d);}
    static boolean heldTotal(Receipt r,long amount){return r.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL&&h.item.price==amount);}
    static void privateReport(Receipt r){String full=ReceiptDiagnostics.anonymousReceipt(r),brief=ReceiptDiagnostics.anonymousFiscal(r);int before=ReceiptPipelineTest.checks;ReceiptPipelineTest.noData(brief);checks+=ReceiptPipelineTest.checks-before;check(brief.length()<=7030&&brief.length()<full.length()&&!brief.contains("[CANDIDATE]"),"short fiscal report excludes item diagnostics and is bounded");}
    static OcrDocument tail(String... lines){List<OcrDocument.Region> a=new ArrayList<>();a.add(b("架空商品A100",70,20,920));a.add(b("架空商品B200",70,60,920));a.add(b("小計300",70,100,920));float y=140;for(String s:lines){a.add(b(s,70,y,920));y+=35;}return new OcrDocument("fictional fiscal inverse",1200,1600,a);}
    public static int run(){
        checks=0;
        for(int mode=0;mode<4;mode++){
            OcrDocument d=ReceiptFiscalRecoveryScenario.fixture(mode);Receipt r=parse(d);
            check(r.items.size()==6&&r.itemSum()==2872,"fiscal views never change six goods or amounts");
            check(r.items.get(1).unitPrice==79&&r.items.get(1).quantity==2&&r.items.get(3).unitPrice==245&&r.items.get(3).quantity==2,"protected pairs survive reconstruction");
            check(new ReceiptReview(r).items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4,"ordinary one proposals remain provisional");
            check(r.taxAmount==268&&r.taxAmountUncertain&&r.taxAmountFromBreakdown&&r.taxDetailsPending,"observed fiscal fields produce only an unconfirmed aggregate");
            check(heldTotal(r,3140)&&r.total==null,"independent total proposal survives without confirmed tax");
            check(r.decisions.stream().anyMatch(row->row.reason.equals("OVERLAPPING_REGIONS_HELD"))&&r.decisions.stream().anyMatch(row->row.reason.equals("FISCAL_RECONSTRUCTED_REVIEW")),"original held evidence remains alongside alternative interpretation");
            check(r.taxBreakdowns.stream().allMatch(t->t.uncertain&&t.rolesInferred),"recovered roles cannot become certain by matching arithmetic");
            if(mode==2)check(heldTotal(r,3190)&&new ReceiptReview(r).fiscalStatus().contains("合計候補"),"conflicting observed amounts remain visible alternatives");
            if(mode==3)check(r.amountBasis==AmountBasis.UNKNOWN&&r.decisions.stream().anyMatch(row->row.audit.fiscal.tax==TaxStop.GRAMMAR_REJECTED)&&r.decisions.stream().anyMatch(row->row.audit.fiscal.tax==TaxStop.ROLES_UNKNOWN&&row.audit.fiscal.basis==AmountBasis.UNKNOWN),"same grammar and role-unknown stops remain in original evidence; basis is not invented");
            privateReport(r);
        }
        Receipt missing=ReceiptParser.parseReceipt("架空商品A100\n架空商品B200\n小計300\n税額O\n8%200\n10%100");
        check(missing.taxAmount==null&&missing.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.GRAMMAR_REJECTED)&&missing.decisions.stream().anyMatch(d->d.audit.fiscal.tax==TaxStop.ROLES_UNKNOWN),"without a trustworthy heading, one number per rate has no provable role");
        Receipt text=ReceiptParser.parseReceipt("税抜表示\n架空商品A100\n架空商品B200\n小計300\n税率区分 対象点数 税抜対象額 消費税額(内訳)\n8%\n1点\n200\n16\n10%\n1点\n100\n10");
        check(text.taxAmount==26&&text.taxAmountUncertain&&text.items.size()==2,"explicit ordered text cells are also proposals, not coordinate evidence");
        Receipt noise=parse(tail("税抜対象額 消費税額(内訳)","---","◇","8%200 16","10%100 10"));
        check(noise.taxAmount==26&&noise.taxAmountUncertain,"bounded symbol-only noise can bridge a column heading without clearing ambiguity");
        for(String bad:new String[]{"8%2OO 16","8%200 99","11%200 16","8%200 -16"}){
            Receipt r=parse(tail("税抜対象額 消費税額(内訳)",bad,"10%100 10"));
            check(r.taxAmount==null||r.taxAmountUncertain&&r.taxConflict,"corrupt numeric fields, wrong arithmetic and unsupported rate cannot supply a trusted aggregate");
        }
        Receipt incomplete=parse(tail("税抜対象額 消費税額(内訳)","8%200 16","10%100"));check(incomplete.taxAmount==null,"partial tax coverage does not masquerade as the total tax");
        Receipt joined=parse(tail("税抜対象額 消費税額(内訳)","8%20016","10%10010"));
        check(joined.taxAmount==26&&joined.taxAmountUncertain&&joined.decisions.stream().anyMatch(d->d.audit.fiscal.recovery==Recovery.JOINED_COLUMNS_PROPOSED),"one observed digit run splits only with explicit columns and a unique arithmetic-supported boundary");
        Receipt reverse=parse(tail("消費税額 税抜対象額(内訳)","8%16200","10%10100"));check(reverse.taxAmount==26&&reverse.taxAmountUncertain,"observed tax-first column order is respected");
        Receipt noBasis=parse(tail("対象額 消費税額(内訳)","8%20016","10%10010"));check(noBasis.taxAmount==null,"missing basis cannot support numeric segmentation");
        Receipt spliced=parse(tail("税抜対象額 消費税額(内訳)","8%200 1 6","10%100 10"));check(spliced.taxAmount==null,"separate observed numeric tokens are never concatenated into a convenient amount");
        Receipt excess=parse(tail("税抜対象額 消費税額(内訳)","8%100000116","10%10010"));check(excess.taxAmount==null,"numeric segmentation cannot bypass the one-million-yen bound");
        Receipt variant=parse(tail("税抜対象額 消費税额(内訳)","8%20016","10%10010"));check(variant.taxAmount==26&&variant.taxAmountUncertain,"observed simplified fiscal glyph is normalized only in the fiscal header view");
        for(String name:new String[]{"合計ノート","日計メモ","税率別ノート","消費税額シール","消費税额ノート"}){
            Receipt r=ReceiptParser.parseReceipt("税込表示\n"+name+"100\n架空商品B200\n小計300\nカード支払300");
            check(r.items.size()==2&&r.items.get(0).name.equals(name),"actual goods retain fiscal-like names");
        }
        for(String payment:new String[]{"カード支払","現金","ポイント支払","預り金","お釣り"}){
            List<OcrDocument.Region> a=new ArrayList<>(tail("税額O").regions);a.add(b("日計",70,250,200));a.add(b(payment,400,250,400));a.add(b("324",850,250,150));
            Receipt r=parse(new OcrDocument("fictional payment competing heading",1200,1600,a));check(!heldTotal(r,324)&&r.total==null,"competing payment context bars amount binding");
        }
        List<OcrDocument.Region> distant=new ArrayList<>(tail("税額O").regions);distant.add(b("日",70,250,120));distant.add(b("計",140,252,120));distant.add(b("324",850,500,150));
        check(!heldTotal(parse(new OcrDocument("fictional distant total",1200,1600,distant)),324),"a distant amount is not bound to reconstructed heading");
        Receipt report=parse(tail("営業日報","税抜対象額 消費税額(内訳)","8%200 16","10%100 10","日計326"));check(report.total==null&&report.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL),"daily report cannot create a purchase total");
        Receipt good=ReceiptParser.parseDocument(ReceiptFiscalTest.fixture(false,false));check(good.taxAmount==268&&good.taxBreakdowns.stream().noneMatch(t->t.uncertain),"valid existing fields are never downgraded by an unused alternative view");
        List<OcrDocument.Region> invalid=new ArrayList<>(tail("税額O").regions);invalid.add(b("日計 32O",70,250,920));invalid.add(b("日計 32O",72,252,920));Receipt invalidTotal=parse(new OcrDocument("fictional invalid overlapping amount",1200,1600,invalid));
        check(invalidTotal.total==null&&invalidTotal.heldItems.stream().noneMatch(h->h.aggregateKind==Kind.TOTAL)&&invalidTotal.decisions.stream().anyMatch(d->d.audit.fiscal.geometry==Geometry.OVERLAP&&d.audit.fiscal.heading==FiscalHeading.OTHER&&d.audit.fiscal.money==MoneyToken.INVALID&&d.audit.fiscal.total==TotalStop.NOT_A_TOTAL),"exact overlap/OTHER/INVALID stop remains unproven when the amount itself was not observed");
        for(int count:new int[]{20,50}){
            List<OcrDocument.Region> a=new ArrayList<>();a.add(b("税抜表示",70,20,300));float y=60;
            for(int n=0;n<count;n++){a.add(b("架空品目R"+n+" 100",70,y,920));y+=32;}
            a.add(b("小計"+(count*100),70,y,920));y+=36;a.add(b("税抜対象額 消費税额(内訳)",70,y,920));y+=36;a.add(b("8%"+(count*100)+" "+(count*8),70,y,920));y+=36;a.add(b("日",70,y,120));a.add(b("計",140,y+2,120));a.add(b(Integer.toString(count*108),850,y,160));
            Receipt r=parse(new OcrDocument("fictional long fiscal recovery",1200,2200,a));
            check(r.items.size()==count&&r.itemSum()==count*100&&heldTotal(r,count*108),"long receipt keeps every product and a recoverable final total");
            check(r.taxAmount==count*8&&r.taxAmountUncertain&&r.items.stream().allMatch(i->i.quantity==null&&i.unitPrice==null),"long fiscal recovery does not manufacture per-item unit or quantity evidence");
        }
        System.out.println("PASS ReceiptFiscalRecovery "+checks+" checks (fictional reconstruction and inverse paths)");return checks;
    }
    public static void main(String[] args){run();}
}
