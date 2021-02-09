package com.example.simpleweighttracker;

import android.content.ContentProvider;
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

public class WeightsValueProvider extends ContentProvider {
    // fields for my content provider
    static final String PROVIDER_NAME = "com.simpleweighttracker.tracker";
    static final String URL = "content://" + PROVIDER_NAME + "/values";
    static final Uri CONTENT_URI = Uri.parse(URL);

    // fields for the database
    static final String ID = "_id";
    static final String VALUE = "value";
    static final String TIMESTAMP = "timestamp";
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
            String sqlCreate = "CREATE TABLE " + TABLE_NAME +
                    " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " value REAL NOT NULL, " +
                    UPDATED_AT + " INTEGER, " +
                    " timestamp INTEGER NOT NULL);";
            sqlCreate += "create index timestamp on " + TABLE_NAME + " (timestamp);";

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
        long id;
        String weight;
        long timestamp;
        long updatedAt;
    }

    private static Weight getWeightFromCursor(Cursor cursor) {
        int idIndex = cursor.getColumnIndex(ID);
        int timestampIndex = cursor.getColumnIndex(TIMESTAMP);
        int weightIndex = cursor.getColumnIndex(VALUE);
        int updatedAtIndex = cursor.getColumnIndex(UPDATED_AT);

        Weight weight = null;
        if (cursor.moveToNext()) {
            weight = new Weight();
            weight.id = cursor.getLong(idIndex);
            weight.timestamp = cursor.getLong(timestampIndex);
            weight.weight = cursor.getString(weightIndex);
            weight.updatedAt = cursor.getLong(updatedAtIndex);
        }

        return weight;
    }

    public static Weight getWeightById(Context context, long id) {
        Cursor cursor = context.getContentResolver().query(
                WeightsValueProvider.CONTENT_URI,
                new String[]{ID, VALUE, TIMESTAMP, UPDATED_AT},
                ID + "= ?",
               new String[]{String.valueOf(id)},
                "timestamp ASC");
        if(cursor == null) return null;

        Weight weight = getWeightFromCursor(cursor);
        cursor.close();
        return weight;
    }

    public static Weight getWeightByTimestamp(Context context, long timestamp) {
        Cursor cursor = context.getContentResolver().query(
                WeightsValueProvider.CONTENT_URI,
                new String[]{ID, VALUE, TIMESTAMP, UPDATED_AT},
                TIMESTAMP + "= ?",
                new String[]{String.valueOf(timestamp)},
                null);
        if(cursor == null) return null;

        Weight weight = getWeightFromCursor(cursor);
        cursor.close();
        return weight;
    }

    public static void insertWeight(Context context, String value, Long timestamp, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put(VALUE, value);
        values.put(TIMESTAMP, timestamp);
        values.put(UPDATED_AT, updatedAt);
        context.getContentResolver().insert(WeightsValueProvider.CONTENT_URI, values);
    }

    public static void deleteWeightWithId(Context context, long id) {
        updateWeightWithId(context, id, "", null);
    }

    public static void updateWeightWithId(Context context, long id, String weight, Long timestamp) {
        ContentValues values = new ContentValues();
        if(weight != null) values.put(VALUE, weight);
        if(timestamp != null) values.put(TIMESTAMP, timestamp);
        context.getContentResolver().update(
                WeightsValueProvider.CONTENT_URI,
                values,
                ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    public static void updateWeight(Context context, String value, Long timestamp, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put(VALUE, value);
        values.put(UPDATED_AT, updatedAt);
        context.getContentResolver().update(
                CONTENT_URI,
                values,
                "timestamp = ?",
                new String[]{String.valueOf(timestamp)});
    }

    public static void updateOrInsertWeight(Context context, String value, Long timestamp, long updatedAt) {
        Weight weight = getWeightByTimestamp(context, timestamp);
        if(weight == null) {
            insertWeight(context, value, timestamp, updatedAt);
        } else {
            updateWeight(context, value, timestamp, updatedAt);
        }
    }

    public interface GetAllWeightsIterator {
        void weight(long timestamp, String weight);
    }

    public static void getAllWeights(Context context, GetAllWeightsIterator responseFun) {
        getAllWeights(context,  false, responseFun);
    }

    public static void getAllWeights(Context context, boolean includeDeleted, GetAllWeightsIterator responseFun) {
        String selection = includeDeleted? "" : VALUE + " != ''";
        Cursor cursor = context.getContentResolver().query(
                CONTENT_URI, new String[]{
                        "value", "timestamp"
                }, selection, null, "timestamp ASC");
        assert cursor != null;

        int timestampIndex = cursor.getColumnIndex("timestamp");
        int weightIndex = cursor.getColumnIndex("value");

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
                queryBuilder.appendWhere(ID + "=" + uri.getLastPathSegment());
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
        long row = database.insert(TABLE_NAME, "", values);
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
                count = database.update(TABLE_NAME, values, ID +
                        " = " + uri.getLastPathSegment() +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
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
                String id = uri.getLastPathSegment();	//gets the id
                count = database.delete( TABLE_NAME, ID +  " = " + id +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
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
