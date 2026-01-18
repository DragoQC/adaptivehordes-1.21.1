package com.dragoqc.adaptivehordes.MobWave;

import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.models.DefaultWaveConfig;
import com.dragoqc.adaptivehordes.models.Wave;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobWave {

    private static DefaultWaveConfig CACHE = null;

    private MobWave() {}

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    /** Loads (or creates) waves.json using JsonFileHelper and caches it. */
    public static DefaultWaveConfig getConfig() {
        if (CACHE != null) return CACHE;

        CACHE = JsonFileHelper.loadOrCreate(
            ConfigConstants.WAVES_CONFIG_FILE,
            DefaultWaveConfig.class
        );

        // Normalize ordering so selection is deterministic
        if (CACHE.waves == null) CACHE.waves = new ArrayList<>();
        CACHE.waves.sort(Comparator.comparingInt(w -> w.strengthRequirement));

        return CACHE;
    }

    /** Convenience: returns the wave list. */
    public static List<Wave> getAll() {
        return getConfig().waves;
    }

    /** Force re-read from disk. */
    public static void reload() {
        CACHE = null;
        getConfig();
    }

    /** Save current cached config back to waves.json. */
    public static void saveAll() {
        DefaultWaveConfig cfg = getConfig();
        File file = new File(JsonFileHelper.getConfigDirectory(), ConfigConstants.WAVES_CONFIG_FILE);
        JsonFileHelper.saveConfig(file, cfg);
    }

    /**
     * Pick the best wave for a given strength:
     * - chooses the highest strengthRequirement <= strength
     * - if none qualifies, returns the lowest wave (or null if empty)
     */
    public static Wave pickWaveForStrength(int strength) {
        List<Wave> waves = getAll();
        if (waves.isEmpty()) return null;

        Wave best = null;
        for (Wave w : waves) {
            if (w == null) continue;
            if (w.strengthRequirement <= strength) best = w;
            else break; // because we sorted
        }

        return (best != null) ? best : waves.get(0);
    }
}
