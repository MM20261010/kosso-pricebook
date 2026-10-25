package org.privateprice.pricebook;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Debug-only local ncnn core; APK assets only, no download or diagnostic/telemetry SDK. */
final class PaddleOcr {
    static { System.loadLibrary("local_paddle"); }
    private PaddleOcr(){}
    private static native byte[] nativeRecognize(AssetManager assets,Bitmap bitmap);
    static String recognize(Context context,Bitmap bitmap)throws Exception{
        if(!BuildConfig.DEBUG)throw new IllegalStateException("比較OCRはデバッグ版専用です");
        verifyAssets(context.getAssets());
        byte[] result=nativeRecognize(context.getAssets(),bitmap);
        String text=new String(result,StandardCharsets.UTF_8);
        if(text.length()>32000)throw new IllegalArgumentException("認識テキストが長すぎます。分割してください");
        return text;
    }
    static void verifyAssets(AssetManager assets)throws Exception{
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        try(InputStream in=assets.open("paddle/manifest.json")){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(buf.size()+n>64000)throw new IllegalArgumentException("manifest limit");buf.write(b,0,n);}
        }
        JSONObject files=new JSONObject(buf.toString("UTF-8")).getJSONObject("files");
        String[] names={"det.param","det.bin","rec.param","rec.bin","keys.bin"};
        if(files.length()!=names.length)throw new IllegalArgumentException("model manifest");
        for(String name:names){
            JSONObject entry=files.getJSONObject(name);String expected=entry.getString("sha256");
            if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("model digest");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(InputStream in=assets.open("paddle/"+name)){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){size+=n;if(size>40_000_000)throw new IllegalArgumentException("model size");digest.update(b,0,n);}
            }
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(size!=entry.getLong("bytes")||!actual.toString().equals(expected))throw new IllegalArgumentException("model integrity");
        }
    }
}
