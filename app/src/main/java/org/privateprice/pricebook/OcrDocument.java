package org.privateprice.pricebook;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ephemeral local OCR output. Never stored in the purchase DB, backup, or logs. */
public final class OcrDocument {
    public static final class Region {
        public final String text;
        private final float[] corners;
        public Region(String text,float... corners){
            if(text==null||text.isEmpty()||text.length()>2048||corners.length!=8)throw new IllegalArgumentException("OCR region limit");
            this.text=text;this.corners=corners.clone();
            for(float v:corners)if(!Float.isFinite(v))throw new IllegalArgumentException("OCR coordinate");
        }
        public float x(int corner){return corners[2*corner];}
        public float y(int corner){return corners[2*corner+1];}
    }
    public final String text;
    public final int width,height;
    public final List<Region> regions;
    public OcrDocument(String text,int width,int height,List<Region> regions){
        if(text==null||text.length()>32000||width<2||height<2||(long)width*height>4_000_000||regions==null||regions.size()>80)throw new IllegalArgumentException("OCR document limit");
        int chars=0;
        for(Region r:regions){
            if(r==null)throw new IllegalArgumentException("OCR region");
            chars+=r.text.length();double area=0;
            for(int i=0;i<4;i++){
                if(r.x(i)<0||r.x(i)>width-1||r.y(i)<0||r.y(i)>height-1)throw new IllegalArgumentException("OCR coordinate bounds");
                int j=(i+1)%4;area+=(double)r.x(i)*r.y(j)-(double)r.x(j)*r.y(i);
            }
            if(Math.abs(area)<2)throw new IllegalArgumentException("OCR empty region");
        }
        if(chars>32000)throw new IllegalArgumentException("OCR text limit");
        this.text=text;this.width=width;this.height=height;this.regions=Collections.unmodifiableList(new ArrayList<>(regions));
    }
    private static String utf8(ByteBuffer b,int maxBytes){
        int n=b.getInt();if(n<0||n>maxBytes||n>b.remaining())throw new IllegalArgumentException("OCR byte length");
        ByteBuffer part=b.slice();part.limit(n);b.position(b.position()+n);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(part).toString();}
        catch(java.nio.charset.CharacterCodingException e){throw new IllegalArgumentException("OCR encoding");}
    }
    public static OcrDocument decode(byte[] bytes){
        if(bytes==null||bytes.length<20||bytes.length>262144)throw new IllegalArgumentException("OCR payload limit");
        try{
            ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x3642504b)throw new IllegalArgumentException("OCR payload version");
            int width=b.getInt(),height=b.getInt();String text=utf8(b,128000);int count=b.getInt();
            if(count<0||count>80)throw new IllegalArgumentException("OCR region count");
            List<Region> regions=new ArrayList<>();
            for(int i=0;i<count;i++){float[] q=new float[8];for(int j=0;j<8;j++)q[j]=b.getFloat();regions.add(new Region(utf8(b,8192),q));}
            if(b.hasRemaining())throw new IllegalArgumentException("OCR trailing bytes");
            return new OcrDocument(text,width,height,regions);
        }catch(java.nio.BufferUnderflowException e){throw new IllegalArgumentException("OCR truncated payload");}
    }
}
