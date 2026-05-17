package com.example.gpslocationlogger;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final String GPS_FOLDER = "Vypeensoft/GPS_Location_Logger";
    private MapView mapView;
    private String baseName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize MapLibre before setContentView
        MapLibre.getInstance(this);

        setContentView(R.layout.activity_map);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Track Map");
        }

        baseName = getIntent().getStringExtra("TRACK_BASENAME");

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        // Use a simple raster style with OpenStreetMap tiles to avoid needing an API key
        String styleJson = "{"
                + "\"version\": 8,"
                + "\"sources\": {"
                + "  \"osm\": {"
                + "    \"type\": \"raster\","
                + "    \"tiles\": [\"https://a.tile.openstreetmap.org/{z}/{x}/{y}.png\"],"
                + "    \"tileSize\": 256"
                + "  }"
                + "},"
                + "\"layers\": [{"
                + "  \"id\": \"osm\","
                + "  \"type\": \"raster\","
                + "  \"source\": \"osm\""
                + "}]"
                + "}";

        mapLibreMap.setStyle(new Style.Builder().fromJson(styleJson), style -> {
            if (baseName != null) {
                loadTrackData(mapLibreMap, style);
            } else {
                Toast.makeText(MapActivity.this, "No track data provided.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTrackData(MapLibreMap mapLibreMap, Style style) {
        File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
        File jsonFile = new File(dir, baseName + ".json");

        if (!jsonFile.exists()) {
            Toast.makeText(this, "Cannot display map: No .json data found for this track.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(jsonFile);
            byte[] data = new byte[(int) jsonFile.length()];
            fis.read(data);
            fis.close();
            
            String jsonStr = new String(data, "UTF-8");
            JSONArray jsonArray = new JSONArray(jsonStr);

            List<LatLng> points = new ArrayList<>();
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                double lat = obj.getDouble("latitude");
                double lon = obj.getDouble("longitude");
                LatLng latLng = new LatLng(lat, lon);
                points.add(latLng);
                boundsBuilder.include(latLng);
            }

            if (points.isEmpty()) {
                Toast.makeText(this, "No location points in this track.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Draw polyline using the annotation plugin
            LineManager lineManager = new LineManager(mapView, mapLibreMap, style);
            LineOptions lineOptions = new LineOptions()
                    .withLatLngs(points)
                    .withLineColor("#D32F2F") // Red color
                    .withLineWidth(5.0f);
            
            lineManager.create(lineOptions);

            // Zoom to fit the bounding box with 100px padding
            mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));

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
