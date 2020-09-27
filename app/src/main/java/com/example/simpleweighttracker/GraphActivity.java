package com.example.simpleweighttracker;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GraphActivity extends AppCompatActivity {

    private LineChart chart;


    // Unix Timestamp for 2022-1-1:
    // seconds: 1640995200
    private static long shift = 1640995200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setTheme(this);
        setContentView(R.layout.activity_graph);

//        findViewById(R.id.clicky).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                double move = Math.random();
//                Log.d("Graph", "Ranges: " + chart.getXChartMax() + " " + chart.getHighestVisibleX());
////                chart.moveViewToX((float) chart.getXChartMax() - chart.);
//            }
//        });

        chart = findViewById(R.id.chart1);
        chart.setData(getLineDataSet());


        // X AXIS
        chart.getAxisRight().setEnabled(false);
        final XAxis xa = chart.getXAxis();
        xa.setGranularity(TimeUnit.DAYS.toSeconds(1)); // show vertical x lines on each day
        xa.setGranularityEnabled(true);
        xa.setPosition(XAxis.XAxisPosition.BOTTOM);
        xa.setTextSize(10f);
        xa.setTextColor(Utils.getTextColor(this));
        xa.setDrawGridLines(true);
        xa.setValueFormatter(new ValueFormatter() {

//            private final SimpleDateFormat mFormat = new SimpleDateFormat("M.d HH:mm", Locale.ENGLISH);

            //            Locale locale = Locale.getDefault();
//            Locale locale = new Locale("sl");
//            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
//            Locale locale = Utils.getDateLocale(GraphActivity.this);
            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());

            @Override
            public String getFormattedValue(float x) {
                return dateFormat.format(new Date(xToSeconds(x) * 1000));
            }
        });

        // Y AXIS
        final YAxis ya = chart.getAxisLeft();
        ya.setTextSize(10f);
        ya.setTextColor(Utils.getTextColor(this));
        ya.setDrawGridLines(true);


        final String weight_unit = Utils.getDefaultWeightUnit(this);
        ya.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Math.round(value) + " " + weight_unit;
            }
        });

        // CHART SETTINGS
        // scale so that last 5 days are visible

        float scaleX = Math.max(1, chart.getXRange() / TimeUnit.DAYS.toSeconds(6));
        chart.zoom(scaleX, 1, 0, 0);
        chart.moveViewToX(chart.getXChartMax()); // move chart far right

        chart.setExtraBottomOffset(10f);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);

        chart.setPinchZoom(true);
        chart.setDragXEnabled(true);
        chart.setDragYEnabled(false);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
    }

    private float secondsToX(long seconds) {
        return seconds - shift;
    }

    private long xToSeconds(float x) {
        return ((long) x) + shift;
    }


    private LineData getLineDataSet() {
        ArrayList<Entry> values = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                WeightsValueProvider.CONTENT_URI, new String[]{
                        "value", "timestamp"
                }, null, null, "timestamp");
        assert cursor != null;

        final int valueIndex = cursor.getColumnIndex("value");
        final int timestampIndex = cursor.getColumnIndex("timestamp");

        long errorSum = 0;
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            float y = cursor.getFloat(valueIndex);
            long seconds = cursor.getLong(timestampIndex);
            float x = secondsToX(seconds);

            long error = Math.abs(seconds - xToSeconds(secondsToX(seconds)));
            errorSum += error;

            Log.d("Graph", "x:" + x + ", y:" + y + " " + seconds + " " + xToSeconds(secondsToX(seconds)) + " " + error);
            values.add(new Entry(x, y)); // add one entry per hour
        }

        Log.d("Graph", "Error sum " + errorSum);


        Collections.sort(values, new Comparator<Entry>() {
            @Override
            public int compare(Entry e1, Entry e2) {
                return (int) (e1.getX() - e2.getX());
            }
        });

        LineDataSet lds = new LineDataSet(values, "data");
        lds.setCircleColor(Color.parseColor("#35b721"));
        lds.setCircleRadius(5f);
        lds.setCircleHoleRadius(2f);
        lds.setCircleHoleColor(Color.WHITE);
        lds.setColor(Color.parseColor("#1ee200"));
        lds.setLineWidth(4f);
        lds.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        lds.setDrawFilled(true);
        lds.setFillColor(Color.parseColor("#96f986"));

        LineData ld = new LineData(lds);
        ld.setDrawValues(false);
        return ld;
    }


    private void setData() {
        ArrayList<Entry> values = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                WeightsValueProvider.CONTENT_URI, new String[]{
                        "value", "timestamp"
                }, null, null, "timestamp");
        assert cursor != null;

        float x;
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            float y = cursor.getFloat(cursor.getColumnIndex("value"));
            long time = cursor.getLong(cursor.getColumnIndex("timestamp")) * 1000;
            Log.d("Graph", "Time: " + time);
            x = (float) TimeUnit.MILLISECONDS.toHours(time);
            Log.d("Graph", "x:" + x + ", y:" + y);
            values.add(new Entry(x, y)); // add one entry per hour
        }

        ArrayList<Entry> values2 = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            values2.add(new Entry(i, (float) Math.sin(((float) i) / 3)));
        }

        Collections.sort(values, new Comparator<Entry>() {
            @Override
            public int compare(Entry e1, Entry e2) {
                return (int) (e2.getX() - e1.getX());
            }
        });

        for (Entry e : values) {
            Log.d("Graph", "values: x:" + e.getX() + ", y:" + e.getY());
        }


        // create a dataset and give it a type
        LineDataSet set1 = new LineDataSet(values2, "DataSet 1");
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);
        set1.setColor(ColorTemplate.getHoloBlue());
        set1.setValueTextColor(ColorTemplate.getHoloBlue());
        set1.setLineWidth(1.5f);
        set1.setDrawCircles(false);
        set1.setDrawValues(true);
        set1.setFillAlpha(65);
        set1.setFillColor(ColorTemplate.getHoloBlue());
        set1.setHighLightColor(Color.rgb(244, 117, 117));
        set1.setDrawCircleHole(false);

        // create a data object with the data sets
        LineData data = new LineData(set1);
        data.setValueTextColor(Color.WHITE);
        data.setValueTextSize(9f);

        // set data
        chart.setData(data);
    }
}