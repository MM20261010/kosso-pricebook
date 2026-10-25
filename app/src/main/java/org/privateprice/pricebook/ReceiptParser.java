package org.privateprice.pricebook;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, unconfirmed suggestions. Printed amounts are never assumed to be gross unit prices. */
public final class ReceiptParser {
    private ReceiptParser() { }
    public enum Kind { ITEM, ITEM_DETAIL, STORE, DATE, QUANTITY, COUNT_TOTAL, SUBTOTAL, TAX, TOTAL, DISCOUNT, PAYMENT, OTHER }
    public enum AmountBasis { UNKNOWN, EXCLUSIVE, INCLUSIVE }
    public static final class Line {
        public final Kind kind; public final String raw;
        Line(Kind k,String raw){kind=k;this.raw=raw;}
    }
    public static final class Candidate {
        public final String name,raw,markers;
        public final long price;
        public final Integer quantity,taxRate;
        public final Long unitPrice;
        public final AmountBasis amountBasis;
        public final boolean uncertainLayout,nameSuffixUncertain;
        Candidate(String n,long p,String r,Integer q,Long u,Integer t,String marks,AmountBasis basis,boolean uncertain){name=n;price=p;raw=r;quantity=q;unitPrice=u;taxRate=t;markers=marks;amountBasis=basis;uncertainLayout=uncertain;nameSuffixUncertain=n.matches(".*[CVcv]$")&&marks.toUpperCase(java.util.Locale.ROOT).contains(n.substring(n.length()-1).toUpperCase(java.util.Locale.ROOT));}
    }
    public static final class TaxBreakdown {
        public final int rate; public Long base,tax;
        public AmountBasis amountBasis=AmountBasis.UNKNOWN;
        public boolean uncertain;
        TaxBreakdown(int rate){this.rate=rate;}
    }
    public static final class Receipt {
        public final List<Candidate> items=new ArrayList<>();
        public final List<Line> lines=new ArrayList<>();
        public final List<String> warnings=new ArrayList<>();
        public final List<TaxBreakdown> taxBreakdowns=new ArrayList<>();
        public String store="",date="";
        public Long total,subtotal,taxAmount;
        public Integer printedItemCount;
        public AmountBasis amountBasis=AmountBasis.UNKNOWN;
        public boolean hasDiscount,includesTax,subtotalUncertain,totalUncertain,taxAmountFromBreakdown;
        public long itemSum(){long n=0;for(Candidate c:items)n+=c.price;return n;}
        public int knownQuantitySum(){int n=0;for(Candidate c:items)if(c.quantity!=null)n+=c.quantity;return n;}
        public int unknownQuantityCount(){int n=0;for(Candidate c:items)if(c.quantity==null)n++;return n;}
        public String reconciliation(){
            long sum=itemSum();StringBuilder out=new StringBuilder("明細 "+items.size()+"種類 / 行金額計 "+sum+"円（"+basisLabel(amountBasis)+"）。");
            if(items.isEmpty())return out+" 商品が見つからず、照合できません。";
            if(printedItemCount!=null){
                out.append(" 印字購入点数 ").append(printedItemCount).append("点 / 明記数量計 ").append(knownQuantitySum()).append("点");
                if(unknownQuantityCount()>0)out.append("（数量未確定 ").append(unknownQuantityCount()).append("明細。1個とは補完しません）");
                else out.append(knownQuantitySum()==printedItemCount?"・点数一致":"・警告：点数が不一致");
                out.append("。");
            }
            if(items.stream().anyMatch(c->c.amountBasis!=AmountBasis.UNKNOWN&&amountBasis!=AmountBasis.UNKNOWN&&c.amountBasis!=amountBasis))return out+" 明細の税込・税抜表示が混在しています。総額照合は未確定です。";
            if(hasDiscount)return out+" 割引・返品があります。配分を確認するまで総額照合は未確定です。";
            if(subtotal!=null)out.append(sum==subtotal?" 明細計と小計が一致。":" 警告：明細計と小計が不一致（小計 "+subtotal+"円）。");
            if(total==null)out.append(" 総額が未確定で照合できません。");
            else if(amountBasis==AmountBasis.EXCLUSIVE){
                if(taxAmount==null)out.append(" 税額が未確定で、税抜明細から総額を照合できません。");
                else{
                    out.append(" 明細計 ").append(sum).append("円 + 税額 ").append(taxAmount).append("円 / 総額 ").append(total).append("円");
                    out.append(sum+taxAmount==total?"・金額一致。":"・警告：金額が不一致。");
                    if(subtotal!=null&&subtotal+taxAmount!=total)out.append(" 警告：小計＋税額と総額が不一致。");
                }
                out.append(" 各商品の税率・端数処理が不明なら税込単価は未確定です。");
            }else if(amountBasis==AmountBasis.INCLUSIVE){
                out.append(sum==total?" 明細の行金額合計と総額が一致。":" 警告：明細計と総額が不一致（総額 "+total+"円）。");
                out.append(" 内税額は重ねて加算しません。数量・単価・税率は別途確認してください。");
            }else out.append(" 税込・税抜が未確定です。総額 ").append(total).append("円は参考照合です。");
            long bases=0,taxes=0;boolean allBase=!taxBreakdowns.isEmpty(),allTax=allBase;
            for(TaxBreakdown b:taxBreakdowns){allBase&=b.base!=null&&!b.uncertain;allTax&=b.tax!=null&&!b.uncertain;if(b.base!=null)bases+=b.base;if(b.tax!=null)taxes+=b.tax;}
            if(allTax&&taxAmount!=null&&taxes!=taxAmount)out.append(" 警告：税率別税額の合算と税額合計が不一致。");
            if(allBase&&amountBasis!=AmountBasis.UNKNOWN&&bases!=sum)out.append(" 警告：税率別対象額の合算と明細計が不一致。");
            if(subtotalUncertain||totalUncertain)out.append(" 集計見出しにOCR表記揺れがあります。金額が一致しても見出しは要確認です。");
            return out.toString();
        }
    }
    public static String basisLabel(AmountBasis b){return b==AmountBasis.EXCLUSIVE?"税抜":b==AmountBasis.INCLUSIVE?"税込":"税込・税抜未確定";}
    private static final String MONEY="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})";
    private static final Pattern AMOUNT=Pattern.compile("^(.*?)\\s*[¥]?(?<![0-9,])(-?"+MONEY+")\\s*円?((?:\\s*(?:(?:8|10)\\s*%|[CVcv*※軽外内非])){0,4})$");
    private static final Pattern RATE=Pattern.compile("(?<![0-9])(8|10)\\s*%");
    private static final Pattern DATE=Pattern.compile("(?<![0-9])(20[0-9]{2})[年/.-]\\s*([0-9]{1,2})[月/.-]\\s*((?>[0-9]{1,2}))日?(?:(?![0-9])|(?=(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?(?![0-9])))");
    private static final Pattern UNIT=Pattern.compile("(?:@|単価\\s*[¥]?|[¥])("+MONEY+")\\s*円?\\s*[×xX]\\s*([0-9]{1,3})(?![0-9])\\s*(?:個|点|本|袋|枚)?");
    private static final Pattern UNIT_ONLY=Pattern.compile("^(?:@|単価\\s*[¥]?|[¥])?("+MONEY+")\\s*円?\\s*[×xX]\\s*([0-9]{1,3})\\s*(?:個|点|本|袋|枚)?$");
    private static final Pattern COUNT_ONLY=Pattern.compile("^(?:数量\\s*|個数\\s*)?([0-9]{1,3})\\s*(?:個|点|本|袋|枚|パック)$");
    private static final Pattern COUNT_PREFIX=Pattern.compile("^(?:数量\\s*|個数\\s*)?([0-9]{1,3})\\s*(?:個|点|本|袋|枚|パック)(?:\\s+|$)");
    private static final Pattern NAMED_UNIT=Pattern.compile("(?:^|\\s)単価\\s*[¥]?("+MONEY+")円?");
    private static final class Input {String text;ReceiptLayout.Row row;Input(String s,ReceiptLayout.Row r){text=s;row=r;}}
    private static final class Pending {String name,raw;Integer quantity;Long unit;ReceiptLayout.Row row;int parts=1;boolean uncertain;Pending(String name,ReceiptLayout.Row row){this.name=name;raw=name;this.row=row;}}
    private static final class Detail {Integer quantity;Long unit;}
    private static final class Amount {String name,markers;Long value;Integer rate;AmountBasis basis;boolean rateUncertain;}
    private static String compact(String s){return s.replaceAll("\\s+","");}
    private static String normal(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).replace('￥','¥').replace('−','-').replace('△','-').replaceAll("\\s+"," ").trim();}
    private static Long money(String s){try{long n=Long.parseLong(s.replace(",",""));return n>=0&&n<=1_000_000?n:null;}catch(Exception e){return null;}}
    private static Integer count(String s){try{int n=Integer.parseInt(s);return n>=1&&n<=999?n:null;}catch(Exception e){return null;}}
    private static String label(String s){Matcher m=AMOUNT.matcher(s);return compact(m.matches()?m.group(1):s).replaceAll("[¥:：()（）]","");}
    private static boolean oneEdit(String a,String b){
        if(Math.abs(a.length()-b.length())>1)return false;int i=0,j=0,edits=0;
        while(i<a.length()&&j<b.length()){if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}if(++edits>1)return false;if(a.length()>=b.length())i++;if(b.length()>=a.length())j++;}
        return edits+(a.length()-i)+(b.length()-j)<=1;
    }
    private static Kind fuzzyKind(String s){
        String t=label(s).replace("言十","計").replace("言ト","計");
        if(t.length()<2||t.length()>6)return Kind.OTHER;
        if(t.matches("[小少][計訐討汁什十]")||t.startsWith("商")&&oneEdit(t,"商品計"))return Kind.SUBTOTAL;
        if(t.matches("[合台含総總][計訐討汁什十]")||t.matches("[総總][額頭顔]"))return Kind.TOTAL;
        for(String a:new String[]{"消費税","税額","内税","外税","税込","税抜"})if((t.length()>=3||t.contains("税")||t.contains("稅"))&&oneEdit(t,a))return Kind.TAX;
        for(String a:new String[]{"点数","支払金額","現金","お釣り"})if(t.length()>=3&&oneEdit(t,a))return Kind.PAYMENT;
        return Kind.OTHER;
    }
    private static Kind exactKind(String s){
        String t=compact(s);
        if(t.matches(".*(?:値引|值引|割引|クーポン|返品|返金|ポイント利用).*"))return Kind.DISCOUNT;
        if(t.matches("^(?:購入点数|商品点数|お買上げ?点数|お買い上げ点数|買上点数|合計点数|点数|数量合計|購入数).*"))return Kind.COUNT_TOTAL;
        if(t.matches("^(?:数量|個数).*"))return Kind.QUANTITY;
        if(t.matches("(?i)^(?:小計|商品計|本体価格合計|SUBTOTAL).*"))return Kind.SUBTOTAL;
        if(t.matches("(?i)^(?:総合計|総計|総額|税込合計|合計|計[¥0-9]|お買上金額|お買い上げ金額|お買上げ金額|お支払(?:い)?(?:金額|合計)|支払(?:い)?金額|TOTAL).*"))return Kind.TOTAL;
        if(t.matches(".*(?:消費税|税額|税金|内税|外税|課税対象|軽減税率|税率|対象額|対象金額).*" )||t.matches("(?i)^(?:[0-9]+%|[(*※]?軽減|税計|税合計|内計|税[0-9¥]|点計|税込|税抜|非課税|課税|うち税|内消費|対象|TAX).*"))return Kind.TAX;
        if(t.matches("(?i)^(?:現金|現計|預り|預かり|お預|釣|お釣|おつり|クレジット|カード|支払|お支払|お買上|お買い上げ|お買上げ|電子マネー|電子決済|決済|残高|ポイント|獲得|今回|累計|利用可能|チャージ|入金|PayPay|楽天ペイ|交通系|商品券|領収|レジ|担当|責任者|会員|取引|伝票|レシート|登録番号|店番号|店番|店舗番号|店No|店舗No|TEL|電話|営業時間|住所|〒|No[.:#]|STORE(?:NO|NUMBER)|REG(?:ISTER)?(?:NO|NUMBER|ID|[0-9:#.])|POS(?:NO|NUMBER|ID|[0-9:#.])|SHOP(?:NO|NUMBER|CODE|ID)|CASH|CHANGE|CREDIT|VISA|MASTERCARD|PAYMENT).*"))return Kind.PAYMENT;
        if(t.matches(".*(?:支払|決済|クレジット|ICカード|取引番号|伝票番号|レシート番号).*"))return Kind.PAYMENT;
        return Kind.OTHER;
    }
    private static Kind kind(String s){Kind k=exactKind(s);return k==Kind.OTHER?fuzzyKind(s):k;}
    private static boolean productName(String s){
        return s.length()>=1&&s.length()<=120&&kind(s)==Kind.OTHER&&s.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z].*")
            &&!s.matches(".*(?:スーパー|ストア|マート|株式会社|領収|支払い|点数).*")&&!s.matches("^(?:[0-9]+[点個本袋枚]|単価|税込|税抜|[¥@]).*")&&!s.matches(".*(?:店|支店)$");
    }
    private static boolean specification(String s){return s.matches("(?i)^[0-9]+(?:[.,][0-9]+)?\\s*(?:kg|g|ml|l|枚入|個入|本入|袋入|パック入).*" );}
    private static AmountBasis basis(String s){
        boolean net=s.contains("税抜")||s.contains("外税方式"),gross=s.contains("税込")||s.contains("内税方式");
        return net==gross?AmountBasis.UNKNOWN:net?AmountBasis.EXCLUSIVE:AmountBasis.INCLUSIVE;
    }
    private static boolean declaration(String s){return compact(s).matches("^(?:全品)?(?:税込|税抜)(?:表示|価格表示|価格)$|^(?:内税|外税)方式$|^表示価格は(?:税込|税抜).*$");}
    private static Amount amount(String s){
        Amount a=new Amount();a.basis=basis(s);String clean=s.replaceAll("[（(]?(?:税込|税抜)[）)]?","");Matcher m=AMOUNT.matcher(clean);if(!m.matches())return null;
        if(m.group(1).matches(".*[0-9][.]$"))return null;
        a.value=money(m.group(2));a.name=m.group(1).trim();a.markers=m.group(3).replaceAll("(?:8|10)\\s*%|\\s","");Matcher rate=RATE.matcher(m.group(3));while(rate.find()){int v=Integer.parseInt(rate.group(1));if(a.rate!=null&&a.rate!=v)a.rateUncertain=true;a.rate=v;}if(a.rateUncertain)a.rate=null;return a;
    }
    private static Detail detail(String s){
        Detail d=new Detail();Matcher u=UNIT_ONLY.matcher(s),q=COUNT_ONLY.matcher(s);
        if(u.matches()){d.unit=money(u.group(1));d.quantity=count(u.group(2));return d.unit!=null&&d.quantity!=null?d:null;}
        if(q.matches()){d.quantity=count(q.group(1));return d.quantity==null?null:d;}return null;
    }
    private static Long lastAmount(String s){Amount a=amount(s);return a==null?null:a.value;}
    private static void warn(Receipt r,String s){if(!r.warnings.contains(s))r.warnings.add(s);}
    private static Candidate candidate(Receipt r,String name,long value,String raw,Integer qty,Long unit,Integer rate,String marks,AmountBasis b,boolean uncertain){
        if(qty!=null&&unit!=null&&unit*qty!=value)warn(r,"単価×数量と行金額が不一致の候補があります。");
        if(!marks.isEmpty())warn(r,"C/V・※等の印は意味未確認です。印から税率を推測しません。");
        if(uncertain)warn(r,"位置関係による複数行の商品名結合候補があります。元の画像で確認してください。");
        Candidate c=new Candidate(name,value,raw,qty,unit,rate,marks,b,uncertain);
        if(c.nameSuffixUncertain)warn(r,"商品名末尾のC/Vは商品名の一部か印か未確定です。文字を勝手に削除しません。元の画像で確認してください。");
        return c;
    }
    private static void tax(Receipt r,String s){
        Matcher rate=RATE.matcher(s);Long n=lastAmount(s);
        if(rate.find()){
            int pct=Integer.parseInt(rate.group(1));TaxBreakdown band=null;for(TaxBreakdown b:r.taxBreakdowns)if(b.rate==pct)band=b;
            if(band==null){band=new TaxBreakdown(pct);r.taxBreakdowns.add(band);}
            if(basis(s)!=AmountBasis.UNKNOWN)band.amountBasis=basis(s);
            Matcher base=Pattern.compile("(?:対象(?:額|金額)?|課税対象)\\s*[¥]?("+MONEY+")").matcher(s);
            Matcher tax=Pattern.compile("(?:消費税(?:額)?|税額|税)\\s*[¥]?("+MONEY+")").matcher(s);
            Long baseValue=base.find()?money(base.group(1)):null,taxValue=tax.find()?money(tax.group(1)):null;
            if(baseValue!=null){if(band.base!=null&&!band.base.equals(baseValue))band.uncertain=true;band.base=baseValue;}
            if(taxValue==null&&baseValue==null&&s.matches(".*(?:税額|消費税|税).*"))taxValue=n;
            if(taxValue!=null){if(band.tax!=null&&!band.tax.equals(taxValue))band.uncertain=true;band.tax=taxValue;}
            if(band.uncertain)warn(r,"同じ税率の対象額・税額が競合しています。");
        }else if(n!=null&&s.matches(".*(?:消費税|税額|内税|税合計).*")){
            if(r.taxAmount!=null&&!r.taxAmount.equals(n)){r.taxAmount=null;warn(r,"税額合計が競合して未確定です。");}else if(!r.warnings.contains("税額合計が競合して未確定です。"))r.taxAmount=n;
        }
    }
    public static List<Candidate> parse(String text){return parseReceipt(text).items;}
    public static Receipt parseReceipt(String text){
        Receipt invalid=new Receipt();if(text==null)return invalid;
        if(text.length()>32000){warn(invalid,"認識テキストが長すぎます。分割してください。");return invalid;}
        String[] lines=text.split("\\r?\\n",-1);if(lines.length>500){warn(invalid,"行数が多すぎます。分割してください。");return invalid;}
        List<Input> input=new ArrayList<>();for(String s:lines)input.add(new Input(s,null));return parseInputs(input,0);
    }
    public static Receipt parseDocument(OcrDocument doc){
        if(doc==null)return new Receipt();List<Input> input=new ArrayList<>();for(ReceiptLayout.Row row:ReceiptLayout.rows(doc))input.add(new Input(row.text,row));return parseInputs(input,doc.width);
    }
    private static Receipt parseInputs(List<Input> input,int imageWidth){
        Receipt r=new Receipt();boolean net=false,gross=false;int dateIndex=-1;
        for(int i=0;i<input.size();i++){
            String s=normal(input.get(i).text);if(declaration(s)){net|=basis(s)==AmountBasis.EXCLUSIVE;gross|=basis(s)==AmountBasis.INCLUSIVE;}
            if(dateIndex<0&&DATE.matcher(s).find())dateIndex=i;
        }
        if(net!=gross)r.amountBasis=net?AmountBasis.EXCLUSIVE:AmountBasis.INCLUSIVE;
        if(net&&gross)warn(r,"税込・税抜表示が競合して未確定です。");r.includesTax=r.amountBasis==AmountBasis.INCLUSIVE;
        Pending pending=null;int gap=0,active=-1;ReceiptLayout.Row activeRow=null;
        boolean afterTotal=false,dateConflict=false,storeConflict=false,totalConflict=false,subtotalConflict=false;
        for(int index=0;index<input.size();index++){
            Input in=input.get(index);String source=in.text;
            if(source.length()>240){pending=null;active=-1;warn(r,"長すぎる行を除外しました。");continue;}
            String s=normal(source);if(s.isEmpty()){if(++gap>2){pending=null;active=-1;}continue;}
            if(in.row!=null&&in.row.uncertain){pending=null;active=-1;r.lines.add(new Line(Kind.OTHER,s));warn(r,"重なり・行位置が曖昧な領域は商品へ結合しませんでした。");continue;}
            Matcher date=DATE.matcher(s);
            if(date.find()){
                try{String d=LocalDate.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3))).toString();if(!r.date.isEmpty()&&!r.date.equals(d))dateConflict=true;if(!dateConflict)r.date=d;}
                catch(Exception e){dateConflict=true;warn(r,"実在しない日付を検出しました。");}
                r.lines.add(new Line(Kind.DATE,s));pending=null;active=-1;gap=0;continue;
            }
            Kind k=kind(s);
            boolean explicitStore=s.matches("(?i)^(?:店名|STORE)\\s*[:：].+");
            boolean namedStore=s.matches(".{2,100}(?:スーパー|ストア|マート|支店|店)$")||s.matches("(?i).{2,80}(?:SUPERMARKET|SUPER MARKET|MART|MARKET|STORE|SHOP)$");
            boolean englishHeader=index<dateIndex&&r.items.isEmpty()&&k==Kind.OTHER&&s.matches("[A-Za-z][A-Za-z .&'-]{1,80}");
            if(explicitStore||((namedStore||englishHeader)&&k==Kind.OTHER)){
                String shop=s.replaceFirst("(?i)^(?:店名|STORE)\\s*[:：]\\s*","");
                if(!r.store.isEmpty()&&!r.store.equals(shop))storeConflict=true;if(!storeConflict)r.store=shop;
                if(englishHeader&&!explicitStore&&!namedStore)warn(r,"英字の見出しを店名候補として抽出しました。確認してください。");
                r.lines.add(new Line(Kind.STORE,s));pending=null;active=-1;gap=0;continue;
            }
            if(declaration(s)){r.lines.add(new Line(Kind.TAX,s));pending=null;active=-1;continue;}
            if(k==Kind.QUANTITY&&!afterTotal&&(pending!=null||active>=0)&&amount(s)!=null)k=Kind.OTHER;
            Detail d=detail(s);
            if(d!=null&&!afterTotal&&(k==Kind.OTHER||k==Kind.QUANTITY)){
                boolean attached=false;
                if(pending!=null&&gap<=2&&ReceiptLayout.adjacent(pending.row,in.row)){
                    if(pending.quantity!=null&&!pending.quantity.equals(d.quantity)||pending.unit!=null&&d.unit!=null&&!pending.unit.equals(d.unit)){warn(r,"隣接する単価・数量が競合しています。");pending=null;}
                    else{pending.quantity=d.quantity;if(d.unit!=null)pending.unit=d.unit;pending.raw+="\n"+s;pending.row=in.row;attached=true;}
                }else if(active>=0&&gap==0&&ReceiptLayout.adjacent(activeRow,in.row)){
                    Candidate old=r.items.get(active);boolean conflict=old.quantity!=null&&!old.quantity.equals(d.quantity)||old.unitPrice!=null&&d.unit!=null&&!old.unitPrice.equals(d.unit);
                    if(conflict)warn(r,"隣接する単価・数量が競合しています。");
                    r.items.set(active,candidate(r,old.name,old.price,old.raw+"\n"+s,conflict?null:d.quantity,conflict?null:d.unit==null?old.unitPrice:d.unit,old.taxRate,old.markers,old.amountBasis,old.uncertainLayout||conflict));activeRow=in.row;attached=true;if(conflict)active=-1;
                }else warn(r,"結び付く商品がない単価・数量行を除外しました。");
                r.lines.add(new Line(attached?Kind.ITEM_DETAIL:Kind.QUANTITY,s));gap=0;continue;
            }
            if(k!=Kind.OTHER){
                r.lines.add(new Line(k,s));pending=null;active=-1;gap=0;Long n=lastAmount(s);
                if(exactKind(s)==Kind.OTHER)warn(r,"集計・支払見出しのOCR表記揺れを検出しました。原文を確認してください。");
                if(k==Kind.TOTAL){
                    r.totalUncertain|=exactKind(s)==Kind.OTHER;
                    if(n!=null){if(r.total!=null&&!r.total.equals(n))totalConflict=true;if(!totalConflict)r.total=n;}
                    afterTotal=true;
                }
                if(k==Kind.SUBTOTAL){r.subtotalUncertain|=exactKind(s)==Kind.OTHER;if(n!=null){if(r.subtotal!=null&&!r.subtotal.equals(n))subtotalConflict=true;if(!subtotalConflict)r.subtotal=n;}}
                if(k==Kind.COUNT_TOTAL){Matcher q=Pattern.compile("(?<![0-9])([0-9]{1,3})\\s*(?:点|個)?$").matcher(s);if(q.find()){Integer v=count(q.group(1));if(r.printedItemCount!=null&&!r.printedItemCount.equals(v)){r.printedItemCount=null;warn(r,"購入点数が競合して未確定です。");}else if(!r.warnings.contains("購入点数が競合して未確定です。"))r.printedItemCount=v;}}
                if(k==Kind.TAX)tax(r,s);if(k==Kind.DISCOUNT)r.hasDiscount=true;continue;
            }
            if(afterTotal){r.lines.add(new Line(Kind.OTHER,s));continue;}
            if(s.matches(".*\\s-\\s*[¥]?"+MONEY+".*")){pending=null;active=-1;r.hasDiscount=true;r.lines.add(new Line(Kind.DISCOUNT,s));warn(r,"負の金額らしい行を除外しました。値引等の配分は未確定です。");continue;}
            Amount a=amount(s);
            if(a!=null){
                String name=a.name;Integer qty=null;Long unit=null;String raw=s;boolean uncertain=false;
                Matcher u=UNIT.matcher(name);
                if(u.find()){unit=money(u.group(1));qty=count(u.group(2));name=(name.substring(0,u.start())+name.substring(u.end())).trim();}
                Detail prefix=detail(name);if(prefix!=null){qty=prefix.quantity;unit=prefix.unit;name="";}
                Matcher q=COUNT_PREFIX.matcher(name);if(q.find()){qty=count(q.group(1));name=name.substring(q.end()).trim();}
                Matcher named=NAMED_UNIT.matcher(name);if(named.find()){unit=money(named.group(1));name=(name.substring(0,named.start())+name.substring(named.end())).trim();}
                boolean close=pending!=null&&ReceiptLayout.adjacent(pending.row,in.row);
                if(close&&(gap==0||qty!=null||unit!=null||pending.quantity!=null)){
                    boolean spec=specification(name);
                    boolean wrap=in.row!=null&&pending.row!=null&&ReceiptLayout.alignedName(pending.row,in.row,imageWidth)&&productName(name);
                    if(name.isEmpty()||spec||wrap){
                        uncertain=pending.uncertain||wrap&&!spec&&!name.isEmpty();name=pending.name+(name.isEmpty()?"":" "+name);raw=pending.raw+"\n"+s;
                        if(qty!=null&&pending.quantity!=null&&!qty.equals(pending.quantity)||unit!=null&&pending.unit!=null&&!unit.equals(pending.unit)){qty=null;unit=null;uncertain=true;warn(r,"隣接する単価・数量が競合しています。");}
                        else{if(qty==null)qty=pending.quantity;if(unit==null)unit=pending.unit;}
                    }
                }
                name=name.replaceFirst("^[*※]\\s*","").replaceFirst("\\s*[*※]$","").trim();
                if(!a.markers.isEmpty()){
                    Matcher marker=Pattern.compile("\\s+([CVcv])$").matcher(name);
                    if(marker.find()&&a.markers.toUpperCase(java.util.Locale.ROOT).contains(marker.group(1).toUpperCase(java.util.Locale.ROOT)))name=name.substring(0,marker.start()).trim();
                }
                if(a.value!=null&&a.value>0&&productName(name)&&r.items.size()<80){
                    if(a.rateUncertain)warn(r,"同一明細に異なる税率があり未確定です。");
                    AmountBasis b=a.basis==AmountBasis.UNKNOWN?r.amountBasis:a.basis;
                    r.items.add(candidate(r,name,a.value,raw,qty,unit,a.rate,a.markers,b,uncertain));r.lines.add(new Line(Kind.ITEM,raw));
                    pending=null;active=r.items.size()-1;activeRow=in.row;gap=0;continue;
                }
            }
            active=-1;
            if(productName(s)||specification(s)&&pending!=null){
                if(pending!=null&&in.row!=null&&gap==0&&pending.parts<3&&ReceiptLayout.adjacent(pending.row,in.row)&&ReceiptLayout.alignedName(pending.row,in.row,imageWidth)){
                    pending.name+=" "+s;pending.raw+="\n"+s;pending.row=in.row;pending.parts++;pending.uncertain|=!specification(s);
                }else pending=new Pending(s,in.row);
            }else pending=null;
            gap=0;r.lines.add(new Line(Kind.OTHER,s));
        }
        if(dateConflict){r.date="";warn(r,"日付に矛盾があるため未確定です。");}
        if(storeConflict){r.store="";warn(r,"店名候補が複数あり未確定です。");}
        if(totalConflict){r.total=null;warn(r,"異なる総額があり未確定です。");}if(subtotalConflict){r.subtotal=null;warn(r,"異なる小計があり未確定です。");}
        if(r.subtotalUncertain||r.totalUncertain)warn(r,"小計・合計の見出しにOCR表記揺れがあります。元の画像で確認してください。");
        if(r.taxAmount==null&&!r.taxBreakdowns.isEmpty()&&!r.warnings.contains("税額合計が競合して未確定です。")){
            long sum=0;boolean known=true;for(TaxBreakdown b:r.taxBreakdowns){known&=b.tax!=null&&!b.uncertain;if(b.tax!=null)sum+=b.tax;}
            if(known){r.taxAmount=sum;r.taxAmountFromBreakdown=true;}
        }
        if(r.items.size()==80)warn(r,"候補は80件までです。認識漏れを確認してください。");return r;
    }
}
