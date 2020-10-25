package com.example.simpleweighttracker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import java.util.Locale;

public class Utils {

    public static Locale getDateLocale(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean useCustomLocale = prefs.getBoolean("use_custom_locale", true);
        String localeName = prefs.getString("locale", null);
        if (!useCustomLocale || localeName == null) {
            return Locale.getDefault();
        } else {
            return new Locale(localeName);
        }
    }

    public static String getDateFormat(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("date_format", "");
    }


    public static @ColorInt
    int getTextColor(Context context) {
        int[] attrs = new int[]{android.R.attr.textColorPrimary};
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs);
        int DEFAULT_TEXT_COLOR = a.getColor(0, Color.RED);
        a.recycle();
        return DEFAULT_TEXT_COLOR;
    }

    public static String getDefaultWeightUnit(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("weight_unit", "?");
    }


    private static String getSelectedThemeString(final Context context) {
        final String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("theme", defaultTheme);
    }

    private static boolean isDarkTheme(final Context context) {
        String blackTheme = context.getResources().getString(R.string.dark_theme_key);
        return getSelectedThemeString(context).equals(blackTheme);
    }

    private static boolean isLightTheme(final Context context) {
        String blackTheme = context.getResources().getString(R.string.light_theme_key);
        return getSelectedThemeString(context).equals(blackTheme);
    }

    private static boolean isBlackTheme(final Context context) {
        String blackTheme = context.getResources().getString(R.string.black_theme_key);
        return getSelectedThemeString(context).equals(blackTheme);
    }


    public static void updateTheme(Activity activity) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        if (sharedPreferences.getBoolean("theme_change", false)) {
            sharedPreferences.edit().putBoolean("theme_change", false).apply();
            // https://stackoverflow.com/questions/10844112/
            // Briefly, let the activity resume
            // properly posting the recreate call to end of the message queue
            new Handler(Looper.getMainLooper()).post(activity::recreate);
        }
    }

    public static void setTheme(Context context) {
        int theme = R.style.AppTheme;
        if (isLightTheme(context)) {
            theme = R.style.AppTheme;
        } else if (isDarkTheme(context)) {
            theme = R.style.AppTheme_Dark;
        } else if (isBlackTheme(context)) {
            theme = R.style.AppTheme_Dark;
        }
        context.setTheme(theme);
    }

    public static int getDialogTheme(Context context) {
        int theme = R.style.AppDialogTheme_Light;

        if (isLightTheme(context)) {
            theme = R.style.AppDialogTheme_Light;
        } else if (isDarkTheme(context)) {
            theme = R.style.AppDialogTheme_Dark;
        } else if (isBlackTheme(context)) {
            theme = R.style.AppDialogTheme_Dark;
        }

        return theme;
    }

}
