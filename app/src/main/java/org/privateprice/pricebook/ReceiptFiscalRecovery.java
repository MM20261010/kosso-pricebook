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
    private static boolean totalLabel(String s){return ReceiptFiscal.extendedHeading(s)||compact(s).matches("(?:税込|税抜)?(?:合計(?:金額)?|総合計|総額|総計|TOTAL)");}
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
        if(!dailyReport)recoverTotals(receipt,views,extra,imageWidth);
        input.addAll(extra);
    }
    private static void recoverTax(Receipt target,List<View> views,List<Input> extra){
        if(!target.taxDetailsPending&&!target.taxConflict&&!target.taxBreakdowns.isEmpty()&&target.taxBreakdowns.stream().allMatch(b->b.tax!=null&&!b.uncertain))return;
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
            Input record=joined.audit(Kind.TAX,inferred?Recovery.JOINED_COLUMNS_PROPOSED:Recovery.RATE_CELLS_VIEW);fiscal.tax(split,logical++,false,null,record.fiscal);record.fiscal.heading=FiscalHeading.TAX;record.fiscal.money=record.fiscal.values==Values.NONE?MoneyToken.INVALID:MoneyToken.OBSERVED;if(inferred)record.fiscal.tax=TaxStop.INFERRED_ROLES;records.add(record);previous=joined;noiseRows=0;changed|=joined.changed||inferred;
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
            if(fresh.base!=null)old.base=fresh.base;if(fresh.tax!=null)old.tax=fresh.tax;if(fresh.itemCount!=null)old.itemCount=fresh.itemCount;if(fresh.amountBasis!=AmountBasis.UNKNOWN)old.amountBasis=fresh.amountBasis;
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
    private static void recoverTotals(Receipt receipt,List<View> views,List<Input> extra,int imageWidth){
        for(int n=0;n<views.size()&&extra.size()<40;n++){
            View head=views.get(n);if(head.parts.isEmpty()||barrier(head.text))continue;
            String heading=head.text;List<View> headingParts=new ArrayList<>();headingParts.add(head);
            if(!totalLabel(heading)){
                for(View next:views)if(next!=head&&!head.parts.isEmpty()&&!next.parts.isEmpty()&&vertical(head.parts.get(0),next.parts.get(0))&&next.left>=head.left&&next.left-head.right<=2*head.height()&&!barrier(next.text)&&totalLabel(heading+next.text)){heading+=next.text;headingParts.add(next);break;}
            }
            if(!totalLabel(heading))continue;
            List<View> amounts=new ArrayList<>();
            for(View value:views){
                if(value==head||ReceiptFiscal.bareAmount(value.text)==null||value.parts.isEmpty()||head.parts.isEmpty())continue;
                boolean same=vertical(head.parts.get(0),value.parts.get(0));
                boolean below=value.top>=head.bottom&&value.top-head.bottom<=1.5*Math.max(head.height(),value.height());
                if(!(same||below)||value.left<imageWidth*.5||value.left<head.left+.35*(head.right-head.left))continue;
                boolean blocked=false;
                for(View between:views){if(between==value||headingParts.contains(between))continue;
                    if(between.top>=head.top-.3*head.height()&&between.top<=value.bottom&&(barrier(between.text)||totalLabel(between.text)||header(between.text)!=null))blocked=true;}
                if(!blocked)amounts.add(value);
            }
            if(amounts.isEmpty()){head.sources.forEach(s->s.fiscal.recovery=Recovery.NO_UNIQUE_BINDING);continue;}
            for(View amount:amounts){
                if(extra.size()>=40)break;
                long value=ReceiptFiscal.bareAmount(amount.text);if(value<=0)continue;
                boolean existing=receipt.total!=null&&receipt.total==value||receipt.heldItems.stream().anyMatch(h->h.aggregateKind==Kind.TOTAL&&h.item.price==value);
                if(existing)continue;
                if(receipt.total!=null&&receipt.total!=value)receipt.totalUncertain=true;
                View combined=new View(heading+" "+amount.text);for(View h:headingParts){for(ReceiptLayout.Part p:h.parts)combined.add(p,h.sources.get(0));for(Input source:h.sources)if(!combined.sources.contains(source))combined.sources.add(source);}for(ReceiptLayout.Part p:amount.parts)combined.add(p,amount.sources.get(0));for(Input source:amount.sources)if(!combined.sources.contains(source))combined.sources.add(source);
                combined.overlap=amount.overlap||amount.duplicate||headingParts.stream().anyMatch(h->h.overlap||h.duplicate);Input record=combined.audit(Kind.HELD,amounts.size()>1?Recovery.ALTERNATIVE_TOTALS:Recovery.TOTAL_FIELDS_PROPOSED);
                record.fiscal.heading=FiscalHeading.EXTENDED_TOTAL;record.fiscal.money=MoneyToken.OBSERVED;record.fiscal.total=TotalStop.LAYOUT_CANDIDATE;
                record.fiscal.boundary=Gate.PASS;
                String raw=combined.parts.stream().map(p->p.text).reduce((a,b)->a+"\n"+b).orElse(heading);
                if(receipt.heldItems.size()<80)receipt.heldItems.add(new HeldItem(new Candidate(heading,value,raw,null,null,null,"",receipt.amountBasis,true),null,true,Kind.TOTAL));
                extra.add(record);
            }
        }
    }
}
