package org.privateprice.pricebook;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative suggestions only: never silently insert OCR results into purchase history. */
public final class ReceiptParser {
    private ReceiptParser() { }
    public static final class Candidate {
        public final String name;
        public final long price;
        public final String raw;
        Candidate(String name, long price, String raw) {this.name=name;this.price=price;this.raw=raw;}
    }
    private static final Pattern LAST_PRICE=Pattern.compile("^(.{2,100}?)\\s+[¥￥]?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})\\s*[円*＊※]?\\s*$");
    private static final Pattern NON_ITEM=Pattern.compile("^(?:小計|合計|総合計|お買上|お買い上げ|お預り|お預かり|お釣|釣銭|現金|クレジット|カード|税込|税抜|消費税|内税|外税|税額|ポイント|お支払|支払|対象|軽減|登録番号|領収|割引合計|値引合計|TEL|電話|取引番号|担当|現計|合 計).*",Pattern.CASE_INSENSITIVE);
    public static List<Candidate> parse(String ocrText) {
        List<Candidate> rows=new ArrayList<>();
        if(ocrText==null)return rows;
        String[] lines=ocrText.replace('￥','¥').split("\\r?\\n",201);
        for(String source:lines){
            if(rows.size()>=80)break;
            String raw=source.trim();if(raw.isEmpty())continue;
            String s=Normalizer.normalize(raw,Normalizer.Form.NFKC).replace('\u3000',' ').replaceAll("\\s+"," ").trim();
            if(s.length()>160||NON_ITEM.matcher(s).matches()||s.startsWith("-")||s.matches("^[0-9:./ -]+$"))continue;
            Matcher m=LAST_PRICE.matcher(s);if(!m.matches())continue;
            String name=m.group(1).trim().replaceFirst("^[*＊※]\s*","");
            if(name.length()<2||name.matches("^[0-9.,/ :]+$")||NON_ITEM.matcher(name).matches())continue;
            // OCR often sees discounts as item lines. Show them only in original text, never as items.
            if(name.matches(".*(?:値引|割引|クーポン|ポイント利用|返品|返金).*"))continue;
            long value;
            try{value=Long.parseLong(m.group(2).replace(",",""));}catch(NumberFormatException e){continue;}
            if(value<1||value>1_000_000)continue;
            rows.add(new Candidate(name,value,raw));
        }
        return rows;
    }
}
