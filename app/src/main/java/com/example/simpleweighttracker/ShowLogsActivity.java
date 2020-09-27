package com.example.simpleweighttracker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class ShowLogsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setTheme(this);
        setContentView(R.layout.activity_show_logs);

        Intent intent = getIntent();
        String logs = intent.getStringExtra("logs");
        ((TextView) findViewById(R.id.tv_logs)).setText(logs);
    }
}