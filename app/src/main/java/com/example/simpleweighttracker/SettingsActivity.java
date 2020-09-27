package com.example.simpleweighttracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    public static void initSettings(final Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.root_preferences, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setTheme(this);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

        SharedPreferences prefs;
        String startThemeKey;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());
            ;
            startThemeKey = prefs.getString("theme", getString(R.string.default_theme_value));

            Preference themePreference = findPreference("theme");
            if (themePreference != null) themePreference.setOnPreferenceChangeListener(this);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {

            prefs.edit().putString("theme", newValue.toString()).apply();

            if (!newValue.equals(startThemeKey) && getActivity() != null) {
                // If it's not the current theme
                getActivity().recreate();
                prefs.edit().putBoolean("theme_change", true).apply();
            }
            return false;
        }
    }
}