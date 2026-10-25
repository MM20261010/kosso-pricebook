import org.privateprice.pricebook.PriceMath;

public class PriceMathTest {
    private static int count=0;
    private static void ok(boolean pass,String test){if(!pass)throw new AssertionError(test);count++;}
    public static void main(String[] args){
        ok(PriceMath.key("牛乳 １Ｌ").equals(PriceMath.key("牛乳1L")),"NFKC name matching");
        ok(Math.abs(PriceMath.excludingTax(108,8)-100)<1e-10,"8% tax");
        ok(Math.abs(PriceMath.excludingTax(101,1)-100)<1e-10,"1% tax");
        ok(Math.abs(PriceMath.percentChange(100,105)-5)<1e-10,"increase detection");
        ok(Math.abs(PriceMath.unitNet(216,8,200)-100)<1e-10,"100g unit price");
        boolean rejected=false;try{PriceMath.excludingTax(-1,8);}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"negative price rejection");
        rejected=false;try{PriceMath.percentChange(0,105);}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"zero prior price rejection");
        System.out.println("PASS "+count+" PriceMath tests");
    }
}
