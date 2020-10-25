package com.example.simpleweighttracker;

import android.os.Bundle;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class AddWeightActivity extends AppCompatActivity {

    EditText weightInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_weight);

        weightInput = findViewById(R.id.weight);
        findViewById(R.id.addWeight).setOnClickListener(view -> addWeightAndFinish());

        weightInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addWeightAndFinish();
            }

            // true -> we consumed the click
            return true;
        });

        /*
        // show keyboard
        weightInput.requestFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(weightInput, InputMethodManager.SHOW_FORCED);
        */
        weightInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(Utils.getDialogTheme(this));
    }

    public boolean isWeightValueValid(String weight) {
        return weight.length() > 0 && isNumeric(weight);
    }

    public boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void addWeightAndFinish() {
        String weight = weightInput.getText().toString();
        if (isWeightValueValid(weight)) {
            long timestamp = System.currentTimeMillis();

            WeightsValueProvider.storeWeight(this, weight, timestamp);
            AddWeightActivity.this.finish();
        } else {
            weightInput.setError("Incorrect weight");
        }
    }
}