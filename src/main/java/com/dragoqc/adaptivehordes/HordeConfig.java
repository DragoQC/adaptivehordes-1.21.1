package com.dragoqc.adaptivehordes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class HordeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HordeConfig instance;
    
    // Config values
    public boolean enableHordes = true;
    public int hordeCheckInterval = 24000;
    public int baseHordeSize = 10;
    public double gearScoreMultiplier = 1.5;
    public int mobDetectionRange = 64;
    public boolean persistentTargeting = true;
    
    public static HordeConfig load() {
        // Use server config directory (works for both singleplayer and multiplayer)
        File configDir = FMLPaths.CONFIGDIR.get().resolve("adaptivehordes").toFile();
        File configFile = new File(configDir, "server-config.json");
        
        System.out.println("[AdaptiveHordes] Config path: " + configFile.getAbsolutePath());
        
        // Check if folder exists, create if not
        if (!configDir.exists()) {
            configDir.mkdirs();
            System.out.println("[AdaptiveHordes] Created config folder");
        }
        
        // Check if file exists, create if not
        if (!configFile.exists()) {
            instance = new HordeConfig();
            instance.save(configFile);
            System.out.println("[AdaptiveHordes] Created default server config");
        } else {
            // File exists, read it
            try (FileReader reader = new FileReader(configFile)) {
                instance = GSON.fromJson(reader, HordeConfig.class);
                System.out.println("[AdaptiveHordes] Loaded server config");
            } catch (Exception e) {
                System.out.println("[AdaptiveHordes] Error reading config, using defaults");
                e.printStackTrace();
                instance = new HordeConfig();
            }
        }
        
        return instance;
    }
    
    public void save(File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
            System.out.println("[AdaptiveHordes] Config saved");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static HordeConfig get() {
        return instance;
    }
}