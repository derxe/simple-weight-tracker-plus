package com.example.simpleweighttracker;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.Serializable;
import java.util.List;

public class WeightsValueProvider extends ContentProvider {
    // fields for my content provider
    static final String PROVIDER_NAME = "com.simpleweighttracker.tracker";
    static final String URL = "content://" + PROVIDER_NAME + "/values";
    static final Uri CONTENT_URI = Uri.parse(URL);

    // fields for the database
    static final String VALUE = "value";
    static final String TIMESTAMP = "_id";
    static final String UPDATED_AT = "updatedAt";

    // integer values used in content URI
    static final int VALUES = 1;
    static final int VALUE_ID = 2;

    DBHelper dbHelper;

    // maps content URI "patterns" to the integer values that were set above
    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "values", VALUES);
        uriMatcher.addURI(PROVIDER_NAME, "values/#", VALUE_ID);
    }

    // database declarations
    private SQLiteDatabase database;
    static final String DATABASE_NAME = "tracker";
    static final String TABLE_NAME = "tracker";
    static final int DATABASE_VERSION = 1;

    // class that creates and manages the provider's database
    private static class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String sqlCreate = "CREATE TABLE " + TABLE_NAME + " (" +
                    TIMESTAMP + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    VALUE + " REAL NOT NULL, " +
                    UPDATED_AT + " INTEGER " +
                    ");";
            //sqlCreate += "create index timestamp on " + TABLE_NAME + " (timestamp);";

            db.execSQL(sqlCreate);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(DBHelper.class.getName(),
                    "Upgrading database from version " + oldVersion + " to "
                            + newVersion + ". Old data will be destroyed");
            db.execSQL("DROP TABLE IF EXISTS " +  TABLE_NAME);
            onCreate(db);
        }

    }

    public static class Weight implements Serializable {
        String weight;
        long timestamp;
        long updatedAt;
    }

    private static Weight getWeightFromCursor(Cursor cursor) {
        int timestampIndex = cursor.getColumnIndex(TIMESTAMP);
        int weightIndex = cursor.getColumnIndex(VALUE);
        int updatedAtIndex = cursor.getColumnIndex(UPDATED_AT);

        Weight weight = null;
        if (cursor.moveToNext()) {
            weight = new Weight();
            weight.timestamp = cursor.getLong(timestampIndex);
            weight.weight = cursor.getString(weightIndex);
            weight.updatedAt = cursor.getLong(updatedAtIndex);
        }

        return weight;
    }

    public static Weight getWeightByTimestamp(Context context, long timestamp) {
        Cursor cursor = context.getContentResolver().query(
                WeightsValueProvider.CONTENT_URI,
                new String[]{VALUE, TIMESTAMP, UPDATED_AT},
                TIMESTAMP + "= ?",
               new String[]{String.valueOf(timestamp)},
                TIMESTAMP + " ASC");
        if(cursor == null) return null;

        Weight weight = getWeightFromCursor(cursor);
        cursor.close();
        return weight;
    }

    public static void insertWeight(Context context, String value, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(VALUE, value);
        values.put(TIMESTAMP, timestamp);
        values.put(UPDATED_AT, System.currentTimeMillis());
        context.getContentResolver().insert(WeightsValueProvider.CONTENT_URI, values);
    }

    public static void deleteWeightWithTimestamp(Context context, long timestamp) {
        updateWeight(context, timestamp, "");
    }


    public static void updateWeight(Context context, long timestamp, String value) {
        ContentValues values = new ContentValues();
        values.put(VALUE, value);
        values.put(UPDATED_AT, System.currentTimeMillis());
        context.getContentResolver().update(
                CONTENT_URI,
                values,
                TIMESTAMP + " = ?",
                new String[]{String.valueOf(timestamp)});
    }

    /*
    public static void updateOrInsertWeight(Context context, String value, Long timestamp, long updatedAt) {
        Weight weight = getWeightByTimestamp(context, timestamp);
        if(weight == null) {
            insertWeight(context, value, timestamp, updatedAt);
        } else {
            updateWeight(context, value, timestamp, updatedAt);
        }
    }
    */

    public interface GetAllWeightsIterator {
        void weight(long timestamp, String weight);
    }

    public static void getAllWeights(Context context, GetAllWeightsIterator responseFun) {
        getAllWeights(context,  false, responseFun);
    }

    public static void getAllWeights(Context context, boolean includeDeleted, GetAllWeightsIterator responseFun) {
        String selection = includeDeleted? "" : VALUE + " != ''";
        Cursor cursor = context.getContentResolver().query(
                CONTENT_URI, new String[]{VALUE, TIMESTAMP},
                selection,
                null,
                TIMESTAMP + " ASC");
        assert cursor != null;

        int timestampIndex = cursor.getColumnIndex(TIMESTAMP);
        int weightIndex = cursor.getColumnIndex(VALUE);

        while (cursor.moveToNext()) {
            long time = cursor.getLong(timestampIndex);
            String weight = cursor.getString(weightIndex);

            responseFun.weight(time, weight);
        }

        cursor.close();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        dbHelper = new DBHelper(context);
        database = dbHelper.getWritableDatabase();

        return database != null;
    }

    private void setNotificationUri(Cursor cursor, Uri uri) {
        Context context = getContext();
        if(context != null) cursor.setNotificationUri(context.getContentResolver(), uri);
    }

    private void notifyChange(Uri uri) {
        Context context = getContext();
        if(context != null) context.getContentResolver().notifyChange(uri, null);
    }

    public static void insertOrUpdate(Context context, List<Weight> weights) {
        ContentProviderClient cpc = context.getContentResolver().acquireContentProviderClient(CONTENT_URI);
        WeightsValueProvider cp = (WeightsValueProvider) cpc.getLocalContentProvider();

        ContentValues values = new ContentValues();
        long updatedAt = System.currentTimeMillis();
        for(Weight weight: weights) {
            values.put(VALUE, weight.weight);
            values.put(TIMESTAMP, weight.timestamp);
            values.put(UPDATED_AT, updatedAt);
            cp.insertOrUpdate(values);
        }

        cpc.release();
    }

    public static void insertOrUpdate(Context context, String value, long timestamp) {
        ContentProviderClient cpc = context.getContentResolver().acquireContentProviderClient(CONTENT_URI);
        WeightsValueProvider cp = (WeightsValueProvider) cpc.getLocalContentProvider();

        ContentValues values = new ContentValues();
        values.put(VALUE, value);
        values.put(TIMESTAMP, timestamp);
        values.put(UPDATED_AT, System.currentTimeMillis());
        cp.insertOrUpdate(values);

        cpc.release();
    }

    private void insertOrUpdate(ContentValues values){
        long row = database.insertWithOnConflict(
                TABLE_NAME,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
        if (row <= 0) throw new SQLException("Fail to add a new record into " + TABLE_NAME);

        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
        notifyChange(newUri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME); // the TABLE_NAME to query on

        switch (uriMatcher.match(uri)) {
            // maps all database column names
            case VALUES:
                queryBuilder.setProjectionMap(null);
                break;
            case VALUE_ID:
                queryBuilder.appendWhere(TIMESTAMP + "=" + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (sortOrder == null) {
            sortOrder = TIMESTAMP + " desc";
        }

        Cursor cursor = queryBuilder.query(database, projection, selection,
                selectionArgs, null, null, sortOrder);

        // register to watch a content URI for changes
        setNotificationUri(cursor, uri);

        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long row = database.insertWithOnConflict(TABLE_NAME, "", values, SQLiteDatabase.CONFLICT_REPLACE);
        if (row <= 0) throw new SQLException("Fail to add a new record into " + uri);

        // If record is added successfully
        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
        notifyChange(newUri);
        return newUri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        int count;
        switch (uriMatcher.match(uri)){
            case VALUES:
                count = database.update(TABLE_NAME, values, selection, selectionArgs);
                break;

            case VALUE_ID:
                count = database.update(
                        TABLE_NAME,
                        values,
                        TIMESTAMP + " = " + uri.getLastPathSegment() +
                                (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unsupported URI " + uri );
        }
        notifyChange(uri);
        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count;
        switch (uriMatcher.match(uri)){
            case VALUES:
                // delete all the records of the table
                count = database.delete(TABLE_NAME, selection, selectionArgs);
                break;

            case VALUE_ID:
                count = database.delete(
                        TABLE_NAME,
                        TIMESTAMP +  " = " + uri.getLastPathSegment() +
                                (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI " + uri);
        }

        notifyChange(uri);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)){

            // Get all friend-birthday records
            case VALUES:
                return "vnd.android.cursor.dir/vnd.laneviss.values";

            // Get a particular friend
            case VALUE_ID:
                return "vnd.android.cursor.item/vnd.laneviss.values";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }


}
