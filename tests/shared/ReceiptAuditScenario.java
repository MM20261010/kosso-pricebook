package org.privateprice.pricebook;
import java.util.*;
/** Fixed fictional perturbations: change one cause at a time, not a receipt transcription. */
public final class ReceiptAuditScenario {
    public static final String[] IDS={"control","position_only","label_erosion","subtotal_split","combined"};
    static OcrDocument.Region box(String s,float x,float y,float w){return new OcrDocument.Region(s,x,y,x+w,y,x+w,y+24,x,y+24);}
    public static String text(int variant){StringBuilder out=new StringBuilder();for(ReceiptLayout.Row row:ReceiptLayout.rows(fixture(variant)))out.append(row.text).append('\n');return out.toString();}
    public static OcrDocument fixture(int variant){
        List<OcrDocument.Region> result=new ArrayList<>();
        for(OcrDocument.Region r:ReceiptExtractionTest.fixture(0).regions){
            if(variant==1||variant==2||variant==4){
                if(r.text.contains("架空受付")){result.add(box(variant==1?r.text:"架空受付店鋪OOレシN0OO取INoI2,345",30,58,900));continue;}
                if(r.text.startsWith("2026/")){result.add(box(r.text,70,106,400));continue;}
                if(r.text.equals("全品税抜表示")){result.add(box(r.text,70,152,300));continue;}
            }
            if((variant==3||variant==4)&&r.text.startsWith("小汁")){
                result.add(box("小鈄8占",70,r.y(0),300));result.add(box("2872",850,r.y(0)+4,140));continue;
            }
            result.add(r);
        }
        return new OcrDocument("fictional one-factor OCR/layout perturbations",1200,1600,result);
    }
}
