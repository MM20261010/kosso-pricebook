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

/** The only persistent storage is the app-private SQLite database. */
public final class PriceDb extends SQLiteOpenHelper {
    static final class Memo {
        long id; String name; String key; int quantity; boolean checked;
    }
    static final class Purchase {
        long id; String name; String key; String store; String date;
        long gross; double tax; int quantity; boolean sale; double size; String unit;
    }

    public PriceDb(Context context) { super(context, "private_pricebook.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, product_key TEXT NOT NULL, quantity INTEGER NOT NULL, checked INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, product_key TEXT NOT NULL, store TEXT NOT NULL, date TEXT NOT NULL, gross INTEGER NOT NULL, tax REAL NOT NULL, quantity INTEGER NOT NULL, sale INTEGER NOT NULL, size REAL NOT NULL DEFAULT 0, unit TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX idx_prices ON purchases(product_key,store,date,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migrations must preserve purchase history");
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
        ContentValues v=new ContentValues();v.put("name",p.name);v.put("product_key",p.key);v.put("store",p.store);v.put("date",p.date);
        v.put("gross",p.gross);v.put("tax",p.tax);v.put("quantity",p.quantity);v.put("sale",p.sale?1:0);v.put("size",p.size);v.put("unit",p.unit);
        return v;
    }
    private static Purchase readPurchase(Cursor c) {
        Purchase p=new Purchase();p.id=c.getLong(0);p.name=c.getString(1);p.key=c.getString(2);p.store=c.getString(3);
        p.date=c.getString(4);p.gross=c.getLong(5);p.tax=c.getDouble(6);p.quantity=c.getInt(7);p.sale=c.getInt(8)!=0;
        p.size=c.getDouble(9);p.unit=c.getString(10);return p;
    }
    private static final String P_COLUMNS="id,name,product_key,store,date,gross,tax,quantity,sale,size,unit";
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
    void deletePurchase(long id) { getWritableDatabase().delete("purchases","id=?",new String[]{Long.toString(id)}); }

    String backupJson() throws JSONException {
        JSONObject root=new JSONObject();root.put("format","kosso-pricebook-v1");
        JSONArray memos=new JSONArray();
        for(Memo m:allMemos()) {JSONObject o=new JSONObject();o.put("name",m.name);o.put("quantity",m.quantity);o.put("checked",m.checked);memos.put(o);}
        JSONArray purchases=new JSONArray();
        for(Purchase p:allPurchases()) {JSONObject o=new JSONObject();o.put("name",p.name);o.put("store",p.store);o.put("date",p.date);o.put("gross",p.gross);o.put("tax",p.tax);o.put("quantity",p.quantity);o.put("sale",p.sale);o.put("size",p.size);o.put("unit",p.unit);purchases.put(o);}
        root.put("memos",memos);root.put("purchases",purchases);return root.toString(2);
    }
    void importBackup(String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        if(!"kosso-pricebook-v1".equals(root.optString("format")))throw new JSONException("非対応のバックアップです");
        JSONArray memos=root.getJSONArray("memos"),purchases=root.getJSONArray("purchases");
        if(memos.length()>5000||purchases.length()>100000)throw new JSONException("バックアップ件数が上限超過です");
        List<Memo> validatedMemos=new ArrayList<>();List<Purchase> validatedPurchases=new ArrayList<>();
        for(int i=0;i<memos.length();i++) {
            JSONObject o=memos.getJSONObject(i);Memo m=new Memo();m.name=o.getString("name").trim();m.key=PriceMath.key(m.name);
            m.quantity=o.getInt("quantity");m.checked=o.getBoolean("checked");
            if(m.name.isEmpty()||m.name.length()>120||m.quantity<1||m.quantity>999)throw new JSONException("不正な買い物メモ");
            validatedMemos.add(m);
        }
        for(int i=0;i<purchases.length();i++) {
            JSONObject o=purchases.getJSONObject(i);Purchase p=new Purchase();p.name=o.getString("name").trim();p.key=PriceMath.key(p.name);p.store=o.getString("store").trim();p.date=o.getString("date");
            p.gross=o.getLong("gross");p.tax=o.getDouble("tax");p.quantity=o.getInt("quantity");p.sale=o.getBoolean("sale");p.size=o.getDouble("size");p.unit=o.getString("unit");
            if(p.name.isEmpty()||p.name.length()>120||p.store.isEmpty()||p.store.length()>120||!p.date.matches("20\\d{2}-\\d{2}-\\d{2}")||p.gross<0||p.gross>10_000_000||p.quantity<1||p.quantity>999||!Double.isFinite(p.tax)||p.tax<0||p.tax>100||!Double.isFinite(p.size)||p.size<0||p.size>1_000_000||!(p.unit.equals("")||p.unit.equals("g")||p.unit.equals("mL")))throw new JSONException("不正な価格履歴");
            validatedPurchases.add(p);
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("memos",null,null);db.delete("purchases",null,null);
            for(Memo m:validatedMemos){ContentValues v=new ContentValues();v.put("name",m.name);v.put("product_key",m.key);v.put("quantity",m.quantity);v.put("checked",m.checked?1:0);db.insertOrThrow("memos",null,v);}
            for(Purchase p:validatedPurchases)db.insertOrThrow("purchases",null,toPurchaseValues(p));
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
