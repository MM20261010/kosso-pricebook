package org.privateprice.pricebook;
/** Runs against the actual baseline/current APK DEX in Android ART; never reads app storage. */
public final class ReceiptClassificationProbe {
    public static void main(String[] args){
        if(args.length!=1||!(args[0].equals("v08")||args[0].equals("current")))throw new IllegalArgumentException("probe mode");
        int[] expected=args[0].equals("v08")?new int[]{6,6,7,7,8}:new int[]{6,6,6,6,6};
        for(int index=0;index<expected.length;index++){
            ReceiptParser.Receipt r=ReceiptParser.parseDocument(ReceiptAuditScenario.fixture(index));
            if(r.items.size()!=expected[index])throw new AssertionError("synthetic matrix "+index);
            boolean pair79=false,pair245=false;
            for(ReceiptParser.Candidate c:r.items){pair79|=c.price==158&&Integer.valueOf(2).equals(c.quantity)&&Long.valueOf(79).equals(c.unitPrice);pair245|=c.price==490&&Integer.valueOf(2).equals(c.quantity)&&Long.valueOf(245).equals(c.unitPrice);}
            if(!pair79||!pair245)throw new AssertionError("synthetic pairs "+index);
            System.out.println("MATRIX "+args[0]+" "+ReceiptAuditScenario.IDS[index]+" candidates="+r.items.size());
        }
        System.out.println("PASS synthetic APK DEX classification matrix "+args[0]);
    }
}
