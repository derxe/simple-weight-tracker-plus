package com.example.simpleweighttracker; /**
 * Created by x on 5/22/15.
 */

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
import android.widget.AdapterView;

import java.io.IOException;
import java.io.Serializable;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class WeightsValueProvider extends ContentProvider {
    // fields for my content provider
    static final String PROVIDER_NAME = "com.simpleweighttracker.tracker";
    static final String URL = "content://" + PROVIDER_NAME + "/values";
    static final Uri CONTENT_URI = Uri.parse(URL);

    // fields for the database
    static final String ID = "_id";
    static final String VALUE = "value";
    static final String TIMESTAMP = "timestamp";

    // integer values used in content URI
    static final int VALUES = 1;
    static final int VALUE_ID = 2;

    DBHelper dbHelper;

    // projection map for a query
    private static HashMap<String, String> ValueMap;

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
            db.execSQL(
                    "CREATE TABLE " + TABLE_NAME +
                    " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " value REAL NOT NULL, " +
                    " timestamp INTEGER NOT NULL); create index timestamp on " + TABLE_NAME + " (timestamp);");
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
    }

    public static Weight getWeightById(Context context, long id) {
        Cursor cursor = context.getContentResolver().query(
                WeightsValueProvider.CONTENT_URI,
                new String[]{ID, VALUE, TIMESTAMP},
                ID + "= ?",
               new String[]{String.valueOf(id)},
                "timestamp ASC");
        if(cursor == null) return null;

        int idIndex = cursor.getColumnIndex(ID);
        int timestampIndex = cursor.getColumnIndex(TIMESTAMP);
        int weightIndex = cursor.getColumnIndex(VALUE);

        Weight weight = null;
        while (cursor.moveToNext()) {
            weight = new Weight();
            weight.id = cursor.getLong(idIndex);
            weight.timestamp = cursor.getLong(timestampIndex);
            weight.weight = cursor.getString(weightIndex);
        }
        cursor.close();

        return weight;
    }

    public static void storeWeight(Context context, String value, Long timestamp) {
        ContentValues values = new ContentValues();
        values.put(WeightsValueProvider.VALUE, value);
        values.put(WeightsValueProvider.TIMESTAMP, timestamp);
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

    public static void updateWeight(Context context, String value, Long timestamp) {
        ContentValues values = new ContentValues();
        values.put(WeightsValueProvider.VALUE, value);
        context.getContentResolver().update(
                WeightsValueProvider.CONTENT_URI,
                values,
                "timestamp = ?",
                new String[]{String.valueOf(timestamp)});
    }

    public interface GetAllWeightsIterator {
        void weight(long timestamp, String weight);
    }

    public static void getAllWeights(Context context, GetAllWeightsIterator responseFun) {
        Cursor cursor = context.getContentResolver().query(
                WeightsValueProvider.CONTENT_URI, new String[]{
                        "value", "timestamp"
                }, VALUE + " != ''", null, "timestamp ASC");
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

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME); // the TABLE_NAME to query on

        switch (uriMatcher.match(uri)) {
            // maps all database column names
            case VALUES:
                queryBuilder.setProjectionMap(ValueMap);
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
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long row = database.insert(TABLE_NAME, "", values);
        if (row <= 0) throw new SQLException("Fail to add a new record into " + uri);

        // If record is added successfully
        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
        getContext().getContentResolver().notifyChange(newUri, null);
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
        getContext().getContentResolver().notifyChange(uri, null);
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

        getContext().getContentResolver().notifyChange(uri, null);
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
