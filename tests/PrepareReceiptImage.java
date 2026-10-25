package org.privateprice.pricebook;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
/** Desktop harness for the identical preprocessing used on Android. Synthetic images only. */
public class PrepareReceiptImage {
 public static void main(String[] args)throws Exception{
  BufferedImage in=ImageIO.read(new File(args[0]));int w=in.getWidth(),h=in.getHeight();
  int[] p=in.getRGB(0,0,w,h,null,0,w);ReceiptImage.normalize(p,w,h);
  double a=ReceiptImage.deskew(p,w,h);int[] b=ReceiptImage.bounds(p,w,h);
  BufferedImage norm=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);norm.setRGB(0,0,w,h,p,0,w);
  double rad=Math.toRadians(a);int ow=(int)Math.ceil(b[2]*Math.cos(rad)+b[3]*Math.abs(Math.sin(rad)))+32;
  int oh=(int)Math.ceil(b[3]*Math.cos(rad)+b[2]*Math.abs(Math.sin(rad)))+32;
  BufferedImage out=new BufferedImage(ow,oh,BufferedImage.TYPE_INT_RGB);Graphics2D g=out.createGraphics();
  g.setColor(Color.WHITE);g.fillRect(0,0,ow,oh);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  g.translate(ow/2.0,oh/2.0);g.rotate(rad);g.translate(-b[2]/2.0-b[0],-b[3]/2.0-b[1]);g.drawImage(norm,0,0,null);g.dispose();
  ImageIO.write(out,"png",new File(args[1]));System.out.println("corrective_angle="+a);
 }
}
