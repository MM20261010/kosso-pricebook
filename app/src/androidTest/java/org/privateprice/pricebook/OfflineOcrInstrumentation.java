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
    private void reviewUi(Context isolated,ReceiptParser.Receipt receipt) throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{
            MainActivity activity=null;
            try{
                android.content.Intent intent=new android.content.Intent(getTargetContext(),MainActivity.class);
                android.content.pm.ActivityInfo info=getTargetContext().getPackageManager().getActivityInfo(intent.getComponent(),0);
                activity=(MainActivity)newActivity(MainActivity.class,isolated,null,(android.app.Application)getTargetContext().getApplicationContext(),intent,info,"synthetic",null,null,null);
                callActivityOnCreate(activity,null);
                ReceiptReview model=new ReceiptReview(receipt);
                java.lang.reflect.Field f=MainActivity.class.getDeclaredField("reviewModel");f.setAccessible(true);f.set(activity,model);
                for(String name:new String[]{"reviewText","reviewedText"}){f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,"synthetic UI only");}
                f=MainActivity.class.getDeclaredField("content");f.setAccessible(true);android.widget.LinearLayout content=(android.widget.LinearLayout)f.get(activity);content.removeAllViews();
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("showOcrReview");render.setAccessible(true);render.invoke(activity);
                check(visibleEditors(content)==2);String visible=visibleText(content);
                check(visible.contains("79円 × 2点 ＝ 158円")&&visible.contains("245円 × 2点 ＝ 490円"));
                check(!visible.contains("000000")&&!visible.contains("原文：")&&!visible.contains("MANAGEMENT")&&!visible.contains("検出座標"));
                check(model.items.stream().noneMatch(i->i.selected||i.grossUnit!=null));
                int width=(int)(360*getTargetContext().getResources().getDisplayMetrics().density);
                content.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                check(content.getMeasuredHeight()>0);
                // This is a detached synthetic rendering, not a phone interaction benchmark.
            }catch(Throwable e){failure.set(e);}finally{if(activity!=null)callActivityOnDestroy(activity);}
        });
        if(failure.get()!=null)throw new IllegalStateException("Synthetic UI failed",failure.get());
    }
    private int visibleEditors(android.view.View view){
        if(view.getVisibility()!=android.view.View.VISIBLE)return 0;int count=view instanceof android.widget.EditText?1:0;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=visibleEditors(group.getChildAt(i));}return count;
    }
    private String visibleText(android.view.View view){
        if(view.getVisibility()!=android.view.View.VISIBLE)return "";StringBuilder text=new StringBuilder();if(view instanceof android.widget.TextView)text.append(((android.widget.TextView)view).getText()).append('\n');
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)text.append(visibleText(group.getChildAt(i)));}return text.toString();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            check(BuildConfig.DEBUG);
            try(InputStream in=getTargetContext().getAssets().open("build-info.json")){
                JSONObject build=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
                check(build.getInt("version_code")==15&&BuildConfig.VERSION_CODE==15);
                check(build.getString("source_commit").equals(BuildConfig.SOURCE_COMMIT)&&build.getBoolean("working_tree_dirty")==BuildConfig.SOURCE_DIRTY);
            }
            checks+=ReceiptClassificationAuditTest.run();
            checks+=ReceiptPairingTest.run();
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
            ReceiptExtractionTest.main(new String[0]);checks+=ReceiptExtractionTest.checks;
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
                // Real v1 SQLite layout -> additive v2 migration, using only this isolated database.
                db.close();isolated.deleteDatabase("private_pricebook.db");
                try(SQLiteDatabase old=isolated.openOrCreateDatabase("private_pricebook.db",0,null)){
                    old.execSQL("CREATE TABLE memos(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,product_key TEXT NOT NULL,quantity INTEGER NOT NULL,checked INTEGER NOT NULL DEFAULT 0)");
                    old.execSQL("CREATE TABLE purchases(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,product_key TEXT NOT NULL,store TEXT NOT NULL,date TEXT NOT NULL,gross INTEGER NOT NULL,tax REAL NOT NULL,quantity INTEGER NOT NULL,sale INTEGER NOT NULL,size REAL NOT NULL DEFAULT 0,unit TEXT NOT NULL DEFAULT '')");
                    old.execSQL("CREATE INDEX idx_prices ON purchases(product_key,store,date,id)");
                    old.execSQL("INSERT INTO memos(name,product_key,quantity,checked) VALUES('架空パン6枚入','架空パン6枚入',2,1)");
                    old.execSQL("INSERT INTO purchases(name,product_key,store,date,gross,tax,quantity,sale,size,unit) VALUES('架空パン6枚入','架空パン6枚入','FICTIVE MARKET','2026-10-25',86,8,2,0,0,'')");old.setVersion(1);
                }
                db=new PriceDb(isolated);check(db.getReadableDatabase().getVersion()==2);check(db.allMemos().size()==1&&db.allMemos().get(0).checked&&db.allMemos().get(0).quantity==2);
                PriceDb.Purchase migrated=db.allPurchases().get(0);check(migrated.gross==86&&migrated.quantity==2&&migrated.taxKnown&&migrated.receiptId==null&&migrated.printedUnit==null);
                // Restore a genuine v1 shape (no new keys) and retain the old purchase/memo fields.
                JSONObject legacy=new JSONObject(db.backupJson());legacy.put("format","kosso-pricebook-v1");legacy.remove("receipts");
                JSONObject original=legacy.getJSONArray("purchases").getJSONObject(0);for(String key:new String[]{"taxKnown","printedUnit","lineAmount","receiptId","amountBasis","specification"})original.remove(key);
                db.importBackup(legacy.toString());check(db.allPurchases().get(0).taxKnown&&db.allMemos().get(0).checked);check(db.latestForProduct("架空パン6枚入").get(0).gross==86);
                JSONObject focus=null;for(int i=0;i<fixtures.length();i++)if(fixtures.getJSONObject(i).getString("id").equals("v07-unit-layout"))focus=fixtures.getJSONObject(i);check(focus!=null);
                Bitmap fake;try(InputStream in=getContext().getAssets().open(focus.getString("image"))){fake=BitmapFactory.decodeStream(in);}
                ReceiptParser.Receipt parsed;try{parsed=ReceiptParser.parseDocument(PaddleOcr.recognizeDocument(getTargetContext(),fake));}finally{fake.recycle();}
                reviewUi(isolated,parsed);parsed=ReceiptExtractionTest.parsed(0);reviewUi(isolated,parsed);
                ReceiptReview model=new ReceiptReview(parsed);check(model.items.size()==6);check(model.items.stream().noneMatch(i->i.selected||i.grossUnit!=null||i.taxRate!=null));
                // No inferred item tax allocation: gross 86 for the 79x2 row is a HUMAN test input.
                ReceiptReview.Item two=model.items.get(1);check(two.printedUnit==79&&two.quantity==2&&two.lineAmount==158);two.grossUnit=86L;two.taxRate=null;two.selected=true;check(!two.ready());two.codeConfirmed=true;check(two.ready());
                PriceDb.Purchase reviewed=new PriceDb.Purchase();reviewed.name=two.name;reviewed.key=PriceMath.key(two.name);reviewed.store=model.store;reviewed.date=model.date;reviewed.gross=two.grossUnit;reviewed.quantity=two.quantity;reviewed.taxKnown=false;reviewed.tax=0;reviewed.unit="";reviewed.printedUnit=two.printedUnit;reviewed.lineAmount=two.lineAmount;reviewed.specification=two.specification;reviewed.basis=two.basis.name();
                db.addReceiptPurchases(java.util.Collections.singletonList(reviewed),parsed,model.store,model.date);
                db.addMemo(reviewed.name,2);check(db.allMemos().stream().anyMatch(m->m.key.equals(reviewed.key)));
                PriceDb.Purchase latest=db.latestForProduct(reviewed.key).get(0);check(latest.gross==86&&latest.quantity==2&&!latest.taxKnown);check(latest.printedUnit==79&&latest.lineAmount==158&&latest.basis.equals("EXCLUSIVE"));check(latest.receiptId!=null&&db.receiptSummary(latest.receiptId).contains("3140"));
                String full=db.backupJson();check(new JSONObject(full).getString("format").equals("kosso-pricebook-v2"));check(!full.contains("00000000")&&!full.contains("regions")&&!full.contains("raw")&&!full.contains("image"));
                db.importBackup(full);latest=db.latestForProduct(reviewed.key).get(0);check(!latest.taxKnown&&latest.tax==0&&latest.gross==86);check(latest.specification.equals(reviewed.specification)&&latest.printedUnit==79&&latest.lineAmount==158);check(db.receiptSummary(latest.receiptId).contains("2872"));
                JSONObject bad=new JSONObject(full);bad.getJSONArray("purchases").getJSONObject(0).put("receiptId",999999999L);
                try{db.importBackup(bad.toString());throw new AssertionError();}catch(org.json.JSONException expected){checks++;}check(db.backupJson().equals(full));
                bad=new JSONObject(full);bad.getJSONArray("purchases").getJSONObject(0).put("printedUnit",79.5);
                try{db.importBackup(bad.toString());throw new AssertionError();}catch(org.json.JSONException expected){checks++;}check(db.backupJson().equals(full));
                bad=new JSONObject(full);bad.getJSONArray("purchases").getJSONObject(0).put("date","2026-02-30");
                try{db.importBackup(bad.toString());throw new AssertionError();}catch(org.json.JSONException expected){checks++;}check(db.backupJson().equals(full));
                PriceDb.Purchase invalid=new PriceDb.Purchase();invalid.quantity=0;
                try{db.addReceiptPurchases(java.util.Arrays.asList(reviewed,invalid),parsed,model.store,model.date);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}check(db.backupJson().equals(full));
                db.setChecked(db.allMemos().get(0).id,true);db.setMemoQuantity(db.allMemos().get(0).id,3);check(db.allMemos().get(0).checked&&db.allMemos().get(0).quantity==3);db.resetMemoChecks();check(db.allMemos().stream().noneMatch(m->m.checked));
                long receiptId=db.latestForProduct(reviewed.key).get(0).receiptId;db.deletePurchase(db.latestForProduct(reviewed.key).get(0).id);check(db.receiptSummary(receiptId).isEmpty());check(db.allPurchases().size()==1&&db.allPurchases().get(0).gross==86);
            }finally{db.close();isolated.deleteDatabase("private_pricebook.db");}
            result.putString("status","PASS synthetic-only offline OCR and JSON roundtrip");result.putInt("checks",checks);finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            // Never include OCR text, purchase data, paths or exception messages in CI output.
            result.putString("status","FAIL synthetic check "+checks);result.putString("failure_type",failure.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
