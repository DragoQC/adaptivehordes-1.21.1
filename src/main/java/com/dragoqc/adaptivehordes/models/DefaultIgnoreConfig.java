package com.dragoqc.adaptivehordes.models;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DefaultIgnoreConfig {
    public String _about = "Players in ignoredPlayerUuids are skipped by adaptivehorde wave spawning and announcements.";
    public Map<String, String> _keyInfo = new LinkedHashMap<>();

    public Set<String> ignoredPlayerUuids = new LinkedHashSet<>();

    public DefaultIgnoreConfig() {
        _keyInfo.put("ignoredPlayerUuids", "Set of player UUID strings to exclude from wave targeting/spawning.");
    }
}
