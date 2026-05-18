package com.example.gpslocationlogger;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.LineManager;
import org.maplibre.android.plugins.annotation.LineOptions;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final String GPS_FOLDER = "Vypeensoft/GPS_Location_Logger";
    
    private MapView mapView;
    private String baseName;
    private LatLngBounds trackBounds;
    private String styleUrl;
    private boolean styleLoaded = false;

    private LinearLayout llDownloadProgress;
    private TextView tvDownloadStatus;
    private ProgressBar pbDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize MapLibre before setContentView
        MapLibre.getInstance(this);

        setContentView(R.layout.activity_map);

        baseName = getIntent().getStringExtra("TRACK_BASENAME");
        String displayName = getIntent().getStringExtra("TRACK_DISPLAY_NAME");
        if (displayName == null || displayName.isEmpty()) {
            displayName = baseName;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(displayName);
        }

        // Load the configured style URL from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String savedStyle = prefs.getString(SettingsActivity.KEY_MAP_STYLE_URL, SettingsActivity.DEFAULT_MAP_STYLE_URL);
        if (SettingsActivity.OLD_DEFAULT_MAP_STYLE_URL.equals(savedStyle)) {
            savedStyle = SettingsActivity.DEFAULT_MAP_STYLE_URL;
            prefs.edit().putString(SettingsActivity.KEY_MAP_STYLE_URL, SettingsActivity.DEFAULT_MAP_STYLE_URL).apply();
        }
        styleUrl = savedStyle;

        mapView = findViewById(R.id.mapView);
        llDownloadProgress = findViewById(R.id.llDownloadProgress);
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus);
        pbDownload = findViewById(R.id.pbDownload);
        
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        // Set up did-fail listener to fall back to local asset style if offline and not cached
        mapView.addOnDidFailLoadingMapListener(new MapView.OnDidFailLoadingMapListener() {
            private boolean fallbackTriggered = false;

            @Override
            public void onDidFailLoadingMap(String errorMessage) {
                Log.w(TAG, "Map style failed to load: " + errorMessage);
                if (!styleLoaded && !fallbackTriggered) {
                    fallbackTriggered = true;
                    Log.i(TAG, "Triggering local style fallback (asset://osm_style.json)");
                    runOnUiThread(() -> {
                        mapLibreMap.setStyle(new Style.Builder().fromUri("asset://osm_style.json"), style -> {
                            styleLoaded = true;
                            if (baseName != null) {
                                loadTrackData(mapLibreMap, style);
                            }
                        });
                    });
                }
            }
        });

        // Attempt loading primary configured map style
        mapLibreMap.setStyle(new Style.Builder().fromUri(styleUrl), style -> {
            styleLoaded = true;
            if (baseName != null) {
                loadTrackData(mapLibreMap, style);
            } else {
                Toast.makeText(MapActivity.this, "No track data provided.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTrackData(MapLibreMap mapLibreMap, Style style) {
        File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
        File gpxFile = new File(dir, baseName + ".gpx");
        File kmlFile = new File(dir, baseName + ".kml");
        File jsonFile = new File(dir, baseName + ".json");

        File fileToParse = null;
        String format = null;

        if (gpxFile.exists()) {
            fileToParse = gpxFile;
            format = "GPX";
        } else if (kmlFile.exists()) {
            fileToParse = kmlFile;
            format = "KML";
        } else if (jsonFile.exists()) {
            fileToParse = jsonFile;
            format = "JSON";
        }

        if (fileToParse == null) {
            Toast.makeText(this, "Cannot display map: No map data found for this track.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(fileToParse);
            byte[] data = new byte[(int) fileToParse.length()];
            fis.read(data);
            fis.close();
            
            String fileStr = new String(data, "UTF-8");
            List<LatLng> points = new ArrayList<>();
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

            if ("GPX".equals(format)) {
                Pattern p = Pattern.compile("<trkpt lat=\"([^\"]+)\" lon=\"([^\"]+)\">");
                Matcher m = p.matcher(fileStr);
                while (m.find()) {
                    double lat = Double.parseDouble(m.group(1));
                    double lon = Double.parseDouble(m.group(2));
                    LatLng latLng = new LatLng(lat, lon);
                    points.add(latLng);
                    boundsBuilder.include(latLng);
                }
            } else if ("KML".equals(format)) {
                // KML coordinates format: lon,lat,alt
                Pattern p = Pattern.compile("([\\d\\.-]+),([\\d\\.-]+),0");
                Matcher m = p.matcher(fileStr);
                while (m.find()) {
                    double lon = Double.parseDouble(m.group(1));
                    double lat = Double.parseDouble(m.group(2));
                    LatLng latLng = new LatLng(lat, lon);
                    points.add(latLng);
                    boundsBuilder.include(latLng);
                }
            } else if ("JSON".equals(format)) {
                JSONArray jsonArray = new JSONArray(fileStr);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    double lat = obj.getDouble("latitude");
                    double lon = obj.getDouble("longitude");
                    LatLng latLng = new LatLng(lat, lon);
                    points.add(latLng);
                    boundsBuilder.include(latLng);
                }
            }

            if (points.isEmpty()) {
                Toast.makeText(this, "No location points found in " + format + " file.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save bounds for offline download feature
            trackBounds = boundsBuilder.build();

            // Draw polyline using the annotation plugin
            LineManager lineManager = new LineManager(mapView, mapLibreMap, style);
            LineOptions lineOptions = new LineOptions()
                    .withLatLngs(points)
                    .withLineColor("#D32F2F") // Red color
                    .withLineWidth(5.0f);
            
            lineManager.create(lineOptions);

            // Zoom to fit the bounding box with 100px padding
            mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds(trackBounds, 100));

        } catch (Exception e) {
            Log.e(TAG, "Failed to load map data", e);
            Toast.makeText(this, "Failed to parse track data.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareTrack();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            deleteTrack();
            return true;
        } else if (item.getItemId() == R.id.action_download_offline) {
            downloadOfflineMap();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareTrack() {
        File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
        File kmlFile = new File(dir, baseName + ".kml");
        File gpxFile = new File(dir, baseName + ".gpx");
        File jsonFile = new File(dir, baseName + ".json");

        File fileToShare = null;
        if (kmlFile.exists()) {
            fileToShare = kmlFile;
        } else if (gpxFile.exists()) {
            fileToShare = gpxFile;
        } else if (jsonFile.exists()) {
            fileToShare = jsonFile;
        }

        if (fileToShare == null) {
            Toast.makeText(this, "No valid files found to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", fileToShare);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Track"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing track file", e);
            Toast.makeText(this, "Could not share track file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteTrack() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Track")
                .setMessage("Are you sure you want to delete all files for this track?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
                    String[] extensions = {".gpx", ".kml", ".json"};
                    boolean deletedAny = false;
                    for (String ext : extensions) {
                        File f = new File(dir, baseName + ext);
                        if (f.exists() && f.delete()) {
                            deletedAny = true;
                        }
                    }
                    if (deletedAny) {
                        Toast.makeText(MapActivity.this, "Track deleted successfully.", Toast.LENGTH_SHORT).show();
                        finish(); // Close map activity and go back to track list
                    } else {
                        Toast.makeText(MapActivity.this, "Failed to delete track.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadOfflineMap() {
        if (trackBounds == null) {
            Toast.makeText(this, "No route data to download.", Toast.LENGTH_SHORT).show();
            return;
        }

        llDownloadProgress.setVisibility(View.VISIBLE);
        pbDownload.setIndeterminate(true);
        tvDownloadStatus.setText("Calculating download size...");

        // Download tiles from zoom 10 to 19 for the bounding box
        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                styleUrl,
                trackBounds,
                10,
                19,
                getResources().getDisplayMetrics().density
        );

        byte[] metadata;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("REGION_NAME", baseName);
            metadata = jsonObject.toString().getBytes("UTF-8");
        } catch (Exception e) {
            metadata = new byte[0];
        }

        OfflineManager.getInstance(this).createOfflineRegion(
                definition,
                metadata,
                new OfflineManager.CreateOfflineRegionCallback() {
                    @Override
                    public void onCreate(OfflineRegion offlineRegion) {
                        offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
                            @Override
                            public void onStatusChanged(OfflineRegionStatus status) {
                                runOnUiThread(() -> {
                                    long completed = status.getCompletedResourceCount();
                                    long required = status.getRequiredResourceCount();
                                    Log.d(TAG, "Download progress: " + completed + "/" + required + " (complete=" + status.isComplete() + ")");

                                    if (status.isComplete()) {
                                        llDownloadProgress.setVisibility(View.GONE);
                                        Toast.makeText(MapActivity.this, "Offline map downloaded successfully!", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    if (required > 0) {
                                        pbDownload.setIndeterminate(false);
                                        double percentage = (100.0 * completed) / required;
                                        pbDownload.setProgress((int) Math.round(percentage));
                                        tvDownloadStatus.setText(String.format("Downloading... %d / %d tiles (%.1f%%)",
                                                completed, required, percentage));
                                    } else {
                                        pbDownload.setIndeterminate(true);
                                        tvDownloadStatus.setText("Calculating download size...");
                                    }
                                });
                            }

                            @Override
                            public void onError(OfflineRegionError error) {
                                runOnUiThread(() -> {
                                    llDownloadProgress.setVisibility(View.GONE);
                                    Log.e(TAG, "Offline region error: " + error.getReason());
                                    Toast.makeText(MapActivity.this, "Error downloading map.", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void mapboxTileCountLimitExceeded(long limit) {
                                runOnUiThread(() -> {
                                    llDownloadProgress.setVisibility(View.GONE);
                                    Log.e(TAG, "Tile count exceeded: " + limit);
                                    Toast.makeText(MapActivity.this, "Tile limit exceeded.", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);
                    }

                    @Override
                    public void onError(String error) {
                        llDownloadProgress.setVisibility(View.GONE);
                        Log.e(TAG, "Failed to create offline region: " + error);
                        Toast.makeText(MapActivity.this, "Failed to start download.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // --- MapView Lifecycle Methods ---
    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
