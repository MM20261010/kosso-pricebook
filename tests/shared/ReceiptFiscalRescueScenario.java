package org.privateprice.pricebook;
import java.util.*;
/** New fictional amounts deliberately differ from phone diagnostics. Compiles against v0.8.12. */
public final class ReceiptFiscalRescueScenario {
    static OcrDocument.Region b(String s,float x,float y,float w){return ReceiptAuditScenario.box(s,x,y,w);}
    public static OcrDocument fixture(int mode){
        List<OcrDocument.Region> a=new ArrayList<>();a.add(b("FICTIVE MARKET",70,20,400));a.add(b("2026/11/04 19:00",70,55,500));
        String[] names={"架空もやし200g","架空パン6枚入","架空コーヒー500g","架空豆腐300g","架空洗剤CV-20","架空ビタミンC"};long[] prices={126,182,1300,630,421,341};float y=100;
        for(int n=0;n<6;n++){a.add(b(names[n],70,y,550));a.add(b(prices[n]+"C",850,y+4,150));if(n==1||n==3){a.add(b(n==1?"@91×2":"@315×2",120,y+38,280));y+=38;}y+=46;}
        a.add(b("小未読8点",70,y,350));a.add(b("3000",850,y+4,150));y+=40;
        a.add(b(mode==4?"内計":mode==6?"対像額":"点計",70,y,400));y+=36;
        String[] bases=mode==7?new String[]{"8%5点2160","10%3点840"}:new String[]{"8%5点1800","10%3点1200"};
        for(String line:bases){a.add(b(line,70,y,920));y+=36;}
        a.add(b("消費税額(内訳)",70,y,400));y+=36;
        for(String line:mode==7?new String[]{"8%160","10%76"}:new String[]{"8%144","10%120"}){a.add(b(line,70,y,920));y+=36;}
        if(mode==1){a.add(b("白",70,y,110));a.add(b("言",125,y+2,110));a.add(b("十",185,y+3,100));a.add(b("3.264",850,y+4,150));}
        else a.add(b(mode==2?"白言十3264":mode==3?"白言十3.274":mode==7?"白言十3.000":"白言十3.264",70,y,920));y+=40;
        if(mode==5){a.add(b("白言十3.000",70,y,920));y+=40;}
        for(String line:new String[]{"カード支払2000","現金支払1000","ポイント支払264","預り金1500","お釣り500"}){a.add(b(line,70,y,920));y+=35;}
        return new OcrDocument("fictional damaged fiscal notation",1200,1600,a);
    }
    public static OcrDocument duplicateFiscal(OcrDocument original){
        List<OcrDocument.Region> a=new ArrayList<>(original.regions);
        // Fixed synthetic drawing's footer only; no text matching can silently disable injection.
        for(OcrDocument.Region r:original.regions)if(r.y(0)>=680&&a.size()<80){float[] corners=new float[8];for(int n=0;n<4;n++){corners[2*n]=Math.min(original.width-1,r.x(n)+2);corners[2*n+1]=Math.min(original.height-1,r.y(n)+2);}a.add(new OcrDocument.Region(r.text,corners));}
        return new OcrDocument(original.text,original.width,original.height,a);
    }
}
