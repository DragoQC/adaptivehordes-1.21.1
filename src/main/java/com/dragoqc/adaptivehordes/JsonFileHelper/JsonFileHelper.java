package com.dragoqc.adaptivehordes.JsonFileHelper;

import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

public final class JsonFileHelper {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
            LOGGER.error("Could not create config directory: {}", configDirectory.getAbsolutePath());
            return;
        }

        LOGGER.info("Config folder ready: {}", configDirectory.getAbsolutePath());
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
        LOGGER.info("Loading config: {}", fileName);

        T defaultConfig = newDefault(configClass);
        JsonObject schema = GSON.toJsonTree(defaultConfig).getAsJsonObject();

        // Create if missing
        if (!file.exists()) {
            LOGGER.warn("Config file not found, creating default: {}", fileName);
            writeJsonObject(file, schema);
            return defaultConfig;
        }

        // Read raw JSON
        JsonObject raw = readJsonObject(file);
        if (raw == null) {
            LOGGER.warn("Config file invalid JSON, backing up and recreating default: {}", fileName);
            backupInvalidConfig(file);
            writeJsonObject(file, schema);
            return defaultConfig;
        }

        // Repair by schema (unknown removed, missing added, invalid types reset per-key)
        RepairStats stats = new RepairStats();
        repairObjectToSchema(raw, schema, stats);
        boolean changed = (stats.removedUnknown + stats.addedMissing + stats.replacedInvalid) > 0;
        if (changed) {
            LOGGER.warn(
                "Config repaired: {} (removedUnknown={}, addedMissing={}, replacedInvalid={})",
                fileName,
                stats.removedUnknown,
                stats.addedMissing,
                stats.replacedInvalid
            );
            writeJsonObject(file, raw);
        }

        // Deserialize AFTER repair
        try {
            T config = GSON.fromJson(raw, configClass);
            LOGGER.info("Successfully loaded: {}", file.getName());
            return config;
        } catch (Exception ex) {
            // Last-resort fallback in memory only. Keep file for manual inspection.
            LOGGER.error("Config could not be deserialized after repair, using defaults in memory: {}", fileName, ex);
            return defaultConfig;
        }
    }

    // ------------------------------------------------------------------------
    // REPAIR LOGIC (RECURSIVE)
    // ------------------------------------------------------------------------
    private static boolean repairObjectToSchema(JsonObject fileJson, JsonObject schemaJson, RepairStats stats) {
        boolean changed = false;

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
            LOGGER.warn("Could not read config JSON: {}", file.getName(), e);
            return null;
        }
    }

    private static void writeJsonObject(File file, JsonObject json) {
        writeJson(file, json);
    }

    private static void writeJson(File file, Object value) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(value, writer);
            LOGGER.info("Saved config: {}", file.getName());
        } catch (Exception e) {
            LOGGER.error("Error saving config: {}", file.getName(), e);
        }
    }

    private static void backupInvalidConfig(File file) {
        if (file == null || !file.exists()) return;
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        File backup = new File(file.getParentFile(), file.getName() + ".invalid-" + timestamp + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Backed up invalid config {} to {}", file.getName(), backup.getName());
        } catch (IOException ex) {
            LOGGER.error("Could not back up invalid config: {}", file.getName(), ex);
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

    public static <T> void saveConfig(File configFile, T config) {
        writeJson(configFile, config);
    }
}
