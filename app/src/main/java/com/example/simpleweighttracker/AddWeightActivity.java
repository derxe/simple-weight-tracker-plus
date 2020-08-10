package com.example.simpleweighttracker;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class AddWeightActivity extends AppCompatActivity {

    EditText weightInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_weight);

        weightInput = findViewById(R.id.weight);
        findViewById(R.id.addWeight).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addWeightAndFinish();
            }
        });

        weightInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if(actionId == EditorInfo.IME_ACTION_DONE){
                    addWeightAndFinish();
                    return true;
                }
                return false;
            }
        });

        /*
        // show keyboard
        weightInput.requestFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(weightInput, InputMethodManager.SHOW_FORCED);
        */
        weightInput.requestFocus();
        getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void addWeightAndFinish() {
        String weight = weightInput.getText().toString();
        long timestamp = System.currentTimeMillis() / 1000L;

        storeEntry(weight, timestamp);
        AddWeightActivity.this.finish();
    }

    private void storeEntry(String value, Long timestamp) {
        ContentValues values = new ContentValues();
        values.put(WeightsValueProvider.VALUE, value);
        values.put(WeightsValueProvider.TIMESTAMP, timestamp);

        getContentResolver().insert(WeightsValueProvider.CONTENT_URI, values);
    }
}