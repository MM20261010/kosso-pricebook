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
                ReceiptReview model=new ReceiptReview(receipt);model.showQuestions=false;
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
    private android.widget.TextView findLabel(android.view.View view,String part){
        if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().contains(part))return (android.widget.TextView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.TextView found=findLabel(group.getChildAt(i),part);if(found!=null)return found;}}return null;
    }
    private void confirmationUi(Context isolated)throws Exception{
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{
            MainActivity activity=null;
            try{
                android.content.Intent intent=new android.content.Intent(getTargetContext(),MainActivity.class);
                android.content.pm.ActivityInfo info=getTargetContext().getPackageManager().getActivityInfo(intent.getComponent(),0);
                activity=(MainActivity)newActivity(MainActivity.class,isolated,null,(android.app.Application)getTargetContext().getApplicationContext(),intent,info,"synthetic confirmation",null,null,null);callActivityOnCreate(activity,null);
                ReceiptReview model=new ReceiptReview(ReceiptConfirmationTest.fixture("小未読",8));
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("reviewModel");field.setAccessible(true);field.set(activity,model);
                for(String name:new String[]{"reviewText","reviewedText"}){field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,"synthetic confirmation only");}
                field=MainActivity.class.getDeclaredField("content");field.setAccessible(true);android.widget.LinearLayout content=(android.widget.LinearLayout)field.get(activity);
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("showOcrReview");render.setAccessible(true);content.removeAllViews();render.invoke(activity);
                check(visibleText(content).contains("集計か商品か要確認")&&visibleText(content).contains("1点と推定"));
                check(visibleText(content).contains("79円 × 2点 ＝ 158円")&&visibleText(content).contains("245円 × 2点 ＝ 490円"));
                check(!visibleText(content).contains("原文：")&&!visibleText(content).contains("検出座標"));
                findLabel(content,"個別の税込購入単価・税率（必要時）").performClick();
                ((android.widget.EditText)content.findViewWithTag("batch-gross-0")).setText("200");
                content.removeAllViews();render.invoke(activity);
                findLabel(content,"個別の税込購入単価・税率（必要時）").performClick();
                check(((android.widget.EditText)content.findViewWithTag("batch-gross-0")).getText().toString().equals("200")&&model.items.get(0).grossUnit==null);
                ((android.widget.EditText)content.findViewWithTag("batch-gross-0")).setText("");
                findLabel(content,"1点と推定した対象行は").performClick();
                findLabel(content,"小計・集計として除外").performClick();
                findLabel(content,"税込単価の入力・税区分をまとめて設定").performClick();
                findLabel(content,"印字金額は税抜・税率と1点単位端数を確認して換算").performClick();
                ((android.widget.EditText)content.findViewWithTag("batch-common-rate")).setText("8");
                findLabel(content,"1点単位で切り捨てと確認").performClick();
                findLabel(content,"対象行の数量・金額・税区分・集計/商品区分を原票で確認した").performClick();
                findLabel(content,"確認をまとめて反映（保存は別）").performClick();
                check(model.held.get(0).choice==ReceiptReview.Choice.SUBTOTAL&&model.items.size()==6);
                check(model.items.stream().allMatch(i->i.ready()&&i.selected)&&model.items.stream().mapToInt(i->i.quantity).sum()==8);
                check(model.aggregate().subtotal==2872&&model.items.get(1).printedUnit==79&&model.items.get(3).printedUnit==245);
                model.showQuestions=true;content=(android.widget.LinearLayout)field.get(activity);content.removeAllViews();render.invoke(activity);
                findLabel(content,"後で確認・今回保存しない").performClick();
                try{model.validateSave();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
                findLabel(content,"未反映の入力を破棄して商品一覧へ").performClick();model.validateSave();
                check(model.batch==null&&model.held.get(0).choice==ReceiptReview.Choice.SUBTOTAL&&model.items.size()==6);
            }catch(Throwable e){failure.set(e);}finally{if(activity!=null)callActivityOnDestroy(activity);}
        });
        if(failure.get()!=null)throw new IllegalStateException("Synthetic inline confirmation UI failed",failure.get());
    }
    private int visibleEditors(android.view.View view){
        if(view.getVisibility()!=android.view.View.VISIBLE)return 0;int count=view instanceof android.widget.EditText?1:0;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=visibleEditors(group.getChildAt(i));}return count;
    }
    private int clickVisibleChecks(android.view.View view,String label){
        if(view.getVisibility()!=android.view.View.VISIBLE)return 0;
        if(view instanceof android.widget.CheckBox&&((android.widget.CheckBox)view).getText().toString().equals(label)){view.performClick();return 1;}
        int n=0;if(view instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)view;for(int i=0;i<g.getChildCount();i++)n+=clickVisibleChecks(g.getChildAt(i),label);}return n;
    }
    private void fiscalUi(Context isolated)throws Exception{
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{MainActivity activity=null;try{
            android.content.Intent intent=new android.content.Intent(getTargetContext(),MainActivity.class);
            android.content.pm.ActivityInfo info=getTargetContext().getPackageManager().getActivityInfo(intent.getComponent(),0);
            activity=(MainActivity)newActivity(MainActivity.class,isolated,null,(android.app.Application)getTargetContext().getApplicationContext(),intent,info,"synthetic fiscal",null,null,null);callActivityOnCreate(activity,null);
            ReceiptReview model=new ReceiptReview(ReceiptParser.parseDocument(ReceiptFiscalTest.fixture(true,false)));
            java.lang.reflect.Field f=MainActivity.class.getDeclaredField("reviewModel");f.setAccessible(true);f.set(activity,model);
            for(String name:new String[]{"reviewText","reviewedText"}){f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,"synthetic fiscal only");}
            f=MainActivity.class.getDeclaredField("content");f.setAccessible(true);android.widget.LinearLayout content=(android.widget.LinearLayout)f.get(activity);
            java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("showOcrReview");render.setAccessible(true);content.removeAllViews();render.invoke(activity);
            check(visibleText(content).contains("税込の最終合計：3140円（要確認）")&&visibleText(content).contains("内訳合算・要確認")&&visibleText(content).contains("照合：未確定"));
            check(visibleEditors(content)==2);
            check(findLabel(content,"未取得の集計を匿名診断コピー").performClick());
            check(model.aggregate().total==null&&model.aggregate().taxAmountUncertain&&model.items.stream().noneMatch(i->i.selected||i.ready()));
            findLabel(content,"小計・集計として除外").performClick();findLabel(content,"レシートの最終合計として採用").performClick();
            check(clickVisibleChecks(content,"この商品は後で確認・今回保存しない")==6);
            findLabel(content,"集計金額・方式の確認と訂正").performClick();findLabel(content,"入力した集計金額・明細の税込税抜を原票で確認して採用").performClick();
            findLabel(content,"対象行の数量・金額・税区分・集計/商品区分を原票で確認した").performClick();findLabel(content,"確認をまとめて反映（保存は別）").performClick();
            check(model.aggregate().total==3140&&model.aggregate().taxAmount==268&&model.items.stream().noneMatch(i->i.ready()||i.selected));
            content=(android.widget.LinearLayout)f.get(activity);content.removeAllViews();render.invoke(activity);
            check(visibleText(content).contains("照合：一致")&&visibleText(content).contains("レシート小計：2872円"));
            model.showQuestions=true;content.removeAllViews();render.invoke(activity);((android.widget.EditText)content.findViewWithTag("summary-total")).setText("3130");
            try{model.validateSave();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
            findLabel(content,"未反映の入力を破棄して商品一覧へ").performClick();check(model.aggregate().total==3140&&model.batch==null);
        }catch(Throwable e){failure.set(e);}finally{if(activity!=null)callActivityOnDestroy(activity);}});
        if(failure.get()!=null)throw new IllegalStateException("Synthetic fiscal UI failed",failure.get());
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
                check(build.getInt("version_code")==19&&BuildConfig.VERSION_CODE==19);
                check(build.getString("source_commit").equals(BuildConfig.SOURCE_COMMIT)&&build.getBoolean("working_tree_dirty")==BuildConfig.SOURCE_DIRTY);
            }
            checks+=ReceiptClassificationAuditTest.run();
            checks+=ReceiptPairingTest.run();
            checks+=ReceiptPipelineTest.run();
            checks+=ReceiptConfirmationTest.run();
            checks+=ReceiptFiscalTest.run();
            checks+=ReceiptFiscalStagesTest.run();
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
                    if(fixture.optBoolean("fiscal_review",false)){
                        check(receipt.items.size()==6&&receipt.heldItems.size()==2&&receipt.heldItems.stream().anyMatch(h->h.aggregateKind==ReceiptParser.Kind.TOTAL&&h.item.price==3140));
                        ReceiptParser.TaxBreakdown eight=receipt.taxBreakdowns.stream().filter(b->b.rate==8).findFirst().orElseThrow(),ten=receipt.taxBreakdowns.stream().filter(b->b.rate==10).findFirst().orElseThrow();
                        check(eight.base==960&&eight.tax==77&&eight.itemCount==5&&!eight.uncertain);
                        check(ten.base==1912&&ten.tax==191&&ten.itemCount==3&&!ten.uncertain);
                        check(receipt.taxAmount==268&&receipt.total==null&&receipt.itemSum()==2872);
                        check(receipt.items.get(1).quantity==2&&receipt.items.get(1).unitPrice==79&&receipt.items.get(3).quantity==2&&receipt.items.get(3).unitPrice==245);
                        ReceiptReview fiscal=new ReceiptReview(receipt);check(fiscal.items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4&&fiscal.fiscalStatus().contains("税込の最終合計：3140円（要確認）"));
                    }
                    if(fixture.optBoolean("fiscal_stages",false)){
                        boolean joined=file.equals("v0811-split-fiscal.png");
                        check(receipt.items.size()==6&&receipt.heldItems.stream().anyMatch(h->h.aggregateKind==ReceiptParser.Kind.TOTAL&&h.item.price==3140));
                        ReceiptParser.TaxBreakdown eight=receipt.taxBreakdowns.stream().filter(b->b.rate==8).findFirst().orElseThrow(),ten=receipt.taxBreakdowns.stream().filter(b->b.rate==10).findFirst().orElseThrow();
                        check(eight.base==960&&eight.tax==77&&eight.itemCount==5&&eight.uncertain==joined);
                        check(ten.base==1912&&ten.tax==191&&ten.itemCount==3&&ten.uncertain==joined);
                        check(receipt.taxAmount==268&&receipt.taxAmountFromBreakdown&&receipt.taxAmountUncertain&&receipt.taxDetailsPending==joined&&receipt.total==null&&receipt.itemSum()==2872);
                        check(receipt.items.get(1).quantity==2&&receipt.items.get(1).unitPrice==79&&receipt.items.get(3).quantity==2&&receipt.items.get(3).unitPrice==245);
                        ReceiptReview fiscal=new ReceiptReview(receipt);
                        check(fiscal.items.stream().filter(i->i.quantityState==ReceiptReview.QuantityState.PROVISIONAL_ONE).count()==4&&fiscal.fiscalStatus().contains("税込の最終合計：3140円（要確認）")&&(!joined||receipt.decisions.stream().anyMatch(d->d.audit.fiscal.tax==ReceiptDiagnostics.TaxStop.JOINED_RATE_PROPOSAL)));
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
                confirmationUi(isolated);
                fiscalUi(isolated);
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
                PriceDb.Purchase weighed=new PriceDb.Purchase();weighed.name="架空量り売り肉";weighed.key=PriceMath.key(weighed.name);weighed.store="架空テスト店";weighed.date="2026-11-03";weighed.gross=399;weighed.quantity=1;weighed.taxKnown=false;weighed.tax=0;weighed.size=201.5;weighed.unit="g";weighed.specification="";weighed.lineAmount=399L;weighed.basis="UNKNOWN";
                db.addPurchase(weighed);String weightedJson=db.backupJson();db.importBackup(weightedJson);PriceDb.Purchase weightRestored=db.latestForProduct(weighed.key).get(0);
                check(weightRestored.size==201.5&&weightRestored.unit.equals("g")&&weightRestored.gross==399&&weightRestored.quantity==1);
                check(weightRestored.printedUnit==null&&!weightedJson.contains("quantityState")&&!weightedJson.contains("PROVISIONAL_ONE")&&!weightedJson.contains("raw"));
                db.deletePurchase(weightRestored.id);
                PriceDb.Purchase taxOnly=new PriceDb.Purchase();taxOnly.name="架空税合算試験";taxOnly.key=PriceMath.key(taxOnly.name);taxOnly.store="架空テスト店";taxOnly.date="2026-11-03";taxOnly.gross=216;taxOnly.quantity=1;taxOnly.taxKnown=false;taxOnly.tax=0;taxOnly.unit="";taxOnly.lineAmount=200L;taxOnly.basis="EXCLUSIVE";
                ReceiptParser.Receipt derivedTax=ReceiptParser.parseReceipt("税抜表示\n架空税合算試験200\n8%消費税16\n合計216");db.addReceiptPurchases(java.util.Collections.singletonList(taxOnly),derivedTax,taxOnly.store,taxOnly.date);
                check(db.receiptSummary(taxOnly.receiptId).contains("要確認")&&derivedTax.taxAmountUncertain);
                String taxJson=db.backupJson();db.importBackup(taxJson);PriceDb.Purchase taxRestored=db.latestForProduct(taxOnly.key).get(0);check(db.receiptSummary(taxRestored.receiptId).contains("要確認")&&taxRestored.gross==216&&taxRestored.quantity==1);db.deletePurchase(taxRestored.id);
                long receiptId=db.latestForProduct(reviewed.key).get(0).receiptId;db.deletePurchase(db.latestForProduct(reviewed.key).get(0).id);check(db.receiptSummary(receiptId).isEmpty());check(db.allPurchases().size()==1&&db.allPurchases().get(0).gross==86);
            }finally{db.close();isolated.deleteDatabase("private_pricebook.db");}
            result.putString("status","PASS synthetic-only offline OCR and JSON roundtrip");result.putInt("checks",checks);finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            // Never include OCR text, purchase data, paths or exception messages in CI output.
            result.putString("status","FAIL synthetic check "+checks);result.putString("failure_type",failure.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
