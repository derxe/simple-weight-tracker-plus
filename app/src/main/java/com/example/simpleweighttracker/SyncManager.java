package com.example.simpleweighttracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public class SyncManager {

    private static final String TAG = "SyncManager";
    Context context;
    //String serverUrl = "https://angular-tst.firebaseio.com/weightTracker";
    String serverUrl = "http://192.168.1.6:8080";
    UserSyncManager user;
    long syncStart;
    private SyncResultListener onResultsListeners;

    public SyncManager(Context context) {
        this.context = context;
        this.user = new UserSyncManager(context, serverUrl);
    }

    /**
     * Upload our data to the server
     * If syncKey is defined it OVERWRITES all the data that is currently on the server
     */
    public void syncUpload() {
        user.getAccessToken(accessToken -> {
            if (accessToken == null) {
                Log.e(TAG, "Unable to get access token. Not uploading data");
                return;
            } else {
                this.syncUpload(accessToken);
            }
        });
    }

    public void syncUpload(String accessToken) {
        Log.d(TAG, "Uploading my data,");

        JSONObject weightsData = new JSONObject();
        WeightsValueProvider.getAllWeights(context, true, (timestamp, weight) -> {
            try {
                weightsData.put(Long.toString(timestamp), weight);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        int method = Request.Method.POST;
        String url = serverUrl + "/api/weights/short";

        Log.d(TAG, "Uploading data to the server.");
        Log.d(TAG, "Url: " + url + " Data:" + weightsData.length() );

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(UserSyncManager.getJSONReuqest
            (accessToken, method, url, weightsData, response -> {
                // success
                Log.d(TAG, "Upload successful!: " + response.toString());
                onSyncResult(true, "Sync successful");

                try {
                    JSONObject updatedAt = response.getJSONObject("updatedAt");
                    long lastUpdateTime = updatedAt.getLong("timestamp");
                    saveLastUpdateTime(lastUpdateTime);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }, error -> {
                Log.e(TAG, "Failed uploading sync data: " + error.toString());
                error.printStackTrace();
                onSyncResult(false, "Sync failed. Unable to upload data to the server");
            }));

    }

    public JSONObject getOnlineData(JSONObject response) {
        if (response == null) return null;
        return response.optJSONObject("data");
    }

    private void updateOrInsertWeights(JSONArray data, long updatedAt) {
        if (data == null) {
            Log.d(TAG, "No data to update. Data is null");
            return;
        }
        Log.d(TAG, "Updating weights, Len: " + data.length());

        for(int i=0; i<data.length(); i++) {
            try {
                JSONObject weightObj = data.getJSONObject(i);
                Long timestamp = Long.parseLong(weightObj.getString("timestamp_id"));
                String weight = weightObj.getString("weight");

                WeightsValueProvider.updateOrInsertWeight(context, weight, timestamp, updatedAt);
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "Updating weights finished");
    }

    private void insertWeights(JSONObject data, long updatedAt) {
        if (data == null) {
            Log.d(TAG, "No data to insert. Data is null");
            return;
        }

        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String timestamp = keys.next();
            try {
                String weight = (String) data.get(timestamp);
                WeightsValueProvider.insertWeight(
                        context,
                        weight + "",
                        Long.parseLong(timestamp),
                        updatedAt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setOnResultListener(SyncResultListener listener) {
        this.onResultsListeners = listener;
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

    public void sync() {
        syncStart = System.currentTimeMillis();
        user.getAccessToken(accessToken -> {
            if (accessToken == null) {
                Log.e(TAG, "Unable to get access token. Not syncing");
                return;
            }

            String url = serverUrl + "/api/weights";

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

                    // TODO save data here
                    updateOrInsertWeights(response, System.currentTimeMillis());

                    saveLastUpdateTime(lastUpdateTime);

                    syncUpload(accessToken);
                },
                (Response.ErrorListener) error -> {
                    Log.e(TAG, "Failed getting data from server: " + error.toString());
                    error.printStackTrace();
                    onSyncResult(false, "Failed getting data from server");
                }));
        });
    }

    private void saveLastUpdateTime(long lastUpdateTime) {
        Log.v(TAG, "Setting new lastUpdateTime: " + lastUpdateTime);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putLong("lastUpdateTime", lastUpdateTime).apply();
    }

    private long getLastUpdateTime() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getLong("lastUpdateTime", -1);
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
            context.getContentResolver().delete(WeightsValueProvider.CONTENT_URI, null, null);

            // insert all the new data from the server
            long updatedAt = System.currentTimeMillis();
            insertWeights(serverData, updatedAt);
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

    interface SyncResultListener {
        void onSyncResult(boolean success, String message);
    }
}
