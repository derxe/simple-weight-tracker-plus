package com.example.simpleweighttracker;

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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class SyncManager {

    private static final String TAG = "SyncManager";
    Context context;
    String serverUrl = "https://angular-tst.firebaseio.com/weightTracker";
    String userKey = "dejanski";

    public SyncManager(Context context) {
        this.context = context;
    }

    public void syncUploadForce(String syncKey) {
        if (syncKey == null) {
            throw new IllegalArgumentException("You need to provide a syncKey if you want to force upload your data!");
        }
        syncUpload(syncKey);
    }

    public void syncUpload() {
        syncUpload(null);
    }

    /**
     * Upload our data to the server
     * If syncKey is defined it OVERWRITES all the data that is currently on the server
     *
     * @param syncKey marks the version of the sync so that we know that this version is the
     *                same as the one on the server
     */
    public void syncUpload(String syncKey) {
        JSONObject weightsData = new JSONObject();
        WeightsValueProvider.getAllWeights(context, (timestamp, weight) -> {
            try {
                weightsData.put(Long.toString(timestamp), weight);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        int method;
        String url;
        JSONObject data;
        if (syncKey != null) {
            // on force upload we delete all the existing data with HTTP put command and we
            // also set new syncKey for the data
            method = Request.Method.PUT;
            url = serverUrl + "/" + userKey + "/.json";
            data = new JSONObject();
            try {
                data.put("syncKey", syncKey);
                data.put("data", weightsData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            // we just want to update existing data on the server so we use
            // PATCH method to only update changes and we set url directly to the
            // data so we don't change syncKey
            method = Request.Method.PATCH;
            url = serverUrl + "/" + userKey + "/data.json";
            data = weightsData;
        }

        Log.d(TAG, "Uploading data to the server.");
        Log.d(TAG, "Url: " + url + "\n" + "Data:" + data);
        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(new JsonObjectRequest
                (method, url, data, response -> {
                    // success
                    Log.d("Upload successful!", response.toString());
                }, error -> {
                    Log.e("my app", "Failed uploading sync data: " + error.toString());
                    error.printStackTrace();
                }));
    }

    public JSONObject getOnlineData(JSONObject response) {
        if (response == null) return null;
        return response.optJSONObject("data");
    }

    private void updateWeights(JSONObject data) {
        if(data == null) {
            Log.d(TAG, "No data to update. Data is null");
            return;
        }

        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            try {
                String timestamp = keys.next();
                String weight = (String) data.get(timestamp);
                WeightsValueProvider.updateWeight(context, weight,  Long.getLong(timestamp));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void insertWeights(JSONObject data) {
        if(data == null) {
            Log.d(TAG, "No data to insert. Data is null");
            return;
        }

        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String timestamp = keys.next();
            try {
                String weight = (String) data.get(timestamp);
                WeightsValueProvider.storeWeight(context, weight + "", Long.parseLong(timestamp));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveSyncKey(String syncKey) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putString("mySyncKey", syncKey).apply();
    }

    private String getMySyncKey() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("mySyncKey", null);
    }

    public String generateNewSyncKey() {
        String newSyncKey = java.util.UUID.randomUUID().toString();
        saveSyncKey(newSyncKey);
        Log.d(TAG, "Generate new sync key: " + newSyncKey);
        return newSyncKey;
    }

    private SyncResultListener onResultsListeners;

    interface SyncResultListener {
        void onSyncResult(boolean success, String message);
    }

    public void setOnResultListener(SyncResultListener listener) {
        this.onResultsListeners = listener;
    }

    private void onSyncResult(boolean success, String message) {
        if(this.onResultsListeners != null) {
            onResultsListeners.onSyncResult(success, message);
        }
    }

    public void sync() {
        String url = serverUrl + "/" + userKey + "/.json";
        Log.d(TAG, "Getting data from the server url: " + url);
        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(new StringRequest(Request.Method.GET, url,
                (Response.Listener<String>) responseStr -> {
                    Log.d(TAG, "Got data from the server: " + responseStr);

                    JSONObject response = null;
                    String onlineSyncKey = null;
                    try {
                        response = new JSONObject(responseStr);
                        onlineSyncKey = (String) response.opt("syncKey");
                        Log.d(TAG, "Sync key from the server: " + onlineSyncKey);
                    } catch (JSONException e) {
                        Log.e(TAG, "Unable to parse the response from server as JSON: Error: " + e.toString() + " data: " + responseStr);
                        e.printStackTrace();
                        onSyncResult(false, "Server error");
                        return;
                    }

                    String mySyncKey = getMySyncKey();

                    if (onlineSyncKey == null) {
                        // no data yet on the server.
                        Log.d(TAG, "No data on the server (no sync key).");
                        if (mySyncKey == null) {
                            // we also have no sync key so we have
                            // to create a new one
                            generateNewSyncKey();
                        }

                        // since no data was downloaded we just need to force upload our data
                        Log.d(TAG, "Force uploading my data. My sync key: " + mySyncKey);
                        syncUploadForce(mySyncKey);
                    } else {
                        // we got some data from the sever
                        if (onlineSyncKey.equals(mySyncKey)) {
                            // versions on the server is the same as mine we can
                            // update our data with the data from the server
                            updateWeights(getOnlineData(response));
                        } else {
                            // data on the server is different from the data that we have.
                            // The user needs to decide which one to keep
                            askUserWhichDataToKeep(getOnlineData(response), mySyncKey, onlineSyncKey);
                        }
                    }

                    onSyncResult(true, "Sync successful");
                },
                (Response.ErrorListener) error -> {
                    Log.e("my app", "Failed getting data from server: " + error.toString());
                    error.printStackTrace();
                    onSyncResult(false, "Failed getting data from server");
                }));
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
                String newSyncKey = generateNewSyncKey();
                syncUploadForce(newSyncKey);
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
            insertWeights(serverData);
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
}
