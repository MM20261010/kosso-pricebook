package org.privateprice.pricebook;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** App-private SQLite only; receipt images and raw OCR are never persisted. */
public final class PriceDb extends SQLiteOpenHelper {
    static final class Memo {
        long id; String name; String key; int quantity; boolean checked;
    }
    static final class Purchase {
        long id; String name; String key; String store; String date;
        long gross; double tax; int quantity; boolean sale; double size; String unit;
        boolean taxKnown=true; Long printedUnit,lineAmount,receiptId; String basis="UNKNOWN",specification="";
    }

    public PriceDb(Context context) { super(context, "private_pricebook.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, product_key TEXT NOT NULL, quantity INTEGER NOT NULL, checked INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, product_key TEXT NOT NULL, store TEXT NOT NULL, date TEXT NOT NULL, gross INTEGER NOT NULL, tax REAL NOT NULL, quantity INTEGER NOT NULL, sale INTEGER NOT NULL, size REAL NOT NULL DEFAULT 0, unit TEXT NOT NULL DEFAULT '')");
        createReceiptStorage(db);
        db.execSQL("CREATE INDEX idx_prices ON purchases(product_key,store,date,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion==1&&newVersion==2){createReceiptStorage(db);return;}
        throw new IllegalStateException("Unsupported non-destructive migration");
    }
    private static void createReceiptStorage(SQLiteDatabase db){
        db.execSQL("ALTER TABLE purchases ADD COLUMN tax_known INTEGER NOT NULL DEFAULT 1");
        db.execSQL("ALTER TABLE purchases ADD COLUMN printed_unit INTEGER");
        db.execSQL("ALTER TABLE purchases ADD COLUMN line_amount INTEGER");
        db.execSQL("ALTER TABLE purchases ADD COLUMN receipt_id INTEGER");
        db.execSQL("ALTER TABLE purchases ADD COLUMN amount_basis TEXT NOT NULL DEFAULT 'UNKNOWN'");
        db.execSQL("ALTER TABLE purchases ADD COLUMN specification TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE TABLE receipts (id INTEGER PRIMARY KEY AUTOINCREMENT, store TEXT NOT NULL, date TEXT NOT NULL, subtotal INTEGER, tax_amount INTEGER, total INTEGER, amount_basis TEXT NOT NULL, uncertain INTEGER NOT NULL)");
    }
    void addReceiptPurchases(List<Purchase> list,ReceiptParser.Receipt receipt,String store,String date){
        if(list.isEmpty()||list.size()>80)throw new IllegalArgumentException();
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("store",store);v.put("date",date);v.put("subtotal",receipt.subtotal);v.put("tax_amount",receipt.taxAmount);v.put("total",receipt.total);v.put("amount_basis",receipt.amountBasis.name());v.put("uncertain",receipt.subtotalUncertain||receipt.totalUncertain||receipt.hasDiscount?1:0);
            long id=database.insertOrThrow("receipts",null,v);
            for(Purchase p:list){p.receiptId=id;database.insertOrThrow("purchases",null,toPurchaseValues(p));}
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }
    String receiptSummary(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT subtotal,tax_amount,total,amount_basis,uncertain FROM receipts WHERE id=?",new String[]{Long.toString(id)})){
            if(!c.moveToFirst())return "";
            return "印字小計 "+(c.isNull(0)?"?":c.getLong(0))+"円 / 税額 "+(c.isNull(1)?"?":c.getLong(1))+"円 / 合計 "+(c.isNull(2)?"?":c.getLong(2))+"円\n"+ReceiptParser.basisLabel(ReceiptParser.AmountBasis.valueOf(c.getString(3)))+"（OCR候補。部分保存でも原票の集計額を保持）"+(c.getInt(4)!=0?"・見出し/割引は要確認":"");
        }
    }
    void addMemo(String name, int quantity) {
        ContentValues v = new ContentValues(); v.put("name",name); v.put("product_key", PriceMath.key(name));v.put("quantity",quantity);v.put("checked",0);
        getWritableDatabase().insertOrThrow("memos",null,v);
    }
    List<Memo> allMemos() {
        List<Memo> items=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,product_key,quantity,checked FROM memos ORDER BY checked ASC,id DESC",null)) {
            while(c.moveToNext()){ Memo m=new Memo();m.id=c.getLong(0);m.name=c.getString(1);m.key=c.getString(2);m.quantity=c.getInt(3);m.checked=c.getInt(4)!=0;items.add(m); }
        }
        return items;
    }
    void setChecked(long id,boolean checked) { ContentValues v=new ContentValues();v.put("checked",checked?1:0);getWritableDatabase().update("memos",v,"id=?",new String[]{Long.toString(id)}); }
    void deleteMemo(long id) {getWritableDatabase().delete("memos","id=?",new String[]{Long.toString(id)});}
    void resetMemoChecks() {ContentValues v=new ContentValues();v.put("checked",0);getWritableDatabase().update("memos",v,null,null);}
    void setMemoQuantity(long id,int quantity){
        if(quantity<1||quantity>999)throw new IllegalArgumentException("Invalid quantity");
        ContentValues v=new ContentValues();v.put("quantity",quantity);
        getWritableDatabase().update("memos",v,"id=?",new String[]{Long.toString(id)});
    }

    void addPurchase(Purchase p) {
        getWritableDatabase().insertOrThrow("purchases",null,toPurchaseValues(p));
    }
    void addPurchases(List<Purchase> list) {
        if(list.isEmpty()||list.size()>80)throw new IllegalArgumentException("購入明細の件数を確認してください");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { for(Purchase p:list)db.insertOrThrow("purchases",null,toPurchaseValues(p));
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    List<Purchase> historyForProductAtStore(String key,String store,int limit) {
        List<Purchase> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+P_COLUMNS+" FROM purchases WHERE product_key=? AND store=? ORDER BY date DESC,id DESC LIMIT ?",new String[]{key,store,String.valueOf(limit)})) {
            while(c.moveToNext())list.add(readPurchase(c));
        }
        return list;
    }
    private static ContentValues toPurchaseValues(Purchase p) {
        if(p.name==null||p.name.trim().isEmpty()||p.name.length()>120||p.store==null||p.store.trim().isEmpty()||p.store.length()>120||!validDate(p.date)||p.gross<0||p.gross>10_000_000||p.quantity<1||p.quantity>999||!Double.isFinite(p.tax)||p.tax<0||p.tax>100||!p.taxKnown&&p.tax!=0||p.specification==null||p.specification.length()>120)throw new IllegalArgumentException("Invalid purchase");
        ContentValues v=new ContentValues();v.put("name",p.name);v.put("product_key",p.key);v.put("store",p.store);v.put("date",p.date);
        v.put("gross",p.gross);v.put("tax",p.tax);v.put("quantity",p.quantity);v.put("sale",p.sale?1:0);v.put("size",p.size);v.put("unit",p.unit);v.put("tax_known",p.taxKnown?1:0);v.put("printed_unit",p.printedUnit);v.put("line_amount",p.lineAmount);v.put("receipt_id",p.receiptId);v.put("amount_basis",p.basis);v.put("specification",p.specification);
        return v;
    }
    private static Purchase readPurchase(Cursor c) {
        Purchase p=new Purchase();p.id=c.getLong(0);p.name=c.getString(1);p.key=c.getString(2);p.store=c.getString(3);
        p.date=c.getString(4);p.gross=c.getLong(5);p.tax=c.getDouble(6);p.quantity=c.getInt(7);p.sale=c.getInt(8)!=0;
        p.size=c.getDouble(9);p.unit=c.getString(10);p.taxKnown=c.getInt(11)!=0;p.printedUnit=c.isNull(12)?null:c.getLong(12);p.lineAmount=c.isNull(13)?null:c.getLong(13);p.receiptId=c.isNull(14)?null:c.getLong(14);p.basis=c.getString(15);p.specification=c.getString(16);return p;
    }
    private static final String P_COLUMNS="id,name,product_key,store,date,gross,tax,quantity,sale,size,unit,tax_known,printed_unit,line_amount,receipt_id,amount_basis,specification";
    List<Purchase> allPurchases() {
        List<Purchase> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+P_COLUMNS+" FROM purchases ORDER BY date DESC,id DESC",null)) {
            while(c.moveToNext())list.add(readPurchase(c));
        }
        return list;
    }
    String mostRecentStore() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT store FROM purchases ORDER BY date DESC,id DESC LIMIT 1",null)) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    String matchKnownStore(String ocrText) {
        if(ocrText==null||ocrText.isEmpty())return "";
        String[] lines=ocrText.split("\\r?\\n",9);
        StringBuilder header=new StringBuilder();
        for(int i=0;i<Math.min(lines.length,7);i++)header.append(lines[i]).append(' ');
        String normalized=PriceMath.key(header.toString());
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT store FROM purchases ORDER BY length(store) DESC",null)) {
            while(c.moveToNext()) {
                String store=c.getString(0),key=PriceMath.key(store);
                if(key.length()>=3&&normalized.contains(key))return store;
            }
        }
        return "";
    }
    List<Purchase> recentPurchases(int limit) {
        List<Purchase> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+P_COLUMNS+" FROM purchases ORDER BY date DESC,id DESC LIMIT ?",new String[]{String.valueOf(limit)})) {
            while(c.moveToNext())list.add(readPurchase(c));
        }
        return list;
    }
    List<Purchase> latestForProduct(String productKey) {
        // Fetch all stores, then take only the last observed purchase at each one.
        // Include sale items but mark them prominently; never present stale prices as live prices.
        List<Purchase> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+P_COLUMNS+" FROM purchases WHERE product_key=? ORDER BY date DESC,id DESC",new String[]{productKey})) {
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            while(c.moveToNext()) {Purchase p=readPurchase(c);if(seen.add(p.store)) list.add(p);}
        }
        return list;
    }
    void deletePurchase(long id) {
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.delete("purchases","id=?",new String[]{Long.toString(id)});
            d.execSQL("DELETE FROM receipts WHERE id NOT IN (SELECT receipt_id FROM purchases WHERE receipt_id IS NOT NULL)");d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }

    String backupJson() throws JSONException {
        JSONObject root=new JSONObject();root.put("format","kosso-pricebook-v2");
        JSONArray memos=new JSONArray();
        for(Memo m:allMemos()) {JSONObject o=new JSONObject();o.put("name",m.name);o.put("quantity",m.quantity);o.put("checked",m.checked);memos.put(o);}
        JSONArray purchases=new JSONArray();
        for(Purchase p:allPurchases()) {JSONObject o=new JSONObject();o.put("name",p.name);o.put("store",p.store);o.put("date",p.date);o.put("gross",p.gross);o.put("tax",p.tax);o.put("quantity",p.quantity);o.put("sale",p.sale);o.put("size",p.size);o.put("unit",p.unit);o.put("taxKnown",p.taxKnown);o.put("printedUnit",p.printedUnit==null?JSONObject.NULL:p.printedUnit);o.put("lineAmount",p.lineAmount==null?JSONObject.NULL:p.lineAmount);o.put("receiptId",p.receiptId==null?JSONObject.NULL:p.receiptId);o.put("amountBasis",p.basis);o.put("specification",p.specification);purchases.put(o);}
        JSONArray receipts=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,store,date,subtotal,tax_amount,total,amount_basis,uncertain FROM receipts ORDER BY id",null)){
            while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getLong(0));o.put("store",c.getString(1));o.put("date",c.getString(2));for(int i=3;i<=5;i++)o.put(new String[]{"subtotal","taxAmount","total"}[i-3],c.isNull(i)?JSONObject.NULL:c.getLong(i));o.put("amountBasis",c.getString(6));o.put("uncertain",c.getInt(7)!=0);receipts.put(o);}
        }
        root.put("receipts",receipts);root.put("memos",memos);root.put("purchases",purchases);return root.toString(2);
    }
    void importBackup(String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        boolean v2="kosso-pricebook-v2".equals(root.optString("format"));
        if(!v2&&!"kosso-pricebook-v1".equals(root.optString("format")))throw new JSONException("非対応のバックアップです");
        JSONArray memos=root.getJSONArray("memos"),purchases=root.getJSONArray("purchases");
        if(memos.length()>5000||purchases.length()>100000)throw new JSONException("バックアップ件数が上限超過です");
        List<Memo> validatedMemos=new ArrayList<>();List<Purchase> validatedPurchases=new ArrayList<>();
        for(int i=0;i<memos.length();i++) {
            JSONObject o=memos.getJSONObject(i);Memo m=new Memo();m.name=o.getString("name").trim();m.key=PriceMath.key(m.name);
            m.quantity=(int)strictLong(o,"quantity",1,999);m.checked=o.getBoolean("checked");
            if(m.name.isEmpty()||m.name.length()>120||m.quantity<1||m.quantity>999)throw new JSONException("不正な買い物メモ");
            validatedMemos.add(m);
        }
        List<ContentValues> validatedReceipts=new ArrayList<>();java.util.HashSet<Long> receiptIds=new java.util.HashSet<>();
        JSONArray receipts=v2?root.getJSONArray("receipts"):new JSONArray();
        if(receipts.length()>100000)throw new JSONException("集計件数が上限超過です");
        for(int i=0;i<receipts.length();i++){
            JSONObject o=receipts.getJSONObject(i);long id=strictLong(o,"id",1,1_000_000_000_000L);String store=o.getString("store"),date=o.getString("date");
            if(id<1||id>1_000_000_000_000L||!receiptIds.add(id)||store.trim().isEmpty()||store.length()>120||!validDate(date))throw new JSONException("不正な集計情報");
            ContentValues v=new ContentValues();v.put("id",id);v.put("store",store);v.put("date",date);v.put("subtotal",optionalMoney(o,"subtotal"));v.put("tax_amount",optionalMoney(o,"taxAmount"));v.put("total",optionalMoney(o,"total"));v.put("amount_basis",validBasis(o.getString("amountBasis")));v.put("uncertain",o.getBoolean("uncertain")?1:0);validatedReceipts.add(v);
        }
        for(int i=0;i<purchases.length();i++) {
            JSONObject o=purchases.getJSONObject(i);Purchase p=new Purchase();p.name=o.getString("name").trim();p.key=PriceMath.key(p.name);p.store=o.getString("store").trim();p.date=o.getString("date");
            p.gross=strictLong(o,"gross",0,10_000_000);p.tax=o.getDouble("tax");p.quantity=(int)strictLong(o,"quantity",1,999);p.sale=o.getBoolean("sale");p.size=o.getDouble("size");p.unit=o.getString("unit");
            if(p.name.isEmpty()||p.name.length()>120||p.store.isEmpty()||p.store.length()>120||!p.date.matches("20\\d{2}-\\d{2}-\\d{2}")||p.gross<0||p.gross>10_000_000||p.quantity<1||p.quantity>999||!Double.isFinite(p.tax)||p.tax<0||p.tax>100||!Double.isFinite(p.size)||p.size<0||p.size>1_000_000||!(p.unit.equals("")||p.unit.equals("g")||p.unit.equals("mL")))throw new JSONException("不正な価格履歴");
            if(!validDate(p.date))throw new JSONException("不正な購入日");
            if(v2){p.taxKnown=o.getBoolean("taxKnown");p.printedUnit=optionalMoney(o,"printedUnit");p.lineAmount=optionalMoney(o,"lineAmount");p.receiptId=o.isNull("receiptId")?null:strictLong(o,"receiptId",1,1_000_000_000_000L);p.basis=validBasis(o.getString("amountBasis"));p.specification=o.getString("specification");if(p.specification.length()>120||p.receiptId!=null&&!receiptIds.contains(p.receiptId)||!p.taxKnown&&p.tax!=0)throw new JSONException("不正な明細情報");}
            validatedPurchases.add(p);
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("memos",null,null);db.delete("purchases",null,null);db.delete("receipts",null,null);
            for(ContentValues v:validatedReceipts)db.insertOrThrow("receipts",null,v);
            for(Memo m:validatedMemos){ContentValues v=new ContentValues();v.put("name",m.name);v.put("product_key",m.key);v.put("quantity",m.quantity);v.put("checked",m.checked?1:0);db.insertOrThrow("memos",null,v);}
            for(Purchase p:validatedPurchases)db.insertOrThrow("purchases",null,toPurchaseValues(p));
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private static boolean validDate(String s){try{return s.matches("20\\d{2}-\\d{2}-\\d{2}")&&java.time.LocalDate.parse(s)!=null;}catch(Exception e){return false;}}
    private static String validBasis(String s) throws JSONException {try{return ReceiptParser.AmountBasis.valueOf(s).name();}catch(Exception e){throw new JSONException("不正な税区分");}}
    private static long strictLong(JSONObject o,String key,long min,long max) throws JSONException {
        Object raw=o.get(key);if(!(raw instanceof Integer||raw instanceof Long))throw new JSONException("整数の形式が不正です");
        long n=((Number)raw).longValue();if(n<min||n>max)throw new JSONException("整数が範囲外です");return n;
    }
    private static Long optionalMoney(JSONObject o,String key) throws JSONException {
        if(o.isNull(key))return null;
        Object raw=o.get(key);if(!(raw instanceof Number)||raw instanceof Double||raw instanceof Float)throw new JSONException("金額の形式が不正です");
        long v=o.getLong(key);if(v<0||v>100_000_000)throw new JSONException("金額が範囲外です");return v;
    }
}
