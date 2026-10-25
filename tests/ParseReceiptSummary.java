package org.privateprice.pricebook;
import java.nio.file.*;
import java.util.*;
public class ParseReceiptSummary {
 public static void main(String[] args)throws Exception{
  ReceiptParser.Receipt r=ReceiptParser.parseReceipt(Files.readString(Path.of(args[0])));
  List<String> expected=Arrays.asList("牛乳","食パン","豆腐");int correct=0,falsePositive=0;
  for(ReceiptParser.Candidate c:r.items){if(expected.contains(c.name))correct++;else falsePositive++;}
  boolean quantityUnit=false;for(ReceiptParser.Candidate c:r.items)if(c.name.equals("牛乳")&&Integer.valueOf(2).equals(c.quantity)&&Long.valueOf(109).equals(c.unitPrice))quantityUnit=true;
  System.out.println(correct+","+falsePositive+","+(r.date.equals("2026-10-25")?1:0)+","+(quantityUnit?1:0));
 }
}
