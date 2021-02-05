package com.example.simpleweighttracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class UserSyncManager {
    private static final String TAG = "UserSyncManager";

    Context context;
    String serverUrl;
    String username = "linuxize";
    String password = "pass";
    String accessToken;

    public interface OnAccessTokenReturn {
        void onAccessTokenReturn(String accessToken);
    }

    public UserSyncManager(Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
    }

    public static JsonObjectRequest getJSONReuqest(String accessToken,
                                                   int method,
                                                   String url,
                                                   @Nullable JSONObject jsonRequest,
                                                   Response.Listener<JSONObject> listener,
                                                   @Nullable Response.ErrorListener errorListener) {
        return new JsonObjectRequest(method, url, jsonRequest, listener, errorListener) {
            /** Passing some request headers* */
            @Override
            public Map getHeaders() throws AuthFailureError {
                HashMap headers = new HashMap();
                headers.put("x-access-token", accessToken);
                return headers;
            }
        };
    }

    public static JsonArrayRequest getJSONArrayReuqest(String accessToken,
                                                       int method,
                                                       String url,
                                                       @Nullable JSONArray jsonRequest,
                                                       Response.Listener<JSONArray> listener,
                                                       @Nullable Response.ErrorListener errorListener) {
        return new JsonArrayRequest(method, url, jsonRequest, listener, errorListener) {
            /** Passing some request headers* */
            @Override
            public Map getHeaders() throws AuthFailureError {
                HashMap headers = new HashMap();
                headers.put("x-access-token", accessToken);
                return headers;
            }
        };
    }

    private void signin(OnAccessTokenReturn returnFun) {
        JSONObject loginData = new JSONObject();
        try {
            loginData.put("username", username);
            loginData.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
            returnFun.onAccessTokenReturn(null);
            return;
        }

        Log.d(TAG, "Logging in");
        String url = serverUrl + "/api/auth/signin";
        Log.d(TAG, "Url: " + url + " Data:" + loginData.toString());
        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(new JsonObjectRequest
                (Request.Method.POST, url, loginData, response -> {
                    // success
                    Log.d(TAG, "Login successful!: " + response.toString());
                    try {
                        String accessToken = response.getString("accessToken");
                        saveAccessToken(accessToken);
                        returnFun.onAccessTokenReturn(accessToken);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.d(TAG, "Unable to find accessToken parameter");
                        returnFun.onAccessTokenReturn(null);
                    }
                }, error -> {
                    Log.e(TAG, "Login filed: " + error.toString());
                    error.printStackTrace();
                    returnFun.onAccessTokenReturn(null);
                }));
    }


    private void saveAccessToken(String accessToken) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putString("accessToken", accessToken).apply();
    }

    private String getAccessToken() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("accessToken", null);
    }

    public void getAccessToken(OnAccessTokenReturn returnFun) {
        if(returnFun == null) return;

        String accessToken = getAccessToken();
        if(accessToken != null) {
            Log.d(TAG, "Returning saved access token: " + accessToken);
            returnFun.onAccessTokenReturn(accessToken);
        } else {
            Log.d(TAG, "No access token saved. singing in to get one");
            signin(returnFun);
        }
    }

}
