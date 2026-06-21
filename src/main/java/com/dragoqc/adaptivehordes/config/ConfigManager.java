package com.dragoqc.adaptivehordes.config;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.mobwave.MobWave;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.models.DefaultIgnoreConfig;
import com.dragoqc.adaptivehordes.models.DefaultMobConfig;
import com.dragoqc.adaptivehordes.models.DefaultModConfig;
import com.dragoqc.adaptivehordes.models.DefaultScalingConfig;
import com.dragoqc.adaptivehordes.models.DefaultWeaponOverridesConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;

public final class ConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigManager() {}

    public static void initializeAndLoadAll() {
        JsonFileHelper.initializeConfigFolder();
        loadAll();
    }

    public static void loadAll() {
        AdaptiveHordes.modConfig = JsonFileHelper.loadOrCreate(ConfigConstants.MOD_CONFIG_FILE, DefaultModConfig.class);
        AdaptiveHordes.scalingConfig = JsonFileHelper.loadOrCreate(ConfigConstants.SCALING_CONFIG_FILE, DefaultScalingConfig.class);
        AdaptiveHordes.mobConfig = JsonFileHelper.loadOrCreate(ConfigConstants.MOB_CONFIG_FILE, DefaultMobConfig.class);
        AdaptiveHordes.ignoreConfig = JsonFileHelper.loadOrCreate(ConfigConstants.IGNORE_CONFIG_FILE, DefaultIgnoreConfig.class);
        AdaptiveHordes.weaponOverridesConfig = JsonFileHelper.loadOrCreate(ConfigConstants.WEAPON_OVERRIDES_CONFIG_FILE, DefaultWeaponOverridesConfig.class);
        MobWave.reload();
        ConfigHelpWriter.writeHelpFile();
    }

    public static void reloadAll() {
        loadAll();
        LOGGER.info("Adaptive Hordes configs reloaded from disk.");
    }

    public static void reloadWeaponOverrides() {
        AdaptiveHordes.weaponOverridesConfig = JsonFileHelper.loadOrCreate(
            ConfigConstants.WEAPON_OVERRIDES_CONFIG_FILE,
            DefaultWeaponOverridesConfig.class
        );
        LOGGER.info("Adaptive Hordes weapon override config reloaded from disk.");
    }

    public static void saveIgnoreConfig() {
        File file = new File(JsonFileHelper.getConfigDirectory(), ConfigConstants.IGNORE_CONFIG_FILE);
        JsonFileHelper.saveConfig(file, AdaptiveHordes.ignoreConfig);
    }

    public static void saveWeaponOverridesConfig() {
        File file = new File(JsonFileHelper.getConfigDirectory(), ConfigConstants.WEAPON_OVERRIDES_CONFIG_FILE);
        JsonFileHelper.saveConfig(file, AdaptiveHordes.weaponOverridesConfig);
    }
}
