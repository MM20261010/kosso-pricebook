package org.privateprice.pricebook;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.database.DatabaseErrorHandler;
import java.io.File;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Only bundled synthetic fixtures. A separate target-cache test database is used, never the purchase database. */
public final class OfflineOcrInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean value){checks++;if(!value)throw new AssertionError("synthetic check "+checks);}
    private long fingerprint(Bitmap image){
        long value=1;int[] row=new int[image.getWidth()];
        for(int y=0;y<image.getHeight();y++){image.getPixels(row,0,row.length,0,y,row.length,1);for(int pixel:row)value=value*31+pixel;}
        return value;
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            check(BuildConfig.DEBUG);
            PaddleOcr.verifyAssets(getTargetContext().getAssets());checks++;
            JSONArray fixtures;
            try(InputStream in=getContext().getAssets().open("synthetic-manifest.json")){
                fixtures=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("fixtures");
            }
            for(int index=0;index<fixtures.length();index++){
                JSONObject fixture=fixtures.getJSONObject(index);String file=fixture.getString("image");
                Bitmap image;
                try(InputStream in=getContext().getAssets().open(file)){image=BitmapFactory.decodeStream(in);}
                check(image!=null);long before=fingerprint(image);
                try{
                    OcrDocument document=PaddleOcr.recognizeDocument(getTargetContext(),image);
                    check(!document.regions.isEmpty());
                    ReceiptParser.Receipt receipt=ReceiptParser.parseDocument(document);
                    check(receipt.items.size()==fixture.getJSONArray("items").length());check(receipt.date.equals(fixture.getString("date")));
                    check(!receipt.store.isEmpty());
                    JSONArray names=fixture.getJSONArray("items"),amounts=fixture.getJSONArray("line_amounts");
                    for(int item=0;item<names.length();item++){
                        final String name=names.getString(item).replaceAll("\\s+","");final long amount=amounts.getLong(item);
                        boolean hasSuffix=false;
                        if(fixture.has("ambiguous_suffix_items")){JSONArray indexes=fixture.getJSONArray("ambiguous_suffix_items");for(int j=0;j<indexes.length();j++)hasSuffix|=indexes.getInt(j)==item;}
                        final boolean suffix=hasSuffix;
                        ReceiptParser.Candidate matched=null;
                        for(ReceiptParser.Candidate c:receipt.items)if((c.name.replaceAll("\\s+","").equals(name)||suffix&&c.nameSuffixUncertain&&c.name.replaceAll("\\s+","").equals(name+"C"))&&c.price==amount){matched=c;break;}
                        check(matched!=null);
                        if(fixture.has("quantities")){
                            final int q=fixture.getJSONArray("quantities").getInt(item);final long unit=fixture.getJSONArray("units").getLong(item);
                            check(matched!=null&&Integer.valueOf(q).equals(matched.quantity)&&Long.valueOf(unit).equals(matched.unitPrice));
                        }
                    }
                    if(fixture.has("subtotal")){
                        check(receipt.itemSum()==2872&&receipt.knownQuantitySum()==8&&receipt.unknownQuantityCount()==0);
                        check(Long.valueOf(2872).equals(receipt.subtotal)&&Long.valueOf(268).equals(receipt.taxAmount)&&Long.valueOf(3140).equals(receipt.total));
                        check(receipt.taxBreakdowns.size()==2&&receipt.items.stream().allMatch(c->c.taxRate==null&&c.amountBasis==ReceiptParser.AmountBasis.EXCLUSIVE));
                        check(receipt.reconciliation().contains("金額一致"));
                    }
                    check(!image.isRecycled()&&fingerprint(image)==before);
                    String tess=ReceiptOcr.recognize(getTargetContext(),image);
                    check(!tess.isEmpty()&&!image.isRecycled());check(fingerprint(image)==before);
                    if(file.equals("multiline-clean.png"))check(receipt.items.stream().anyMatch(c->Integer.valueOf(2).equals(c.quantity)&&Long.valueOf(100).equals(c.unitPrice)));
                }finally{image.recycle();}
            }
            // Never open/delete the real purchase database, even if this runner is invoked on a phone.
            File folder=new File(getTargetContext().getCacheDir(),"synthetic-db-test");
            if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException();
            Context isolated=new ContextWrapper(getTargetContext()){
                @Override public File getDatabasePath(String name){if(!name.equals("private_pricebook.db"))throw new IllegalArgumentException();return new File(folder,name);}
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
                @Override public boolean deleteDatabase(String name){return SQLiteDatabase.deleteDatabase(getDatabasePath(name));}
            };
            isolated.deleteDatabase("private_pricebook.db");
            PriceDb db=new PriceDb(isolated);
            try{
                PriceDb.Purchase p=new PriceDb.Purchase();p.name="架空牛乳1L";p.key=PriceMath.key(p.name);p.store="架空テスト店";p.date="2026-10-25";p.gross=218;p.quantity=2;p.tax=8;p.unit="";
                db.addPurchase(p);String backup=db.backupJson();db.importBackup(backup);
                List<PriceDb.Purchase> rows=db.recentPurchases(10);check(rows.size()==1);check(rows.get(0).gross==218&&rows.get(0).quantity==2&&rows.get(0).date.equals("2026-10-25"));
            }finally{db.close();isolated.deleteDatabase("private_pricebook.db");}
            result.putString("status","PASS synthetic-only offline OCR and JSON roundtrip");result.putInt("checks",checks);finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            // Never include OCR text, purchase data, paths or exception messages in CI output.
            result.putString("status","FAIL synthetic check "+checks);result.putString("failure_type",failure.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
