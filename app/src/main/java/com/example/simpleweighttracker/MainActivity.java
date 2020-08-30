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
import android.net.Uri;
import android.os.Build;
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
import androidx.preference.PreferenceManager;

import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
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

import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
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
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);

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

    public static String format = "M/dd/yy h:mm a";


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Context context = MainActivity.this;

        switch(item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(context, SettingsActivity.class));
                break;

            case R.id.open_graph:
                startActivity(new Intent(context, GraphActivity.class));
                break;

            case R.id.export_csv:
                if (hasWritePermission())
                    userSelectDirectory();
                break;

            case R.id.import_csv:
                if (hasWritePermission())
                    userSelectFile();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    // request codes
    public static int USER_SELECT_DIRECTORY_RC = 1;
    public static int USER_SELECT_FILE_RC = 2;

    private void userSelectDirectory() {
        Uri downloadsUri = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE, "weights.csv");

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker when your app creates the document.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri);
            }

            startActivityForResult(intent, USER_SELECT_DIRECTORY_RC);
        } else {
            Intent intent = new Intent();
            intent.setData(downloadsUri);
            onActivityResult(USER_SELECT_DIRECTORY_RC, RESULT_OK, intent);
        }
    }

    private void userSelectFile() {
        Intent intent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            intent = new Intent()
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setAction(Intent.ACTION_OPEN_DOCUMENT);

        } else {
            intent = new Intent()
                    .setType("*/*")
                    .setAction(Intent.ACTION_GET_CONTENT);
        }

        startActivityForResult(intent, USER_SELECT_FILE_RC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == USER_SELECT_FILE_RC && resultCode == RESULT_OK) {
            Uri selectedFile = data.getData(); //The uri with the location of the file
            Toast.makeText(this, "Selected file: " + selectedFile, Toast.LENGTH_LONG).show();
            importCSV(selectedFile);
        }

        if(requestCode == USER_SELECT_DIRECTORY_RC && resultCode == RESULT_OK) {
            Uri selectedFile = data.getData(); //The uri with the location of the file
            Toast.makeText(this, "Selected file: " + selectedFile, Toast.LENGTH_LONG).show();
            exportCSV(selectedFile);
        }
    }

    public void exportCSV(Uri fileUri) {
        try {
            FileOutputStream f = (FileOutputStream) getContentResolver().openOutputStream(fileUri);
            assert f != null;

            Cursor cursor = getContentResolver().query(
                    WeightsValueProvider.CONTENT_URI, new String[]{
                            "value", "timestamp"
                    }, null, null, "timestamp ASC");
            assert cursor != null;

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());

            int timestampIndex = cursor.getColumnIndex("timestamp");
            int valueIndex = cursor.getColumnIndex("value");

            while (cursor.moveToNext()) {
                long time = cursor.getLong(timestampIndex) * 1000L;
                cal.setTimeInMillis(time);
                String timestamp = sdf.format(cal.getTime());
                String value = cursor.getString(valueIndex);
                String line = String.format("%s,%s\n", timestamp, value);

                f.write(line.getBytes());
            }

            cursor.close();
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void importCSV(Uri fileUri) {
        try {
            FileInputStream f = (FileInputStream) getContentResolver().openInputStream(fileUri);
            assert f != null;

            final ArrayList<String[]> resultList = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",");
                resultList.add(row);
            }
            f.close();

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Import records?")
                    .setMessage("Found " + resultList.size() + " records to import.  This will delete any records already in the app.")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            importEntries(resultList);
                        }
                    }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void importEntries(List<String[]> entries) {
        // Delete all records in app
        getContentResolver().delete(
                WeightsValueProvider.CONTENT_URI, null, null);

        // Add all the new records
        int nInported = 0;
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, new Locale("en"));
        for (String[] entry : entries) {
            Log.d("Import", "Entry: " + Arrays.toString(entry));
            try {
                String weight = entry[1];
                Long timestamp = dateFormat.parse(entry[0]).getTime() / 1000;
                Log.d("Import", "weight: " + weight + " timestamp:" + timestamp);
                storeEntry(weight, timestamp);
                nInported++;
            } catch (Exception e) {
                Log.e("Import", "Error: couldn't parse " + Arrays.toString(entry));
            }
        }

        Toast.makeText(MainActivity.this, "Imported " + nInported + " entries!", Toast.LENGTH_LONG).show();
    }

    public static class MyAdapter extends CursorAdapter {
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

    private void storeEntry(String value, Long timestamp) {
        ContentValues values = new ContentValues();
        values.put(WeightsValueProvider.VALUE, value);
        values.put(WeightsValueProvider.TIMESTAMP, timestamp);
        getContentResolver().insert(WeightsValueProvider.CONTENT_URI, values);
    }

    public boolean hasWritePermission() {
        return hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
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

}