package org.privateprice.pricebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded geometric row recovery. Coordinates remain in this process only. */
final class ReceiptLayout {
    static final class Row {
        String text=""; double left,right,top,bottom;
        boolean uncertain;
        final List<Part> parts=new ArrayList<>();
        double height(){return bottom-top;}
        double center(){return (top+bottom)/2;}
        void add(Part p){
            if(parts.isEmpty()){left=p.left;right=p.right;top=p.top;bottom=p.bottom;}
            else{left=Math.min(left,p.left);right=Math.max(right,p.right);top=Math.min(top,p.top);bottom=Math.max(bottom,p.bottom);}
            parts.add(p);
        }
    }
    private static final class Part {
        String text;double left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY,top=Double.POSITIVE_INFINITY,bottom=Double.NEGATIVE_INFINITY;
        double height(){return bottom-top;}
        double center(){return (top+bottom)/2;}
    }
    static List<Row> rows(OcrDocument doc){
        List<Double> angles=new ArrayList<>();
        for(OcrDocument.Region r:doc.regions){double a=Math.atan2(r.y(1)-r.y(0),r.x(1)-r.x(0));if(Math.abs(a)<Math.PI/4)angles.add(a);}
        angles.sort(Double::compare);double angle=angles.isEmpty()?0:angles.get(angles.size()/2),cos=Math.cos(angle),sin=Math.sin(angle);
        List<Part> parts=new ArrayList<>();
        for(OcrDocument.Region region:doc.regions){
            Part p=new Part();p.text=region.text;
            for(int i=0;i<4;i++){
                double x=region.x(i)*cos+region.y(i)*sin,y=region.y(i)*cos-region.x(i)*sin;
                p.left=Math.min(p.left,x);p.right=Math.max(p.right,x);p.top=Math.min(p.top,y);p.bottom=Math.max(p.bottom,y);
            }parts.add(p);
        }
        parts.sort(Comparator.comparingDouble(Part::center).thenComparingDouble(p->p.left));
        List<Row> rows=new ArrayList<>();
        for(Part p:parts){
            Row best=null;double distance=Double.POSITIVE_INFINITY;int matches=0;boolean overlapConflict=false;List<Row> compatible=new ArrayList<>();
            for(Row row:rows){
                double small=Math.min(p.height(),row.height()),large=Math.max(p.height(),row.height());
                double overlap=Math.min(p.bottom,row.bottom)-Math.max(p.top,row.top);
                if(overlap<.25*small||Math.abs(p.center()-row.center())>.75*large||Math.abs(p.bottom-row.bottom)>.85*large)continue;
                // Overlapping regions are not silently concatenated into one product.
                if(row.parts.stream().anyMatch(q->Math.min(q.right,p.right)-Math.max(q.left,p.left)>.3*Math.min(q.right-q.left,p.right-p.left))){row.uncertain=true;overlapConflict=true;continue;}
                matches++;compatible.add(row);double d=Math.abs(p.center()-row.center())/Math.max(1,small);
                if(d<distance){distance=d;best=row;}
            }
            if(matches>1){Row row=new Row();row.uncertain=true;row.add(p);rows.add(row);for(Row other:compatible)other.uncertain=true;}
            else if(best!=null)best.add(p);else{Row row=new Row();row.uncertain=overlapConflict;row.add(p);rows.add(row);}
        }
        rows.sort(Comparator.comparingDouble(Row::center).thenComparingDouble(r->r.left));
        for(Row row:rows){
            row.parts.sort(Comparator.comparingDouble(p->p.left));StringBuilder text=new StringBuilder();
            for(Part p:row.parts){if(text.length()>0)text.append(' ');text.append(p.text.replace('\n',' '));}
            row.text=text.toString();
            if(row.parts.stream().filter(p->p.text.matches(".*[\\p{L}].*\\s+[¥]?[0-9][0-9,]*\\s*[CVcv*※軽外内非]*$")).count()>1)row.uncertain=true;
        }
        return rows;
    }
    static boolean adjacent(Row before,Row after){
        if(before==null||after==null)return true; // text-only fallback uses its own line-gap limit
        double height=Math.max(before.height(),after.height()),gap=after.top-before.bottom;
        return !before.uncertain&&!after.uncertain&&gap>=-.4*height&&gap<=1.5*height;
    }
    static boolean alignedName(Row before,Row after,int imageWidth){
        return before==null||after==null||Math.abs(before.left-after.left)<=Math.max(2*Math.min(before.height(),after.height()),.1*imageWidth);
    }
}
