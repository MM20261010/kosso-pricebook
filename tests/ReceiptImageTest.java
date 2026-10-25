package org.privateprice.pricebook;
import java.util.Arrays;
public class ReceiptImageTest {
 public static void main(String[] args){
  int w=640,h=900;int[] p=new int[w*h];Arrays.fill(p,0xffd8d8d8);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int g=180+x*60/w;p[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;
  }
  for(int line=0;line<12;line++)for(int x=60;x<580;x++)if(x%12<8){
   int y=80+line*55+(int)Math.round(x*Math.tan(Math.toRadians(3)));
   for(int d=0;d<4;d++)p[(y+d)*w+x]=0xff303030;
  }
  ReceiptImage.normalize(p,w,h);
  if((p[10*w+10]&255)<240)throw new AssertionError("shadow correction");
  double angle=ReceiptImage.deskew(p,w,h);
  if(Math.abs(angle+3)>0.5)throw new AssertionError("deskew: "+angle);
  int[] b=ReceiptImage.bounds(p,w,h);if(b[2]>=w||b[3]>=h)throw new AssertionError("text crop");
  Arrays.fill(p,0xffffffff);if(ReceiptImage.deskew(p,w,h)!=0)throw new AssertionError("blank rotation");
  boolean rejected=false;try{ReceiptImage.normalize(new int[1],Integer.MAX_VALUE,2);}catch(IllegalArgumentException e){rejected=true;}
  if(!rejected)throw new AssertionError("image bounds");
  System.out.println("PASS ReceiptImage 5 checks (shadow, skew, crop, blank, bounds)");
 }
}
