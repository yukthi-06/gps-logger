package com.example.gpslocationlogger;

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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TracksListActivity extends AppCompatActivity {

    private static final String TAG = "TracksListActivity";
    private static final String GPS_FOLDER = "Vypeensoft/GPS_Location_Logger";

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
        adapter = new TrackAdapter(this, trackList, this::onTrackClicked);
        recyclerView.setAdapter(adapter);

        loadTracks();
    }

    private void loadTracks() {
        File dir = new File("/sdcard/Vypeensoft/GPS_Location_Logger/");
        
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

        // Sort descending by last modified
        Collections.sort(trackList, (t1, t2) -> Long.compare(t2.lastModified, t1.lastModified));

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

    private void onTrackClicked(TrackItem trackItem) {
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
            Toast.makeText(this, "Error sharing file.", Toast.LENGTH_SHORT).show();
        }
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
