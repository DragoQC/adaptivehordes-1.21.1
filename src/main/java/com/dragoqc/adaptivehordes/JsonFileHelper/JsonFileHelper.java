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
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

public final class JsonFileHelper {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static File configDirectory;
    private static final Map<String, String> KEY_ALIASES = new LinkedHashMap<>();

    static {
        // Backward-compatible key aliases used by existing user configs.
        KEY_ALIASES.put("mountEntityID", "mountEntityId");
    }

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
    // LOAD / CREATE (REPAIR FIELD NAMES + TYPES + MISSING FIELDS)
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

        // Repair by schema (unknown removed, missing added, invalid types reset per-key)
        RepairStats stats = new RepairStats();
        repairObjectToSchema(raw, schema, stats);
        boolean changed = (stats.removedUnknown + stats.addedMissing + stats.replacedInvalid) > 0;
        if (changed) {
            LOGGER.warn(ColorConstants.YELLOW +
                "Config repaired (removedUnknown=" + stats.removedUnknown +
                ", addedMissing=" + stats.addedMissing +
                ", replacedInvalid=" + stats.replacedInvalid +
                ")." + ColorConstants.RESET
            );
            writeJsonObject(file, raw);
        }

        // Deserialize AFTER repair
        try {
            T config = GSON.fromJson(raw, configClass);
            LOGGER.info(ColorConstants.GREEN + "Successfully loaded: " + file.getName() + ColorConstants.RESET);
            return config;
        } catch (Exception ex) {
            // Last-resort fallback in memory only. Keep file for manual inspection.
            LOGGER.error(ColorConstants.RED + "Config could not be deserialized after repair, using defaults in memory: " + fileName + ColorConstants.RESET, ex);
            return defaultConfig;
        }
    }

    // ------------------------------------------------------------------------
    // REPAIR LOGIC (RECURSIVE)
    // ------------------------------------------------------------------------
    private static boolean repairObjectToSchema(JsonObject fileJson, JsonObject schemaJson, RepairStats stats) {
        boolean changed = false;

        // Migrate known legacy aliases before unknown-key removal.
        changed |= migrateAliases(fileJson, schemaJson);

        // 1) Remove unknown keys (renamed/old fields)
        Iterator<Map.Entry<String, JsonElement>> it = fileJson.entrySet().iterator();
        while (it.hasNext()) {
            String key = it.next().getKey();
            if (!schemaJson.has(key)) {
                it.remove();
                stats.removedUnknown++;
                changed = true;
            }
        }

        // 2) Add missing keys and repair existing keys
        for (Map.Entry<String, JsonElement> e : schemaJson.entrySet()) {
            String key = e.getKey();
            JsonElement schemaValue = e.getValue();

            if (!fileJson.has(key)) {
                fileJson.add(key, schemaValue.deepCopy());
                stats.addedMissing++;
                changed = true;
                continue;
            }

            JsonElement repairedValue = repairElementToSchema(fileJson.get(key), schemaValue, stats);
            if (repairedValue != fileJson.get(key)) {
                fileJson.add(key, repairedValue);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean migrateAliases(JsonObject fileJson, JsonObject schemaJson) {
        boolean changed = false;
        for (Map.Entry<String, String> alias : KEY_ALIASES.entrySet()) {
            String legacyKey = alias.getKey();
            String canonicalKey = alias.getValue();
            if (!fileJson.has(legacyKey)) continue;
            if (!schemaJson.has(canonicalKey)) continue;
            if (fileJson.has(canonicalKey)) continue;

            fileJson.add(canonicalKey, fileJson.get(legacyKey));
            fileJson.remove(legacyKey);
            changed = true;
        }
        return changed;
    }

    private static JsonElement repairElementToSchema(JsonElement fileValue, JsonElement schemaValue, RepairStats stats) {
        if (!isTypeCompatible(fileValue, schemaValue)) {
            stats.replacedInvalid++;
            return schemaValue.deepCopy();
        }

        // Optional fields with null defaults are untyped; keep user value as-is.
        if (schemaValue == null || schemaValue.isJsonNull()) {
            return fileValue;
        }

        if (schemaValue.isJsonObject()) {
            JsonObject fileObj = fileValue.getAsJsonObject();
            JsonObject schemaObj = schemaValue.getAsJsonObject();
            repairObjectToSchema(fileObj, schemaObj, stats);
            return fileObj;
        }

        if (schemaValue.isJsonArray()) {
            JsonArray schemaArr = schemaValue.getAsJsonArray();
            JsonArray fileArr = fileValue.getAsJsonArray();

            // No template element available -> keep user array content.
            if (schemaArr.isEmpty()) return fileArr;

            JsonElement template = schemaArr.get(0);
            for (int i = 0; i < fileArr.size(); i++) {
                JsonElement repaired = repairElementToSchema(fileArr.get(i), template, stats);
                if (repaired != fileArr.get(i)) {
                    fileArr.set(i, repaired);
                }
            }

            return fileArr;
        }

        return fileValue; // Primitive, already type-compatible.
    }

    private static boolean isTypeCompatible(JsonElement fileValue, JsonElement schemaValue) {
        if (schemaValue == null || schemaValue.isJsonNull()) {
            return true; // null default means "accept any type"
        }
        if (fileValue == null || fileValue.isJsonNull()) {
            return false;
        }
        if (schemaValue.isJsonObject()) return fileValue.isJsonObject();
        if (schemaValue.isJsonArray()) return fileValue.isJsonArray();
        if (!schemaValue.isJsonPrimitive()) return false;
        if (!fileValue.isJsonPrimitive()) return false;

        JsonPrimitive schemaPrim = schemaValue.getAsJsonPrimitive();
        JsonPrimitive filePrim = fileValue.getAsJsonPrimitive();

        if (schemaPrim.isBoolean()) return filePrim.isBoolean();
        if (schemaPrim.isNumber()) return filePrim.isNumber();
        if (schemaPrim.isString()) return filePrim.isString();
        return false;
    }

    private static final class RepairStats {
        int removedUnknown = 0;
        int addedMissing = 0;
        int replacedInvalid = 0;
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
