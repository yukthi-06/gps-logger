package com.example.gpslocationlogger;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

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
import org.maplibre.android.plugins.annotation.CircleManager;
import org.maplibre.android.plugins.annotation.CircleOptions;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final String GPS_FOLDER = "Vypeensoft/GPS_Location_Logger/tracks";
    private static final int REQUEST_LOCATION_PERMISSION = 101;
    
    private MapView mapView;
    private String baseName;
    private LatLngBounds trackBounds;
    private String styleUrl;
    private boolean styleLoaded = false;

    private LinearLayout llDownloadProgress;
    private TextView tvDownloadStatus;
    private ProgressBar pbDownload;

    // Retrace / Navigation mode
    private LinearLayout btnRetraceRoute;
    private LinearLayout btnStopNavigation;
    private LinearLayout llNavStatus;
    private boolean isRetracing = false;
    private CircleManager locationDotManager;
    private org.maplibre.android.plugins.annotation.Circle locationDot;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MapLibreMap activeMap;  // held for retrace mode
    private Style activeStyle;      // held for retrace mode

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
        btnRetraceRoute   = findViewById(R.id.btnRetraceRoute);
        btnStopNavigation = findViewById(R.id.btnStopNavigation);
        llNavStatus       = findViewById(R.id.llNavStatus);
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        findViewById(R.id.btnShareMap).setOnClickListener(v -> shareTrack());
        findViewById(R.id.btnDeleteMap).setOnClickListener(v -> deleteTrack());
        findViewById(R.id.btnDownloadMap).setOnClickListener(v -> downloadOfflineMap());
        btnRetraceRoute.setOnClickListener(v -> onRetraceClicked());
        btnStopNavigation.setOnClickListener(v -> stopRetraceMode());

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        // Allow user to zoom in up to level 24.0 (beyond default limits)
        mapLibreMap.setMaxZoomPreference(24.0);
        activeMap = mapLibreMap;

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
                            activeStyle = style;
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
            activeStyle = style;
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
                Pattern p = Pattern.compile("<(trkpt|wpt) lat=\"([^\"]+)\" lon=\"([^\"]+)\">");
                Matcher m = p.matcher(fileStr);
                while (m.find()) {
                    double lat = Double.parseDouble(m.group(2));
                    double lon = Double.parseDouble(m.group(3));
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

            if (points.size() == 1) {
                LatLng p = points.get(0);
                double delta = 0.00025; // roughly 25 meters offset
                boundsBuilder.include(new LatLng(p.getLatitude() + delta, p.getLongitude() + delta));
                boundsBuilder.include(new LatLng(p.getLatitude() - delta, p.getLongitude() - delta));
            }

            // Save bounds for offline download feature (ensuring at least 50m x 50m span)
            trackBounds = ensureMinBoundsSize(boundsBuilder.build(), 50.0);

            // Draw polyline or marker using the annotation plugin
            if (points.size() == 1) {
                CircleManager circleManager = new CircleManager(mapView, mapLibreMap, style);
                CircleOptions circleOptions = new CircleOptions()
                        .withLatLng(points.get(0))
                        .withCircleColor("#D32F2F") // Red color
                        .withCircleRadius(10.0f)
                        .withCircleStrokeColor("#FFFFFF")
                        .withCircleStrokeWidth(2.0f);
                circleManager.create(circleOptions);
            } else {
                LineManager lineManager = new LineManager(mapView, mapLibreMap, style);
                LineOptions lineOptions = new LineOptions()
                        .withLatLngs(points)
                        .withLineColor("#D32F2F") // Red color
                        .withLineWidth(5.0f);
                lineManager.create(lineOptions);

                // --- Start / End markers (bitmap icons with S / E letters) ---
                LatLng startPoint = points.get(0);
                LatLng endPoint   = points.get(points.size() - 1);

                // Build bitmap icons and register them in the map style
                Bitmap startBitmap = createMarkerBitmap("S", Color.parseColor("#388E3C"));
                Bitmap endBitmap   = createMarkerBitmap("E", Color.parseColor("#B71C1C"));
                style.addImage("marker-start", startBitmap);
                style.addImage("marker-end",   endBitmap);

                // Place icons using SymbolManager
                SymbolManager symbolManager = new SymbolManager(mapView, mapLibreMap, style);
                symbolManager.setIconAllowOverlap(true);
                symbolManager.setTextAllowOverlap(true);

                symbolManager.create(new SymbolOptions()
                        .withLatLng(startPoint)
                        .withIconImage("marker-start")
                        .withIconSize(1.0f)
                        .withIconAnchor("center"));

                symbolManager.create(new SymbolOptions()
                        .withLatLng(endPoint)
                        .withIconImage("marker-end")
                        .withIconSize(1.0f)
                        .withIconAnchor("center"));
            }

            // Zoom to fit the bounding box with 100px padding
            mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds(trackBounds, 100));

        } catch (Exception e) {
            Log.e(TAG, "Failed to load map data", e);
            Toast.makeText(this, "Failed to parse track data.", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------------
    // Retrace / Navigation mode
    // -------------------------------------------------------------------------

    private void onRetraceClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }
        startRetraceMode();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRetraceMode();
        } else {
            Toast.makeText(this, "Location permission needed for navigation.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRetraceMode() {
        if (activeMap == null || activeStyle == null) {
            Toast.makeText(this, "Map not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        isRetracing = true;

        // Swap buttons
        btnRetraceRoute.setVisibility(View.GONE);
        btnStopNavigation.setVisibility(View.VISIBLE);
        llNavStatus.setVisibility(View.VISIBLE);

        // Create the live-location dot manager
        locationDotManager = new CircleManager(mapView, activeMap, activeStyle);

        // Set up location updates (high accuracy, every 2 seconds)
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    updateLocationDot(new LatLng(loc.getLatitude(), loc.getLongitude()));
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper());
            // Immediately move camera to last known location if available
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, loc -> {
                if (loc != null) {
                    LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                    updateLocationDot(pos);
                    activeMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(pos, 17.0));
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
        }

        // Keep screen on while driving/navigating
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toast.makeText(this, "Navigation started. Follow the red route.", Toast.LENGTH_SHORT).show();
    }

    private void stopRetraceMode() {
        isRetracing = false;

        // Clear keep-screen-on flag
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Stop GPS updates
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }

        // Remove the live dot
        if (locationDotManager != null) {
            locationDotManager.deleteAll();
            locationDotManager = null;
            locationDot = null;
        }

        // Restore UI
        btnStopNavigation.setVisibility(View.GONE);
        llNavStatus.setVisibility(View.GONE);
        btnRetraceRoute.setVisibility(View.VISIBLE);

        // Zoom back out to show full track
        if (activeMap != null && trackBounds != null) {
            activeMap.easeCamera(CameraUpdateFactory.newLatLngBounds(trackBounds, 100));
        }
    }

    /**
     * Places or moves the cyan live-location dot on the map.
     */
    private void updateLocationDot(LatLng position) {
        if (!isRetracing || locationDotManager == null) return;

        if (locationDot == null) {
            // First fix — create the dot
            locationDot = locationDotManager.create(new CircleOptions()
                    .withLatLng(position)
                    .withCircleColor("#00BCD4")       // cyan
                    .withCircleRadius(12.0f)
                    .withCircleStrokeColor("#FFFFFF")
                    .withCircleStrokeWidth(3.0f)
                    .withCircleOpacity(0.95f));
        } else {
            // Subsequent updates — move the dot
            locationDot.setLatLng(position);
            locationDotManager.update(locationDot);
        }

        // Camera follows user
        if (activeMap != null) {
            activeMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(position, 17.0));
        }
    }

    /**
     * Creates a circular Bitmap marker with the given letter painted in white at the center.
     *
     * @param letter     Single character to display (e.g. "S" or "E")
     * @param circleColor Filled circle background color (ARGB int)
     * @return           Bitmap ready to be added as a map style image
     */
    private Bitmap createMarkerBitmap(String letter, int circleColor) {
        int size = 72; // px — sufficient for map icon clarity
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw filled circle
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(circleColor);
        circlePaint.setStyle(Paint.Style.FILL);
        float radius = size / 2.0f - 3;
        canvas.drawCircle(size / 2.0f, size / 2.0f, radius, circlePaint);

        // Draw white stroke
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        canvas.drawCircle(size / 2.0f, size / 2.0f, radius, strokePaint);

        // Draw the letter centered
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(size * 0.45f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        // Vertically center: baseline = center + half cap-height
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = size / 2.0f - (fm.ascent + fm.descent) / 2.0f;
        canvas.drawText(letter, size / 2.0f, textY, textPaint);

        return bitmap;
    }

    private LatLngBounds ensureMinBoundsSize(LatLngBounds bounds, double minSizeMeters) {
        if (bounds == null) return null;

        double latNorth = bounds.getLatNorth();
        double latSouth = bounds.getLatSouth();
        double lonEast = bounds.getLonEast();
        double lonWest = bounds.getLonWest();

        double centerLat = (latNorth + latSouth) / 2.0;
        double centerLon = (lonEast + lonWest) / 2.0;

        double currentLatSpan = latNorth - latSouth;
        double currentLonSpan = lonEast - lonWest;

        // 1 degree of latitude is ~111,111 meters
        double latDeltaForMin = minSizeMeters / 111111.0;

        double cosLat = Math.cos(Math.toRadians(centerLat));
        if (cosLat < 0.01) {
            cosLat = 0.01;
        }
        double lonDeltaForMin = minSizeMeters / (111111.0 * cosLat);

        boolean adjusted = false;

        if (currentLatSpan < latDeltaForMin) {
            latNorth = centerLat + latDeltaForMin / 2.0;
            latSouth = centerLat - latDeltaForMin / 2.0;

            // Bounds check
            if (latNorth > 90.0) {
                double shift = latNorth - 90.0;
                latNorth = 90.0;
                latSouth -= shift;
            }
            if (latSouth < -90.0) {
                double shift = -90.0 - latSouth;
                latSouth = -90.0;
                latNorth += shift;
            }
            adjusted = true;
        }

        if (currentLonSpan < lonDeltaForMin) {
            lonEast = centerLon + lonDeltaForMin / 2.0;
            lonWest = centerLon - lonDeltaForMin / 2.0;

            // Bounds check
            if (lonEast > 180.0) {
                double shift = lonEast - 180.0;
                lonEast = 180.0;
                lonWest -= shift;
            }
            if (lonWest < -180.0) {
                double shift = -180.0 - lonWest;
                lonWest = -180.0;
                lonEast += shift;
            }
            adjusted = true;
        }

        if (adjusted) {
            return LatLngBounds.from(latNorth, lonEast, latSouth, lonWest);
        }

        return bounds;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void shareTrack() {
        if (baseName != null && baseName.contains("_Recorded_Point")) {
            String displayName = getIntent().getStringExtra("TRACK_DISPLAY_NAME");
            SharePointHelper.showShareOptionsDialog(this, baseName, displayName, () -> shareTrackFiles());
        } else {
            shareTrackFiles();
        }
    }

    private void shareTrackFiles() {
        File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
        File gpxFile = new File(dir, baseName + ".gpx");
        File kmlFile = new File(dir, baseName + ".kml");
        File jsonFile = new File(dir, baseName + ".json");

        List<String> availableExtensions = new ArrayList<>();
        List<File> availableFiles = new ArrayList<>();

        if (gpxFile.exists()) {
            availableExtensions.add("GPX (.gpx)");
            availableFiles.add(gpxFile);
        }
        if (kmlFile.exists()) {
            availableExtensions.add("KML (.kml)");
            availableFiles.add(kmlFile);
        }
        if (jsonFile.exists()) {
            availableExtensions.add("JSON (.json)");
            availableFiles.add(jsonFile);
        }

        if (availableFiles.isEmpty()) {
            Toast.makeText(this, "No valid files found for this track.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (availableFiles.size() == 1) {
            shareSingleFile(availableFiles.get(0));
            return;
        }

        CharSequence[] items = availableExtensions.toArray(new CharSequence[0]);
        boolean[] checkedItems = new boolean[items.length];
        // Default to all selected
        for (int i = 0; i < checkedItems.length; i++) {
            checkedItems[i] = true;
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Select formats to share")
                .setMultiChoiceItems(items, checkedItems, (dialogInterface, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Share", (dialogInterface, which) -> {
                    List<File> selectedFiles = new ArrayList<>();
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            selectedFiles.add(availableFiles.get(i));
                        }
                    }

                    if (selectedFiles.isEmpty()) {
                        Toast.makeText(MapActivity.this, "Please select at least one format.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (selectedFiles.size() == 1) {
                        shareSingleFile(selectedFiles.get(0));
                    } else {
                        try {
                            File zippedFile = zipFiles(selectedFiles, baseName + ".zip");
                            shareSingleFile(zippedFile);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to zip files", e);
                            Toast.makeText(MapActivity.this, "Failed to pack files for sharing.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.parseColor("#1E88E5"));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#888EAB"));
    }

    private void shareSingleFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (file.getName().endsWith(".zip")) {
                shareIntent.setType("application/zip");
            } else {
                shareIntent.setType("application/octet-stream");
            }
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Track"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file: " + file.getName(), e);
            Toast.makeText(this, "Could not share file.", Toast.LENGTH_SHORT).show();
        }
    }

    private File zipFiles(List<File> filesToZip, String zipFileName) throws IOException {
        File zipFile = new File(getExternalCacheDir(), zipFileName);
        if (zipFile.exists()) {
            zipFile.delete();
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            byte[] buffer = new byte[4096];
            for (File file : filesToZip) {
                if (!file.exists()) continue;
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                    zos.closeEntry();
                }
            }
        }
        return zipFile;
    }

    private void deleteTrack() {
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Delete Track")
                .setMessage("Are you sure you want to delete all files for this track?")
                .setPositiveButton("Delete", (dialogInterface, which) -> {
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

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.parseColor("#D32F2F"));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#888EAB"));
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
        // Stop navigation if app goes to background
        if (isRetracing) stopRetraceMode();
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
