package com.example.gpslocationlogger;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * SettingsActivity — App Options Screen
 *
 * Persists user preferences via SharedPreferences.
 * Currently exposes: Logging Frequency (interval between GPS fixes).
 */
public class SettingsActivity extends AppCompatActivity {

    /** SharedPreferences file name — shared with MainActivity. */
    public static final String PREFS_NAME = "gps_logger_prefs";

    /** Key for the logging interval in milliseconds. */
    public static final String KEY_INTERVAL_MS = "logging_interval_ms";

    /** Keys for format selection. */
    public static final String KEY_SAVE_JSON = "save_json";
    public static final String KEY_SAVE_GPX  = "save_gpx";
    public static final String KEY_SAVE_KML  = "save_kml";

    /** Key for Map Style URL. */
    public static final String KEY_MAP_STYLE_URL = "map_style_url";
    public static final String DEFAULT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    public static final String OLD_DEFAULT_MAP_STYLE_URL = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json";

    /** Default interval: 5 seconds. */
    public static final long DEFAULT_INTERVAL_MS = 5_000L;

    private RadioGroup rgFrequency;
    private TextView   tvIntervalPreview;
    private CheckBox   cbJson, cbGpx, cbKml;
    private EditText   etMapStyleUrl;
    private Button     btnClearCache;
    private SharedPreferences prefs;

    // Maps each RadioButton ID to its interval in milliseconds
    private static final int[] RADIO_IDS = {
            R.id.rb5s,
            R.id.rb10s,
            R.id.rb30s,
            R.id.rb1m,
            R.id.rb5m
    };

    private static final long[] INTERVALS_MS = {
            5_000L,
            10_000L,
            30_000L,
            60_000L,
            300_000L
    };

    private static final String[] INTERVAL_LABELS = {
            "Every 5 seconds",
            "Every 10 seconds",
            "Every 30 seconds",
            "Every 1 minute",
            "Every 5 minutes"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Show back arrow in the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Options");
        }

        rgFrequency      = findViewById(R.id.rgFrequency);
        tvIntervalPreview = findViewById(R.id.tvIntervalPreview);
        cbJson           = findViewById(R.id.cbJson);
        cbGpx            = findViewById(R.id.cbGpx);
        cbKml            = findViewById(R.id.cbKml);
        etMapStyleUrl    = findViewById(R.id.etMapStyleUrl);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ── 1. Logging Frequency ──
        long savedInterval = prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        selectRadioForInterval(savedInterval);
        updatePreview(savedInterval);

        rgFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            long interval = intervalForRadioId(checkedId);
            prefs.edit().putLong(KEY_INTERVAL_MS, interval).apply();
            updatePreview(interval);
        });

        // ── 2. Format Selection ──
        cbJson.setChecked(prefs.getBoolean(KEY_SAVE_JSON, true));
        cbGpx.setChecked(prefs.getBoolean(KEY_SAVE_GPX, true));
        cbKml.setChecked(prefs.getBoolean(KEY_SAVE_KML, true));

        cbJson.setOnCheckedChangeListener((v, checked) -> handleFormatChange(KEY_SAVE_JSON, checked, cbJson));
        cbGpx.setOnCheckedChangeListener((v, checked) -> handleFormatChange(KEY_SAVE_GPX, checked, cbGpx));
        cbKml.setOnCheckedChangeListener((v, checked) -> handleFormatChange(KEY_SAVE_KML, checked, cbKml));

        // ── 3. Map Style URL Settings ──
        String savedMapStyle = prefs.getString(KEY_MAP_STYLE_URL, DEFAULT_MAP_STYLE_URL);
        if (savedMapStyle.equals(OLD_DEFAULT_MAP_STYLE_URL)) {
            savedMapStyle = DEFAULT_MAP_STYLE_URL;
            prefs.edit().putString(KEY_MAP_STYLE_URL, DEFAULT_MAP_STYLE_URL).apply();
        }
        etMapStyleUrl.setText(savedMapStyle);

        etMapStyleUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_MAP_STYLE_URL, s.toString().trim()).apply();
            }
        });

        // ── 4. Offline Map Cache Settings ──
        btnClearCache = findViewById(R.id.btnClearCache);
        btnClearCache.setOnClickListener(v -> clearOfflineMapCache());
    }

    /**
     * Saves the format preference.
     * Prevents unchecking if it's the last one selected.
     */
    private void handleFormatChange(String key, boolean isChecked, CheckBox checkBox) {
        if (!isChecked && !cbJson.isChecked() && !cbGpx.isChecked() && !cbKml.isChecked()) {
            // Revert: can't uncheck the last one
            checkBox.setChecked(true);
            Toast.makeText(this, R.string.toast_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putBoolean(key, isChecked).apply();
    }

    /** Selects the radio button that matches the stored interval. */
    private void selectRadioForInterval(long intervalMs) {
        for (int i = 0; i < INTERVALS_MS.length; i++) {
            if (INTERVALS_MS[i] == intervalMs) {
                rgFrequency.check(RADIO_IDS[i]);
                return;
            }
        }
        // Fallback to default (5 s)
        rgFrequency.check(R.id.rb5s);
    }

    /** Returns the interval in ms corresponding to a radio button ID. */
    private long intervalForRadioId(int radioId) {
        for (int i = 0; i < RADIO_IDS.length; i++) {
            if (RADIO_IDS[i] == radioId) {
                return INTERVALS_MS[i];
            }
        }
        return DEFAULT_INTERVAL_MS;
    }

    /** Returns the human-readable label for a given interval in ms. */
    private String labelForInterval(long intervalMs) {
        for (int i = 0; i < INTERVALS_MS.length; i++) {
            if (INTERVALS_MS[i] == intervalMs) {
                return INTERVAL_LABELS[i];
            }
        }
        return getString(R.string.freq_5s);
    }

    /** Updates the summary text below the RadioGroup. */
    private void updatePreview(long intervalMs) {
        tvIntervalPreview.setText(getString(R.string.preview_interval, labelForInterval(intervalMs).toLowerCase()));
    }

    /** Handle the toolbar back arrow. */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /** Lists and deletes all offline map downloaded regions programmatically. */
    private void clearOfflineMapCache() {
        // Initialize MapLibre in this context first
        org.maplibre.android.MapLibre.getInstance(this);

        org.maplibre.android.offline.OfflineManager.getInstance(this).listOfflineRegions(new org.maplibre.android.offline.OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(org.maplibre.android.offline.OfflineRegion[] offlineRegions) {
                if (offlineRegions == null || offlineRegions.length == 0) {
                    Toast.makeText(SettingsActivity.this, "No offline map data found.", Toast.LENGTH_SHORT).show();
                    return;
                }

                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Clear Offline Maps?")
                        .setMessage("This will delete all downloaded offline map tiles. You will need to download them again for offline use.")
                        .setPositiveButton("Clear", (dialogInterface, which) -> {
                            final int total = offlineRegions.length;
                            final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);

                            for (org.maplibre.android.offline.OfflineRegion region : offlineRegions) {
                                region.delete(new org.maplibre.android.offline.OfflineRegion.OfflineRegionDeleteCallback() {
                                    @Override
                                    public void onDelete() {
                                        int progress = count.incrementAndGet();
                                        if (progress == total) {
                                            Toast.makeText(SettingsActivity.this, "Successfully cleared all offline maps!", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Log.e("SettingsActivity", "Error deleting region: " + error);
                                        Toast.makeText(SettingsActivity.this, "Error deleting region: " + error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.parseColor("#D32F2F"));
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#888EAB"));
            }

            @Override
            public void onError(String error) {
                Log.e("SettingsActivity", "Failed to list offline regions: " + error);
                Toast.makeText(SettingsActivity.this, "Failed to load offline regions: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
