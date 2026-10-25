package org.privateprice.pricebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
        TessBaseAPI api=new TessBaseAPI();
        try {
            if(!api.init(privateRoot.getAbsolutePath(),"jpn+eng"))throw new IllegalStateException("OCR初期化に失敗しました");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            api.setImage(image);
            String result=api.getUTF8Text();
            return result==null?"":result;
        } finally {api.recycle();image.recycle();}
    }
    private static Bitmap decodeScaled(Context context,Uri uri)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IllegalArgumentException("画像形式に未対応です");
        int sample=1;
        while(options.outWidth/sample>2800||options.outHeight/sample>4800||(long)options.outWidth/sample*(options.outHeight/sample)>12_000_000L)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IllegalArgumentException("画像を展開できません");
        int degrees=0;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            int orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            switch(orientation){case ExifInterface.ORIENTATION_ROTATE_90:degrees=90;break;case ExifInterface.ORIENTATION_ROTATE_180:degrees=180;break;case ExifInterface.ORIENTATION_ROTATE_270:degrees=270;break;default:break;}
        }catch(Exception ignore){ /* Missing/corrupt EXIF should not block basic OCR. */ }
        if(degrees==0)return bitmap;
        Matrix matrix=new Matrix();matrix.postRotate(degrees);
        Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
        bitmap.recycle();return rotated;
    }
}
