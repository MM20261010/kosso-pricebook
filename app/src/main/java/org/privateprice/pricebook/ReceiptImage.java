package org.privateprice.pricebook;

/** Pure Java preprocessing, shared by the Android path and synthetic regression benchmark. */
public final class ReceiptImage {
    private ReceiptImage(){}
    public static void normalize(int[] pixels,int width,int height){
        if(width<1||height<1||(long)width*height>4_000_000L||pixels.length!=width*height)throw new IllegalArgumentException("Image limit");
        // Local bright background estimate handles broad shadows without smoothing fine Japanese strokes.
        for(int top=0;top<height;top+=48)for(int left=0;left<width;left+=48){
            int[] histogram=new int[256];int bottom=Math.min(top+48,height),right=Math.min(left+48,width),count=0;
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){int p=pixels[y*width+x];histogram[gray(p)]++;count++;}
            int bright=255,n=0;for(;bright>1;bright--){n+=histogram[bright];if(n>=Math.max(1,count/5))break;}
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
                int g=gray(pixels[y*width+x]);g=Math.max(0,Math.min(255,(g-(bright-160))*255/160));
                pixels[y*width+x]=0xff000000|(g<<16)|(g<<8)|g;
            }
        }
    }
    private static int gray(int p){return (((p>>16)&255)*77+((p>>8)&255)*150+(p&255)*29)>>8;}
    /** Projection profile search. Return corrective rotation, restricted to small receipt skew. */
    public static double deskew(int[] p,int width,int height){
        if(width<1||height<1||p.length!=(long)width*height||p.length>4_000_000)throw new IllegalArgumentException("Image limit");
        int stride=2;
        double best=0,bestScore=0,zeroScore=0;
        for(int half=-10;half<=10;half++){
            double angle=half*0.5, slope=Math.tan(Math.toRadians(angle));int[] bins=new int[height+width/4+8];
            for(int y=0;y<height;y+=stride)for(int x=0;x<width;x+=stride)if((p[y*width+x]&255)<100){
                int b=(int)Math.round((y+x*slope)/stride)+width/8+2;if(b>=0&&b<bins.length)bins[b]++;
            }
            double score=0;for(int n:bins)score+=(long)n*n;
            if(half==0)zeroScore=score;
            if(score>bestScore){bestScore=score;best=angle;}
        }
        return bestScore>zeroScore*1.06?best:0; // Avoid rotating low evidence/noisy images.
    }
    /** Foreground bounding region (x,y,width,height), with a white margin added by the caller. */
    public static int[] bounds(int[] p,int width,int height){
        int left=width,right=-1,top=height,bottom=-1;int[] columns=new int[width];
        for(int y=0;y<height;y++){
            int count=0;for(int x=0;x<width;x++)if((p[y*width+x]&255)<120){count++;columns[x]++;}
            if(count>=Math.max(3,width/200)){top=Math.min(top,y);bottom=y;}
        }
        for(int x=0;x<width;x++)if(columns[x]>=Math.max(3,height/300)){left=Math.min(left,x);right=x;}
        if(right<left||bottom<top)return new int[]{0,0,width,height};
        return new int[]{Math.max(0,left-12),Math.max(0,top-12),Math.min(width,right+13)-Math.max(0,left-12),Math.min(height,bottom+13)-Math.max(0,top-12)};
    }
}
