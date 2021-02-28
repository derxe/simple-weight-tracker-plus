package com.example.simpleweighttracker.Data;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.example.simpleweighttracker.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import static com.example.simpleweighttracker.Data.WeightsValueProvider.*;

public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SyncManager";
    Context context;
    public UserSyncManager user;
    String serverUrl = "http://poljch.home.kg/node";
    long syncStart;
    private SyncResultListener onResultsListeners;
    ContentResolver contentResolver;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.context = context;
        this.user = new UserSyncManager(context, serverUrl);

        contentResolver = context.getContentResolver();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }


    @Override
    public void onPerformSync(Account account,
                              Bundle extras,
                              String authority,
                              ContentProviderClient provider,
                              SyncResult syncResult) {
        sync();
    }

    public void setOnResultListener(SyncResultListener listener) {
        this.onResultsListeners = listener;
    }

    /**
     * Upload our data to the server
     * If syncKey is defined it OVERWRITES all the data that is currently on the server
     */
    public void syncUpload() {
        user.getAccessToken(accessToken -> {
            if (accessToken == null) {
                Log.e(TAG, "Unable to get access token. Not uploading data");
                onSyncResult(false, "Unable to login!");
            } else {
                this.syncUpload(accessToken);
            }
        });
    }

    public void syncUpload(String accessToken) {
        Log.d(TAG, "Uploading my data,");

        // collect all the weights that we have to send to the server
        JSONArray weightsData = new JSONArray();
        getAllWeightsUpdateTime(context, this.getLastUpdateTime(), (timestamp, weight, updatedAt) -> {
            try {
                JSONObject weightObj = new JSONObject();
                weightObj.put("weight", weight);
                weightObj.put("timestamp_id", timestamp);
                weightObj.put("updatedLocal", updatedAt);
                weightsData.put(weightObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        int method = Request.Method.POST;
        String url = serverUrl + "/api/weights";

        Log.d(TAG, "Uploading data to the server.");
        try {
            Log.d(TAG, String.format(Locale.getDefault(), "Url: %s, DataLen:%d, Data:%s",
                    url, weightsData.length(), weightsData.toString(1)));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(UserSyncManager.getJSONArrayReuqest
                (accessToken, method, url, weightsData, response -> {
                    // success
                    Log.d(TAG, "Upload successful!: " + response.toString());
                    onSyncResult(true, "Sync successful");

                    try {
                        JSONObject updatedAt = response.getJSONObject(0).getJSONObject("updatedAt");
                        long lastUpdateTime = updatedAt.getLong("timestamp");
                        saveLastUpdateTime(lastUpdateTime);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
                    Log.e(TAG, "Failed uploading sync data: " + error.toString());
                    Log.e(TAG, "Response: " + Utils.getErrorMessage(error));
                    error.printStackTrace();
                    onSyncResult(false, "Sync failed. Unable to upload data to the server");
                }));

    }

    /**
     * From the raw data received from server update the local weights.
     *
     * @param data that was send from the server
     */
    private void updateOrInsertWeights(JSONArray data) {
        if (data == null) {
            Log.d(TAG, "No data to update. Data is null");
            return;
        }
        Log.d(TAG, "Updating weights, Len: " + data.length());

        // load weights that are relevant - the last update time is greater then the last
        // time we uploaded weights to the server. These weights are later compared
        // to the ones that we got from the server to see if we need to update them
        HashMap<Long, Weight> weights = new HashMap<>();
        getAllWeightsUpdateTime(context, this.getLastUpdateTime(),
                (timestamp, weight, updatedAt) -> {
                    Weight w = new Weight();
                    w.timestamp = timestamp;
                    w.updatedAt = updatedAt;
                    w.weight = weight;
                    Log.d(TAG, "My weights: " + w.toString());

                    weights.put(timestamp, w);
                }
        );


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'", Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("UTC")); // Z ad the end (because it is in quotes 'Z') does't get parsed so we need to set the timezone manually
        for (int i = 0; i < data.length(); i++) {
            try {
                JSONObject weightObj = data.getJSONObject(i);
                long timestamp = Long.parseLong(weightObj.getString("timestamp_id"));
                String weightStr = weightObj.getString("weight");
                long updatedLocal = Long.parseLong(weightObj.getString("updatedLocal"));

                Weight weight = weights.get(timestamp);
                if (weight == null || weight.updatedAt < updatedLocal) {
                    // if the weight doesn't exist or if the updated time on the server is more recent
                    // then update the wieght value
                    Log.v(TAG, "YE weight: " + weight + " " + weightStr + " " + updatedLocal);
                    insertOrUpdate(context, weightStr, timestamp, updatedLocal);
                } else {
                    // there is no need to update this weight
                    Log.v(TAG, "NO weight: " + weight + " " + weightStr + " " + updatedLocal);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "Updating weights finished");
    }

    private void insertWeights(JSONObject data) {
        if (data == null) {
            Log.d(TAG, "No data to insert. Data is null");
            return;
        }

        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String timestamp = keys.next();
            try {
                String weight = (String) data.get(timestamp);
                insertWeight(
                        context,
                        Long.parseLong(timestamp),
                        weight + "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sync() {
        syncStart = System.currentTimeMillis();
        user.getAccessToken(accessToken -> {
            if (accessToken == null) {
                Log.e(TAG, "Unable to get access token. Not syncing");
                onSyncResult(false, "Unable to login!");
                return;
            }

            String url = serverUrl + "/api/weights";

            // only request weights that are newer then the last updateTime time.
            long lastUpdateTime = getLastUpdateTime();
            if(lastUpdateTime != -1) {
                url += "?unix_after=" + lastUpdateTime;
            } else {
                Log.d(TAG, "LastUpdateTime not defined, Downloading all the data.");
            }
            Log.d(TAG, "Getting data from the server url: " + url);
            RequestQueue queue = Volley.newRequestQueue(context);
            queue.add(UserSyncManager.getJSONArrayReuqest(accessToken, Request.Method.GET, url, null,
                    (JSONArray response) -> {
                        Log.d(TAG, "Got data from the server: len" + response.length() + ": " + response.toString());
                        updateOrInsertWeights(response);
                        syncUpload(accessToken);
                    },
                    (Response.ErrorListener) error -> {
                        Log.e(TAG, "Failed getting data from server: " + error.toString());
                        Log.e(TAG, "Response: " + Utils.getErrorMessage(error));
                        error.printStackTrace();
                        onSyncResult(false, "Failed getting data from server");
                    }));
        });
    }


    private void onSyncResult(boolean success, String message) {
        if (this.onResultsListeners != null) {
            long syncDuration = System.currentTimeMillis() - syncStart;
            Log.d(TAG, String.format(Locale.ENGLISH,
                    "Sync result:%s, message:'%s', duration:%.3fs",
                    success, message, syncDuration / 1000f));
            onResultsListeners.onSyncResult(success, message);
        }
    }

    public void clearLastUpdateTime() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("lastUpdateTime").apply();
    }

    /**
     * Saves the time when weights were last updated to the server
     */
    private void saveLastUpdateTime(long lastUpdateTime) {
        Log.v(TAG, "Setting new lastUpdateTime: " + lastUpdateTime);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putLong("lastUpdateTime", lastUpdateTime).apply();
    }

    /**
     * When data on the server has different sync key compared to the data here.
     * User needs to decide which data to keep
     */
    public void askUserWhichDataToKeep(JSONObject serverData, String mySyncKey, String onlineSyncKey) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Error syncing data ");
        b.setMessage("Data from the server is different from your local data.");

        b.setCancelable(true); //"Cancel", (dialogInterface, i) -> {});

        b.setNeutralButton("Overwrite online data", (dialogInterface, i) -> {
            // user decided to keep local data
            // do nothing just upload our data to the server
            if (mySyncKey == null) {
//                String newSyncKey = generateNewSyncKey();
//                syncUploadForce(newSyncKey);
            } else {
                syncUpload();
            }

        });

        b.setNegativeButton("Overwrite my data", (dialogInterface, i) -> {
            // user decided to keep data from the server
            // delete our data and replace it with the data from the server

            // update our syncKey
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putString("mySyncKey", onlineSyncKey).apply();

            // delete all records in app
            context.getContentResolver().delete(CONTENT_URI, null, null);

            // insert all the new data from the server
            insertWeights(serverData);
            onSyncResult(true, "Sync successful");
        });
/*
        b.setPositiveButton("both (merge)", (dialogInterface, i) -> {
            // user decided to merge the data
            // do nothing just upload our data to the server

            // update our syncKey
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putString("mySyncKey", onlineSyncKey).apply();

            updateWeights(serverData);
        });
        */
        b.show();
    }

    private long getLastUpdateTime() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getLong("lastUpdateTime", -1);
    }


    public interface SyncResultListener {
        void onSyncResult(boolean success, String message);
    }


}
