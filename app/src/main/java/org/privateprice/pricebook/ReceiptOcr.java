package org.privateprice.pricebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Local-only Tesseract OCR; supplied models are copied from APK assets, never downloaded at runtime. */
final class ReceiptOcr {
    private ReceiptOcr(){}
    static String recognize(Context context, Uri uri) throws Exception {
        File privateRoot=new File(context.getFilesDir(),"ocr-data");
        File tessdata=new File(privateRoot,"tessdata");
        if(!tessdata.exists()&&!tessdata.mkdirs())throw new IllegalStateException("OCRモデル保存場所を作れません");
        for(String language:new String[]{"jpn","eng"}){
            String filename=language+".traineddata";
            File output=new File(tessdata,filename);
            if(!output.isFile()||output.length()==0){
                File part=new File(tessdata,filename+".part");
                try(InputStream in=context.getAssets().open("tessdata/"+filename); FileOutputStream out=new FileOutputStream(part)) {
                    byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                if(!part.renameTo(output))throw new IllegalStateException("OCRモデルコピー失敗");
            }
        }
        Bitmap image=decodeScaled(context,uri);
        if(image==null)throw new IllegalArgumentException("画像の読み込みに失敗しました");
        TessBaseAPI api=new TessBaseAPI();Bitmap corrected=null;
        try {
            if(!api.init(privateRoot.getAbsolutePath(),"jpn+eng"))throw new IllegalStateException("OCR初期化に失敗しました");
            api.setVariable("preserve_interword_spaces","1");
            String best=read(api,image,TessBaseAPI.PageSegMode.PSM_AUTO);
            int bestScore=score(best,api.meanConfidence());
            corrected=correct(image);
            // Compare full-page layout and a single receipt text block; never merge alternate outputs.
            for(int mode:new int[]{TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,TessBaseAPI.PageSegMode.PSM_AUTO}){
                String candidate=read(api,corrected,mode);int quality=score(candidate,api.meanConfidence());
                if(quality>bestScore){best=candidate;bestScore=quality;}
            }
            return best;
        } finally {api.recycle();if(corrected!=null)corrected.recycle();image.recycle();}
    }
    private static String read(TessBaseAPI api,Bitmap bitmap,int mode){
        api.clear();api.setPageSegMode(mode);api.setImage(bitmap);
        String text=api.getUTF8Text();
        if(text==null)return "";
        if(text.length()>32000)throw new IllegalArgumentException("認識結果が長すぎます。レシートを分割してください");
        return text;
    }
    private static int score(String text,int confidence){
        ReceiptParser.Receipt r=ReceiptParser.parseReceipt(text);
        // Prefer coherent metadata/layout to confidence alone; this is NOT registration confidence.
        int score=Math.min(100,confidence)+(r.date.isEmpty()?0:30)+(r.store.isEmpty()?0:20)+(r.total==null?0:40);
        score+=Math.min(15,r.items.size())*12;
        if(r.total!=null&&r.includesTax&&!r.hasDiscount){long sum=0;for(ReceiptParser.Candidate c:r.items)sum+=c.price;if(sum==r.total)score+=80;}
        return score-r.warnings.size()*10;
    }
    private static Bitmap correct(Bitmap input){
        int width=input.getWidth(),height=input.getHeight();int[] pixels=new int[width*height];
        input.getPixels(pixels,0,width,0,0,width,height);
        ReceiptImage.normalize(pixels,width,height);
        double angle=ReceiptImage.deskew(pixels,width,height);int[] crop=ReceiptImage.bounds(pixels,width,height);
        Bitmap normalized=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        int cw=crop[2],ch=crop[3];double radians=Math.toRadians(angle);
        int outW=(int)Math.ceil(cw*Math.cos(radians)+ch*Math.abs(Math.sin(radians)))+32;
        int outH=(int)Math.ceil(ch*Math.cos(radians)+cw*Math.abs(Math.sin(radians)))+32;
        Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);canvas.drawColor(Color.WHITE);
        canvas.translate(outW/2f,outH/2f);canvas.rotate((float)angle);
        canvas.translate(-cw/2f-crop[0],-ch/2f-crop[1]);
        canvas.drawBitmap(normalized,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));normalized.recycle();return out;
    }
    private static Bitmap decodeScaled(Context context,Uri uri)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IllegalArgumentException("画像形式に未対応です");
        int sample=1;
        while(options.outWidth/sample>2800||options.outHeight/sample>4800||(long)options.outWidth/sample*(options.outHeight/sample)>4_000_000L)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IllegalArgumentException("画像を展開できません");
        int degrees=0;boolean mirror=false;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            int orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            switch(orientation){case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:mirror=true;break;case ExifInterface.ORIENTATION_FLIP_VERTICAL:mirror=true;degrees=180;break;case ExifInterface.ORIENTATION_TRANSPOSE:mirror=true;degrees=90;break;case ExifInterface.ORIENTATION_TRANSVERSE:mirror=true;degrees=270;break;case ExifInterface.ORIENTATION_ROTATE_90:degrees=90;break;case ExifInterface.ORIENTATION_ROTATE_180:degrees=180;break;case ExifInterface.ORIENTATION_ROTATE_270:degrees=270;break;default:break;}
        }catch(Exception ignore){ /* Missing/corrupt EXIF should not block basic OCR. */ }
        if(degrees==0&&!mirror)return bitmap;
        Matrix matrix=new Matrix();if(mirror)matrix.postScale(-1,1);matrix.postRotate(degrees);
        Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
        bitmap.recycle();return rotated;
    }
}
