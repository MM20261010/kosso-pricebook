package org.privateprice.pricebook;

import java.util.*;
import java.util.regex.*;
import org.privateprice.pricebook.ReceiptParser.*;

/** Tentative fiscal interpretations only; never normalizes a product or supplies unread digits. */
final class ReceiptFiscalNotation {
    private ReceiptFiscalNotation(){}
    static String compact(String s){return ReceiptFiscal.clean(s).replace(" ","");}
    static boolean exactHeading(String s){return ReceiptFiscal.extendedHeading(s)||compact(s).matches("(?i)(?:税込|税抜)?(?:合計(?:金額)?|総合計|総額|総計|TOTAL)");}
    static boolean variantHeading(String s){return compact(s).replaceAll("[（(]?(?:税込|税抜)[）)]?","").replaceAll("[:：¥()円]","").replace("言十","計").matches("[白曰目](?:計|訐|討|汁|什|十)");}
    static boolean heading(String s){return exactHeading(s)||variantHeading(s);}
    static final class Amount {
        final long value;final boolean grouping;
        Amount(long value,boolean grouping){this.value=value;this.grouping=grouping;}
    }
    static Amount amount(String s){
        Long ordinary=ReceiptFiscal.bareAmount(s);if(ordinary!=null)return new Amount(ordinary,false);
        String token=compact(s);if(!token.matches("¥?[1-9][0-9]{0,2}(?:[.][0-9]{3}){1,2}円?"))return null;
        try{long value=Long.parseLong(token.replaceAll("[¥円.]",""));return value<=1_000_000?new Amount(value,true):null;}catch(NumberFormatException e){return null;}
    }
    static String[] inline(String s){
        Matcher m=Pattern.compile("^(.{1,24}?)\\s*([¥]?\\s*[0-9][0-9.,]{0,14}\\s*円?)$").matcher(ReceiptFiscal.clean(s));
        return m.matches()&&heading(m.group(1))?new String[]{m.group(1),m.group(2)}:null;
    }
    static Long subtotal(Receipt r){
        Set<Long> observed=new LinkedHashSet<>();if(r.subtotal!=null)observed.add(r.subtotal);
        for(HeldItem h:r.heldItems)if(h.aggregateKind==Kind.SUBTOTAL)observed.add(h.item.price);
        return observed.size()==1?observed.iterator().next():null;
    }
    static Long taxObservation(Receipt r){
        if(r.taxConflict)return null;
        if(r.taxAmount!=null&&!r.taxAmountFromBreakdown&&!r.taxAmountUncertain)return r.taxAmount;
        if(r.taxBreakdowns.isEmpty())return null;
        long sum=0;for(TaxBreakdown b:r.taxBreakdowns){if(b.tax==null||b.taxSource!=ValueSource.PRINTED)return null;sum+=b.tax;}
        return sum<=1_000_000&&(r.taxAmount==null||sum==r.taxAmount)?sum:null;
    }
    static boolean corroborates(Receipt r,long value){
        Long subtotal=subtotal(r);if(r.items.size()<2||r.hasDiscount||r.taxConflict||subtotal==null||subtotal!=r.itemSum())return false;
        if(r.amountBasis==AmountBasis.INCLUSIVE)return value==subtotal;
        Long tax=taxObservation(r);
        if(r.amountBasis==AmountBasis.EXCLUSIVE)return tax!=null&&value==subtotal+tax;
        return value==subtotal||tax!=null&&value==subtotal+tax;
    }
    static void basisProposal(Receipt r){
        if(r.amountBasis!=AmountBasis.UNKNOWN||r.basisConflict||r.taxConflict||r.hasDiscount||r.taxBreakdowns.isEmpty())return;
        Long subtotal=subtotal(r);if(subtotal==null||subtotal!=r.itemSum())return;
        boolean net=true,gross=true;long sum=0;
        for(TaxBreakdown b:r.taxBreakdowns){
            if(b.base==null||b.tax==null||!b.baseAlternatives.isEmpty())return;sum+=b.base;
            net&=ReceiptFiscal.compatible(b.base,b.tax,b.rate,AmountBasis.EXCLUSIVE)&&(b.amountBasis==AmountBasis.UNKNOWN||b.amountBasis==AmountBasis.EXCLUSIVE);
            gross&=ReceiptFiscal.compatible(b.base,b.tax,b.rate,AmountBasis.INCLUSIVE)&&(b.amountBasis==AmountBasis.UNKNOWN||b.amountBasis==AmountBasis.INCLUSIVE);
        }
        if(sum==subtotal&&net!=gross){r.basisProposal=net?AmountBasis.EXCLUSIVE:AmountBasis.INCLUSIVE;r.basisSource=ValueSource.PROPOSED;}
    }
    static String sourceLabel(ValueSource source){return source==ValueSource.USER_CONFIRMED?"本人確認":source==ValueSource.PROPOSED?"構造候補・要確認":source==ValueSource.PRINTED?"読取":"未取得";}
    static ReceiptFiscal.Match relations(Receipt r,long sum){
        if(r.taxConflict)return ReceiptFiscal.Match.MISMATCH;if(r.hasDiscount)return ReceiptFiscal.Match.PENDING;
        AmountBasis basis=r.amountBasis!=AmountBasis.UNKNOWN?r.amountBasis:r.basisProposal;
        boolean bases=!r.taxBreakdowns.isEmpty(),taxes=bases,aligned=basis!=AmountBasis.UNKNOWN;long baseSum=0,taxSum=0;
        for(TaxBreakdown b:r.taxBreakdowns){bases&=b.base!=null&&b.baseAlternatives.isEmpty();taxes&=b.tax!=null;aligned&=b.amountBasis==AmountBasis.UNKNOWN||b.amountBasis==basis;if(b.base!=null)baseSum+=b.base;if(b.tax!=null)taxSum+=b.tax;}
        if(r.subtotal!=null&&r.subtotal!=sum||bases&&aligned&&r.subtotal!=null&&baseSum!=r.subtotal||taxes&&r.taxAmount!=null&&taxSum!=r.taxAmount)return ReceiptFiscal.Match.MISMATCH;
        if(r.total!=null&&r.subtotal!=null){if(basis==AmountBasis.EXCLUSIVE&&r.taxAmount!=null&&r.subtotal+r.taxAmount!=r.total||basis==AmountBasis.INCLUSIVE&&!r.subtotal.equals(r.total))return ReceiptFiscal.Match.MISMATCH;}
        return bases&&taxes&&aligned&&r.subtotal!=null&&r.taxAmount!=null&&r.total!=null?ReceiptFiscal.Match.MATCH:ReceiptFiscal.Match.PENDING;
    }
}
