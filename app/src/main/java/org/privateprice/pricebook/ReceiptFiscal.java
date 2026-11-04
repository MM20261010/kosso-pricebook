package org.privateprice.pricebook;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.privateprice.pricebook.ReceiptParser.AmountBasis;
import org.privateprice.pricebook.ReceiptParser.Kind;
import org.privateprice.pricebook.ReceiptParser.Receipt;
import org.privateprice.pricebook.ReceiptParser.TaxBreakdown;
import org.privateprice.pricebook.ReceiptParser.ValueSource;
import org.privateprice.pricebook.ReceiptDiagnostics.*;

/** Bounded fiscal interpretation of already recognized text. No IO or item tax allocation. */
final class ReceiptFiscal {
    private static final String MONEY="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})";
    private static final Pattern RATE=Pattern.compile("(?<![0-9])(8|10)\\s*%");
    private static final Pattern NUMBER=Pattern.compile("(?<![0-9,.])("+MONEY+")(?![0-9,.])");
    private static final Pattern COUNT=Pattern.compile("(?<![0-9])([0-9]{1,3})\\s*(?:点|個)");
    private static final String WORDS="消費税額合計|消費税合計|税額合計|税合計|税計|内消費税等?|うち消費税等?|消費税(?:額|等)?|内税額?|外税額?|税額|税金|税率別|税率|標準税率|軽減税率|標準|軽減|課税対象|対象購入点数|購入点数|対象金額|対象額|対象|本体価格|本体|税抜|税込|非課税|課税|うち税|内消費|内計|点計|点数|金額|税|額|内|外|点|個|TAX";
    private final Receipt receipt;
    private FiscalRole role=FiscalRole.NONE;private String columns="";private int lastTax=-10;
    private AmountBasis sectionBasis=AmountBasis.UNKNOWN;
    private Integer pendingRate;private int pendingIndex;private ReceiptLayout.Row pendingRow;
    private boolean totalTaxConflict,headerOpen,sectionUncertain;private int headerRows;
    private Long joinedTaxCandidate;
    ReceiptFiscal(Receipt receipt){this.receipt=receipt;}
    String columnsForRecovery(){return columns;}
    static String clean(String s){return ReceiptParser.normal(s).replace('稅','税').replaceAll("(?<=[\\p{IsHan}]) +(?=[\\p{IsHan}])","");}
    private static String compact(String s){return clean(s).replace(" ","");}
    private static Long money(String s){try{long v=Long.parseLong(s.replace(",",""));return v>=0&&v<=1_000_000?v:null;}catch(RuntimeException e){return null;}}
    static Long bareAmount(String text){String s=clean(text);return s.matches("[¥]?\\s*"+MONEY+"\\s*円?")?money(s.replaceAll("[¥円 ]","")):null;}
    static boolean numericGrammar(String s){return clean(s).replaceAll("(?i)(?:"+WORDS+")","").replaceAll("[0-9, %¥円()（）:：/*※.+-]","").isEmpty();}
    static boolean taxLike(String text){
        String s=clean(text);
        return s.length()<=240&&numericGrammar(s)&&(compact(s).replaceFirst("^[(*※]+","").matches("(?i)^(?:[0-9]{1,3}\\s*%|税|消費税|内税|外税|課税|非課税|軽減|標準|うち税|うち消費税|内消費|内計|点計|対象|TAX).*"));
    }
    /** Reject OCR-corrupted financial numbers without accepting those characters as money. */
    static boolean taxEnvelope(String text){String s=clean(text);return s.length()<=240&&compact(s).replaceFirst("^[(*※]+","").matches("^(?:[0-9]{1,3}%|消費税|税額|内消費税|対象).*" )
        &&s.replaceAll("(?i)(?:"+WORDS+")","").replaceAll("[0-9, %¥円()（）:：/*※.+\\-OIl|]","").isEmpty();}
    static boolean extendedHeading(String text){return compact(text).replaceAll("[（(]?(?:税込|税抜)[）)]?", "").replace("言十","計").replaceAll("[:：¥()円]","").matches("(?:日[計訐討汁什十]|お会計|会計|お買上計|買上合計|ご利用合計)");}
    static boolean shortTotalShape(String s){return compact(s).replace("言十","計").replaceAll("[¥()円:：]","").matches("[\\p{IsHan}]{1,2}計");}
    Long observedTaxSum(){if(receipt.taxAmount!=null&&!receipt.taxAmountUncertain)return receipt.taxAmount;if(receipt.taxDetailsPending||receipt.taxConflict||receipt.taxBreakdowns.isEmpty())return null;long n=0;for(TaxBreakdown b:receipt.taxBreakdowns){if(b.tax==null||b.uncertain)return null;n+=b.tax;}return n<=1_000_000?n:null;}
    static boolean dailyReport(List<ReceiptParser.Input> input){
        for(ReceiptParser.Input in:input)if(compact(in.text).matches("^(?:レジ日計|日計表|日報|営業日報|精算日報|一日売上|本日売上|売上日計|本日取引件数)[0-9,./年月日¥円:：# -]*$"))return true;
        return false;
    }
    /** Join only a fiscal heading and its immediately following standalone amount. */
    static void mergeHeadings(List<ReceiptParser.Input> input){
        for(int n=0;n+1<input.size();n++){
            ReceiptParser.Input a=input.get(n),b=input.get(n+1);String label=compact(a.text);
            if(!(extendedHeading(label)||label.matches("(?:税込|税抜)?(?:合計(?:金額)?|総合計|総額|小計|税額合計|消費税額合計|税合計|消費税額?)")))continue;
            if(bareAmount(b.text)==null){a.fiscal.binding=Binding.NO_AMOUNT;continue;}
            if(a.row!=null&&(b.row==null||a.row.uncertain||b.row.uncertain)){a.fiscal.binding=Binding.LAYOUT_REJECTED;continue;}
            if(a.row!=null&&(b.row.top-a.row.bottom>2.5*Math.max(a.row.height(),b.row.height())||b.row.top<a.row.top)){a.fiscal.binding=Binding.GAP_REJECTED;continue;}
            a.fiscal.binding=Binding.NEXT_ROW;
            a.text=clean(a.text)+" "+clean(b.text);b.fiscalValue=true;
            if(a.row!=null){ReceiptLayout.Row joined=new ReceiptLayout.Row();for(ReceiptLayout.Part p:a.row.parts)joined.add(p);for(ReceiptLayout.Part p:b.row.parts)joined.add(p);joined.text=a.row.text+"\n"+b.row.text;a.row=joined;}
        }
    }
    boolean continuation(String s,int index,ReceiptLayout.Row row){return index-lastTax<=2&&role!=FiscalRole.NONE&&RATE.matcher(s).find()&&numericGrammar(s)
        ||pendingRate!=null&&index==pendingIndex+1&&bareAmount(s)!=null&&ReceiptLayout.adjacent(pendingRow,row);}
    private TaxBreakdown band(int rate){for(TaxBreakdown b:receipt.taxBreakdowns)if(b.rate==rate)return b;TaxBreakdown b=new TaxBreakdown(rate);receipt.taxBreakdowns.add(b);return b;}
    private static AmountBasis basis(String s){boolean net=s.contains("税抜"),gross=s.contains("税込");return net==gross?AmountBasis.UNKNOWN:net?AmountBasis.EXCLUSIVE:AmountBasis.INCLUSIVE;}
    private static Long labeled(String body,String labels){Matcher m=Pattern.compile("(?:"+labels+")\\s*[:：]?\\s*[¥]?\\s*("+MONEY+")(?![0-9,.%点個])").matcher(body);return m.find()?money(m.group(1)):null;}
    static int baseColumn(String columns){Matcher m=Pattern.compile("対象(?:金額|額)|本体(?:価格)?|対象(?!購入点数|点数)").matcher(columns);return m.find()?m.start():-1;}
    private static List<Long> numbers(String body){List<Long> out=new ArrayList<>();Matcher m=NUMBER.matcher(body);while(m.find()){Long v=money(m.group(1));if(v==null)return List.of();out.add(v);}return out;}
    static boolean compatible(long base,long tax,int rate,AmountBasis basis){
        if(basis==AmountBasis.UNKNOWN)return false;
        long numerator=base*rate,denominator=basis==AmountBasis.INCLUSIVE?100+rate:100;
        return tax==numerator/denominator||tax==(numerator+denominator-1)/denominator;
    }
    static boolean roundingPossible(long base,long tax,int rate,AmountBasis basis,Integer count){
        if(base<0||tax<0||tax>base)return false;
        if(count==null)return true; // Unknown taxable calculation unit: do not claim a strict contradiction.
        long divisor=basis==AmountBasis.INCLUSIVE?100+rate:100,numerator=base*rate,tolerance=Math.max(0,count-1);
        return tax>=Math.max(0,numerator/divisor-tolerance)&&tax<=(numerator+divisor-1)/divisor+tolerance;
    }
    /** A missing rate boundary can only be proposed with independently observed matching bases. */
    private boolean joinedTaxProposal(String s,int index,FiscalAudit audit){
        if(!numericGrammar(s)||s.contains("対象")||!(role==FiscalRole.TAX||s.matches(".*(?:消費税|税額|内税|外税).*")))return false;
        String compact=s.replaceAll("(?i)(?:"+WORDS+")","").replaceAll("[ ¥円()（）:：/*※]","");
        Matcher m=Pattern.compile("^(8|10)%("+MONEY+")(8|10)%("+MONEY+")$").matcher(compact);
        if(!m.matches())return false;
        int first=Integer.parseInt(m.group(1)),second=Integer.parseInt(m.group(3));if(first==second)return false;
        Long a=money(m.group(2)),b=money(m.group(4));if(a==null||b==null||a+b>1_000_000)return false;
        TaxBreakdown left=null,right=null;for(TaxBreakdown known:receipt.taxBreakdowns){if(known.rate==first)left=known;if(known.rate==second)right=known;}
        if(left==null||right==null||left.base==null||right.base==null||left.uncertain||right.uncertain||receipt.taxConflict
            ||!compatible(left.base,a,first,left.amountBasis)||!compatible(right.base,b,second,right.amountBasis))return false;
        if(left.tax!=null&&!left.tax.equals(a)||right.tax!=null&&!right.tax.equals(b)){receipt.taxConflict=true;return false;}
        left.tax=a;right.tax=b;left.taxSource=ValueSource.PROPOSED;right.taxSource=ValueSource.PROPOSED;left.uncertain=true;right.uncertain=true;left.rolesInferred=true;right.rolesInferred=true;
        joinedTaxCandidate=a+b;receipt.taxDetailsPending=true;role=FiscalRole.TAX;headerOpen=false;lastTax=index;
        audit.tax=TaxStop.JOINED_RATE_PROPOSAL;audit.rates=Rates.JOINED_PROPOSAL;audit.role=role;audit.values=Values.BASE_AND_TAX;
        audit.basis=left.amountBasis==right.amountBasis?left.amountBasis:AmountBasis.UNKNOWN;return true;
    }
    void tax(String text,int index,boolean uncertainLayout,ReceiptLayout.Row row,FiscalAudit audit){
        String s=clean(text);audit.grammar=ReceiptDiagnostics.result(numericGrammar(s));
        Integer inherited=pendingRate!=null&&index==pendingIndex+1&&bareAmount(s)!=null&&ReceiptLayout.adjacent(pendingRow,row)?pendingRate:null;
        List<int[]> groups=new ArrayList<>();Matcher m=RATE.matcher(s);while(m.find())groups.add(new int[]{m.start(),m.end(),Integer.parseInt(m.group(1))});
        Matcher any=Pattern.compile("(?<![0-9])[0-9]+\\s*%").matcher(s);int all=0;while(any.find())all++;
        if(all!=groups.size()&&joinedTaxProposal(s,index,audit))return;
        audit.rates=all!=groups.size()?Rates.UNSUPPORTED_OR_JOINED:groups.size()>1?Rates.MULTIPLE_SUPPORTED:groups.size()==1?Rates.SINGLE_SUPPORTED:Rates.NONE;
        if(inherited!=null){audit.rates=Rates.INHERITED_SUPPORTED;audit.binding=Binding.NEXT_ROW;}
        if(groups.size()>1&&all==groups.size()&&groups.size()<=4&&numericGrammar(s)){
            String prefix=s.substring(0,groups.get(0)[0]);
            if(!numbers(prefix).isEmpty()){receipt.taxDetailsPending=true;audit.tax=TaxStop.RATE_REJECTED;}
            else{
                if(prefix.matches(".*[\\p{L}].*"))taxOne(prefix,index,uncertainLayout,row);
                boolean complete=true;
                for(int n=0;n<groups.size();n++){String part=s.substring(groups.get(n)[0],n+1<groups.size()?groups.get(n+1)[0]:s.length());TaxStop stop=taxOne(part,index,uncertainLayout,row);complete&=stop==TaxStop.OBSERVED_FIELDS;}
                audit.tax=complete?TaxStop.MULTI_RATE_FIELDS:TaxStop.PARTIAL_MULTI_RATE;
            }
        }else audit.tax=taxOne(s,index,uncertainLayout,row);
        audit.role=role;audit.basis=sectionBasis==AmountBasis.UNKNOWN?receipt.amountBasis:sectionBasis;
        AmountBasis applied=null;boolean mixed=false;
        boolean base=false,tax=false;for(int[] group:groups)for(TaxBreakdown b:receipt.taxBreakdowns)if(b.rate==group[2]){base|=b.base!=null;tax|=b.tax!=null;if(applied!=null&&applied!=b.amountBasis)mixed=true;applied=b.amountBasis;}
        if(inherited!=null)for(TaxBreakdown b:receipt.taxBreakdowns)if(b.rate==inherited){base|=b.base!=null;tax|=b.tax!=null;applied=b.amountBasis;}
        if(applied!=null)audit.basis=mixed?AmountBasis.UNKNOWN:applied;
        audit.values=base&&tax?Values.BASE_AND_TAX:base?Values.BASE_ONLY:tax?Values.TAX_ONLY:Values.NONE;
    }
    private TaxStop taxOne(String text,int index,boolean uncertainLayout,ReceiptLayout.Row row){
        String s=clean(text);if(pendingRate!=null){if(index==pendingIndex+1&&bareAmount(s)!=null&&ReceiptLayout.adjacent(pendingRow,row)){s=pendingRate+"% "+s;pendingRate=null;}else{receipt.taxDetailsPending=true;pendingRate=null;}}
        if(!numericGrammar(s)){receipt.taxDetailsPending=true;return TaxStop.GRAMMAR_REJECTED;}
        boolean gap=index-lastTax>2&&role!=FiscalRole.NONE;if(index-lastTax>2){role=FiscalRole.NONE;columns="";sectionBasis=AmountBasis.UNKNOWN;headerOpen=false;headerRows=0;sectionUncertain=false;}int previous=lastTax;lastTax=index;
        Matcher rates=RATE.matcher(s);Integer rate=null;boolean multiple=false;
        while(rates.find()){if(rate!=null)multiple=true;rate=Integer.valueOf(rates.group(1));}
        Matcher anyRate=Pattern.compile("[0-9]{1,3}\\s*%").matcher(s);int rateCount=0;while(anyRate.find())rateCount++;
        if(multiple||rateCount>1){receipt.taxDetailsPending=true;return TaxStop.RATE_REJECTED;}
        if(rate==null){
            List<Long> values=numbers(s);
            if(s.contains("%")||s.contains("標準")||s.contains("軽減")){receipt.taxDetailsPending=true;return TaxStop.RATE_REJECTED;}
            boolean hasBase=s.matches(".*(?:対象|本体).*"),hasTax=s.matches(".*(?:消費税|税額|内税|外税|税合計|税計|税金|うち税|内消費).*"),hasCount=s.contains("点数");
            if(values.isEmpty()){
                boolean combine=headerOpen&&index==previous+1&&headerRows<4;
                columns=combine?columns+" "+s:s;headerRows=combine?headerRows+1:1;headerOpen=true;
                sectionUncertain=uncertainLayout||combine&&sectionUncertain;
                if(columns.length()>240){role=FiscalRole.NONE;headerOpen=false;receipt.taxDetailsPending=true;return TaxStop.GRAMMAR_REJECTED;}
                sectionBasis=basis(columns);
                hasBase=columns.matches(".*(?:対象|本体).*");hasTax=columns.matches(".*(?:消費税|税額|内税|外税|税合計|税計|税金|うち税|内消費).*");hasCount=columns.contains("点数");
                role=hasBase&&(hasTax||hasCount)?FiscalRole.COLUMNS:hasBase?FiscalRole.BASE:hasTax?FiscalRole.TAX:FiscalRole.NONE;
                if(columns.contains("税込")&&columns.contains("税抜")){receipt.taxConflict=true;receipt.taxDetailsPending=true;return TaxStop.CONFLICT;}
                return TaxStop.HEADER;
            }
            if(hasTax&&!hasBase&&!hasCount&&values.size()==1){
                Long v=values.get(0);
                if(uncertainLayout){receipt.taxAmountUncertain=true;receipt.taxDetailsPending=true;}
                if(receipt.taxAmount!=null&&!receipt.taxAmount.equals(v)){totalTaxConflict=true;receipt.taxAmountUncertain=true;receipt.taxConflict=true;}
                if(!totalTaxConflict)receipt.taxAmount=v;
                role=FiscalRole.NONE;headerOpen=false;return totalTaxConflict?TaxStop.CONFLICT:TaxStop.OBSERVED_FIELDS;
            }
            receipt.taxDetailsPending=true;return TaxStop.ROLES_UNKNOWN;
        }
        headerOpen=false;
        TaxBreakdown b=band(rate);b.uncertain|=uncertainLayout||sectionUncertain;
        AmountBasis declared=basis(s);if(declared==AmountBasis.UNKNOWN)declared=sectionBasis;if(declared==AmountBasis.UNKNOWN)declared=receipt.amountBasis;
        if(declared!=AmountBasis.UNKNOWN){if(b.amountBasis!=AmountBasis.UNKNOWN&&b.amountBasis!=declared){b.uncertain=true;receipt.taxConflict=true;}else b.amountBasis=declared;}
        String body=RATE.matcher(s).replaceAll(" ");Matcher count=COUNT.matcher(body);boolean explicitCount=count.find();
        if(explicitCount){int value=Integer.parseInt(count.group(1));if(value<0||value>999)b.uncertain=true;else if(b.itemCount!=null&&b.itemCount!=value){b.uncertain=true;receipt.taxConflict=true;}else{b.itemCount=value;b.countSource=ValueSource.PRINTED;}body=count.replaceAll(" ");}
        if(body.contains("-")||!numericGrammar(body)){b.uncertain=true;receipt.taxDetailsPending=true;return TaxStop.INVALID_NUMBER;}
        Long base=labeled(body,"課税対象|対象(?:金額|額)?|本体(?:価格)?"),tax=labeled(body,"消費税(?:額|等)?|内消費税等?|内税額?|外税額?|税額|税(?!率)");
        List<Long> values=numbers(body);boolean inferredRoles=false;
        if(values.isEmpty()){if(role==FiscalRole.BASE||role==FiscalRole.TAX){pendingRate=rate;pendingIndex=index;pendingRow=row;return TaxStop.WAITING_AMOUNT;}receipt.taxDetailsPending=true;return TaxStop.INVALID_NUMBER;}
        if(role==FiscalRole.COLUMNS&&base==null&&tax==null){
            List<int[]> order=new ArrayList<>();int atBase=baseColumn(columns),atTax=columns.indexOf("消費税"),atCount=columns.indexOf("点数");if(atTax<0)atTax=columns.indexOf("税額");
            if(atBase>=0)order.add(new int[]{0,atBase});if(atTax>=0)order.add(new int[]{1,atTax});if(atCount>=0&&!explicitCount)order.add(new int[]{2,atCount});order.sort((a,c)->Integer.compare(a[1],c[1]));
            if(values.size()==order.size())for(int n=0;n<order.size();n++){long v=values.get(n);switch(order.get(n)[0]){case 0:base=v;break;case 1:tax=v;break;default:if(v>999||b.itemCount!=null&&b.itemCount!=v){b.uncertain=true;receipt.taxConflict=true;}else{b.itemCount=(int)v;b.countSource=ValueSource.PRINTED;}}}
        }
        if(values.size()==1&&base==null&&tax==null){if(role==FiscalRole.BASE||body.contains("対象"))base=values.get(0);else if(role==FiscalRole.TAX)tax=values.get(0);}
        if(values.size()==2&&base==null&&tax==null){
            if(role==FiscalRole.COLUMNS&&(!columns.contains("点数")||explicitCount)){int atBase=baseColumn(columns),atTax=columns.indexOf("消費税");if(atTax<0)atTax=columns.indexOf("税額");if(atBase>=0&&atTax>=0){base=values.get(atBase<atTax?0:1);tax=values.get(atBase<atTax?1:0);}}
            if(base==null&&tax==null){boolean forward=compatible(values.get(0),values.get(1),rate,b.amountBasis),reverse=compatible(values.get(1),values.get(0),rate,b.amountBasis);if(forward!=reverse){base=values.get(forward?0:1);tax=values.get(forward?1:0);b.rolesInferred=true;b.uncertain=true;inferredRoles=true;}}
        }
        if(base==null&&tax==null){b.uncertain=true;receipt.taxDetailsPending=true;return gap?TaxStop.SECTION_GAP:TaxStop.ROLES_UNKNOWN;}
        if(body.contains("対象")&&base==null||body.matches(".*(?:消費税|税額|内税|外税).*" )&&tax==null){b.uncertain=true;receipt.taxDetailsPending=true;}
        if(base!=null){if(b.base!=null&&!b.base.equals(base)){b.uncertain=true;receipt.taxConflict=true;}else{b.base=base;b.baseSource=inferredRoles?ValueSource.PROPOSED:ValueSource.PRINTED;}}
        if(tax!=null){if(b.tax!=null&&!b.tax.equals(tax)){b.uncertain=true;receipt.taxConflict=true;}else{b.tax=tax;b.taxSource=inferredRoles?ValueSource.PROPOSED:ValueSource.PRINTED;}}
        return receipt.taxConflict?TaxStop.CONFLICT:b.rolesInferred?TaxStop.INFERRED_ROLES:TaxStop.OBSERVED_FIELDS;
    }
    void finish(){
        if(pendingRate!=null)receipt.taxDetailsPending=true;
        long taxSum=0;boolean allTax=!receipt.taxBreakdowns.isEmpty();
        for(TaxBreakdown b:receipt.taxBreakdowns){
            if(b.base!=null&&b.tax!=null&&b.amountBasis!=AmountBasis.UNKNOWN&&!compatible(b.base,b.tax,b.rate,b.amountBasis)){b.uncertain=true;if(roundingPossible(b.base,b.tax,b.rate,b.amountBasis,b.itemCount))receipt.taxDetailsPending=true;else receipt.taxConflict=true;}
            allTax&=b.tax!=null&&!b.uncertain;if(b.tax!=null)taxSum+=b.tax;
        }
        if(taxSum>1_000_000){allTax=false;receipt.taxConflict=true;receipt.taxAmountUncertain=true;}
        if(allTax&&!receipt.taxDetailsPending&&!totalTaxConflict){
            if(receipt.taxAmount==null){receipt.taxAmount=taxSum;receipt.taxAmountFromBreakdown=true;receipt.taxAmountUncertain=true;}
            else if(receipt.taxAmount!=taxSum){receipt.taxConflict=true;receipt.taxAmountUncertain=true;}
        }
        if(totalTaxConflict){receipt.taxAmount=null;receipt.taxAmountUncertain=true;}
        if(joinedTaxCandidate!=null&&!receipt.taxConflict&&!totalTaxConflict){
            if(receipt.taxAmount==null){receipt.taxAmount=joinedTaxCandidate;receipt.taxAmountFromBreakdown=true;receipt.taxAmountUncertain=true;}
            else if(!receipt.taxAmount.equals(joinedTaxCandidate)){receipt.taxConflict=true;receipt.taxAmountUncertain=true;}
        }
    }
    static boolean numericConflict(Receipt r,long sum){
        if(r.taxConflict)return true;
        long bases=0,taxes=0;int count=0;boolean allBase=!r.taxBreakdowns.isEmpty(),allTax=allBase,allCount=allBase;
        for(TaxBreakdown b:r.taxBreakdowns){allBase&=b.base!=null&&!b.uncertain&&b.amountBasis==r.amountBasis;allTax&=b.tax!=null&&!b.uncertain;allCount&=b.itemCount!=null&&(!b.uncertain||b.countSource==ValueSource.PRINTED);if(b.base!=null)bases+=b.base;if(b.tax!=null)taxes+=b.tax;if(b.itemCount!=null)count+=b.itemCount;}
        return allBase&&r.amountBasis!=AmountBasis.UNKNOWN&&!r.hasDiscount&&bases!=sum||allTax&&r.taxAmount!=null&&taxes!=r.taxAmount
            ||allCount&&r.printedItemCount!=null&&count!=r.printedItemCount;
    }
    static Integer bandCount(Receipt r){int n=0;if(r.taxBreakdowns.isEmpty())return null;for(TaxBreakdown b:r.taxBreakdowns){if(b.itemCount==null||b.uncertain&&b.countSource!=ValueSource.PRINTED)return null;n+=b.itemCount;}return n;}
    enum Match { MATCH, MISMATCH, PENDING }
    static Match match(Receipt r,long sum,boolean pendingChoice){
        if(numericConflict(r,sum)||!r.hasDiscount&&r.subtotal!=null&&sum!=r.subtotal)return Match.MISMATCH;
        if(r.total!=null&&!r.hasDiscount){if(r.amountBasis==AmountBasis.INCLUSIVE&&sum!=r.total)return Match.MISMATCH;if(r.amountBasis==AmountBasis.EXCLUSIVE&&r.taxAmount!=null&&(sum+r.taxAmount!=r.total||r.subtotal!=null&&r.subtotal+r.taxAmount!=r.total))return Match.MISMATCH;}
        if(pendingChoice||r.total==null||r.totalUncertain||r.subtotalUncertain||r.hasDiscount||r.amountBasis==AmountBasis.UNKNOWN||r.taxDetailsPending||r.taxAmountUncertain||r.amountBasis==AmountBasis.EXCLUSIVE&&r.taxAmount==null)return Match.PENDING;
        return Match.MATCH;
    }
}
