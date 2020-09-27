package com.example.simpleweighttracker;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize default settings first because others inits can use its values
        SettingsActivity.initSettings(this);
    }
}
