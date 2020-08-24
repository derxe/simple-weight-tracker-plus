package com.example.simpleweighttracker;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int STORAGE_PERMISSION_CODE = 123;
    MyAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportLoaderManager().initLoader(0, null, this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddWeightActivity.class));
            }
        });

        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                startActivity(new Intent(MainActivity.this, GraphActivity.class));
                return true;
            }
        });

        ListView listView = findViewById(R.id.list);

        listAdapter = new MyAdapter(this, null);
        listView.setAdapter(listAdapter);
    }



    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        String[] projection = { "_id", "value", "timestamp" };

        CursorLoader cursorLoader = new CursorLoader(this,
                WeightsValueProvider.CONTENT_URI, projection, null, null, null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        listAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        listAdapter.changeCursor(null);
    }

    static int i = 0;
    public class MyAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;

        public MyAdapter(Context context, Cursor cursor) {
            super(context, cursor, false);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.list_row, parent, false);
        }


        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            // get the fields from the row
            TextView weightLabel = view.findViewById(R.id.weight);
            TextView timestampLabel = view.findViewById(R.id.timestamp);

            // set value label
            final String value = cursor.getString(cursor.getColumnIndex("value"));
            weightLabel.setText(value);

            // set the timestamp label
            long timeMs = cursor.getLong(cursor.getColumnIndex("timestamp")) * 1000L;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timeMs);

            Locale locale = new Locale("de");
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
            timestampLabel.setText(dateFormat.format(cal.getTime()));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.export_csv) {
            exportToSdCard();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    // Function to check and request permission
    public boolean hasPermission(String permission, int requestCode)
    {

        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                            this, new String[] { permission }, requestCode);
            return false;
        }
        else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this,
                        "Storage Permission Granted",
                        Toast.LENGTH_SHORT)
                        .show();
            }
            else {
                Toast.makeText(MainActivity.this,
                        "Storage Permission Denied",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }


    private String export_filename="moj1.csv";
    public static String format = "M/dd/yy h:mm a";

    public String getCsvFile() {
        StringBuilder sb = new StringBuilder();

        Cursor cursor = getContentResolver().query(
                WeightsValueProvider.CONTENT_URI, new String[] {
                        "value", "timestamp"
                }, null, null, "timestamp");
        assert cursor != null;

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(format);

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            long time = cursor.getLong(cursor.getColumnIndex("timestamp")) * 1000L;
            cal.setTimeInMillis(time);
            String timestamp = sdf.format(cal.getTime());

            String value = cursor.getString(cursor.getColumnIndex("value"));
            sb.append(timestamp).append(",").append(value).append("\n");
        }
        return sb.toString();
    }

    public void exportToSdCard() {
        if(!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE))
            return;

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, export_filename);
        FileOutputStream f;
        try {
            boolean created = file.createNewFile();
            f = new FileOutputStream(file);
            f.write(getCsvFile().getBytes());
            f.close();
            Toast.makeText(this, "Success?", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String[]> readCsvFile(File file) throws IOException {
        FileInputStream f = new FileInputStream(file);
        final List<String[]> resultList = new ArrayList();
        BufferedReader reader = new BufferedReader(new InputStreamReader(f));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] row = line.split(",");
            resultList.add(row);
        }
        f.close();

        return resultList;
    }


    public void importFromSdCard() {
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath());
        File file = new File(dir, export_filename);

        getContentResolver().delete(WeightsValueProvider.CONTENT_URI, null, null);

        if (file.exists()) {

            try {
                final List<String[]> resultList = readCsvFile(file);
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Import records?")
                        .setMessage("Found " + resultList.size() + " records to import.  This will delete any records already in the app.")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // Delete all records in app
                                getContentResolver().delete(WeightsValueProvider.CONTENT_URI, null, null);

                                // Add all the new records
                                SimpleDateFormat dateFormat = new SimpleDateFormat(format);
                                for (String[] row : resultList) {
                                    try {
                                        //storeEntry(row[1], dateFormat.parse(row[0]).getTime() / 1000);
                                    } catch (Exception e) {
                                        Toast.makeText(MainActivity.this, "Error: couldn't parse " + row[0], Toast.LENGTH_LONG).show();
                                    }
                                }
                                Toast.makeText(MainActivity.this, "Import succeeded!", Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton("Cancel", null).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Import Instructions")
                    .setMessage("Create a CSV file in the root of the SD card named " + export_filename + " containing a column with the date and time in format " + format + ", and a column with the weight.")
                    .setPositiveButton("Ok", null).show();
        }
    }
}