package com.dragoqc.adaptivehordes.JsonFileHelper;

import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;

public final class JsonFileHelper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static File configDirectory;

    private JsonFileHelper() { }

    // ------------------------------------------------------------------------
    // INIT
    // ------------------------------------------------------------------------
    public static void initializeConfigFolder() {
        configDirectory = FMLPaths.CONFIGDIR.get()
            .resolve(ConfigConstants.CONFIG_FOLDER)
            .toFile();

        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            LOGGER.error(ColorConstants.RED + "Could not create config directory!" + ColorConstants.RESET);
            return;
        }

        LOGGER.info(ColorConstants.GREEN + "Config folder ready: " + configDirectory.getAbsolutePath() + ColorConstants.RESET);
    }

    public static File getConfigDirectory() {
        return configDirectory;
    }

    // ------------------------------------------------------------------------
    // LOAD / CREATE (REPAIR FIELD NAMES + ADD MISSING FIELDS)
    // ------------------------------------------------------------------------
    public static <T> T loadOrCreate(String fileName, Class<T> configClass) {
        ensureInitialized();

        File file = new File(configDirectory, fileName);
        LOGGER.info(ColorConstants.CYAN + "Loading config: " + fileName + ColorConstants.RESET);

        T defaultConfig = newDefault(configClass);
        JsonObject schema = GSON.toJsonTree(defaultConfig).getAsJsonObject();

        // Create if missing
        if (!file.exists()) {
            LOGGER.warn(ColorConstants.YELLOW + "Config file not found, creating default: " + fileName + ColorConstants.RESET);
            writeJsonObject(file, schema);
            return defaultConfig;
        }

        // Read raw JSON
        JsonObject raw = readJsonObject(file);
        if (raw == null) {
            LOGGER.warn(ColorConstants.YELLOW + "Config file invalid JSON, recreating default: " + fileName + ColorConstants.RESET);
            writeJsonObject(file, schema);
            return defaultConfig;
        }

        // Repair by FIELD NAMES (unknown removed, missing added)
        boolean changed = repairToSchema(raw, schema);
        if (changed) {
            LOGGER.warn(ColorConstants.YELLOW + "Config repaired (unknown fields removed / missing fields added)." + ColorConstants.RESET);
            writeJsonObject(file, raw);
        }

        // Deserialize AFTER repair
        try {
            T config = GSON.fromJson(raw, configClass);
            LOGGER.info(ColorConstants.GREEN + "Successfully loaded: " + file.getName() + ColorConstants.RESET);
            return config;
        } catch (Exception ex) {
            // If types are totally broken, don’t guess: recreate default
            LOGGER.error(ColorConstants.RED + "Config types invalid, recreating default: " + fileName + ColorConstants.RESET, ex);
            writeJsonObject(file, schema);
            return defaultConfig;
        }
    }

    // ------------------------------------------------------------------------
    // REPAIR LOGIC (FIELD NAMES ONLY)
    // ------------------------------------------------------------------------
    private static boolean repairToSchema(JsonObject fileJson, JsonObject schemaJson) {
        boolean changed = false;

        // 1) Remove unknown keys (renamed/old fields)
        Iterator<Map.Entry<String, JsonElement>> it = fileJson.entrySet().iterator();
        while (it.hasNext()) {
            String key = it.next().getKey();
            if (!schemaJson.has(key)) {
                it.remove();
                changed = true;
            }
        }

        // 2) Add missing keys (new fields)
        for (Map.Entry<String, JsonElement> e : schemaJson.entrySet()) {
            String key = e.getKey();
            if (!fileJson.has(key)) {
                fileJson.add(key, e.getValue());
                changed = true;
            }
        }

        return changed;
    }

    // ------------------------------------------------------------------------
    // IO
    // ------------------------------------------------------------------------
    private static JsonObject readJsonObject(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) return null;
            return root.getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeJsonObject(File file, JsonObject json) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
            LOGGER.info(ColorConstants.GREEN + "Saved config: " + file.getName() + ColorConstants.RESET);
        } catch (Exception e) {
            LOGGER.error(ColorConstants.RED + "Error saving config: " + file.getName() + ColorConstants.RESET, e);
        }
    }

    private static <T> T newDefault(Class<T> configClass) {
        try {
            return configClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default config: " + configClass.getName(), e);
        }
    }

    private static void ensureInitialized() {
        if (configDirectory == null) {
            throw new IllegalStateException("JsonFileHelper not initialized. Call initializeConfigFolder() first.");
        }
    }
		/**
	 * Save a config to file
	 */
	public static <T> void saveConfig(File configFile, T config) {
		try (FileWriter writer = new FileWriter(configFile)) {
			GSON.toJson(config, writer);
			LOGGER.info(ColorConstants.GREEN + "Saved config: " + configFile.getName() + ColorConstants.RESET);
		} catch (Exception e) {
			LOGGER.error(ColorConstants.RED + "Error saving config: " + configFile.getName() + ColorConstants.RESET);
			e.printStackTrace();
		}
	}
}
