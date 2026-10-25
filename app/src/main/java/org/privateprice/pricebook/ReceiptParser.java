package org.privateprice.pricebook;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded suggestions. A printed line amount is never assumed to be a gross unit price. */
public final class ReceiptParser {
    private ReceiptParser() { }
    public enum Kind { ITEM, STORE, DATE, QUANTITY, SUBTOTAL, TAX, TOTAL, DISCOUNT, PAYMENT, OTHER }
    public static final class Line {
        public final Kind kind; public final String raw;
        Line(Kind k,String raw){kind=k;this.raw=raw;}
    }
    public static final class Candidate {
        public final String name,raw;
        public final long price; // Printed line amount; NOT a unit price.
        public final Integer quantity,taxRate;
        public final Long unitPrice;
        Candidate(String n,long p,String r,Integer q,Long u,Integer t){name=n;price=p;raw=r;quantity=q;unitPrice=u;taxRate=t;}
    }
    public static final class Receipt {
        public final List<Candidate> items=new ArrayList<>();
        public final List<Line> lines=new ArrayList<>();
        public final List<String> warnings=new ArrayList<>();
        public String store="",date="";
        public Long total,subtotal,taxAmount;
        public boolean hasDiscount,includesTax;
        public String reconciliation(){
            if(total==null)return "総額が未確定で照合できません。";
            if(hasDiscount)return "割引・返品があります。商品への配分を確認してください。総額との自動照合は未確定です。";
            long sum=0;for(Candidate c:items)sum+=c.price;
            if(items.isEmpty())return "商品が見つからず、総額と照合できません。";
            if(!includesTax)return "税込・税抜が未確定です。明細計 "+sum+"円 / 総額 "+total+"円（参考）。";
            return sum==total?"明細の行金額合計と総額が一致。数量・単価・税率は別途確認してください。":
                "警告：明細計 "+sum+"円 と総額 "+total+"円 が不一致。認識漏れ・誤読を確認してください。";
        }
    }
    private static final String MONEY="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})";
    private static final Pattern AMOUNT=Pattern.compile("^(.*?)\\s*[¥]?(-?"+MONEY+")\\s*円?\\s*(?:(8|10)\\s*%|([*※軽外内非]))?$");
    private static final Pattern DATE=Pattern.compile("(?<![0-9])(20[0-9]{2})[年/.-]\\s*([0-9]{1,2})[月/.-]\\s*((?>[0-9]{1,2}))日?(?:(?![0-9])|(?=(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?(?![0-9])))");
    private static final Pattern COUNT=Pattern.compile("(?:^|\\s)([0-9]{1,3})\\s*(?:個|点|本|袋|枚|パック)(?=\\s|$|[×xX])");
    private static final Pattern UNIT=Pattern.compile("(?:@|単価\\s*|[¥])("+MONEY+")\\s*円?\\s*[×xX]\\s*([0-9]{1,3})");
    private static final Pattern NAMED_UNIT=Pattern.compile("(?:^|\\s)単価\\s*[¥]?("+MONEY+")円?");
    private static Kind kind(String s){
        String t=s.replaceAll("\\s+","");
        if(t.matches(".*(?:値引|割引|クーポン|返品|返金|ポイント利用).*"))return Kind.DISCOUNT;
        if(t.matches("^(?:数量|個数|購入点数|購入数|商品点数|お買上げ点数).*"))return Kind.QUANTITY;
        if(t.matches("^(?:小計|商品計|本体価格合計|買上点数|お買上点数|お買い上げ点数|点数|数量合計|合計点数).*"))return Kind.SUBTOTAL;
        if(t.matches("^(?:総合計|総計|総額|税込合計|合計|計[¥0-9]|お買上金額|お買い上げ金額|お買上げ金額|お支払(?:い)?(?:金額|合計)|支払(?:い)?金額).*"))return Kind.TOTAL;
        if(t.matches(".*(?:消費税|税額|税金|内税|外税|課税対象|軽減税率|税率|対象額|対象金額).*" )
                ||t.matches("^(?:[0-9]+%|[(*※]?軽減|税計|税合計|内計|税[0-9¥]|点計|税込|税抜|非課税|課税|うち税|内消費|対象|※|\\*).*"))return Kind.TAX;
        if(t.matches("(?i)^(?:現金|現計|預り|預かり|お預|釣|お釣|おつり|クレジット|カード|支払|お支払|お買上|お買い上げ|お買上げ|電子マネー|電子決済|決済|残高|ポイント|獲得|今回|累計|利用可能|チャージ|入金|PayPay|楽天ペイ|交通系|商品券|領収|レジ|担当|責任者|会員|取引|伝票|レシート|登録番号|TEL|電話|営業時間|住所|〒|No[.:]).*"))return Kind.PAYMENT;
        // Reject near-miss administrative labels too (e.g. OCR 消貴税 / 台計).
        Matcher amount=AMOUNT.matcher(s);
        if(amount.matches()){
            String label=amount.group(1).replaceAll("[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]", "");
            for(String administrative:new String[]{"合計","小計","消費税","税額","点数","支払金額","現金","お釣り","内税","外税","税込","税抜"})
                if(label.length()>=2&&oneEdit(label,administrative))return Kind.PAYMENT;
        }
        return Kind.OTHER;
    }
    private static boolean oneEdit(String a,String b){
        if(Math.abs(a.length()-b.length())>1)return false;
        int i=0,j=0,edits=0;
        while(i<a.length()&&j<b.length()){
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(a.length()>=b.length())i++;
            if(b.length()>=a.length())j++;
        }
        return edits+(a.length()-i)+(b.length()-j)<=1;
    }
    private static String normal(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).replace('￥','¥').replace('−','-').replace('△','-').replaceAll("\\s+"," ").trim();}
    private static Long money(String s){try{long n=Long.parseLong(s.replace(",",""));return n>=0&&n<=1_000_000?n:null;}catch(Exception e){return null;}}
    private static Integer count(String s){try{int n=Integer.parseInt(s);return n>=1&&n<=999?n:null;}catch(Exception e){return null;}}
    private static Long lastAmount(String s){Matcher m=AMOUNT.matcher(s);return m.matches()?money(m.group(2)):null;}
    private static boolean productName(String s){
        return s.length()>=1&&s.length()<=120&&kind(s)==Kind.OTHER
            &&s.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z].*")
            &&!s.matches(".*(?:スーパー|ストア|マート|株式会社|領収|支払い|点数).*")
            &&!s.matches("^(?:[0-9]+[点個本袋枚]|単価|税込|税抜|[¥@]).*")
            &&!s.matches(".*(?:店|支店)$");
    }
    public static List<Candidate> parse(String text){return parseReceipt(text).items;}
    public static Receipt parseReceipt(String text){
        Receipt r=new Receipt();if(text==null)return r;
        if(text.length()>32000){r.warnings.add("認識テキストが長すぎます。分割してください。");return r;}
        String[] input=text.split("\\r?\\n",-1);
        if(input.length>500){r.warnings.add("行数が多すぎます。分割してください。");return r;}
        String pending="";int pendingGap=0;boolean afterTotal=false,dateConflict=false,storeConflict=false;
        for(String source:input){
            if(source.length()>240){pending="";r.warnings.add("長すぎる行を除外しました。");continue;}
            String s=normal(source);if(s.isEmpty()){if(++pendingGap>2)pending="";continue;}
            Matcher date=DATE.matcher(s);
            if(date.find()){
                try{String d=LocalDate.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3))).toString();
                    if(!r.date.isEmpty()&&!r.date.equals(d))dateConflict=true;
                    if(!dateConflict)r.date=d;
                }catch(Exception e){dateConflict=true;r.warnings.add("実在しない日付を検出しました。");}
                r.lines.add(new Line(Kind.DATE,s));pending="";continue;
            }
            if(s.startsWith("店名:")||s.matches(".{2,100}(?:スーパー|ストア|マート|支店|店)$")){
                String shop=s.replaceFirst("^店名:\\s*","");
                if(!r.store.isEmpty()&&!r.store.equals(shop))storeConflict=true;
                if(!storeConflict)r.store=shop;
                r.lines.add(new Line(Kind.STORE,s));pending="";continue;
            }
            s=s.replaceFirst("^数量(?=[0-9])","数量 ");
            Kind k=kind(s);
            if(k==Kind.QUANTITY&&!pending.isEmpty()&&s.startsWith("数量"))k=Kind.OTHER;
            if(k!=Kind.OTHER){
                r.lines.add(new Line(k,s));pending="";Long n=lastAmount(s);
                if(k==Kind.TOTAL){
                    if(n!=null){if(r.total!=null&&!r.total.equals(n))r.warnings.add("異なる総額があります。");r.total=n;}
                    afterTotal=true;
                }
                if(k==Kind.SUBTOTAL&&s.replace(" ","").startsWith("小計"))r.subtotal=n;
                if(k==Kind.TAX&&s.matches(".*(?:消費税|税額|内税).*"))r.taxAmount=n;
                if(k==Kind.DISCOUNT)r.hasDiscount=true;
                continue;
            }
            if(afterTotal){r.lines.add(new Line(Kind.OTHER,s));continue;}
            if(s.matches(".*\\s-\\s*[¥]?"+MONEY+".*")){pending="";r.warnings.add("負の金額らしい行を除外しました。");continue;}
            Matcher m=AMOUNT.matcher(s);
            if(m.matches()){
                Long amount=money(m.group(2));String name=m.group(1).trim();
                Integer qty=null;Long unit=null;
                Matcher u=UNIT.matcher(name);
                if(u.find()){unit=money(u.group(1));qty=count(u.group(2));name=(name.substring(0,u.start())+name.substring(u.end())).trim();}
                Matcher q=COUNT.matcher(name);
                if(q.find()&&(q.start()==0||name.substring(0,q.start()).trim().equals("数量"))){qty=count(q.group(1));name=name.substring(q.end()).trim();}
                Matcher named=NAMED_UNIT.matcher(name);
                if(named.find()){unit=money(named.group(1));name=(name.substring(0,named.start())+name.substring(named.end())).trim();}
                String raw=s;
                if(!pending.isEmpty()&&(pendingGap==0||qty!=null||unit!=null)&&(name.isEmpty()||qty!=null||unit!=null)){
                    if(name.isEmpty()){name=pending;raw=pending+"\n"+s;}
                }
                name=name.replaceFirst("^[*※]\\s*","").trim();
                if(amount!=null&&amount>0&&productName(name)&&r.items.size()<80){
                    Integer tax=m.group(3)==null?null:Integer.valueOf(m.group(3));
                    if(unit!=null&&qty!=null&&unit*qty!=amount)r.warnings.add("単価×数量と行金額が不一致の候補があります。");
                    r.items.add(new Candidate(name,amount,raw,qty,unit,tax));
                    r.lines.add(new Line(Kind.ITEM,raw));pending="";continue;
                }
            }
            pending=productName(s)&&!s.matches(".*[¥].*")?s:"";pendingGap=0;
            r.lines.add(new Line(Kind.OTHER,s));
        }
        if(dateConflict){r.date="";r.warnings.add("日付に矛盾があるため未確定です。");}
        if(storeConflict){r.store="";r.warnings.add("店名候補が複数あり未確定です。");}
        r.includesTax=normal(text).matches(".*(?:税込価格|税込表示|全品税込|内税方式).*" );
        if(r.items.size()==80)r.warnings.add("候補は80件までです。認識漏れを確認してください。");
        return r;
    }
}
