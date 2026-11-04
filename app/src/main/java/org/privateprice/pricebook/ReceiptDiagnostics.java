package org.privateprice.pricebook;

import java.util.List;

/** Ephemeral stage results only. The exporter reads enums, never OCR/evidence strings or numbers. */
public final class ReceiptDiagnostics {
    private ReceiptDiagnostics(){}
    public enum Token { TEXT_NO_REGIONS, NONE_OBSERVED, PARTIAL_MARKER, UNIT_LABEL, COUNT_LABEL, BARE_COUNT, PAIR, COMPLEMENTARY_FIELDS, LIMIT_REACHED }
    public enum Layout { TEXT_ONLY, SINGLE_REGION, MERGED_REGIONS, AMBIGUOUS }
    public enum Extraction { NONE, UNIT_ONLY, COUNT_ONLY, PAIR }
    public enum Association { NOT_ATTEMPTED, BOUND, INLINE_BOUND, AMOUNT_MISMATCH, FIELD_CONFLICT, GEOMETRY_REJECTED, NO_TARGET, BARE_COUNT_UNPROVEN }
    public enum Gate { PASS, FAIL, NOT_APPLICABLE }
    public enum CountCheck { NO_HINT, POSSIBLE_WITH_UNKNOWN, EXACT_KNOWN, BELOW_MINIMUM, KNOWN_CONTRADICTION }
    public enum SummaryStop { NOT_EVALUATED, INELIGIBLE, NO_AMOUNT, TOO_FEW_ITEMS, NOT_SMALL_HEADING, NO_COUNT_HINT, HEADING_NOT_ALLOWED, UNKNOWN_HEADING_REVIEW, COUNT_CONTRADICTION, SUM_CONTRADICTION, NO_POSITION, COLUMN_MISMATCH, NO_FOOTER_HELD, MATCHED, AMOUNT_MISMATCH_HELD }
    public enum Known { KNOWN, UNKNOWN }
    public enum Relation { LINKED, ATTEMPTED_NOT_LINKED, UNBOUND }
    public enum Route { POSITIONED, TEXT }
    public enum MoneyToken { NOT_EVALUATED, MISSING, OBSERVED, INVALID }
    public enum FiscalHeading { UNKNOWN, TOTAL, TOTAL_VARIANT, EXTENDED_TOTAL, SHORT_SHAPE, TAX, OTHER }
    public enum Binding { NOT_ATTEMPTED, INLINE, NEXT_ROW, FRAGMENT_ROWS, NO_AMOUNT, GAP_REJECTED, LAYOUT_REJECTED, CONSUMED }
    public enum Geometry { TEXT_ONLY, CLEAR, OVERLAP, COMPETING_ROWS, MULTIPLE_TEXT_AMOUNTS, MULTIPLE_RISKS, UNSPECIFIED }
    public enum TotalStop { NOT_EVALUATED, NOT_A_TOTAL, AMOUNT_MISSING, HEADING_REJECTED, NO_GOODS, NO_BOUNDARY, STRUCTURE_REJECTED, CANDIDATE, STRUCTURAL_CANDIDATE, LAYOUT_CANDIDATE, OBSERVED, LAYOUT_HELD, DAILY_REPORT, VALUE_BOUND, INPUT_LIMIT }
    public enum TaxStop { NOT_VISITED, HEADER, WAITING_AMOUNT, GRAMMAR_REJECTED, RATE_REJECTED, SECTION_GAP, ROLES_UNKNOWN, INVALID_NUMBER, OBSERVED_FIELDS, INFERRED_ROLES, MULTI_RATE_FIELDS, PARTIAL_MULTI_RATE, JOINED_RATE_PROPOSAL, CONFLICT }
    public enum FiscalRole { NONE, BASE, TAX, COLUMNS }
    public enum Rates { NONE, SINGLE_SUPPORTED, MULTIPLE_SUPPORTED, INHERITED_SUPPORTED, JOINED_PROPOSAL, UNSUPPORTED_OR_JOINED }
    public enum FiscalResult { NOT_OBTAINED, OBSERVED, CANDIDATE, CONFLICT, NEEDS_REVIEW }
    public enum Values { NONE, BASE_ONLY, TAX_ONLY, BASE_AND_TAX }
    /** Only bounded enum states; no raw values or text can enter this record. */
    public static final class FiscalAudit {
        MoneyToken money=MoneyToken.NOT_EVALUATED;
        FiscalHeading heading=FiscalHeading.UNKNOWN;
        Binding binding=Binding.NOT_ATTEMPTED;
        Geometry geometry=Geometry.TEXT_ONLY;
        TotalStop total=TotalStop.NOT_EVALUATED;
        TaxStop tax=TaxStop.NOT_VISITED;
        FiscalRole role=FiscalRole.NONE;
        Rates rates=Rates.NONE;
        Values values=Values.NONE;
        Gate grammar=Gate.NOT_APPLICABLE,boundary=Gate.NOT_APPLICABLE,column=Gate.NOT_APPLICABLE,sum=Gate.NOT_APPLICABLE,payment=Gate.NOT_APPLICABLE;
        ReceiptParser.AmountBasis basis=ReceiptParser.AmountBasis.UNKNOWN;
        FiscalAudit copy(){FiscalAudit n=new FiscalAudit();n.money=money;n.heading=heading;n.binding=binding;n.geometry=geometry;n.total=total;n.tax=tax;n.role=role;n.rates=rates;n.values=values;n.grammar=grammar;n.boundary=boundary;n.column=column;n.sum=sum;n.payment=payment;n.basis=basis;return n;}
        String localStatus(){return "集計段階：見出し="+heading+" / 金額="+money+" / 分割="+binding+" / 位置="+geometry+"\n合計停止="+total+" / 文脈="+boundary+" / 金額列="+column+" / 合算="+sum+" / 決済="+payment+"\n税停止="+tax+" / 文法="+grammar+" / 税率表記="+rates+" / 役割="+role+" / 方式="+basis+" / 内訳保持="+values;}
    }
    static Geometry geometry(ReceiptLayout.Row r){if(r==null)return Geometry.TEXT_ONLY;int n=(r.overlap?1:0)+(r.competing?1:0)+(r.multipleAmounts?1:0);return n>1?Geometry.MULTIPLE_RISKS:r.overlap?Geometry.OVERLAP:r.competing?Geometry.COMPETING_ROWS:r.multipleAmounts?Geometry.MULTIPLE_TEXT_AMOUNTS:r.uncertain?Geometry.UNSPECIFIED:Geometry.CLEAR;}
    static Gate result(boolean b){return b?Gate.PASS:Gate.FAIL;}
    public enum Reason {
        UNAVAILABLE, TOTAL_STRUCTURE_REVIEW, TOTAL_LAYOUT_REVIEW, TOTAL_CANDIDATE_REVIEW, TOTAL_CONTEXT_MATCHED_REVIEW, FISCAL_VALUE_BOUND, DAILY_REPORT_HELD, FUZZY_SUMMARY_REVIEW, STRUCTURAL_SUMMARY_REVIEW, WEIGHT_DETAIL_REVIEW, UNCLASSIFIED_HELD, STANDALONE_CODE_REVIEW, INPUT_LIMIT_HELD,
        PRE_DATE_HEADER_PRICE_HELD, PRE_DATE_TEXT_PRICE_HELD, MULTIPLE_ADMIN_SIGNALS_HELD,
        ADMIN_LABEL_STRUCTURE, OVERLAPPING_REGIONS_HELD, DATE_VALIDATION, STORE_HEADER,
        PRICE_BASIS_DECLARATION, UNIT_QUANTITY_UNBOUND, UNIT_QUANTITY_AMOUNT_MISMATCH,
        UNIT_QUANTITY_BOUND_TO_ITEM, UNIT_QUANTITY_GEOMETRY_REJECTED, UNIT_QUANTITY_NO_TARGET,
        UNIT_QUANTITY_FIELD_CONFLICT, BARE_COUNT_UNPROVEN,
        MONETARY_TAX_FOOTER_BOUNDARY, AFTER_SUMMARY_HELD, NEGATIVE_ADJUSTMENT,
        ITEM_POSITIONED_AMOUNT, ITEM_TEXT_AMOUNT, UNPRICED_NAME_OR_HELD, NAME_USED_IN_ITEM,
        SUMMARY_LIKE_NO_POSITION_HELD, SUMMARY_LIKE_COLUMN_MISMATCH_HELD,
        SUMMARY_LIKE_NO_FOOTER_HELD, SUBTOTAL_COLUMN_COUNT_SUM_FOOTER,
        SUMMARY_LIKE_AMOUNT_MISMATCH_HELD,
        LABEL_ITEM, LABEL_ITEM_DETAIL, LABEL_STORE, LABEL_DATE, LABEL_QUANTITY, LABEL_COUNT_TOTAL,
        LABEL_SUBTOTAL, LABEL_TAX, LABEL_TOTAL, LABEL_DISCOUNT, LABEL_PAYMENT, LABEL_MANAGEMENT,
        LABEL_HELD, LABEL_OTHER
    }
    public static final class Summary {
        public final SummaryStop stop;
        public final Gate knownStem,strongHeading,plausibleHeading,countHint,amountColumn,sumMatches,futureFooter;
        public final CountCheck count;
        Summary(SummaryStop stop,boolean knownStem,boolean strong,boolean plausible,boolean hint,boolean column,boolean sum,boolean footer,CountCheck count){
            this.stop=stop;this.knownStem=gate(knownStem);strongHeading=gate(strong);plausibleHeading=gate(plausible);countHint=gate(hint);amountColumn=gate(column);sumMatches=gate(sum);futureFooter=gate(footer);this.count=count;
        }
    }
    public static final class RowAudit {
        public final Token recognizedRegions,assembledRow;
        public final Layout layout;
        public final Association association;
        public final Extraction extraction;
        public final Summary summary;
        public final FiscalAudit fiscal;
        // A failed attempt is deliberately separate from a successful candidate link.
        final Integer attemptedCandidate;
        RowAudit(Token raw,Token row,Layout layout,Extraction extraction,Association association,Summary summary,Integer target){
            this(raw,row,layout,extraction,association,summary,target,new FiscalAudit());
        }
        RowAudit(Token raw,Token row,Layout layout,Extraction extraction,Association association,Summary summary,Integer target,FiscalAudit fiscal){this.extraction=extraction;recognizedRegions=raw;assembledRow=row;this.layout=layout;this.association=association;this.summary=summary;attemptedCandidate=target;this.fiscal=fiscal.copy();}
        public String localStatus(){
            return "OCR領域の数量表記："+recognizedRegions+"\n行構築："+layout+" / 行内数量表記："+assembledRow
                +"\n単価・数量の抽出："+extraction+" / 関連付け："+association+"\n小計の停止条件："+(summary==null?SummaryStop.NOT_EVALUATED:summary.stop)
                +"\n"+fiscal.localStatus()+"\nNONE_OBSERVEDはOCR出力に表記を確認できない状態です。原本に印字がないのか、OCRが落としたのかは原本照合が必要です。";
        }
    }
    private static Gate gate(boolean value){return value?Gate.PASS:Gate.FAIL;}
    private static Reason reason(String value){try{return Reason.valueOf(value);}catch(Exception ignored){return Reason.UNAVAILABLE;}}
    private static void field(StringBuilder out,String key,Enum<?> value){out.append(key).append('=').append(value==null?"NOT_AVAILABLE":value.name()).append('\n');}
    private static StringBuilder header(ReceiptParser.Receipt receipt){
        StringBuilder out=new StringBuilder("SCHEMA=OCR_PIPELINE_V1\n");
        out.append("FISCAL_SCHEMA=FISCAL_STAGES_V1\n");
        field(out,"FISCAL_TOTAL_RESULT",receipt.totalResult);field(out,"FISCAL_TAX_RESULT",receipt.taxResult);field(out,"FISCAL_SUBTOTAL_RESULT",receipt.subtotalResult);
        field(out,"ROUTE","POSITIONED".equals(receipt.analysisMode)?Route.POSITIONED:Route.TEXT);return out;
    }
    private static void row(StringBuilder out,ReceiptParser.Decision d,Relation relation){
        out.append("[ROW]\n");field(out,"RELATION",relation);field(out,"KIND",d.kind);field(out,"REASON",reason(d.reason));
        RowAudit a=d.audit;if(a==null)return;
        field(out,"REGION_TOKEN",a.recognizedRegions);field(out,"LAYOUT",a.layout);field(out,"ROW_TOKEN",a.assembledRow);field(out,"EXTRACTION",a.extraction);field(out,"ASSOCIATION",a.association);
        FiscalAudit f=a.fiscal;
        field(out,"FISCAL_HEADING",f.heading);field(out,"FISCAL_AMOUNT",f.money);field(out,"FISCAL_BINDING",f.binding);field(out,"FISCAL_GEOMETRY",f.geometry);
        field(out,"TOTAL_STOP",f.total);field(out,"FISCAL_BOUNDARY",f.boundary);field(out,"FISCAL_COLUMN",f.column);field(out,"FISCAL_SUM",f.sum);field(out,"FISCAL_PAYMENT",f.payment);
        field(out,"TAX_STOP",f.tax);field(out,"TAX_GRAMMAR",f.grammar);field(out,"TAX_RATES",f.rates);field(out,"TAX_ROLE",f.role);field(out,"TAX_BASIS",f.basis);field(out,"TAX_VALUES",f.values);
        Summary s=a.summary;if(s==null)return;
        field(out,"SUMMARY_STOP",s.stop);field(out,"KNOWN_STEM",s.knownStem);field(out,"STRONG_HEADING",s.strongHeading);field(out,"PLAUSIBLE_HEADING",s.plausibleHeading);
        field(out,"COUNT_HINT",s.countHint);field(out,"COUNT_CHECK",s.count);field(out,"AMOUNT_COLUMN",s.amountColumn);field(out,"SUM_MATCH",s.sumMatches);field(out,"FUTURE_FOOTER",s.futureFooter);
    }
    private static boolean relates(ReceiptParser.Decision d,int candidate){return d.candidates.contains(candidate)||d.audit!=null&&Integer.valueOf(candidate).equals(d.audit.attemptedCandidate);}
    private static Relation relation(ReceiptParser.Decision d,int candidate){return d.candidates.contains(candidate)?Relation.LINKED:Relation.ATTEMPTED_NOT_LINKED;}
    private static void candidate(StringBuilder out,ReceiptParser.Receipt receipt,int number){
        ReceiptParser.Candidate c=receipt.items.get(number-1);out.append("[CANDIDATE]\n");
        field(out,"UNIT",c.unitPrice==null?Known.UNKNOWN:Known.KNOWN);field(out,"QUANTITY",c.quantity==null?Known.UNKNOWN:Known.KNOWN);
    }
    public static String anonymousCandidate(ReceiptParser.Receipt receipt,int number){
        if(receipt==null||number<1||number>receipt.items.size())return "";
        StringBuilder out=header(receipt);candidate(out,receipt,number);
        int rows=0;
        for(ReceiptParser.Decision d:receipt.decisions)if(relates(d,number)){
            StringBuilder block=new StringBuilder();row(block,d,relation(d,number));
            if(++rows>80||out.length()+block.length()>60000)return out.append("REPORT=TRUNCATED\n").toString();out.append(block);
        }return out.toString();
    }
    /** One explicit copy covers every candidate and failed/unbound measure/summary row.
     * No numerical IDs/counts, free-form reasons/evidence, names, amounts, dates or coordinates. */
    public static String anonymousReceipt(ReceiptParser.Receipt receipt){
        if(receipt==null)return "";StringBuilder out=header(receipt);int rows=0;
        for(int n=1;n<=receipt.items.size();n++){
            candidate(out,receipt,n);
            for(ReceiptParser.Decision d:receipt.decisions)if(relates(d,n)){
                StringBuilder block=new StringBuilder();row(block,d,relation(d,n));
                if(++rows>80||out.length()+block.length()>60000)return out.append("REPORT=TRUNCATED\n").toString();out.append(block);
            }
        }
        for(ReceiptParser.Decision d:receipt.decisions){
            RowAudit a=d.audit;
            // Include OTHER/AFTER_SUMMARY_HELD too: an unsupported total used to vanish here.
            if(!d.candidates.isEmpty()||a==null||a.attemptedCandidate!=null)continue;
            StringBuilder block=new StringBuilder();row(block,d,Relation.UNBOUND);
            if(++rows>80||out.length()+block.length()>60000)return out.append("REPORT=TRUNCATED\n").toString();out.append(block);
        }
        return out.toString();
    }
    public static List<ReceiptParser.Decision> attemptsForCandidate(ReceiptParser.Receipt receipt,int number){
        java.util.ArrayList<ReceiptParser.Decision> out=new java.util.ArrayList<>();
        for(ReceiptParser.Decision d:receipt.decisions)if(relates(d,number)&&!d.candidates.contains(number))out.add(d);return out;
    }
}
