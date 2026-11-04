package org.privateprice.pricebook;

import java.util.*;
import java.util.regex.*;
import org.privateprice.pricebook.ReceiptParser.*;
import org.privateprice.pricebook.ReceiptDiagnostics.*;

/** Alternative fiscal views only. Original rows/regions and every product decision survive. */
final class ReceiptFiscalRecovery {
    private ReceiptFiscalRecovery() { }
    private static String clean(String s){return ReceiptFiscal.clean(s);}
    private static String compact(String s){return clean(s).replace(" ","");}
    private static boolean totalLabel(String s){return ReceiptFiscalNotation.heading(s);}
    private static boolean barrier(String s){return compact(s).matches("(?i).*(?:支払|クレジット|現金|ポイント|釣銭|お釣|預り|預かり|日報|売上|値引|割引|返品|返金|取引|レジ|担当|CREDIT|CASH|CHANGE).*");}
    private static boolean noise(String s){return compact(s).matches("[-=―ー・:：*※◇◆□()（）]{1,24}");}
    /** Recognize observed header words, never repair digits or accept arbitrary product text. */
    static String header(String text){
        String s=clean(text).replace('额','額');if(s.length()>100||s.matches(".*[0-9%].*")||barrier(s))return null;
        String rest=s.replaceAll("税率別|税率|対象購入点数|購入点数|対象点数|点数|課税対象|対象金額|対象額|対象|本体価格|本体|消費税額|消費税|内消費税|内税額|外税額|税額|税抜|税込","")
            .replaceAll("内訳|区分|欄|金額|円|[\\s・/／:：()（）*※]","");
        if(!rest.isEmpty())return null;
        // Preserve the observed column order. Wrapper words are not numeric grammar.
        String normalized=s.replaceAll("内訳|区分|欄|円|[・／]"," ").trim();
        return normalized.matches(".*(?:税率|点数|対象|本体|消費税|税額).*" )?normalized:null;
    }
    private static boolean numeric(String s){return clean(s).matches("[0-9, %¥円点個()（）:：/*※.+-]+")&&s.matches(".*[0-9].*");}
    private static boolean rate(String s){return clean(s).matches(".*(?<![0-9])(?:8|10)\\s*%.*");}
    /** Propose a lost numeric boundary only when observed columns, basis and one split agree. */
    private static String splitColumns(String text,String columns,AmountBasis basis){
        int base=ReceiptFiscal.baseColumn(columns),tax=columns.indexOf("消費税");if(tax<0)tax=columns.indexOf("税額");
        if(base<0||tax<0||basis==AmountBasis.UNKNOWN)return text;
        Matcher m=Pattern.compile("^(8|10)%\\s*(?:([0-9]{1,3}[点個])\\s*)?([0-9]{2,14})$").matcher(clean(text).replaceAll("\\s*%\\s*","%"));
        if(!m.matches())return text;String digits=m.group(3),proposal=null;int possibilities=0,rate=Integer.parseInt(m.group(1));
        for(int n=1;n<digits.length();n++){
            String left=digits.substring(0,n),right=digits.substring(n);if(left.length()>1&&left.startsWith("0")||right.length()>1&&right.startsWith("0"))continue;
            long a=Long.parseLong(left),b=Long.parseLong(right);if(a>1_000_000||b>1_000_000)continue;
            if(ReceiptFiscal.compatible(base<tax?a:b,base<tax?b:a,rate,basis)){possibilities++;proposal=m.group(1)+"% "+(m.group(2)==null?"":m.group(2)+" ")+left+" "+right;}
        }
        return possibilities==1?proposal:text;
    }
    private static boolean vertical(ReceiptLayout.Part a,ReceiptLayout.Part b){
        double h=Math.max(a.height(),b.height());return Math.abs(a.center()-b.center())<=.55*h&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>=.3*Math.min(a.height(),b.height());
    }
    private static boolean duplicate(ReceiptLayout.Part a,ReceiptLayout.Part b){
        return clean(a.text).equals(clean(b.text))&&vertical(a,b)&&Math.min(a.right,b.right)-Math.max(a.left,b.left)>=.8*Math.min(a.right-a.left,b.right-b.left);
    }
    private static final class View {
        String text;final List<ReceiptLayout.Part> parts=new ArrayList<>();final List<Input> sources=new ArrayList<>();
        boolean duplicate,overlap,changed,conflicting;
        double top,bottom,left,right;
        View(String text){this.text=text;}
        void add(ReceiptLayout.Part p,Input source){if(parts.isEmpty()){top=p.top;bottom=p.bottom;left=p.left;right=p.right;}else{top=Math.min(top,p.top);bottom=Math.max(bottom,p.bottom);left=Math.min(left,p.left);right=Math.max(right,p.right);}parts.add(p);if(!sources.contains(source))sources.add(source);}
        double height(){return Math.max(1,bottom-top);}
        Input audit(Kind kind,Recovery state){
            ReceiptLayout.Row row=null;if(!parts.isEmpty()){row=new ReceiptLayout.Row();for(ReceiptLayout.Part p:parts)row.add(p);row.text=String.join("\n",sources.stream().map(s->s.row.text).distinct().toArray(String[]::new));row.uncertain=true;row.overlap=overlap||duplicate;}
            Input in=new Input(text,row);in.finalKind=kind;in.reason="FISCAL_RECONSTRUCTED_REVIEW";in.fiscal.recovery=state;in.fiscal.binding=parts.size()>1||sources.size()>1?Binding.FRAGMENT_ROWS:Binding.INLINE;
            for(Input original:sources)original.fiscal.recovery=state;
            return in;
        }
    }
    private static List<View> views(List<Input> input,int start){
        List<View> out=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(int n=start;n<input.size();n++){
            Input in=input.get(n);if(in.finalKind==Kind.ITEM||in.finalKind==Kind.ITEM_DETAIL||in.fiscalValue)continue;
            if(in.row==null){if(in.text.length()>240)continue;View v=new View(clean(in.text));v.sources.add(in);out.add(v);continue;}
            for(ReceiptLayout.Part p:in.row.parts){
                if(!seen.add(p.sourceId)||p.text.length()>240)continue;
                View same=null;for(View v:out)if(!v.parts.isEmpty()&&duplicate(v.parts.get(0),p)){same=v;break;}
                if(same!=null){same.add(p,in);same.duplicate=true;same.changed=true;continue;}
                View v=new View(clean(p.text));v.add(p,in);v.overlap=in.row.overlap;out.add(v);
            }
        }
        out.sort(Comparator.comparingDouble((View v)->v.top).thenComparingDouble(v->v.left));
        // Combine complementary fiscal cells on the same baseline, never product cells.
        for(int n=0;n<out.size();n++){
            View a=out.get(n);if(a.parts.isEmpty()||barrier(a.text))continue;
            for(int j=n+1;j<out.size();){View b=out.get(j);
                if(b.parts.isEmpty()||b.top-a.bottom>Math.max(a.height(),b.height()))break;
                boolean aligned=vertical(a.parts.get(0),b.parts.get(0));
                boolean headerPair=header(a.text)!=null&&header(b.text)!=null;
                boolean numberPair=numeric(a.text)&&numeric(b.text);
                if(aligned&&b.left>=a.left&&!barrier(b.text)&&(headerPair||numberPair)){
                    // Distinct, overlapping numeric readings are alternatives, not concatenated digits.
                    if(b.left<a.right&&numberPair){a.conflicting=true;b.conflicting=true;a.changed=true;b.changed=true;j++;continue;}
                    a.text+=" "+b.text;for(int p=0;p<b.parts.size();p++)a.add(b.parts.get(p),b.sources.get(0));for(Input source:b.sources)if(!a.sources.contains(source))a.sources.add(source);
                    a.changed=true;a.duplicate|=b.duplicate;a.overlap|=b.overlap;out.remove(j);
                }else j++;
            }
        }
        return out;
    }
    private static boolean close(View a,View b){return a.parts.isEmpty()&&b.parts.isEmpty()||!a.parts.isEmpty()&&!b.parts.isEmpty()&&b.top-a.bottom<=2.2*Math.max(a.height(),b.height())&&b.top>=a.top-.3*a.height();}
    static void apply(Receipt receipt,List<Input> input,int imageWidth,boolean dailyReport){
        if(receipt.items.isEmpty()||input.size()>500)return;
        int last=-1;for(int n=0;n<input.size();n++)if(input.get(n).finalKind==Kind.ITEM||input.get(n).finalKind==Kind.ITEM_DETAIL)last=n;
        int start=-1;for(int n=last+1;n<input.size();n++){Input in=input.get(n);if(in.finalKind==Kind.SUBTOTAL||in.finalKind==Kind.TOTAL||in.finalKind==Kind.TAX||in.reviewSummary||in.structural){start=n;break;}}
        if(start<0)return;
        List<View> views=views(input,start);List<Input> extra=new ArrayList<>();
        recoverTax(receipt,views,extra);
        recoverBases(receipt,views,extra);
        Long printedTax=ReceiptFiscalNotation.taxObservation(receipt);
        if(receipt.taxAmount==null&&printedTax!=null){receipt.taxAmount=printedTax;receipt.taxAmountFromBreakdown=true;receipt.taxAmountUncertain=true;}
        ReceiptFiscalNotation.basisProposal(receipt);
        if(!dailyReport)recoverTotals(receipt,views,extra,imageWidth);
        input.addAll(extra);
    }
    private static String baseLabel(String text){
        String s=compact(text).replace('额','額').replaceAll("税抜|税込|内訳|[()（）:：]","");
        if(s.matches("対象(?:額|金額|計)?|課税対象(?:額|金額)?|本体(?:価格|額)?|点計|内計"))return s;
        // A short damaged target heading is only a proposal, corroborated with an independent tax row.
        if(s.length()>=3&&s.length()<=6&&s.startsWith("対")&&(s.endsWith("額")||s.endsWith("計"))){
            for(String canonical:new String[]{"対象額","対象金額","対象計"}){
                int[][] d=new int[s.length()+1][canonical.length()+1];for(int i=0;i<=s.length();i++)d[i][0]=i;for(int j=0;j<=canonical.length();j++)d[0][j]=j;
                for(int i=1;i<=s.length();i++)for(int j=1;j<=canonical.length();j++)d[i][j]=Math.min(d[i-1][j]+1,Math.min(d[i][j-1]+1,d[i-1][j-1]+(s.charAt(i-1)==canonical.charAt(j-1)?0:1)));
                if(d[s.length()][canonical.length()]<=1)return s;
            }
        }
        return null;
    }
    private static final class BaseReading {
        int rate;Integer count;ReceiptFiscalNotation.Amount amount;View view;Input record;
    }
    private static BaseReading baseReading(View view,String section){
        String s=clean(view.text).replaceAll("[()（）]","");Matcher rate=Pattern.compile("^(8|10)%\\s*(.*)$").matcher(s);if(!rate.matches())return null;
        String body=rate.group(2);Matcher points=Pattern.compile("(?<![0-9])([0-9]{1,3})[点個]").matcher(body);Integer count=null;
        if(points.find()){count=Integer.valueOf(points.group(1));if(count==0)return null;body=points.replaceAll(" ").trim();}
        Matcher value=Pattern.compile("^(.*?)([¥]?\\s*[0-9][0-9.,]{0,14}\\s*円?)$").matcher(body);if(!value.matches())return null;
        String label=value.group(1).trim();if(label.isEmpty()?section==null:baseLabel(label)==null)return null;
        ReceiptFiscalNotation.Amount amount=ReceiptFiscalNotation.amount(value.group(2));if(amount==null)return null;
        BaseReading read=new BaseReading();read.rate=Integer.parseInt(rate.group(1));read.count=count;read.amount=amount;read.view=view;return read;
    }
    private static void recoverBases(Receipt receipt,List<View> views,List<Input> extra){
        Map<Integer,List<BaseReading>> byRate=new LinkedHashMap<>();String section=null;View previous=null;int skipped=0;
        for(int n=0;n<views.size()&&byRate.values().stream().mapToInt(List::size).sum()<12;n++){
            View v=views.get(n);String label=baseLabel(v.text);
            if(label!=null&&!v.text.matches(".*[0-9].*")){section=label;previous=v;skipped=0;if(extra.size()<40){Input header=v.audit(Kind.TAX,Recovery.HEADER_VIEW);header.fiscal.base=BaseStop.HEADER_VIEW;header.fiscal.heading=FiscalHeading.TAX;header.fiscal.role=FiscalRole.BASE;header.fiscal.money=MoneyToken.MISSING;extra.add(header);}continue;}
            if(section!=null&&noise(v.text)&&skipped++<2&&close(previous,v)){previous=v;continue;}
            if(barrier(v.text)||header(v.text)!=null){section=null;previous=null;continue;}
            if(previous!=null&&!close(previous,v))section=null;
            if(v.conflicting)continue;
            View joined=v;BaseReading read=baseReading(joined,section);
            if(read==null&&section!=null&&compact(v.text).matches("(?:8|10)%(?:[0-9]{1,3}[点個])?")){
                joined=new View(v.text);joined.parts.addAll(v.parts);joined.sources.addAll(v.sources);joined.top=v.top;joined.bottom=v.bottom;joined.left=v.left;joined.right=v.right;
                View last=v;
                for(int cell=0;cell<2&&n+1<views.size()&&read==null;cell++){
                    View next=views.get(n+1);if(next.conflicting||!numeric(next.text)||rate(next.text)||!close(last,next)||barrier(next.text))break;
                    joined.text+=" "+next.text;for(ReceiptLayout.Part p:next.parts)joined.add(p,next.sources.get(0));for(Input source:next.sources)if(!joined.sources.contains(source))joined.sources.add(source);n++;last=next;read=baseReading(joined,section);
                }
            }
            if(read!=null){read.record=joined.audit(Kind.TAX,Recovery.TAX_FIELDS_PROPOSED);read.record.fiscal.base=BaseStop.TAX_NOT_OBSERVED;read.record.fiscal.heading=FiscalHeading.TAX;read.record.fiscal.role=FiscalRole.BASE;read.record.fiscal.rates=Rates.SINGLE_SUPPORTED;read.record.fiscal.money=MoneyToken.OBSERVED;
                if(read.amount.grouping)read.record.fiscal.correction=Correction.JPY_GROUPING;
                byRate.computeIfAbsent(read.rate,k->new ArrayList<>()).add(read);
            }
            else if(section!=null&&rate(v.text)&&extra.size()<40){Input failed=joined.audit(Kind.TAX,Recovery.UNPROVEN_VIEW);failed.fiscal.base=BaseStop.MONEY_MISSING;failed.fiscal.money=MoneyToken.INVALID;failed.fiscal.role=FiscalRole.BASE;failed.fiscal.rates=Rates.SINGLE_SUPPORTED;extra.add(failed);}
            previous=joined;
        }
        for(Map.Entry<Integer,List<BaseReading>> entry:byRate.entrySet()){
            TaxBreakdown band=null;for(TaxBreakdown known:receipt.taxBreakdowns)if(known.rate==entry.getKey())band=known;
            Set<Long> amounts=new LinkedHashSet<>();for(BaseReading read:entry.getValue())amounts.add(read.amount.value);
            if(band!=null&&band.base!=null)amounts.add(band.base);
            for(BaseReading read:entry.getValue()){
                Input record=read.record;
                if(amounts.size()>1){record.fiscal.base=BaseStop.AMBIGUOUS_VALUES;if(band!=null){for(long amount:amounts)if(!band.baseAlternatives.contains(amount)&&band.baseAlternatives.size()<8)band.baseAlternatives.add(amount);band.uncertain=true;}}
                else if(band!=null&&band.base!=null&&band.base==read.amount.value){record.fiscal.base=BaseStop.OBSERVED;}
                else if(band!=null&&band.tax!=null&&band.taxSource==ValueSource.PRINTED&&!receipt.taxConflict){
                    boolean matches=band.amountBasis==AmountBasis.UNKNOWN?(ReceiptFiscal.compatible(read.amount.value,band.tax,band.rate,AmountBasis.EXCLUSIVE)||ReceiptFiscal.compatible(read.amount.value,band.tax,band.rate,AmountBasis.INCLUSIVE)):ReceiptFiscal.compatible(read.amount.value,band.tax,band.rate,band.amountBasis);
                    boolean rounding=read.count!=null&&(band.amountBasis==AmountBasis.UNKNOWN?(ReceiptFiscal.roundingPossible(read.amount.value,band.tax,band.rate,AmountBasis.EXCLUSIVE,read.count)||ReceiptFiscal.roundingPossible(read.amount.value,band.tax,band.rate,AmountBasis.INCLUSIVE,read.count)):ReceiptFiscal.roundingPossible(read.amount.value,band.tax,band.rate,band.amountBasis,read.count));
                    if(!matches&&!rounding)record.fiscal.base=BaseStop.RELATION_MISMATCH;
                    else if(band.itemCount!=null&&read.count!=null&&!band.itemCount.equals(read.count))record.fiscal.base=BaseStop.COUNT_CONFLICT;
                    else{band.base=read.amount.value;band.baseSource=ValueSource.PROPOSED;band.uncertain=true;band.rolesInferred=true;if(band.itemCount==null&&read.count!=null){band.itemCount=read.count;band.countSource=ValueSource.PRINTED;}record.fiscal.base=matches?BaseStop.CANDIDATE:BaseStop.ROUNDING_REVIEW;record.fiscal.values=Values.BASE_AND_TAX;receipt.taxDetailsPending=true;}
                }
                else if(band!=null&&band.base==null){if(!band.baseAlternatives.contains(read.amount.value)&&band.baseAlternatives.size()<8)band.baseAlternatives.add(read.amount.value);band.uncertain=true;receipt.taxDetailsPending=true;}
                if(extra.size()<40)extra.add(record);
            }
        }
    }
    private static void recoverTax(Receipt target,List<View> views,List<Input> extra){
        if(!target.taxDetailsPending&&!target.taxConflict&&!target.taxBreakdowns.isEmpty()&&target.taxBreakdowns.stream().allMatch(b->b.tax!=null&&b.base!=null&&!b.uncertain))return;
        Receipt observed=new Receipt();observed.amountBasis=target.amountBasis;ReceiptFiscal fiscal=new ReceiptFiscal(observed);
        int logical=0,noiseRows=0;boolean open=false,changed=false;View previous=null;String columns="";AmountBasis columnBasis=AmountBasis.UNKNOWN;
        List<Input> records=new ArrayList<>();
        for(int n=0;n<views.size()&&records.size()<32;n++){
            View v=views.get(n);String heading=header(v.text);
            if(heading!=null){
                if(previous!=null&&!close(previous,v))logical+=4;
                Input record=v.audit(Kind.TAX,Recovery.HEADER_VIEW);record.fiscal.heading=FiscalHeading.TAX;record.fiscal.money=MoneyToken.MISSING;fiscal.tax(heading,logical++,false,null,record.fiscal);records.add(record);previous=v;open=true;noiseRows=0;
                columns=fiscal.columnsForRecovery();columnBasis=record.fiscal.basis;
                changed|=v.changed||!ReceiptFiscal.numericGrammar(v.text);continue;
            }
            if(open&&noise(v.text)&&noiseRows++<2&&previous!=null&&close(previous,v)){changed=true;previous=v;continue;}
            if(!open||barrier(v.text)||!rate(v.text)||!numeric(v.text)&&!ReceiptFiscal.taxLike(v.text)){if(open)logical+=4;open=false;columns="";columnBasis=AmountBasis.UNKNOWN;continue;}
            if(v.conflicting){Input record=v.audit(Kind.TAX,Recovery.CONFLICTING_VIEW);record.fiscal.tax=TaxStop.ROLES_UNKNOWN;records.add(record);changed=true;open=false;continue;}
            View joined=new View(v.text);joined.parts.addAll(v.parts);joined.sources.addAll(v.sources);joined.top=v.top;joined.bottom=v.bottom;joined.left=v.left;joined.right=v.right;joined.duplicate=v.duplicate;joined.overlap=v.overlap;joined.changed=v.changed;
            // A rate and its count/base/tax cells may be printed on consecutive lines.
            int cells=0;View lastCell=v;while(n+1<views.size()&&cells<3){View next=views.get(n+1);
                if(next.conflicting||!numeric(next.text)||rate(next.text)||!close(lastCell,next)||next.text.contains("-")||next.parts.size()>0&&joined.parts.size()>0&&Math.abs(next.left-joined.left)>Math.max(160,lastCell.height()*3))break;
                joined.text+=" "+next.text;for(ReceiptLayout.Part p:next.parts)joined.add(p,next.sources.get(0));for(Input source:next.sources)if(!joined.sources.contains(source))joined.sources.add(source);joined.changed=true;lastCell=next;n++;cells++;
            }
            if(previous!=null&&!close(previous,v)){logical+=4;columns="";columnBasis=AmountBasis.UNKNOWN;}
            String split=splitColumns(joined.text,columns,columnBasis);boolean inferred=!split.equals(joined.text);
            Input record=joined.audit(Kind.TAX,inferred?Recovery.JOINED_COLUMNS_PROPOSED:Recovery.RATE_CELLS_VIEW);fiscal.tax(split,logical++,false,null,record.fiscal);record.fiscal.heading=FiscalHeading.TAX;record.fiscal.money=record.fiscal.values==Values.NONE?MoneyToken.INVALID:MoneyToken.OBSERVED;if(inferred){record.fiscal.tax=TaxStop.INFERRED_ROLES;for(TaxBreakdown band:observed.taxBreakdowns){if(!compact(joined.text).startsWith(band.rate+"%"))continue;if(band.base!=null)band.baseSource=ValueSource.PROPOSED;if(band.tax!=null)band.taxSource=ValueSource.PROPOSED;}}records.add(record);previous=joined;noiseRows=0;changed|=joined.changed||inferred;
        }
        fiscal.finish();if(!changed||records.isEmpty())return;
        boolean accepted=false,conflict=target.taxConflict||observed.taxConflict;
        for(TaxBreakdown fresh:observed.taxBreakdowns){
            if(fresh.base==null&&fresh.tax==null)continue;
            TaxBreakdown old=null;for(TaxBreakdown b:target.taxBreakdowns)if(b.rate==fresh.rate)old=b;
            if(old!=null&&(old.base!=null&&fresh.base!=null&&!old.base.equals(fresh.base)||old.tax!=null&&fresh.tax!=null&&!old.tax.equals(fresh.tax)||old.itemCount!=null&&fresh.itemCount!=null&&!old.itemCount.equals(fresh.itemCount)||old.amountBasis!=AmountBasis.UNKNOWN&&fresh.amountBasis!=AmountBasis.UNKNOWN&&old.amountBasis!=fresh.amountBasis)){conflict=true;continue;}
            if(fresh.uncertain&&!fresh.rolesInferred)continue;
            boolean needed=old==null||old.uncertain||old.base==null&&fresh.base!=null||old.tax==null&&fresh.tax!=null||old.itemCount==null&&fresh.itemCount!=null||old.amountBasis==AmountBasis.UNKNOWN&&fresh.amountBasis!=AmountBasis.UNKNOWN;
            if(!needed)continue;
            if(old==null){old=new TaxBreakdown(fresh.rate);target.taxBreakdowns.add(old);}
            if(fresh.base!=null){old.base=fresh.base;old.baseSource=fresh.baseSource;}if(fresh.tax!=null){old.tax=fresh.tax;old.taxSource=fresh.taxSource;}if(fresh.itemCount!=null){old.itemCount=fresh.itemCount;old.countSource=fresh.countSource;}if(fresh.amountBasis!=AmountBasis.UNKNOWN)old.amountBasis=fresh.amountBasis;
            old.uncertain=true;old.rolesInferred=true;accepted=true;
        }
        if(conflict){target.taxConflict=true;target.taxAmountUncertain=true;}
        if(accepted){
            target.taxDetailsPending=true;
            Long sum=observed.taxAmount;
            if(sum==null&&!observed.taxDetailsPending&&!observed.taxBreakdowns.isEmpty()&&observed.taxBreakdowns.stream().allMatch(b->b.tax!=null)){long total=0;for(TaxBreakdown b:observed.taxBreakdowns)total+=b.tax;if(total<=1_000_000)sum=total;}
            if(target.taxBreakdowns.stream().anyMatch(b->b.tax==null))sum=null;
            if(sum!=null&&!conflict){
                if(target.taxAmount==null){target.taxAmount=sum;target.taxAmountFromBreakdown=true;target.taxAmountUncertain=true;}
                else if(!target.taxAmount.equals(sum)){target.taxConflict=true;target.taxAmountUncertain=true;}
            }
        }
        for(Input record:records){record.fiscal.recovery=conflict?Recovery.CONFLICTING_VIEW:accepted?(record.fiscal.recovery==Recovery.JOINED_COLUMNS_PROPOSED?Recovery.JOINED_COLUMNS_PROPOSED:Recovery.TAX_FIELDS_PROPOSED):Recovery.UNPROVEN_VIEW;extra.add(record);}
    }
    private static boolean payment(View view){return compact(view.text).matches("(?i)^(?:クレジット|カード|現金|ポイント支払|お支払|支払|CREDIT|CASH|PAYMENT).*");}
    private static boolean followingPayment(List<View> views,View head){
        int at=views.indexOf(head);
        for(int n=Math.max(0,at+1);n<Math.min(views.size(),at+8);n++){View v=views.get(n);if(payment(v)&&(head.parts.isEmpty()||v.top>=head.bottom-.2*head.height()&&v.top-head.bottom<=7*head.height()))return true;}
        return false;
    }
    private static View combine(String text,List<View> originals){
        View out=new View(text);for(View v:originals){for(ReceiptLayout.Part part:v.parts)out.add(part,v.sources.get(0));for(Input source:v.sources)if(!out.sources.contains(source))out.sources.add(source);out.overlap|=v.overlap||v.duplicate;}
        return out;
    }
    private static List<View> headingParts(List<View> views,View head){
        if(totalLabel(head.text))return new ArrayList<>(List.of(head));
        if(head.parts.isEmpty()||!compact(head.text).matches("[\\p{IsHan}]{1,3}"))return List.of();
        List<View> nearby=new ArrayList<>();for(View next:views)if(next!=head&&!next.parts.isEmpty()&&vertical(head.parts.get(0),next.parts.get(0))&&next.left>=head.left&&next.left-head.right<=3*head.height()&&compact(next.text).matches("[\\p{IsHan}]{1,3}"))nearby.add(next);
        nearby.sort(Comparator.comparingDouble(v->v.left));
        for(int n=0;n<Math.min(8,nearby.size());n++){
            View second=nearby.get(n);if(totalLabel(head.text+second.text))return new ArrayList<>(List.of(head,second));
            for(int j=n+1;j<Math.min(8,nearby.size());j++){View third=nearby.get(j);if(third.left>=second.left&&third.left-second.right<=2*head.height()&&totalLabel(head.text+second.text+third.text))return new ArrayList<>(List.of(head,second,third));}
        }
        return List.of();
    }
    private static void totalProposal(Receipt receipt,List<View> views,View head,List<View> labels,View money,String heading,String token,boolean inline,int alternatives,List<Input> extra){
        if(extra.size()>=40)return;
        ReceiptFiscalNotation.Amount reading=ReceiptFiscalNotation.amount(token);
        boolean variant=ReceiptFiscalNotation.variantHeading(heading)||compact(heading).contains("言十"),grouping=reading==null?token.contains("."):reading.grouping;
        Correction correction=variant?(grouping?Correction.HEADING_AND_GROUPING:Correction.HEADING_VARIANT):grouping?Correction.JPY_GROUPING:Correction.NONE;
        List<View> sources=new ArrayList<>(labels);if(!sources.contains(money))sources.add(money);
        View combined=combine(heading+" "+token,sources);
        Input record=combined.audit(Kind.HELD,alternatives>1?Recovery.ALTERNATIVE_TOTALS:Recovery.TOTAL_FIELDS_PROPOSED);
        record.fiscal.correction=correction;record.fiscal.heading=variant?FiscalHeading.TOTAL_VARIANT:FiscalHeading.EXTENDED_TOTAL;record.fiscal.boundary=Gate.PASS;record.fiscal.binding=inline?Binding.INLINE:Binding.FRAGMENT_ROWS;
        if(reading==null){record.fiscal.money=MoneyToken.INVALID;record.fiscal.total=TotalStop.CORRECTION_REJECTED;record.fiscal.corroboration=Gate.FAIL;extra.add(record);return;}
        record.fiscal.money=grouping?MoneyToken.PROPOSED_GROUPING:MoneyToken.OBSERVED;
        boolean corroborated=ReceiptFiscalNotation.corroborates(receipt,reading.value),paid=followingPayment(views,head);
        record.fiscal.sum=ReceiptDiagnostics.result(corroborated);record.fiscal.payment=ReceiptDiagnostics.result(paid);record.fiscal.corroboration=ReceiptDiagnostics.result(corroborated&&paid);
        if(correction!=Correction.NONE&&!(corroborated&&paid)){record.fiscal.total=TotalStop.CORRECTION_REJECTED;extra.add(record);return;}
        if(reading.value<=0)return;
        if(receipt.total!=null&&receipt.total==reading.value||receipt.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL&&h.item.price==reading.value))return;
        if(receipt.total!=null)receipt.totalUncertain=true;
        if(receipt.heldItems.size()>=80)return;
        Candidate candidate=new Candidate(heading,reading.value,combined.parts.isEmpty()?combined.text:combined.parts.stream().map(v->v.text).reduce((a,b)->a+"\n"+b).orElse(combined.text),null,null,null,"",receipt.amountBasis,true);
        candidate.amountSource=grouping?ValueSource.PROPOSED:ValueSource.PRINTED;
        receipt.heldItems.add(new HeldItem(candidate,null,true,Kind.TOTAL));record.fiscal.total=TotalStop.LAYOUT_CANDIDATE;extra.add(record);
    }
    private static void recoverTotals(Receipt receipt,List<View> views,List<Input> extra,int imageWidth){
        for(View head:views){
            if(extra.size()>=40)break;if(barrier(head.text)||head.sources.stream().anyMatch(s->s.finalKind==Kind.PAYMENT||s.finalKind==Kind.MANAGEMENT))continue;
            String[] inline=ReceiptFiscalNotation.inline(head.text);
            if(inline!=null){
                if(head.parts.isEmpty()||head.right>=imageWidth*.65)totalProposal(receipt,views,head,List.of(head),head,inline[0],inline[1],true,1,extra);
                continue;
            }
            List<View> labels=headingParts(views,head);if(labels.isEmpty())continue;
            String heading=String.join("",labels.stream().map(v->v.text).toArray(String[]::new));List<View> amounts=new ArrayList<>();
            for(View value:views){
                if(labels.contains(value)||ReceiptFiscalNotation.amount(value.text)==null||value.sources.stream().anyMatch(s->s.finalKind==Kind.PAYMENT||s.finalKind==Kind.MANAGEMENT))continue;
                if(value.parts.isEmpty()||head.parts.isEmpty())continue;
                boolean same=vertical(head.parts.get(0),value.parts.get(0)),below=value.top>=head.bottom&&value.top-head.bottom<=1.5*Math.max(head.height(),value.height());
                if(!(same||below)||value.left<imageWidth*.5||value.left<head.left+.35*(head.right-head.left))continue;
                boolean blocked=false;for(View between:views){if(between==value||labels.contains(between))continue;double center=(between.top+between.bottom)/2;if(center>=(head.top+head.bottom)/2-.3*head.height()&&center<=(value.top+value.bottom)/2+.3*value.height()&&(barrier(between.text)||totalLabel(between.text)||header(between.text)!=null))blocked=true;}
                if(!blocked)amounts.add(value);
            }
            if(amounts.isEmpty()){for(Input source:head.sources){source.fiscal.recovery=Recovery.NO_UNIQUE_BINDING;if(ReceiptFiscalNotation.variantHeading(heading)){source.fiscal.correction=Correction.HEADING_VARIANT;source.fiscal.total=TotalStop.CORRECTION_REJECTED;}}continue;}
            for(View amount:amounts)totalProposal(receipt,views,head,labels,amount,heading,amount.text,false,amounts.size(),extra);
        }
    }
}
