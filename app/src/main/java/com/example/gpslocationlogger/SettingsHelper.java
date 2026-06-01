package com.example.gpslocationlogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class SettingsHelper {
    private static final String TAG = "SettingsHelper";
    private static final String SETTINGS_FOLDER = "Vypeensoft/GPS_Location_Logger/settings";
    private static final String SETTINGS_FILE_NAME = "settings.json";

    public static void saveSettingsToJson(Context context, SharedPreferences prefs) {
        File dir = new File(Environment.getExternalStorageDirectory(), SETTINGS_FOLDER);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create settings directory: " + dir.getAbsolutePath());
                return;
            }
        }

        File file = new File(dir, SETTINGS_FILE_NAME);
        try {
            JSONObject json = new JSONObject();
            json.put(SettingsActivity.KEY_INTERVAL_MS, prefs.getLong(SettingsActivity.KEY_INTERVAL_MS, SettingsActivity.DEFAULT_INTERVAL_MS));
            json.put(SettingsActivity.KEY_SAVE_JSON, prefs.getBoolean(SettingsActivity.KEY_SAVE_JSON, true));
            json.put(SettingsActivity.KEY_SAVE_GPX, prefs.getBoolean(SettingsActivity.KEY_SAVE_GPX, true));
            json.put(SettingsActivity.KEY_SAVE_KML, prefs.getBoolean(SettingsActivity.KEY_SAVE_KML, true));
            json.put(SettingsActivity.KEY_MAP_STYLE_URL, prefs.getString(SettingsActivity.KEY_MAP_STYLE_URL, SettingsActivity.DEFAULT_MAP_STYLE_URL));
            json.put(SettingsActivity.KEY_INTELLIGENT_TRACKING, prefs.getBoolean(SettingsActivity.KEY_INTELLIGENT_TRACKING, false));
            json.put(SettingsActivity.KEY_MIN_DISTANCE_METERS, (double) prefs.getFloat(SettingsActivity.KEY_MIN_DISTANCE_METERS, SettingsActivity.DEFAULT_MIN_DISTANCE_METERS));

            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(json.toString(2));
                writer.flush();
                Log.i(TAG, "Settings saved to external storage: " + file.getAbsolutePath());
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error saving settings to JSON", e);
        }
    }

    public static void loadSettingsFromJson(Context context, SharedPreferences prefs) {
        File file = new File(Environment.getExternalStorageDirectory(), SETTINGS_FOLDER + "/" + SETTINGS_FILE_NAME);
        if (!file.exists()) {
            Log.i(TAG, "No external settings file found at: " + file.getAbsolutePath());
            return;
        }

        try {
            String content;
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int read = fis.read(data);
                content = new String(data, 0, read, "UTF-8");
            }

            JSONObject json = new JSONObject(content);
            SharedPreferences.Editor editor = prefs.edit();

            if (json.has(SettingsActivity.KEY_INTERVAL_MS)) {
                editor.putLong(SettingsActivity.KEY_INTERVAL_MS, json.getLong(SettingsActivity.KEY_INTERVAL_MS));
            }
            if (json.has(SettingsActivity.KEY_SAVE_JSON)) {
                editor.putBoolean(SettingsActivity.KEY_SAVE_JSON, json.getBoolean(SettingsActivity.KEY_SAVE_JSON));
            }
            if (json.has(SettingsActivity.KEY_SAVE_GPX)) {
                editor.putBoolean(SettingsActivity.KEY_SAVE_GPX, json.getBoolean(SettingsActivity.KEY_SAVE_GPX));
            }
            if (json.has(SettingsActivity.KEY_SAVE_KML)) {
                editor.putBoolean(SettingsActivity.KEY_SAVE_KML, json.getBoolean(SettingsActivity.KEY_SAVE_KML));
            }
            if (json.has(SettingsActivity.KEY_MAP_STYLE_URL)) {
                editor.putString(SettingsActivity.KEY_MAP_STYLE_URL, json.getString(SettingsActivity.KEY_MAP_STYLE_URL));
            }
            if (json.has(SettingsActivity.KEY_INTELLIGENT_TRACKING)) {
                editor.putBoolean(SettingsActivity.KEY_INTELLIGENT_TRACKING, json.getBoolean(SettingsActivity.KEY_INTELLIGENT_TRACKING));
            }
            if (json.has(SettingsActivity.KEY_MIN_DISTANCE_METERS)) {
                editor.putFloat(SettingsActivity.KEY_MIN_DISTANCE_METERS, (float) json.getDouble(SettingsActivity.KEY_MIN_DISTANCE_METERS));
            }

            editor.apply();
            Log.i(TAG, "Settings loaded from external storage: " + file.getAbsolutePath());
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error loading settings from JSON", e);
        }
    }
}
