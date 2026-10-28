package org.privateprice.pricebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ClipData;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Native offline receipt reader and shopping memo. No INTERNET permission. */
public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(244,247,245), GREEN=Color.rgb(10,94,77), INK=Color.rgb(28,44,40), MUTED=Color.rgb(86,105,99);
    private static final int CREATE_BACKUP=501, RESTORE_BACKUP=502, TAKE_PHOTO=503, PICK_PHOTO=504;
    private final DecimalFormat priceFormat=new DecimalFormat("#,##0.##");
    private PriceDb db;
    private LinearLayout frame, content, nav;
    private int tab=0;
    private String lastStore="";
    private String lastDate=LocalDate.now().toString();
    private Uri cameraUri;
    private File cameraFile;
    private String reviewText="",reviewOrigin="not_set";
    private OcrDocument paddleDocument,reviewDocument;
    private ReceiptReview reviewModel; private String reviewedText="";
    private volatile boolean ocrRunning;
    private static final AtomicBoolean OCR_BUSY=new AtomicBoolean();
    private volatile boolean closing;
    private Bitmap comparisonImage;
    private String selectedOcr="PaddleOCR v5 / ncnn",tesseractOriginal="",paddleOriginal="",tesseractStats="",paddleStats="";


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);db=new PriceDb(this);lastStore=db.mostRecentStore();
        if(savedInstanceState!=null){
            String savedPhoto=savedInstanceState.getString("camera_path","");
            if(!savedPhoto.isEmpty()){
                cameraFile=new File(savedPhoto);
                cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",cameraFile);
            }
            reviewText=savedInstanceState.getString("review_text","");reviewOrigin="restored_text:"+savedInstanceState.getString("review_origin","unknown");
        }
        if(getLastNonConfigurationInstance() instanceof ReceiptReview){reviewModel=(ReceiptReview)getLastNonConfigurationInstance();reviewedText=reviewText;reviewOrigin="retained_review:"+(savedInstanceState==null?"unknown":savedInstanceState.getString("review_origin","unknown"));}
        if(savedInstanceState!=null)tab=savedInstanceState.getInt("tab",0);
        frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(BG);
        frame.setPadding(dp(14),dp(10),dp(14),0);
        if(Build.VERSION.SDK_INT>=30){frame.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets sys=insets.getInsets(WindowInsets.Type.systemBars());
            frame.setPadding(dp(14),sys.top+dp(10),dp(14),sys.bottom);return insets;});}
        setContentView(frame);
        TextView title=text("こっそり価格帳",23,true,INK);frame.addView(title);
        TextView sub=text("買い物はあなたのもの。端末内だけで記録",12,false,MUTED);frame.addView(sub);
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(0,dp(12),0,dp(8));frame.addView(nav);
        ScrollView scroll=new ScrollView(this);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(5),0,dp(28));scroll.addView(content);
        render();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable background(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(border!=0)d.setStroke(dp(1),border);return d;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());return b;}
    private EditText edit(String hint,String initial,int inputType){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setText(initial);e.setTextSize(16);e.setInputType(inputType);return e;}
    private void section(String title){TextView t=text(title,18,true,INK);t.setPadding(0,dp(16),0,dp(8));content.addView(t);}
    private void hint(String message){TextView t=text(message,13,false,MUTED);t.setPadding(0,dp(3),0,dp(12));content.addView(t);}
    private void box(LinearLayout inner){inner.setPadding(dp(14),dp(12),dp(14),dp(12));inner.setBackground(background(Color.WHITE,Color.rgb(225,233,228)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(inner,p);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void render(){
        nav.removeAllViews();String[] tabs={"🛒 買い物","✍ 記録","🗃 管理"};
        for(int i=0;i<tabs.length;i++){final int idx=i;Button b=button(tabs[i],()->{tab=idx;render();});
            b.setTextSize(12);b.setPadding(dp(4),dp(2),dp(4),dp(2));b.setTextColor(tab==i?Color.WHITE:GREEN);
            b.setBackground(background(tab==i?GREEN:Color.rgb(226,236,231),0));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.rightMargin=i<2?dp(5):0;nav.addView(b,p);}
        content.removeAllViews();
        if(tab==0)showShopping();else if(tab==1)showRecord();else showAdmin();
    }
    private void showShopping(){
        section("買い物メモ");
        content.addView(button("買い終えたチェックを全部戻す",()->{db.resetMemoChecks();render();}));
        hint("商品を追加すると、過去に購入した店舗ごとの前回価格が表示されます。価格は現在の店頭価格ではありません。");
        LinearLayout add=row();EditText name=edit("例：牛乳 1L","",InputType.TYPE_CLASS_TEXT);add.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button plus=button("追加",()->{String s=name.getText().toString().trim();if(s.isEmpty()||s.length()>120){toast("商品名を入力してください");return;}db.addMemo(s,1);render();});add.addView(plus);content.addView(add);
        List<PriceDb.Memo> memos=db.allMemos();if(memos.isEmpty()){hint("まだ買い物メモはありません。先に商品を追加してみよう。");return;}
        for(PriceDb.Memo memo:memos){
            LinearLayout card=col();LinearLayout top=row();CheckBox checked=new CheckBox(this);checked.setChecked(memo.checked);
            checked.setOnCheckedChangeListener((btn,ok)->db.setChecked(memo.id,ok));top.addView(checked);
            TextView nm=text(memo.name,16,true,INK);top.addView(nm,new LinearLayout.LayoutParams(0,-2,1));
            Button del=button("削除",()->{db.deleteMemo(memo.id);render();});del.setTextSize(12);top.addView(del);card.addView(top);
            LinearLayout amountControls=row();amountControls.addView(text("買う数",12,false,MUTED));
            TextView count=text(" ×"+memo.quantity+" ",15,true,INK);
            amountControls.addView(button("−",()->{if(memo.quantity>1){db.setMemoQuantity(memo.id,--memo.quantity);count.setText(" ×"+memo.quantity+" ");}}));
            amountControls.addView(count);
            amountControls.addView(button("＋",()->{if(memo.quantity<999){db.setMemoQuantity(memo.id,++memo.quantity);count.setText(" ×"+memo.quantity+" ");}}));
            card.addView(amountControls);
            List<PriceDb.Purchase> latest=db.latestForProduct(memo.key);
            if(latest.isEmpty()){card.addView(text("購入履歴なし。買った後で価格を登録してね。",13,false,MUTED));}
            else {for(PriceDb.Purchase p:latest){
                String label=p.store+"  税込 ¥"+priceFormat.format(p.gross)+" /個"+"  "+p.date+(p.sale?"  [特売]":"");
                TextView line=text(label+"  ▸履歴",13,false,p.sale?MUTED:GREEN);line.setPadding(dp(6),dp(7),0,dp(7));
                line.setOnClickListener(v->showPriceHistory(memo.key,p.store));card.addView(line);
            }}box(card);
        }
        hint("※同名の商品を比較します。内容量や規格が違う場合、価格を直接比べないでください。今後は商品IDと100g単価の比較を追加予定。");
    }
    private void showRecord(){
        section("帰宅したらレシートを撮る");
        hint("画像は端末内だけでOCR。明細の誤読や割引を確認してから、複数商品をまとめて保存します。カメラアプリ自体の写真保存設定にはご注意ください。");
        LinearLayout capture=row();Button shot=button("📷 撮影",this::startCamera);
        capture.addView(shot,new LinearLayout.LayoutParams(0,dp(54),1));
        Button pick=button("🖼 写真を選ぶ",this::startImagePick);capture.addView(pick,new LinearLayout.LayoutParams(0,dp(54),1));content.addView(capture);
        if(BuildConfig.DEBUG){LinearLayout parent=content;content=disclosure("端末内OCR比較（詳細）",parent);showComparison();content=parent;}
        if(!reviewText.isEmpty())showOcrReview();
        LinearLayout recordParent=content;content=disclosure("手入力で1商品登録",recordParent);hint("レシートを読めない場合、または価格を追加入力する場合に使います。");
        EditText store=edit("店名（例：スーパーA）",lastStore,InputType.TYPE_CLASS_TEXT);content.addView(store);
        EditText date=edit("購入日 YYYY-MM-DD",lastDate,InputType.TYPE_CLASS_DATETIME);content.addView(date);
        EditText name=edit("商品名（買い物メモと同じ表記で）","",InputType.TYPE_CLASS_TEXT);content.addView(name);
        EditText amount=edit("税込単価（円、1個分）","",InputType.TYPE_CLASS_NUMBER);content.addView(amount);
        LinearLayout options=row();EditText tax=edit("税率%（不明は空欄）","",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        options.addView(tax,new LinearLayout.LayoutParams(0,-2,1));
        EditText quantity=edit("個数","1",InputType.TYPE_CLASS_NUMBER);options.addView(quantity,new LinearLayout.LayoutParams(0,-2,1));content.addView(options);
        CheckBox sale=new CheckBox(this);sale.setText("特売・値引きの価格");content.addView(sale);
        Button save=button("この商品を記録",()->{
            try{
                PriceDb.Purchase p=new PriceDb.Purchase();p.store=store.getText().toString().trim();p.name=name.getText().toString().trim();p.key=PriceMath.key(p.name);
                p.date=date.getText().toString().trim();p.gross=Long.parseLong(amount.getText().toString().trim());p.taxKnown=!tax.getText().toString().trim().isEmpty();p.tax=p.taxKnown?Double.parseDouble(tax.getText().toString().trim()):0;
                p.quantity=Integer.parseInt(quantity.getText().toString().trim());p.sale=sale.isChecked();p.size=0;p.unit="";
                if(p.store.isEmpty()||p.store.length()>120||p.name.isEmpty()||p.name.length()>120||!p.date.matches("20\\d{2}-\\d{2}-\\d{2}")||p.gross<0||p.gross>10_000_000||p.quantity<1||p.quantity>999||p.tax<0||p.tax>100||!Double.isFinite(p.tax))throw new IllegalArgumentException();
                LocalDate.parse(p.date);db.addPurchase(p);lastStore=p.store;lastDate=p.date;toast("端末内へ記録しました");render();
            }catch(Exception ex){toast("店名・商品名・日付・税込単価・税率・個数を確認してください");}
        });content.addView(save);content=recordParent;
        section("最近の購入履歴");
        List<PriceDb.Purchase> purchases=db.recentPurchases(20);
        if(purchases.isEmpty())hint("まだ価格の記録はありません。");
        for(PriceDb.Purchase p:purchases){
            LinearLayout card=col();card.addView(text(p.name+"  ¥"+p.gross+(p.sale?"  [特売]":""),15,true,INK));
            card.addView(text(p.store+" / "+p.date+" / "+(p.taxKnown?"税率"+p.tax+"%":"税率未確定"),12,false,MUTED));
            if(p.receiptId!=null)card.addView(button("原票の集計を確認",()->new AlertDialog.Builder(this).setTitle("レシート集計").setMessage(db.receiptSummary(p.receiptId)).setPositiveButton("閉じる",null).show()));
            Button del=button("この明細を削除",()->new AlertDialog.Builder(this).setMessage(p.name+"の購入履歴を削除しますか？").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{db.deletePurchase(p.id);render();}).show());
            del.setTextSize(12);card.addView(del);box(card);
        }
    }
    private void showPriceHistory(String key,String store){
        List<PriceDb.Purchase> history=db.historyForProductAtStore(key,store,15);
        StringBuilder result=new StringBuilder("過去の購入実績です。店頭の最新価格ではありません。\n\n");
        PriceDb.Purchase newestNormal=null,previousNormal=null;
        for(PriceDb.Purchase p:history){
            result.append(p.date).append("   ").append(p.gross).append("円").append(p.sale?" [特売]":"").append(p.taxKnown?" 税率"+p.tax+"%":" 税率未確定").append("\n");
            if(!p.sale){ if(newestNormal==null)newestNormal=p;else if(previousNormal==null)previousNormal=p; }
        }
        if(newestNormal!=null&&previousNormal!=null&&previousNormal.gross>0&&newestNormal.taxKnown&&previousNormal.taxKnown){
            double now=PriceMath.excludingTax(newestNormal.gross,newestNormal.tax);
            double old=PriceMath.excludingTax(previousNormal.gross,previousNormal.tax);
            result.insert(0,"直近の通常価格の税抜換算前回比: "+String.format(Locale.JAPAN,"%+.1f%%",PriceMath.percentChange(old,now))+"\n（規格・数量が同じ場合のみ比較可）\n\n");
        }
        new AlertDialog.Builder(this).setTitle(store+" の購入価格推移").setMessage(result.toString()).setPositiveButton("閉じる",null).show();
    }
    private void startImagePick(){
        if(OCR_BUSY.get()){toast("読み取り中です。完了までお待ちください");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_PHOTO);
    }
    private void startCamera(){
        if(OCR_BUSY.get()){toast("読み取り中です。完了までお待ちください");return;}
        try{
            File folder=new File(getCacheDir(),"receipts");if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("撮影場所を準備できません");
            cameraFile=File.createTempFile("private_receipt_",".jpg",folder);
            cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",cameraFile);
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            intent.setClipData(ClipData.newUri(getContentResolver(),"レシート一時画像",cameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent,TAKE_PHOTO);
        }catch(Exception ex){toast("撮影準備失敗: "+ex.getMessage());}
    }
    private synchronized void releaseComparisonImage(){
        if(comparisonImage!=null){comparisonImage.recycle();comparisonImage=null;}
    }
    private void showComparison(){
        section("v0.8 端末内OCR比較");
        hint("同じ展開画像を両方式で読み直せます。比較結果は購入履歴に自動保存しません。元テキストはこの画面内だけで確認できます。画面回転・終了で比較画像は破棄します。");
        RadioGroup engines=new RadioGroup(this);engines.setOrientation(RadioGroup.VERTICAL);
        for(String label:new String[]{"Tesseract","PaddleOCR v5 / ncnn"}){
            RadioButton choice=new RadioButton(this);choice.setId(View.generateViewId());choice.setText(label);choice.setChecked(selectedOcr.equals(label));choice.setEnabled(!ocrRunning);engines.addView(choice);
            choice.setOnCheckedChangeListener((v,checked)->{if(checked){selectedOcr=label;}});
        }content.addView(engines);
        if(comparisonImage!=null){
            hint("比較対象："+comparisonImage.getWidth()+" × "+comparisonImage.getHeight()+" px。同一画像を保持中。");
            Button reread=button("同じ画像を選択中の方式で読む",()->processReceipt(null,false));reread.setEnabled(!ocrRunning);content.addView(reread);
            Button discard=button("比較画像と両結果を破棄",()->{
                if(ocrRunning){toast("読み取り完了までお待ちください");return;}
                releaseComparisonImage();tesseractOriginal="";paddleOriginal="";tesseractStats="";paddleStats="";reviewText="";paddleDocument=null;reviewDocument=null;reviewModel=null;render();
            });content.addView(discard);
        }
        if(ocrRunning)hint("端末内で読み取り中です。画像は送信していません。");
        for(String engine:new String[]{"Tesseract","PaddleOCR v5 / ncnn"}){
            String original=engine.equals("Tesseract")?tesseractOriginal:paddleOriginal;
            String stats=engine.equals("Tesseract")?tesseractStats:paddleStats;
            if(stats.isEmpty())continue;
            LinearLayout card=col();card.addView(text(engine,16,true,INK));card.addView(text(stats,12,false,MUTED));
            OcrDocument document=engine.equals("Tesseract")?null:paddleDocument;
            ReceiptParser.Receipt parsed=document==null?ReceiptParser.parseReceipt(original):ReceiptParser.parseDocument(document);
            card.addView(text(document==null?"テキスト解析":"検出位置から明細を解析（"+document.regions.size()+"領域）",12,false,MUTED));
            StringBuilder candidates=new StringBuilder("抽出候補 "+parsed.items.size()+"件（未登録）\n");
            for(ReceiptParser.Candidate c:parsed.items)candidates.append(c.name).append(" / 行金額 ").append(c.price).append("円（").append(ReceiptParser.basisLabel(c.amountBasis)).append("） / 数量 ").append(c.quantity==null?"未確定":c.quantity).append('\n');
            card.addView(text(candidates.toString(),14,false,INK));
            TextView raw=text("元の認識テキスト：\n"+original,13,false,MUTED);raw.setTextIsSelectable(true);card.addView(raw);
            Button show=button("この結果で確認・修正画面を開く",()->{if(ocrRunning){toast("読み取り中です");return;}selectedOcr=engine;reviewText=original;reviewOrigin=engine;reviewDocument=document;reviewModel=null;render();});card.addView(show);box(card);
        }
        hint("時間はモデル検証・初期化とOCRを含み、画像展開は含みません。メモリは200ms間隔で採ったプロセスPSSの最大標本です。正確なネイティブピークやエンジン単独の使用量ではありません。");
    }
    private void processReceipt(Uri uri,boolean deleteCachePhoto){
        if(uri==null&&comparisonImage==null){toast("先に比較する画像を選んでください");return;}
        if(!OCR_BUSY.compareAndSet(false,true)){toast("別の読み取りが完了するまでお待ちください");return;}
        ocrRunning=true;
        final String engine=BuildConfig.DEBUG?selectedOcr:"Tesseract";
        final File sourcePhoto=deleteCachePhoto?cameraFile:null;
        if(uri!=null){releaseComparisonImage();tesseractOriginal="";paddleOriginal="";tesseractStats="";paddleStats="";reviewText="";paddleDocument=null;reviewDocument=null;}
        tab=1;render();toast("端末内で文字を読み取っています");
        new Thread(()->{
            String result=null,error=null;OcrDocument document=null;long elapsed=0,peak=0;
            OcrMemory memory=null;
            try{
                if(uri!=null){Bitmap decoded=ReceiptOcr.decodeScaled(this,uri);synchronized(this){comparisonImage=decoded;}}
                Bitmap image=comparisonImage;
                if(image==null||image.isRecycled())throw new IllegalArgumentException();
                memory=new OcrMemory();long start=SystemClock.elapsedRealtime();
                if(engine.equals("Tesseract"))result=ReceiptOcr.recognize(this,image);
                else{document=PaddleOcr.recognizeDocument(this,image);result=document.text;}
                elapsed=SystemClock.elapsedRealtime()-start;memory.close();peak=memory.maximumKb();
            }catch(OutOfMemoryError e){error="メモリが不足しました。画像を小さくするか分割してください。";}
            catch(Exception|LinkageError e){error="読み取れませんでした。画像を小さくするか分割して、もう一度お試しください。";}
            finally{
                if(memory!=null)memory.close();if(sourcePhoto!=null)sourcePhoto.delete();
                ocrRunning=false;OCR_BUSY.set(false);if(closing)releaseComparisonImage();
            }
            final String output=result==null?"":result.trim(),fail=error;
            final OcrDocument recognizedDocument=document;
            final String stats=String.format(Locale.JAPAN,"読取 %.2f秒 / プロセスPSS最大標本 ",elapsed/1000.0)+(peak>0?String.format(Locale.JAPAN,"%.1f MiB",peak/1024.0):"未取得");
            runOnUiThread(()->{
                if(closing||isFinishing()||isDestroyed())return;
                if(fail!=null){render();new AlertDialog.Builder(this).setTitle("OCRを実行できませんでした").setMessage(fail+"\n画像・認識内容は送信していません。手入力も使えます。").setPositiveButton("閉じる",null).show();return;}
                selectedOcr=engine;
                if(engine.equals("Tesseract")){tesseractOriginal=output;tesseractStats=stats;}else{paddleOriginal=output;paddleStats=stats;paddleDocument=recognizedDocument;}
                reviewText=output;reviewOrigin=engine;reviewDocument=recognizedDocument;reviewModel=null;if(output.isEmpty())toast("文字が見つかりませんでした。明るい場所で撮影し直してください");render();
            });
        },"local-receipt-ocr").start();
    }
    private LinearLayout disclosure(String title,LinearLayout parent){
        LinearLayout body=col();body.setVisibility(View.GONE);
        Button toggle=button("▸ "+title,()->{});
        toggle.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText((open?"▾ ":"▸ ")+title);});
        parent.addView(toggle);parent.addView(body);return body;
    }
    private void showOcrReview(){
        if(reviewModel==null||!reviewedText.equals(reviewText)){
            reviewModel=new ReceiptReview(reviewDocument==null?ReceiptParser.parseReceipt(reviewText):ReceiptParser.parseDocument(reviewDocument));reviewedText=reviewText;
        }
        ReceiptReview model=reviewModel;ReceiptParser.Receipt receipt=model.receipt;
        section("購入商品の確認");
        hint("商品を選び、単価を確認してまとめて保存。選択だけでは保存されません。");
        EditText store=edit("企業名・店舗名（必須）",model.store,InputType.TYPE_CLASS_TEXT);content.addView(store);
        watch(store,()->model.store=store.getText().toString().trim());
        EditText date=edit("購入日 YYYY-MM-DD",model.date,InputType.TYPE_CLASS_DATETIME);content.addView(date);
        watch(date,()->model.date=date.getText().toString().trim());
        LinearLayout actions=row();
        actions.addView(button("全商品を選択",()->{for(ReceiptReview.Item i:model.items)i.selected=true;render();}),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("選択を解除",()->{for(ReceiptReview.Item i:model.items)i.selected=false;render();}),new LinearLayout.LayoutParams(0,-2,1));content.addView(actions);
        content.addView(button("選択商品の単価をまとめて確認",()->confirmUnits(model)));
        if(model.items.isEmpty())hint("商品候補がありません。詳細の原文を見直すか、手入力してください。");
        for(ReceiptReview.Item item:model.items){
            LinearLayout card=col(),top=row();CheckBox selected=new CheckBox(this);selected.setChecked(item.selected);selected.setContentDescription(item.name+"を保存対象に選択");selected.setOnCheckedChangeListener((b,ok)->item.selected=ok);top.addView(selected);
            TextView name=text(item.name,19,true,INK);name.setPadding(dp(6),dp(8),0,dp(8));name.setOnClickListener(v->editReviewItem(item));top.addView(name,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
            card.addView(text(item.equation(),16,true,INK));
            if(item.grossUnit!=null)card.addView(text("確認済み税込単価 "+item.grossUnit+"円 / 点",16,true,GREEN));
            String issue=item.issue();Button detail=button(issue.isEmpty()?"詳細・修正":"要確認："+issue+"  ›",()->editReviewItem(item));card.addView(detail);box(card);
        }
        if(!model.items.isEmpty())content.addView(button("選択した確認済み商品を一括保存",()->saveReviewed(model)));
        LinearLayout details=disclosure("レシート詳細・集計・認識テキスト",content);
        if(BuildConfig.DEBUG)details.addView(button("ビルド・領域別の分類理由（端末内だけ）",()->showClassificationAudit(receipt)));
        details.addView(text(receipt.reconciliation(),13,false,MUTED));
        for(String warning:receipt.warnings)details.addView(text("・"+warning,13,false,MUTED));
        for(ReceiptParser.TaxBreakdown b:receipt.taxBreakdowns)details.addView(text(b.rate+"% 対象額 "+b.base+"円 / 税額 "+b.tax+"円"+(b.uncertain?"（競合）":""),13,false,MUTED));
        StringBuilder classified=new StringBuilder();
        for(ReceiptParser.Line line:receipt.lines)if(line.kind!=ReceiptParser.Kind.ITEM&&line.kind!=ReceiptParser.Kind.ITEM_DETAIL)classified.append(line.kind).append("：").append(line.raw).append('\n');
        TextView lines=text(classified.toString(),12,false,MUTED);lines.setTextIsSelectable(true);details.addView(lines);
        if(reviewDocument!=null){
            StringBuilder positions=new StringBuilder("検出座標（端末内・未保存）\n");
            for(OcrDocument.Region region:reviewDocument.regions){positions.append(region.text).append("：");for(int n=0;n<4;n++)positions.append('(').append(Math.round(region.x(n))).append(',').append(Math.round(region.y(n))).append(") ");positions.append('\n');}
            LinearLayout geometry=disclosure("検出領域",details);geometry.addView(text(positions.toString(),12,false,MUTED));
        }
        details.addView(text("再解析では未保存の修正を破棄します。画面回転時は確認中の明細をメモリ内に保持します。アプリ終了後は画像を選び直してください。",13,false,MUTED));
        EditText raw=new EditText(this);raw.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32000)});raw.setText(reviewText);raw.setMinLines(4);raw.setMaxLines(8);details.addView(raw);
        details.addView(button("原文を修正して再解析",()->new AlertDialog.Builder(this).setMessage("未保存の明細修正を破棄して再解析しますか？").setNegativeButton("戻る",null).setPositiveButton("再解析",(d,w)->{reviewText=raw.getText().toString();reviewOrigin="edited_text";reviewDocument=null;reviewModel=null;render();}).show()));
        details.addView(button("OCR結果を破棄",()->new AlertDialog.Builder(this).setMessage("確認中の明細を破棄しますか？").setNegativeButton("戻る",null).setPositiveButton("破棄",(d,w)->{reviewText="";reviewDocument=null;reviewModel=null;render();}).show()));
    }
    private String buildIdentity(){return "v"+BuildConfig.VERSION_NAME+" / versionCode "+BuildConfig.VERSION_CODE+"\nソース "+BuildConfig.SOURCE_COMMIT+(BuildConfig.SOURCE_DIRTY?"（未コミット変更あり）":"（コミット済みソース）")+"\n解析 "+BuildConfig.PARSER_REVISION;}
    /** Private, opt-in raw text remains hidden until the user expands its own row. */
    private void appendDecisionAudit(LinearLayout target,ReceiptParser.Decision decision){
        target.addView(text("行"+decision.row+" / 領域["+decision.regions+"] / "+decision.kind+"\n理由 "+decision.reason+" / 候補番号 "+decision.candidates,13,true,INK));
        target.addView(text("判定信号（原文・番号・金額は含みません）：\n"+decision.evidence,12,false,MUTED));
        if(Double.isFinite(decision.left))
            target.addView(text(String.format(Locale.ROOT,"位置 %.0f,%.0f → %.0f,%.0f",decision.left,decision.top,decision.right,decision.bottom),12,false,MUTED));
        LinearLayout privateText=disclosure("原文と正規化を端末内で照合",target);
        privateText.addView(text("原文："+ReceiptParser.visibleText(decision.raw)+"\n正規化："+ReceiptParser.visibleText(decision.normalized),13,false,MUTED));
    }
    private void copyAnonymousCandidateAudit(ReceiptParser.Receipt receipt,int number){
        String report=ReceiptParser.anonymizedCandidateAudit(receipt,number);
        if(report.isEmpty()){toast("この候補の診断情報がありません");return;}
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null){toast("クリップボードが利用できません");return;}
        ClipData clip=ClipData.newPlainText("匿名OCR分類診断",report);
        if(Build.VERSION.SDK_INT>=33){
            android.os.PersistableBundle extras=new android.os.PersistableBundle();
            extras.putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        toast("候補 ["+number+"] の匿名診断をコピーしました");
    }
    private void showClassificationAudit(ReceiptParser.Receipt receipt){
        if(!BuildConfig.DEBUG)return;
        LinearLayout body=col();body.setPadding(dp(16),dp(8),dp(16),dp(20));ScrollView scroll=new ScrollView(this);scroll.addView(body);
        body.addView(text(buildIdentity(),13,true,INK));
        body.addView(text("解析経路："+receipt.analysisMode+" / 元の操作："+reviewOrigin+"\n候補 "+receipt.items.size()+"件 / 入力行 "+receipt.decisions.size()+"件\nPOSITIONEDは検出座標あり、TEXTは座標なしです。診断は端末内表示のみで、保存・ログ・送信は行いません。原文や管理番号を共有しないでください。",13,false,MUTED));
        body.addView(text("商品候補の番号から採用理由を確認できます。番号は商品候補の表示順です。匿名診断のコピーは明示操作のみ。Androidのクリップボードには他アプリからアクセスされ得るため、原文・実番号・金額・座標はコピーしません。貼り付け先も確認してください。",14,true,INK));
        for(int number=1;number<=receipt.items.size();number++){
            List<ReceiptParser.Decision> related=ReceiptParser.decisionsForCandidate(receipt,number);
            String adoption="採用理由の対応行なし";
            for(ReceiptParser.Decision decision:related)
                if(decision.kind==ReceiptParser.Kind.ITEM){adoption=decision.reason;break;}
            LinearLayout focus=disclosure("候補番号 ["+number+"] / "+adoption+" / 関連行 "+related.size(),body);
            final int selectedCandidate=number;
            focus.addView(button("この候補の匿名診断をコピー",()->copyAnonymousCandidateAudit(receipt,selectedCandidate)));
            if(related.isEmpty())focus.addView(text("候補と分類行の対応を追跡できませんでした。推測で補完しません。",12,false,MUTED));
            for(ReceiptParser.Decision decision:related)appendDecisionAudit(focus,decision);
        }
        LinearLayout all=disclosure("すべての領域の分類理由（詳細調査用）",body);
        for(ReceiptParser.Decision decision:receipt.decisions)appendDecisionAudit(all,decision);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("分類理由・調査用").setView(scroll).setPositiveButton("閉じる",null).create();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }
    private void watch(EditText edit,Runnable change){
        edit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){change.run();}public void afterTextChanged(android.text.Editable s){}});
    }
    private EditText labeled(LinearLayout parent,String title,String initial,int input){parent.addView(text(title,13,false,MUTED));EditText e=edit("未確定",initial,input);parent.addView(e);return e;}
    private void editReviewItem(ReceiptReview.Item item){
        LinearLayout form=col();form.setPadding(dp(16),dp(4),dp(16),dp(20));ScrollView scroll=new ScrollView(this);scroll.addView(form);
        EditText name=labeled(form,"商品名",item.name,InputType.TYPE_CLASS_TEXT);
        CheckBox codeCheck=new CheckBox(this);codeCheck.setText("先頭コード分離後の商品名を原票で確認した（型番・ブランドなら商品名を戻す）");codeCheck.setChecked(item.codeConfirmed);if(item.source.codeUncertain)form.addView(codeCheck);
        EditText spec=labeled(form,"容量・規格（名称にも残して別規格と区別）",item.specification,InputType.TYPE_CLASS_TEXT);
        EditText qty=labeled(form,"購入数量（容量・規格とは別）",item.quantity==null?"":item.quantity.toString(),InputType.TYPE_CLASS_NUMBER);
        EditText printed=labeled(form,"印字単価（1点分）",item.printedUnit==null?"":item.printedUnit.toString(),InputType.TYPE_CLASS_NUMBER);
        EditText line=labeled(form,"行金額（複数購入の合計）",String.valueOf(item.lineAmount),InputType.TYPE_CLASS_NUMBER);
        RadioGroup basis=new RadioGroup(this);String[] labels={"区分未確定","税抜の印字金額","税込の印字金額"};int[] ids=new int[3];
        for(int n=0;n<3;n++){RadioButton r=new RadioButton(this);ids[n]=View.generateViewId();r.setId(ids[n]);r.setText(labels[n]);basis.addView(r);if(item.basis.ordinal()==n)basis.check(ids[n]);}form.addView(basis);
        EditText gross=labeled(form,"確認した税込単価（1点分・値引後）",item.grossUnit==null?"":item.grossUnit.toString(),InputType.TYPE_CLASS_NUMBER);
        EditText tax=labeled(form,"確認した税率 %（不明なら空欄）",item.taxRate==null?"":priceFormat.format(item.taxRate),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox sale=new CheckBox(this);sale.setText("特売・値引きあり");sale.setChecked(item.sale);form.addView(sale);
        LinearLayout original=disclosure("原文・記号を照合",form);original.addView(text(item.source.raw+"\n先頭コード分離候補："+item.source.managementCode+"\n管理記号："+item.source.markers+"（税率は推測しません）",13,false,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("商品を確認・修正").setView(scroll).setNegativeButton("戻る",null).setPositiveButton("修正を反映",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            try{
                String nm=name.getText().toString().trim(),sp=spec.getText().toString().trim();if(nm.isEmpty()||nm.length()>120||sp.length()>120)throw new IllegalArgumentException();
                Integer q=qty.getText().toString().trim().isEmpty()?null:Integer.valueOf(qty.getText().toString().trim());
                Long pu=optionalAmount(printed),gu=optionalAmount(gross),la=optionalAmount(line);if(la==null||la<1||q!=null&&(q<1||q>999))throw new IllegalArgumentException();
                Double rate=tax.getText().toString().trim().isEmpty()?null:Double.valueOf(tax.getText().toString().trim());if(rate!=null&&(!Double.isFinite(rate)||rate<0||rate>100))throw new IllegalArgumentException();
                int selected=0;for(int n=0;n<3;n++)if(basis.getCheckedRadioButtonId()==ids[n])selected=n;
                item.codeConfirmed=!item.source.codeUncertain||codeCheck.isChecked();item.name=nm;item.specification=sp;item.quantity=q;item.printedUnit=pu;item.lineAmount=la;item.basis=ReceiptParser.AmountBasis.values()[selected];item.grossUnit=gu;item.taxRate=rate;item.sale=sale.isChecked();item.detailsConfirmed=true;
                dialog.dismiss();render();
            }catch(Exception e){toast("名前・金額・数量・税率の範囲を確認してください。未確定値は空欄にできます。");}
        }));dialog.show();
    }
    private Long optionalAmount(EditText edit){String s=edit.getText().toString().trim();if(s.isEmpty())return null;long n=Long.parseLong(s);if(n<1||n>1_000_000)throw new IllegalArgumentException();return n;}
    private void confirmUnits(ReceiptReview model){
        List<ReceiptReview.Item> selected=new ArrayList<>();for(ReceiptReview.Item i:model.items)if(i.selected)selected.add(i);
        if(selected.isEmpty()){toast("対象商品を選択してください");return;}
        LinearLayout form=col();form.setPadding(dp(16),dp(4),dp(16),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(form);
        StringBuilder names=new StringBuilder();for(ReceiptReview.Item i:selected)names.append(i.name).append("：").append(i.equation()).append('\n');form.addView(text(names.toString(),14,true,INK));
        RadioGroup modes=new RadioGroup(this);RadioButton gross=new RadioButton(this),net=new RadioButton(this);gross.setId(View.generateViewId());net.setId(View.generateViewId());gross.setText("印字金額は値引後の税込と確認");net.setText("印字金額は税抜・単価ごとの換算と確認");modes.addView(gross);modes.addView(net);form.addView(modes);
        EditText tax=labeled(form,"この選択商品の税率 %（税込なら不明のまま可）","",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        RadioGroup rounding=new RadioGroup(this);int[] ids=new int[3];String[] roundLabels={"1点単価ごとに切り捨て","1点単価ごとに四捨五入","1点単価ごとに切り上げ"};
        for(int n=0;n<3;n++){RadioButton b=new RadioButton(this);ids[n]=View.generateViewId();b.setId(ids[n]);b.setText(roundLabels[n]);rounding.addView(b);}form.addView(rounding);
        CheckBox one=new CheckBox(this);one.setText("数量未確定の選択商品は、原票で1点と確認した");form.addView(one);
        CheckBox codeCheck=new CheckBox(this);codeCheck.setText("先頭コード分離候補の名称も原票で確認した（型番・ブランドは商品詳細で戻す）");boolean needsCode=false;for(ReceiptReview.Item i:selected)needsCode|=!i.codeConfirmed;if(needsCode)form.addView(codeCheck);
        CheckBox checked=new CheckBox(this);checked.setText("選択商品の名称・数量・価格区分・値引を原票で確認した（混在税率は分けて選択）");form.addView(checked);
        form.addView(text("税抜換算は、1点単位の端数処理を確認できる場合だけ使ってください。合計・税区分単位で処理される場合や割引は、商品詳細で税込単価を手入力してください。C/Vから税率は決めません。",13,false,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(selected.size()+"商品の単価を確認").setView(scroll).setNegativeButton("戻る",null).setPositiveButton("確認値を反映（保存は別）",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            try{
                if(!checked.isChecked()||modes.getCheckedRadioButtonId()==-1)throw new IllegalArgumentException("確認欄と価格区分を選んでください");
                for(ReceiptReview.Item i:selected)if(!i.codeConfirmed&&!codeCheck.isChecked())throw new IllegalArgumentException("先頭コードの分離候補を原票で確認してください");
                boolean isNet=modes.getCheckedRadioButtonId()==net.getId();Double rate=tax.getText().toString().trim().isEmpty()?null:Double.valueOf(tax.getText().toString().trim());
                if(rate!=null&&(!Double.isFinite(rate)||rate<0||rate>100))throw new IllegalArgumentException("税率の範囲を確認してください");
                ReceiptReview.Rounding method=null;for(int n=0;n<3;n++)if(rounding.getCheckedRadioButtonId()==ids[n])method=ReceiptReview.Rounding.values()[n];
                List<Long> units=new ArrayList<>();
                for(ReceiptReview.Item i:selected){
                    ReceiptReview.Item copy=new ReceiptReview.Item(i.source);copy.quantity=i.quantity==null&&one.isChecked()?1:i.quantity;copy.printedUnit=i.printedUnit;copy.lineAmount=i.lineAmount;copy.basis=i.basis;
                    if(i.source.taxRate!=null&&rate!=null&&i.source.taxRate.doubleValue()!=rate)throw new IllegalArgumentException("印字税率と異なります。商品詳細で個別に確認してください");
                    units.add(ReceiptReview.confirmedUnit(copy,isNet,rate,method));
                }
                for(int n=0;n<selected.size();n++){ReceiptReview.Item i=selected.get(n);if(i.quantity==null&&one.isChecked())i.quantity=1;i.grossUnit=units.get(n);i.taxRate=rate;i.detailsConfirmed=true;if(codeCheck.isChecked())i.codeConfirmed=true;}
                dialog.dismiss();render();
            }catch(Exception e){toast(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"値を確認してください");}
        }));dialog.show();
    }
    private void saveReviewed(ReceiptReview model){
        try{
            String shop=model.store.trim(),date=model.date.trim();LocalDate.parse(date);if(shop.isEmpty()||shop.length()>120||!date.matches("20\\d{2}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();
            List<PriceDb.Purchase> list=new ArrayList<>();long sum=0;boolean mismatch=false;
            for(ReceiptReview.Item item:model.items){if(!item.selected)continue;if(!item.ready())throw new IllegalArgumentException();
                PriceDb.Purchase p=new PriceDb.Purchase();p.name=item.name.trim();if(!item.specification.isEmpty()&&!PriceMath.key(p.name).contains(PriceMath.key(item.specification)))p.name+=" "+item.specification;if(p.name.length()>120)throw new IllegalArgumentException();p.key=PriceMath.key(p.name);p.store=shop;p.date=date;p.quantity=item.quantity;p.gross=item.grossUnit;p.taxKnown=item.taxRate!=null;p.tax=p.taxKnown?item.taxRate:0;p.sale=item.sale;p.unit="";p.specification=item.specification;p.printedUnit=item.printedUnit;p.lineAmount=item.lineAmount;p.basis=item.basis.name();
                sum+=p.gross*p.quantity;mismatch|=item.basis!=ReceiptParser.AmountBasis.EXCLUSIVE&&p.gross*p.quantity!=item.lineAmount;list.add(p);
            }
            if(list.isEmpty()){toast("保存する商品を選択してください");return;}
            String summary=list.size()+"商品 / 税込計 "+sum+"円";
            if(model.receipt.total!=null)summary+="\n原票合計 "+model.receipt.total+"円"+(sum!=model.receipt.total?"（不一致：部分保存・端数・値引を確認）":"（一致）");
            if(mismatch)summary+="\n入力単価×数量と印字行金額が異なる商品があります。";
            summary+="\n原票集計はOCR候補として端末内に保存します。画像・原文・管理番号は保存しません。二重登録にも注意してください。";
            new AlertDialog.Builder(this).setTitle("確認済み商品を保存しますか？").setMessage(summary).setNegativeButton("戻る",null).setPositiveButton("確認して保存",(d,w)->{
                try{db.addReceiptPurchases(list,model.receipt,shop,date);lastStore=shop;lastDate=date;reviewText="";reviewDocument=null;reviewModel=null;toast(list.size()+"商品を保存しました");render();}catch(Exception e){toast("保存できませんでした。既存データは変更していません。");}
            }).show();
        }catch(Exception e){toast("選択商品の数量・確認済み税込単価、店舗名・購入日を確認してください。税率不明は空欄で保存できます。");}
    }
    private void showAdmin(){
        section("データは端末内だけ");
        hint("インターネット権限・位置情報権限・広告・解析ツールなし。買い物履歴はアプリ専用のSQLiteデータベースに保存します。");
        if(BuildConfig.DEBUG)hint(buildIdentity());
        section("画像の扱い");
        hint("レシート画像は保存しません。比較中の画像はメモリ内だけに保持し、破棄・終了で消えます。任意の長期保管・閲覧機能は今回未実装です。JSONに画像・OCR原文・管理番号は含みません。外部カメラアプリが写真を保存する場合は、そのアプリ側で管理してください。");
        section("バックアップ / 復元");
        hint("v0.7は旧v1 JSONを復元できます。書き出すv2 JSONはv0.7以降専用です（税率未確定・集計・印字単価を保持）。旧版へ戻す前に旧版のJSONも保管してください。端末故障や機種変更に備え、JSONを定期的に保存してください。書き出し先は自分で選べます。クラウドを選ぶと、そのサービスにはファイルが渡ります。");
        content.addView(button("JSONバックアップを書き出す",()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"kosso-pricebook-"+LocalDate.now()+".json");startActivityForResult(intent,CREATE_BACKUP);
        }));
        content.addView(button("JSONを復元（現在の記録と置換）",()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,RESTORE_BACKUP);
        }));
        section("試作版 v0.8 明細抽出の修正 / 予定");
        hint("✅ 買い物メモと店舗別の前回税込価格\n✅ レシート撮影・端末内OCR候補・まとめて保存の実装\n✅ 履歴と前回比、手入力・バックアップ\n✅ 自動バックアップなし・外部送信なし\n⌛ Android実機でのOCR精度・APK検証\n⌛ 同一商品の表記ゆれ学習\n⌛ 内容量・100g単価比較画面\n⌛ 減税制度確定後の税率更新");
        hint("F-Droid申請前の開発用ビルドです。公式掲載はまだ行っていません。");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==TAKE_PHOTO){
            if(resultCode==RESULT_OK)processReceipt(cameraUri,true);
            else if(cameraFile!=null)cameraFile.delete();
            return;
        }
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==PICK_PHOTO){processReceipt(uri,false);return;}
        if(requestCode==CREATE_BACKUP){
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null)throw new IllegalStateException("出力先を開けません");
                out.write(db.backupJson().getBytes(StandardCharsets.UTF_8));toast("バックアップを保存しました");
            }catch(Exception ex){toast("保存失敗: "+ex.getMessage());}
        }else if(requestCode==RESTORE_BACKUP){
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalStateException("読み込みできません");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                byte[] chunk=new byte[8192];int count;
                while((count=in.read(chunk))!=-1){
                    if(buffer.size()+count>10*1024*1024)throw new IllegalArgumentException("10MBを超えるバックアップは復元できません");
                    buffer.write(chunk,0,count);
                }
                byte[] bytes=buffer.toByteArray();
                if(bytes.length>10*1024*1024)throw new IllegalArgumentException("10MBを超えるバックアップは復元できません");
                String json=new String(bytes,StandardCharsets.UTF_8);
                new AlertDialog.Builder(this).setTitle("復元の確認").setMessage("現在のすべての記録を置き換えます。復元前にバックアップしましたか？")
                    .setNegativeButton("キャンセル",null).setPositiveButton("置き換える",(dialog,which)->{
                        try{db.importBackup(json);toast("復元しました");render();}catch(Exception ex){toast("復元失敗: "+ex.getMessage());}
                    }).show();
            }catch(Exception ex){toast("読み込み失敗: "+ex.getMessage());}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("camera_path",cameraFile==null?"":cameraFile.getAbsolutePath());
        state.putString("review_text",reviewText);state.putString("review_origin",reviewOrigin);state.putInt("tab",tab);
        super.onSaveInstanceState(state);
    }
    @Override public Object onRetainNonConfigurationInstance(){return reviewModel;}
    @Override protected void onDestroy(){closing=true;if(!ocrRunning)releaseComparisonImage();paddleDocument=null;reviewDocument=null;db.close();super.onDestroy();}
}
