package com.example.simpleweighttracker;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GraphActivity extends AppCompatActivity {



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

        LineChart chart = findViewById(R.id.chart1);
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
                return dateFormat.format(new Date(xToMillis(x)));
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

    // Unix Timestamp for 2022-1-1:
    // seconds: 1640995200
    private static long shift = 1640995200;

    // we need to shift the timestamp so that values can be more accurately represented in float format
    // the basic idea is that we want to make number as small as possible in order to be still
    // accurately represented by float. We could also convert seconds to minutes in order to get
    // a smaller number. But calculating the error the seconds work good enough.
    //
    // The best method would be to calculate shift so that min timestamp and max timestamp are on
    // the opposite site of number line -> their center is on 0
    private float millisToX(long milliseconds) {
        return TimeUnit.MILLISECONDS.toSeconds(milliseconds) - shift;
    }

    private long xToMillis(float x) {
        return TimeUnit.SECONDS.toMillis((long) x) + shift;
    }


    private LineData getLineDataSet() {

        ArrayList<Entry> values = new ArrayList<>();
        long errorSum = 0;
        WeightsValueProvider.getAllWeights(this, (timestamp, weight) -> {
            float y = Float.parseFloat(weight);
            float x = millisToX(timestamp);
            long error = Math.abs(timestamp - xToMillis(millisToX(timestamp)));

            Log.d("Graph", "x:" + x + ", y:" + y + " " + timestamp + " " + error);
            values.add(new Entry(x, y)); // add one entry per hour
        });
        Log.d("Graph", "Error sum " + errorSum);

        Collections.sort(values, (e1, e2) -> (int) (e1.getX() - e2.getX()));

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
}