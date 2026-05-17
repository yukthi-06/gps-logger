package com.example.gpslocationlogger;

import java.util.ArrayList;
import java.util.List;

public class TrackItem {
    public String baseName;
    public long lastModified;
    public List<String> extensions;

    public TrackItem(String baseName) {
        this.baseName = baseName;
        this.extensions = new ArrayList<>();
    }
}
