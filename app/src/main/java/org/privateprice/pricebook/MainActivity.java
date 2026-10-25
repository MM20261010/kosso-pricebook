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
    private String reviewText="";
    private volatile boolean ocrRunning;
    private static final AtomicBoolean OCR_BUSY=new AtomicBoolean();
    private volatile boolean closing;
    private Bitmap comparisonImage;
    private String selectedOcr="Tesseract",tesseractOriginal="",paddleOriginal="",tesseractStats="",paddleStats="";


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);db=new PriceDb(this);lastStore=db.mostRecentStore();
        if(savedInstanceState!=null){
            String savedPhoto=savedInstanceState.getString("camera_path","");
            if(!savedPhoto.isEmpty()){
                cameraFile=new File(savedPhoto);
                cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",cameraFile);
            }
            reviewText=savedInstanceState.getString("review_text","");
        }
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
                String label=p.store+"  ¥"+priceFormat.format(p.gross)+" /個"+"  "+p.date+(p.sale?"  [特売]":"");
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
        if(BuildConfig.DEBUG)showComparison();
        if(!reviewText.isEmpty())showOcrReview();
        section("手入力で1商品登録");hint("レシートを読めない場合、または価格を追加入力する場合に使います。");
        EditText store=edit("店名（例：スーパーA）",lastStore,InputType.TYPE_CLASS_TEXT);content.addView(store);
        EditText date=edit("購入日 YYYY-MM-DD",lastDate,InputType.TYPE_CLASS_DATETIME);content.addView(date);
        EditText name=edit("商品名（買い物メモと同じ表記で）","",InputType.TYPE_CLASS_TEXT);content.addView(name);
        EditText amount=edit("税込単価（円、1個分）","",InputType.TYPE_CLASS_NUMBER);content.addView(amount);
        LinearLayout options=row();EditText tax=edit("税率%","8",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        options.addView(tax,new LinearLayout.LayoutParams(0,-2,1));
        EditText quantity=edit("個数","1",InputType.TYPE_CLASS_NUMBER);options.addView(quantity,new LinearLayout.LayoutParams(0,-2,1));content.addView(options);
        CheckBox sale=new CheckBox(this);sale.setText("特売・値引きの価格");content.addView(sale);
        Button save=button("この商品を記録",()->{
            try{
                PriceDb.Purchase p=new PriceDb.Purchase();p.store=store.getText().toString().trim();p.name=name.getText().toString().trim();p.key=PriceMath.key(p.name);
                p.date=date.getText().toString().trim();p.gross=Long.parseLong(amount.getText().toString().trim());p.tax=Double.parseDouble(tax.getText().toString().trim());
                p.quantity=Integer.parseInt(quantity.getText().toString().trim());p.sale=sale.isChecked();p.size=0;p.unit="";
                if(p.store.isEmpty()||p.store.length()>120||p.name.isEmpty()||p.name.length()>120||!p.date.matches("20\\d{2}-\\d{2}-\\d{2}")||p.gross<0||p.gross>10_000_000||p.quantity<1||p.quantity>999||p.tax<0||p.tax>100||!Double.isFinite(p.tax))throw new IllegalArgumentException();
                LocalDate.parse(p.date);db.addPurchase(p);lastStore=p.store;lastDate=p.date;toast("端末内へ記録しました");render();
            }catch(Exception ex){toast("店名・商品名・日付・税込単価・税率・個数を確認してください");}
        });content.addView(save);
        section("最近の購入履歴");
        List<PriceDb.Purchase> purchases=db.recentPurchases(20);
        if(purchases.isEmpty())hint("まだ価格の記録はありません。");
        for(PriceDb.Purchase p:purchases){
            LinearLayout card=col();card.addView(text(p.name+"  ¥"+p.gross+(p.sale?"  [特売]":""),15,true,INK));
            card.addView(text(p.store+" / "+p.date+" / 税率"+p.tax+"%",12,false,MUTED));
            Button del=button("この明細を削除",()->new AlertDialog.Builder(this).setMessage(p.name+"の購入履歴を削除しますか？").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{db.deletePurchase(p.id);render();}).show());
            del.setTextSize(12);card.addView(del);box(card);
        }
    }
    private void showPriceHistory(String key,String store){
        List<PriceDb.Purchase> history=db.historyForProductAtStore(key,store,15);
        StringBuilder result=new StringBuilder("過去の購入実績です。店頭の最新価格ではありません。\n\n");
        PriceDb.Purchase newestNormal=null,previousNormal=null;
        for(PriceDb.Purchase p:history){
            result.append(p.date).append("   ").append(p.gross).append("円").append(p.sale?" [特売]":"").append(" 税率").append(p.tax).append("%\n");
            if(!p.sale){ if(newestNormal==null)newestNormal=p;else if(previousNormal==null)previousNormal=p; }
        }
        if(newestNormal!=null&&previousNormal!=null&&previousNormal.gross>0){
            double now=PriceMath.excludingTax(newestNormal.gross,newestNormal.tax);
            double old=PriceMath.excludingTax(previousNormal.gross,previousNormal.tax);
            result.insert(0,"直近の通常価格の税抜換算前回比: "+String.format(Locale.JAPAN,"%+.1f%%",PriceMath.percentChange(old,now))+"\n（規格・数量が同じ場合のみ比較可）\n\n");
        }
        new AlertDialog.Builder(this).setTitle(store+" の購入価格推移").setMessage(result.toString()).setPositiveButton("閉じる",null).show();
    }
    private void startImagePick(){
        if(ocrRunning){toast("読み取り中です。完了までお待ちください");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_PHOTO);
    }
    private void startCamera(){
        if(ocrRunning){toast("読み取り中です。完了までお待ちください");return;}
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
        section("v0.5 端末内OCR比較");
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
                releaseComparisonImage();tesseractOriginal="";paddleOriginal="";tesseractStats="";paddleStats="";reviewText="";render();
            });content.addView(discard);
        }
        if(ocrRunning)hint("端末内で読み取り中です。画像は送信していません。");
        for(String engine:new String[]{"Tesseract","PaddleOCR v5 / ncnn"}){
            String original=engine.equals("Tesseract")?tesseractOriginal:paddleOriginal;
            String stats=engine.equals("Tesseract")?tesseractStats:paddleStats;
            if(stats.isEmpty())continue;
            LinearLayout card=col();card.addView(text(engine,16,true,INK));card.addView(text(stats,12,false,MUTED));
            ReceiptParser.Receipt parsed=ReceiptParser.parseReceipt(original);
            StringBuilder candidates=new StringBuilder("抽出候補 "+parsed.items.size()+"件（未登録）\n");
            for(ReceiptParser.Candidate c:parsed.items)candidates.append(c.name).append(" / 行金額 ").append(c.price).append("円 / 数量 ").append(c.quantity==null?"未確定":c.quantity).append('\n');
            card.addView(text(candidates.toString(),14,false,INK));
            TextView raw=text("元の認識テキスト：\n"+original,13,false,MUTED);raw.setTextIsSelectable(true);card.addView(raw);
            Button show=button("この結果で確認・修正画面を開く",()->{if(ocrRunning){toast("読み取り中です");return;}selectedOcr=engine;reviewText=original;render();});card.addView(show);box(card);
        }
        hint("時間はモデル検証・初期化とOCRを含み、画像展開は含みません。メモリは200ms間隔で採ったプロセスPSSの最大標本です。正確なネイティブピークやエンジン単独の使用量ではありません。");
    }
    private void processReceipt(Uri uri,boolean deleteCachePhoto){
        if(uri==null&&comparisonImage==null){toast("先に比較する画像を選んでください");return;}
        if(!OCR_BUSY.compareAndSet(false,true)){toast("別の読み取りが完了するまでお待ちください");return;}
        ocrRunning=true;
        final String engine=BuildConfig.DEBUG?selectedOcr:"Tesseract";
        final File sourcePhoto=deleteCachePhoto?cameraFile:null;
        if(uri!=null){releaseComparisonImage();tesseractOriginal="";paddleOriginal="";tesseractStats="";paddleStats="";reviewText="";}
        tab=1;render();toast("端末内で文字を読み取っています");
        new Thread(()->{
            String result=null,error=null;long elapsed=0,peak=0;
            OcrMemory memory=null;
            try{
                if(uri!=null){Bitmap decoded=ReceiptOcr.decodeScaled(this,uri);synchronized(this){comparisonImage=decoded;}}
                Bitmap image=comparisonImage;
                if(image==null||image.isRecycled())throw new IllegalArgumentException();
                memory=new OcrMemory();long start=SystemClock.elapsedRealtime();
                result=engine.equals("Tesseract")?ReceiptOcr.recognize(this,image):PaddleOcr.recognize(this,image);
                elapsed=SystemClock.elapsedRealtime()-start;memory.close();peak=memory.maximumKb();
            }catch(OutOfMemoryError e){error="メモリが不足しました。画像を小さくするか分割してください。";}
            catch(Exception|LinkageError e){error="読み取れませんでした。画像を小さくするか分割して、もう一度お試しください。";}
            finally{
                if(memory!=null)memory.close();if(sourcePhoto!=null)sourcePhoto.delete();
                ocrRunning=false;OCR_BUSY.set(false);if(closing)releaseComparisonImage();
            }
            final String output=result==null?"":result.trim(),fail=error;
            final String stats=String.format(Locale.JAPAN,"読取 %.2f秒 / プロセスPSS最大標本 ",elapsed/1000.0)+(peak>0?String.format(Locale.JAPAN,"%.1f MiB",peak/1024.0):"未取得");
            runOnUiThread(()->{
                if(closing||isFinishing()||isDestroyed())return;
                if(fail!=null){render();new AlertDialog.Builder(this).setTitle("OCRを実行できませんでした").setMessage(fail+"\n画像・認識内容は送信していません。手入力も使えます。").setPositiveButton("閉じる",null).show();return;}
                selectedOcr=engine;
                if(engine.equals("Tesseract")){tesseractOriginal=output;tesseractStats=stats;}else{paddleOriginal=output;paddleStats=stats;}
                reviewText=output;if(output.isEmpty())toast("文字が見つかりませんでした。明るい場所で撮影し直してください");render();
            });
        },"local-receipt-ocr").start();
    }
    private void showOcrReview(){
        section("OCR候補を1件ずつ確認");
        hint("全候補は未選択です。行金額は単価とは限りません。商品名・数量・税込単価・税率を確認した商品だけチェックしてください。※や軽の印だけでは税率を確定しません。");
        ReceiptParser.Receipt receipt=ReceiptParser.parseReceipt(reviewText);
        content.addView(text("店名（未認識なら入力）",13,true,INK));
        EditText store=edit("店名（必須）",receipt.store,InputType.TYPE_CLASS_TEXT);content.addView(store);
        content.addView(text("購入日（未認識なら入力）",13,true,INK));
        EditText date=edit("YYYY-MM-DD",receipt.date,InputType.TYPE_CLASS_DATETIME);content.addView(date);
        hint(receipt.reconciliation());
        for(String warning:receipt.warnings)hint("要確認："+warning);
        if(receipt.taxAmount!=null)hint("印字された税額："+receipt.taxAmount+"円（各商品の税率・税込単価とは別です）");
        List<ReceiptParser.Candidate> parsed=receipt.items;
        List<EditText> names=new ArrayList<>(),prices=new ArrayList<>(),quantities=new ArrayList<>(),taxes=new ArrayList<>();
        List<CheckBox> include=new ArrayList<>(),sale=new ArrayList<>();
        if(parsed.isEmpty())hint("確かな商品候補がありません。下の原文を修正・再解析するか手入力してください。");
        for(ReceiptParser.Candidate candidate:parsed){
            LinearLayout card=col();
            TextView raw=text("原文："+candidate.raw,14,false,MUTED);card.addView(raw);
            card.addView(text("行金額："+candidate.price+"円 / 印字単価："+(candidate.unitPrice==null?"未確定":candidate.unitPrice+"円（税区分要確認）"),14,true,INK));
            card.addView(text("商品名",13,false,MUTED));
            EditText name=edit("商品名",candidate.name,InputType.TYPE_CLASS_TEXT);card.addView(name);
            card.addView(text("購入数量（規格・内容量とは別）",13,false,MUTED));
            EditText quantity=edit("未確定",candidate.quantity==null?"":candidate.quantity.toString(),InputType.TYPE_CLASS_NUMBER);card.addView(quantity);
            card.addView(text("税込単価（1個分・値引後）",13,false,MUTED));
            EditText price=edit("行金額から自動確定しません","",InputType.TYPE_CLASS_NUMBER);card.addView(price);
            card.addView(button("行金額を税込合計と確認して単価計算",()->{
                try{
                    int q=Integer.parseInt(quantity.getText().toString().trim());
                    if(q<1||q>999)throw new IllegalArgumentException();
                    if(candidate.price%q!=0){toast("割り切れない単価は自動入力できません。1円未満の単価は現在の履歴では正確に保存できません。");return;}
                    new AlertDialog.Builder(this).setTitle("金額の意味を確認")
                        .setMessage("この行の "+candidate.price+"円は、購入数量 "+q+" 個分の値引後・税込合計ですか？税抜や値引前の場合は戻って修正してください。")
                        .setNegativeButton("戻る",null).setPositiveButton("税込合計と確認",(d,w)->price.setText(String.valueOf(candidate.price/q))).show();
                }catch(Exception e){toast("先に、確認した購入数量を入力してください");}
            }));
            card.addView(text("購入時の税率（%）",13,false,MUTED));
            EditText tax=edit("未確定",candidate.taxRate==null?"":candidate.taxRate.toString(),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);card.addView(tax);
            CheckBox discount=new CheckBox(this);discount.setText("特売・値引きあり（配分を確認）");card.addView(discount);
            CheckBox use=new CheckBox(this);use.setChecked(false);use.setText("商品・数量・税込単価・税率を確認したので登録する");card.addView(use);box(card);
            names.add(name);prices.add(price);quantities.add(quantity);taxes.add(tax);include.add(use);sale.add(discount);
        }
        if(!parsed.isEmpty()){
            content.addView(button("確認済みの商品だけ保存",()->{
                try{
                    String shop=store.getText().toString().trim(),purchaseDate=date.getText().toString().trim();
                    LocalDate.parse(purchaseDate);
                    if(shop.isEmpty()||shop.length()>120||!purchaseDate.matches("20\\d{2}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();
                    List<PriceDb.Purchase> list=new ArrayList<>();long selectedSum=0;boolean changedLineAmount=false;
                    for(int n=0;n<parsed.size();n++){
                        if(!include.get(n).isChecked())continue;
                        PriceDb.Purchase p=new PriceDb.Purchase();p.store=shop;p.date=purchaseDate;
                        p.name=names.get(n).getText().toString().trim();p.key=PriceMath.key(p.name);
                        p.gross=Long.parseLong(prices.get(n).getText().toString().trim());p.quantity=Integer.parseInt(quantities.get(n).getText().toString().trim());
                        p.tax=Double.parseDouble(taxes.get(n).getText().toString().trim());p.sale=sale.get(n).isChecked();p.size=0;p.unit="";
                        if(p.name.isEmpty()||p.name.length()>120||p.gross<1||p.gross>1_000_000||p.quantity<1||p.quantity>999||!Double.isFinite(p.tax)||p.tax<0||p.tax>100)throw new IllegalArgumentException();
                        selectedSum+=p.gross*p.quantity;if(p.gross*p.quantity!=parsed.get(n).price)changedLineAmount=true;list.add(p);
                    }
                    if(list.isEmpty()){toast("確認した商品にチェックを入れてください");return;}
                    String comparison=receipt.total==null?"レシート総額は未確定です。":
                        "選択商品の税込合計 "+selectedSum+"円 / レシート総額 "+receipt.total+"円"+
                        (selectedSum!=receipt.total?"\n警告：総額が一致しません。部分登録・認識漏れ・値引きを確認してください。":"\n総額は一致しています。");
                    if(changedLineAmount)comparison+="\n警告：入力した単価×数量とOCRの行金額が異なる商品があります。訂正・割引配分が正しいか確認してください。";
                    new AlertDialog.Builder(this).setTitle(list.size()+"件の確認済み商品を保存しますか？")
                        .setMessage(comparison+"\n"+receipt.reconciliation()+"\n同じレシートの二重登録に注意してください。")
                        .setNegativeButton("戻って修正",null).setPositiveButton("確認して保存",(d,w)->{
                            try{db.addPurchases(list);lastStore=shop;lastDate=purchaseDate;reviewText="";
                                toast(list.size()+"件を端末内に保存しました");render();}
                            catch(Exception e){toast("保存できませんでした。入力を確認してください。");}
                        }).show();
                }catch(Exception ex){toast("選択した商品の数量・税込単価・税率、店名・実在する購入日を入力してください");}
            }));
        }
        section("集計・税・支払などの除外行");
        StringBuilder excluded=new StringBuilder();
        for(ReceiptParser.Line line:receipt.lines)if(line.kind!=ReceiptParser.Kind.ITEM&&line.kind!=ReceiptParser.Kind.OTHER)
            excluded.append(line.kind).append("：").append(line.raw).append('\n');
        hint(excluded.length()==0?"分類できない行もあります。原文と候補を照合してください。":excluded.toString());
        section("認識テキストの修正・再解析");
        hint("再解析や画面切替では、この画面で編集した明細は引き継がれません。保存前に確認してください。");
        EditText raw=new EditText(this);raw.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32000)});raw.setText(reviewText);raw.setSingleLine(false);raw.setMinLines(4);raw.setMaxLines(8);raw.setTextSize(13);content.addView(raw);
        content.addView(button("このテキストで再解析",()->{reviewText=raw.getText().toString();render();}));
        content.addView(button("このOCR結果を破棄",()->{reviewText="";render();}));
    }
    private void showAdmin(){
        section("データは端末内だけ");
        hint("インターネット権限・位置情報権限・広告・解析ツールなし。買い物履歴はアプリ専用のSQLiteデータベースに保存します。");
        section("バックアップ / 復元");
        hint("端末故障や機種変更に備え、JSONを定期的に保存してください。書き出し先は自分で選べます。クラウドを選ぶと、そのサービスにはファイルが渡ります。");
        content.addView(button("JSONバックアップを書き出す",()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"kosso-pricebook-"+LocalDate.now()+".json");startActivityForResult(intent,CREATE_BACKUP);
        }));
        content.addView(button("JSONを復元（現在の記録と置換）",()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,RESTORE_BACKUP);
        }));
        section("試作版 v0.5 OCR比較 / 予定");
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
        state.putString("review_text",reviewText);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){closing=true;if(!ocrRunning)releaseComparisonImage();db.close();super.onDestroy();}
}
