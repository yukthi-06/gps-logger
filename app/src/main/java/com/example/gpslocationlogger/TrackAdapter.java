package com.example.gpslocationlogger;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private final Context context;
    private final List<TrackItem> trackList;
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(TrackItem trackItem);
    }

    public TrackAdapter(Context context, List<TrackItem> trackList, OnTrackClickListener listener) {
        this.context = context;
        this.trackList = trackList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        TrackItem trackItem = trackList.get(position);
        holder.tvTrackName.setText(trackItem.displayName != null ? trackItem.displayName : trackItem.baseName);
        holder.tvTrackTimestamp.setText(trackItem.displayTimestamp != null ? trackItem.displayTimestamp : "");
        
        String extensionsStr = TextUtils.join(", ", trackItem.extensions);
        holder.tvTrackFiles.setText("Files: " + extensionsStr);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(trackItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return trackList.size();
    }

    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView tvTrackName;
        TextView tvTrackTimestamp;
        TextView tvTrackFiles;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTrackName = itemView.findViewById(R.id.tvTrackName);
            tvTrackTimestamp = itemView.findViewById(R.id.tvTrackTimestamp);
            tvTrackFiles = itemView.findViewById(R.id.tvTrackFiles);
        }
    }
}
