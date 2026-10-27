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
    static final class Part {
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
    /** Remove only a separate long numeric region left of a name and a separate right amount.
     * Embedded model numbers/capacities, and uncertain arrangements, remain untouched. */
    static String productText(Row row,List<Row> rows){
        if(row.uncertain||row.parts.size()<3)return row.text;
        Part last=row.parts.get(row.parts.size()-1);
        if(!last.text.matches("[¥]?[0-9][0-9,]*円?\\s*[CVcv*※軽外内非]*"))return row.text;
        StringBuilder out=new StringBuilder();
        for(int i=0;i<row.parts.size();i++){
            Part p=row.parts.get(i);
            boolean code=hasCodeHeading(p,rows)&&p.text.matches("[0-9]{5,18}")&&i+1<row.parts.size()-1
                &&row.parts.get(i+1).text.matches(".*[\\p{L}].*")
                &&p.right<row.parts.get(i+1).left&&last.left>row.parts.get(i+1).right;
            if(!code){if(out.length()>0)out.append(' ');out.append(p.text);}
        }
        return out.toString();
    }
    static final class Code {
        final String text,value;final boolean uncertain;
        Code(String text,String value,boolean uncertain){this.text=text;this.value=value;this.uncertain=uncertain;}
    }
    /** Repeated, separately detected numeric/name/price columns are the anchors.
     * Glued prefixes are only proposals; they require a separate human name confirmation. */
    static java.util.Map<Row,Code> codes(List<Row> rows){
        java.util.Map<Row,Code> result=new java.util.IdentityHashMap<>();
        List<Row> anchors=new ArrayList<>();
        for(Row row:rows){
            if(row.uncertain||row.parts.size()<3||!pricePart(row.parts.get(row.parts.size()-1)))continue;
            Part first=row.parts.get(0),name=row.parts.get(1);
            if(first.text.matches("[0-9]{1,3}")&&name.text.matches(".*[\\p{L}].*")&&first.right<name.left&&name.right<row.parts.get(row.parts.size()-1).left)anchors.add(row);
        }
        for(Row row:rows){
            if(row.uncertain||row.parts.size()<2||!pricePart(row.parts.get(row.parts.size()-1)))continue;
            Part first=row.parts.get(0),last=row.parts.get(row.parts.size()-1);
            List<Row> family=new ArrayList<>();
            for(Row anchor:anchors){Part a=anchor.parts.get(0),p=anchor.parts.get(anchor.parts.size()-1);if(Math.abs(a.left-first.left)<=Math.max(20,first.height())&&Math.abs(p.left-last.left)<=Math.max(30,last.height()*2))family.add(anchor);}
            if(family.size()<2)continue;
            if(anchors.contains(row)){
                StringBuilder text=new StringBuilder();for(int i=1;i<row.parts.size();i++){if(text.length()>0)text.append(' ');text.append(row.parts.get(i).text);}
                result.put(row,new Code(text.toString(),first.text,!hasCodeHeading(first,rows)));continue;
            }
            // No arbitrary first-two-digit deletion: the value and column must be observed
            // independently in other product rows, with at least two separated anchors.
            for(Row anchor:family){
                String value=anchor.parts.get(0).text;
                String normalized=java.text.Normalizer.normalize(first.text,java.text.Normalizer.Form.NFKC);
                if(!normalized.startsWith(value)||normalized.length()==value.length())continue;
                String rest=normalized.substring(value.length()),trimmed=rest.trim();
                if(!trimmed.matches("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*"))continue;
                if(trimmed.matches("(?i)^(?:号|年|型|世紀|茶|倍|枚|個|本|袋|点).*"))continue;
                StringBuilder text=new StringBuilder(trimmed);for(int i=1;i<row.parts.size();i++)text.append(' ').append(row.parts.get(i).text);
                result.put(row,new Code(text.toString(),value,!rest.startsWith(" ")));break;
            }
        }
        for(Row row:rows){
            if(row.uncertain||row.parts.size()!=1||!row.text.matches("[0-9]{1,3}"))continue;
            Part part=row.parts.get(0);int matches=0;
            for(Row anchor:anchors)if(Math.abs(anchor.left-part.left)<=Math.max(20,part.height()))matches++;
            if(matches>=2&&anchors.stream().anyMatch(a->a.parts.get(0).text.equals(part.text)))result.put(row,new Code("",part.text,true));
        }
        return result;
    }
    private static boolean pricePart(Part part){return part.text.matches("[¥]?[0-9][0-9,]*円?\\s*[CVcv*※軽外内非]*");}
    private static boolean hasCodeHeading(Part part,List<Row> rows){
        for(Row header:rows)for(Part p:header.parts){
            if(header.uncertain||p.bottom>=part.top||!p.text.replaceAll("\\s+","").matches("(?i)(?:商品コード|JAN(?:コード)?|SKU)"))continue;
            if(Math.abs((p.left+p.right-part.left-part.right)/2)<=Math.max(part.height()*2,(p.right-p.left)/2))return true;
        }
        return false;
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
