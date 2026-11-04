package org.privateprice.pricebook;
import java.util.*;
/** Fixed fictional geometry. No personal receipt text or identifiers. Also compiles against v0.8.11. */
public final class ReceiptFiscalRecoveryScenario {
    static OcrDocument.Region b(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    public static OcrDocument fixture(int mode){
        List<OcrDocument.Region> a=new ArrayList<>();float y=0;
        for(OcrDocument.Region r:ReceiptFiscalTest.fixture(false,false).regions){if(r.text.startsWith("税率")){y=r.y(0);break;}if(mode!=3||!r.text.equals("税抜表示"))a.add(r);}
        if(mode==3){a.add(b("税額O",70,y,180));y+=40;}
        a.add(b(mode==3?"税率区分 対象点数 対象額 消費税額(内訳)":"税率区分 対象点数 税抜対象額 消費税額(内訳)",70,y,920));y+=40;
        if(mode==1){
            for(String s:new String[]{"8%","5点","960","77","10%","3点","1912","191"}){a.add(b(s,70,y,200));y+=32;}
        }else{
            for(String s:new String[]{"8%5点960 77","10%3点1912 191"}){a.add(b(s,70,y,920));y+=40;}
        }
        a.add(b("日",70,y,120));a.add(b("計",140,y+2,120));a.add(b("3,140",850,y,160));
        if(mode==2)a.add(b("3,190",852,y+2,160));y+=45;
        for(String s:new String[]{"カード支払2000","現金支払1000","ポイント支払140","預り金1500","お釣り500"}){a.add(b(s,70,y,920));y+=35;}
        return new OcrDocument("fictional fiscal reconstruction",1200,1800,a);
    }
    /** Explicit fault injection into actual model output; not a claim that the model emitted duplicates. */
    public static OcrDocument duplicateFiscal(OcrDocument original){
        List<OcrDocument.Region> a=new ArrayList<>(original.regions);float after=original.height;
        for(OcrDocument.Region r:original.regions)if(r.text.contains("小未読"))after=Math.min(after,r.y(0));
        for(OcrDocument.Region r:original.regions)if(r.y(0)>after&&a.size()<80){float[] corners=new float[8];for(int n=0;n<4;n++){corners[2*n]=Math.min(original.width-1,r.x(n)+2);corners[2*n+1]=Math.min(original.height-1,r.y(n)+2);}a.add(new OcrDocument.Region(r.text,corners));}
        return new OcrDocument(original.text,original.width,original.height,a);
    }
}
