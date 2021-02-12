package com.example.simpleweighttracker;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

import static android.text.format.DateFormat.is24HourFormat;
import static com.example.simpleweighttracker.WeightsValueProvider.*;

public class AddWeightActivity extends AppCompatActivity {

    public static final String DEBUG_TAB = "AddWeightActivity";
    TextView title;
    EditText weightInput;
    Button changeDateBtn;
    Button changeTimeBtn;
    Button addWeightBtn;
    Button cancelBtn;
    Button updateWeightBtn;
    Button deleteWeightBtn;
    Calendar cal = Calendar.getInstance(Locale.getDefault());;
    Weight weight = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(DEBUG_TAB, "On create");
        setContentView(R.layout.activity_add_weight);

        title = findViewById(R.id.addWeight_title);
        weightInput = findViewById(R.id.weight);
        changeDateBtn = findViewById(R.id.addWeight_date);
        changeTimeBtn = findViewById(R.id.addWeight_time);
        addWeightBtn = findViewById(R.id.addWeight_add);
        cancelBtn = findViewById(R.id.addWeight_cancel);
        updateWeightBtn = findViewById(R.id.addWeight_update);
        deleteWeightBtn = findViewById(R.id.addWeight_delete);

        boolean updateWeight = false;
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            weight = (Weight) extras.getSerializable("weight");
            updateWeight = weight != null;
        }

        if(updateWeight) {
            title.setText("Update weight:");
            updateWeightBtn.setVisibility(View.VISIBLE);
            deleteWeightBtn.setVisibility(View.VISIBLE);
            cancelBtn.setVisibility(View.GONE);
            addWeightBtn.setVisibility(View.GONE);

            cal.setTimeInMillis(weight.timestamp);
            weightInput.setText(weight.weight);

            updateWeightBtn.setOnClickListener(view -> updateWeightAndFinish());
            deleteWeightBtn.setOnClickListener(view -> deleteWeightAndFinish());
        } else {
            title.setText("Add new weight:");
            updateWeightBtn.setVisibility(View.GONE);
            deleteWeightBtn.setVisibility(View.GONE);
            cancelBtn.setVisibility(View.VISIBLE);
            addWeightBtn.setVisibility(View.VISIBLE);

            addWeightBtn.setOnClickListener(view -> addWeightAndFinish());
            cancelBtn.setOnClickListener(view -> this.finish());
        }

        updateDateTimeButtons();


        changeDateBtn.setOnClickListener(v -> changeDate());
        changeTimeBtn.setOnClickListener(v -> changeTime());
        weightInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addWeightAndFinish();
            }
            return true; // true -> we consumed the click
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
    protected void onStop() {
        super.onStop();
        finish();
    }


    private void updateDateTimeButtons() {
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
        changeDateBtn.setText(dateFormat.format(cal.getTimeInMillis()));

        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        changeTimeBtn.setText(timeFormat.format(cal.getTimeInMillis()));
    }

    private void changeDate() {
        DatePickerDialog.OnDateSetListener listener = (view, year, month, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateTimeButtons();
        };

        new DatePickerDialog(
                this,
                listener, cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void changeTime() {
        TimePickerDialog.OnTimeSetListener listener = (view, hourOfDay, minute) -> {
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            cal.set(Calendar.MINUTE, minute);
            updateDateTimeButtons();
        };

        new TimePickerDialog(
                this,
                listener,
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                is24HourFormat(this)
        ).show();
    }


    @Override
    public void setTheme(int resid) {
        super.setTheme(Utils.getDialogTheme(this));
    }

    public boolean validateWeightField() {
        String weight = weightInput.getText().toString();
        boolean isValid = weight.length() > 0 && isNumeric(weight);
        weightInput.setError(isValid? null : "Incorrect weight");
        return isValid;
    }

    public boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void deleteWeightAndFinish() {
        deleteWeightWithTimestamp(this, weight.timestamp);
        finish();
    }

    private void updateWeightAndFinish() {
        String weightValue = weightInput.getText().toString();
        if (validateWeightField()) {
            long timestamp = cal.getTimeInMillis();

            updateWeight(this, weight.timestamp, weightValue);
            finish();
        }
    }

    private void addWeightAndFinish() {
        String weight = weightInput.getText().toString();
        if (validateWeightField()) {
            long timestamp = cal.getTimeInMillis();
            insertWeight(this, weight, timestamp);
            finish();
        }
    }
}