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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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

    private Spinner    spFrequency;
    private TextView   tvIntervalPreview;
    private CheckBox   cbJson, cbGpx, cbKml;
    private EditText   etMapStyleUrl;
    private Button     btnClearCache;
    private SharedPreferences prefs;

    private static final long[] INTERVALS_MS = {
            1_000L,
            2_000L,
            3_000L,
            5_000L,
            10_000L,
            30_000L,
            60_000L,
            300_000L
    };

    private static final String[] INTERVAL_LABELS = {
            "Every 1 second (Highest frequency)",
            "Every 2 seconds",
            "Every 3 seconds",
            "Every 5 seconds (High accuracy)",
            "Every 10 seconds",
            "Every 30 seconds",
            "Every 1 minute",
            "Every 5 minutes (Battery saver)"
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

        spFrequency      = findViewById(R.id.spFrequency);
        tvIntervalPreview = findViewById(R.id.tvIntervalPreview);
        cbJson           = findViewById(R.id.cbJson);
        cbGpx            = findViewById(R.id.cbGpx);
        cbKml            = findViewById(R.id.cbKml);
        etMapStyleUrl    = findViewById(R.id.etMapStyleUrl);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ── 1. Logging Frequency (Dropdown Spinner) ──
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, INTERVAL_LABELS);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFrequency.setAdapter(adapter);

        long savedInterval = prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        int selectionIndex = 3; // default: 5s (index 3)
        for (int i = 0; i < INTERVALS_MS.length; i++) {
            if (INTERVALS_MS[i] == savedInterval) {
                selectionIndex = i;
                break;
            }
        }

        final int finalIndex = selectionIndex;
        spFrequency.post(() -> {
            spFrequency.setSelection(finalIndex, false);
            spFrequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    long interval = INTERVALS_MS[position];
                    prefs.edit().putLong(KEY_INTERVAL_MS, interval).apply();
                    updatePreview(interval);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
        updatePreview(savedInterval);

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

    /** Returns the human-readable label for a given interval in ms. */
    private String labelForInterval(long intervalMs) {
        for (int i = 0; i < INTERVALS_MS.length; i++) {
            if (INTERVALS_MS[i] == intervalMs) {
                return INTERVAL_LABELS[i];
            }
        }
        return "Every 5 seconds";
    }

    /** Updates the summary text below the Spinner. */
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
