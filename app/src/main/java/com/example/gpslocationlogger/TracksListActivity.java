package com.example.gpslocationlogger;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TracksListActivity extends AppCompatActivity implements TrackAdapter.TrackActionListener {

    private static final String TAG = "TracksListActivity";
    private static final String GPS_FOLDER = "Vypeensoft/GPS_Location_Logger";
    private static final int STORAGE_PERMISSION_CODE = 1002;

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private TrackAdapter adapter;
    private List<TrackItem> trackList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracks_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.menu_tracks_list);
        }

        recyclerView = findViewById(R.id.recyclerViewTracks);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(this, trackList, this);
        recyclerView.setAdapter(adapter);

        checkPermissionsAndLoadTracks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permissions again in case user granted them from settings
        if (hasStoragePermission()) {
            loadTracks();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkPermissionsAndLoadTracks() {
        if (hasStoragePermission()) {
            loadTracks();
        } else {
            requestStoragePermission();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadTracks();
            } else {
                showEmptyState("Storage permission denied. Cannot read tracks.");
            }
        }
    }

    private void loadTracks() {
        File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
        
        if (!dir.exists()) {
            showEmptyState("Folder does not exist:\n" + dir.getAbsolutePath());
            return;
        }
        if (!dir.isDirectory()) {
            showEmptyState("Path is not a directory:\n" + dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            showEmptyState("Cannot read folder (Permission denied?):\n" + dir.getAbsolutePath());
            return;
        }
        if (files.length == 0) {
            showEmptyState("Folder is empty:\n" + dir.getAbsolutePath());
            return;
        }

        Map<String, TrackItem> trackMap = new HashMap<>();

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName();
                String baseName = getBaseName(name);
                String extension = getExtension(name);

                if (baseName.isEmpty()) continue;

                TrackItem trackItem = trackMap.get(baseName);
                if (trackItem == null) {
                    trackItem = new TrackItem(baseName);
                    parseTrackItemDetails(trackItem);
                    trackItem.lastModified = file.lastModified();
                    trackMap.put(baseName, trackItem);
                }

                // Keep the latest modified date
                if (file.lastModified() > trackItem.lastModified) {
                    trackItem.lastModified = file.lastModified();
                }

                if (!extension.isEmpty()) {
                    trackItem.extensions.add(extension);
                }
            }
        }

        trackList.clear();
        trackList.addAll(trackMap.values());

        // Sort descending by timestamp (lexicographically via baseName)
        Collections.sort(trackList, (t1, t2) -> t2.baseName.compareTo(t1.baseName));

        adapter.notifyDataSetChanged();

        if (trackList.isEmpty()) {
            showEmptyState("No valid tracks found in folder.");
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState(String message) {
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    private void parseTrackItemDetails(TrackItem item) {
        String name = item.baseName;
        String prefix = "location_logs_";
        if (name.startsWith(prefix)) {
            String remainder = name.substring(prefix.length());
            int underscoreIdx = remainder.indexOf('_');
            if (underscoreIdx != -1) {
                // There is tracking info
                String timestamp = remainder.substring(0, underscoreIdx);
                String info = remainder.substring(underscoreIdx + 1);
                item.displayName = info.replace("_", " ");
                item.displayTimestamp = formatTimestamp(timestamp);
            } else {
                // Only timestamp
                item.displayName = formatTimestamp(remainder);
                item.displayTimestamp = null;
            }
        } else {
            item.displayName = name;
            item.displayTimestamp = null;
        }
    }

    private String formatTimestamp(String rawTimestamp) {
        // e.g. 2026-05-17T18-15-09.123+05-30 -> 2026-05-17 18:15:09
        String clean = rawTimestamp;
        int dotIdx = clean.indexOf('.');
        if (dotIdx != -1) clean = clean.substring(0, dotIdx);
        int plusIdx = clean.indexOf('+');
        if (plusIdx != -1) clean = clean.substring(0, plusIdx);

        // Replace T with space
        clean = clean.replace("T", " ");
        // In the time part, replace - with :
        int spaceIdx = clean.indexOf(' ');
        if (spaceIdx != -1 && clean.length() > spaceIdx + 1) {
            String datePart = clean.substring(0, spaceIdx);
            String timePart = clean.substring(spaceIdx + 1).replace("-", ":");
            return datePart + " " + timePart;
        }
        return clean;
    }

    @Override
    public void onShareClick(TrackItem trackItem) {
        // Find the best file to open/share (e.g. .kml, then .gpx, then .json)
        String extToUse = null;
        if (trackItem.extensions.contains(".kml")) {
            extToUse = ".kml";
        } else if (trackItem.extensions.contains(".gpx")) {
            extToUse = ".gpx";
        } else if (trackItem.extensions.contains(".json")) {
            extToUse = ".json";
        } else if (!trackItem.extensions.isEmpty()) {
            extToUse = trackItem.extensions.get(0);
        }

        if (extToUse == null) {
            Toast.makeText(this, "No valid files found for this track.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(new File(Environment.getExternalStorageDirectory(), GPS_FOLDER), trackItem.baseName + extToUse);
        if (!file.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Track"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
            Toast.makeText(this, "Could not share file.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapClick(TrackItem trackItem) {
        String extToUse = null;
        if (trackItem.extensions.contains(".kml")) {
            extToUse = ".kml";
        } else if (trackItem.extensions.contains(".gpx")) {
            extToUse = ".gpx";
        }

        if (extToUse == null) {
            Toast.makeText(this, "No map-compatible file (.kml or .gpx) found for this track.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(new File(Environment.getExternalStorageDirectory(), GPS_FOLDER), trackItem.baseName + extToUse);
        if (!file.exists()) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW);
            if (extToUse.equals(".kml")) {
                mapIntent.setDataAndType(uri, "application/vnd.google-earth.kml+xml");
            } else {
                mapIntent.setDataAndType(uri, "application/gpx+xml");
            }
            mapIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(mapIntent, "Open with"));
        } catch (Exception e) {
            Log.e(TAG, "Error opening map", e);
            Toast.makeText(this, "Could not find an app to open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteClick(TrackItem trackItem) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Track")
                .setMessage("Are you sure you want to delete all files for this track?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    File dir = new File(Environment.getExternalStorageDirectory(), GPS_FOLDER);
                    boolean deletedAny = false;
                    for (String ext : trackItem.extensions) {
                        File f = new File(dir, trackItem.baseName + ext);
                        if (f.exists() && f.delete()) {
                            deletedAny = true;
                        }
                    }
                    if (deletedAny) {
                        Toast.makeText(TracksListActivity.this, "Track deleted", Toast.LENGTH_SHORT).show();
                        loadTracks();
                    } else {
                        Toast.makeText(TracksListActivity.this, "Failed to delete track", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
