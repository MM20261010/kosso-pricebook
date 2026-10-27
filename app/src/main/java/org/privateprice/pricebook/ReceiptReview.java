package org.privateprice.pricebook;

import java.util.ArrayList;
import java.util.List;

/** In-memory, explicit human review. No Android, storage, network, or tax inference. */
final class ReceiptReview {
    enum Rounding { DOWN, NEAREST, UP }
    static final class Item {
        final ReceiptParser.Candidate source;
        String name,specification;
        Long printedUnit; long lineAmount; ReceiptParser.AmountBasis basis; boolean detailsConfirmed;
        Integer quantity;
        Long grossUnit;
        Double taxRate;
        boolean selected,sale;
        Item(ReceiptParser.Candidate source){this.source=source;name=source.name;quantity=source.quantity;printedUnit=source.unitPrice;lineAmount=source.price;basis=source.amountBasis;specification=source.specification;}
        String equation(){return (printedUnit==null?"単価 ?":printedUnit+"円")+" × "+(quantity==null?"?":quantity)+"点 ＝ "+lineAmount+"円（"+ReceiptParser.basisLabel(basis)+"）";}
        String issue(){
            if(!detailsConfirmed&&(source.uncertainLayout||source.nameSuffixUncertain))return "名称・位置を確認";
            if(quantity==null)return "数量を確認";
            if(printedUnit!=null&&printedUnit*quantity!=lineAmount)return "単価×数量が不一致";
            if(grossUnit==null)return basis==ReceiptParser.AmountBasis.EXCLUSIVE?"税込単価を確認":"価格の区分を確認";
            return "";
        }
        boolean ready(){return name!=null&&!name.trim().isEmpty()&&name.trim().length()<=120&&quantity!=null&&quantity>=1&&quantity<=999&&grossUnit!=null&&grossUnit>=1&&grossUnit<=1_000_000&&(taxRate==null||Double.isFinite(taxRate)&&taxRate>=0&&taxRate<=100);}
    }
    final ReceiptParser.Receipt receipt;
    final List<Item> items=new ArrayList<>();
    String store,date;
    ReceiptReview(ReceiptParser.Receipt receipt){this.receipt=receipt;store=receipt.store;date=receipt.date;for(ReceiptParser.Candidate c:receipt.items)items.add(new Item(c));}
    static long printedUnit(Item item){
        if(item.quantity==null||item.quantity<1||item.quantity>999)throw new IllegalArgumentException("数量を確認してください");
        if(item.printedUnit!=null){
            if(item.printedUnit*item.quantity!=item.lineAmount)throw new IllegalArgumentException("値引・単価と行金額を確認してください");
            return item.printedUnit;
        }
        if(item.lineAmount%item.quantity!=0)throw new IllegalArgumentException("割り切れない単価は手入力してください");
        return item.lineAmount/item.quantity;
    }
    /** Call only after a human confirms gross/net, rate and per-unit rounding for the selected rows. */
    static long confirmedUnit(Item item,boolean net,Double tax,Rounding rounding){
        long unit=printedUnit(item);
        if(!net&&item.basis==ReceiptParser.AmountBasis.EXCLUSIVE)throw new IllegalArgumentException("税抜表示です。税込として扱えません");
        if(net&&item.basis==ReceiptParser.AmountBasis.INCLUSIVE)throw new IllegalArgumentException("税込表示です。税を加算できません");
        if(net){
            if(tax==null||!Double.isFinite(tax)||tax<0||tax>100||rounding==null)throw new IllegalArgumentException("税率と単価ごとの端数処理を確認してください");
            java.math.BigDecimal value=java.math.BigDecimal.valueOf(unit).multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(tax).movePointLeft(2)));
            java.math.RoundingMode mode=rounding==Rounding.DOWN?java.math.RoundingMode.FLOOR:rounding==Rounding.UP?java.math.RoundingMode.CEILING:java.math.RoundingMode.HALF_UP;
            unit=value.setScale(0,mode).longValueExact();
        }
        if(unit<1||unit>1_000_000)throw new IllegalArgumentException("単価が範囲外です");return unit;
    }
}
