package com.dragoqc.adaptivehordes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.constants.ColorConstants;

public class ConfigHelper {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static File configDirectory;

	/**
	 * Initialize the config directory
	 */
	public static void initialize() {
		configDirectory = FMLPaths.CONFIGDIR.get()
				.resolve(ConfigConstants.CONFIG_FOLDER)
				.toFile();

		if (!configDirectory.exists()) {
			if (configDirectory.mkdirs()) {
				LOGGER.info(ColorConstants.GREEN + "Created config directory: " + 
					configDirectory.getAbsolutePath() + ColorConstants.RESET);
			} else {
				LOGGER.error(ColorConstants.RED + "Failed to create config directory!" + 
					ColorConstants.RESET);
			}
		} else {
			LOGGER.info(ColorConstants.CYAN + "Config directory already exists" + 
				ColorConstants.RESET);
		}
	}

	/**
	 * Load a config file, creating it with defaults if it doesn't exist
	 * 
	 * @param fileName    The name of the config file
	 * @param configClass The class type of the config
	 * @param <T>         The config type
	 * @return The loaded or default config instance
	 */
	public static <T> T loadOrCreate(String fileName, Class<T> configClass) {
		File configFile = new File(configDirectory, fileName);

		LOGGER.info(ColorConstants.CYAN + "Loading config: " + fileName + ColorConstants.RESET);

		if (!configFile.exists()) {
			LOGGER.warn(ColorConstants.YELLOW + "Config file not found, creating default: " + 
				fileName + ColorConstants.RESET);
			return createDefaultConfig(configFile, configClass);
		}

		return loadConfig(configFile, configClass);
	}

	/**
	 * Load an existing config file
	 */
	private static <T> T loadConfig(File configFile, Class<T> configClass) {
		try (FileReader reader = new FileReader(configFile)) {
			T config = GSON.fromJson(reader, configClass);
			LOGGER.info(ColorConstants.GREEN + "Successfully loaded: " + 
				configFile.getName() + ColorConstants.RESET);
			return config;
		} catch (Exception e) {
			LOGGER.error(ColorConstants.RED + "Error loading config: " + 
				configFile.getName() + ColorConstants.RESET);
			e.printStackTrace();
			LOGGER.warn(ColorConstants.YELLOW + "Falling back to default config" + 
				ColorConstants.RESET);
			return createDefaultConfig(configFile, configClass);
		}
	}

	/**
	 * Create a default config file
	 */
	private static <T> T createDefaultConfig(File configFile, Class<T> configClass) {
		try {
			T defaultConfig = configClass.getDeclaredConstructor().newInstance();
			saveConfig(configFile, defaultConfig);
			LOGGER.info(ColorConstants.GREEN + "Created default config: " + 
				configFile.getName() + ColorConstants.RESET);
			return defaultConfig;
		} catch (Exception e) {
			LOGGER.error(ColorConstants.RED + "Error creating default config: " + 
				configFile.getName() + ColorConstants.RESET);
			e.printStackTrace();
			throw new RuntimeException("Failed to create default config", e);
		}
	}

	/**
	 * Save a config to file
	 */
	public static <T> void saveConfig(File configFile, T config) {
		try (FileWriter writer = new FileWriter(configFile)) {
			GSON.toJson(config, writer);
			LOGGER.info(ColorConstants.GREEN + "Saved config: " + 
				configFile.getName() + ColorConstants.RESET);
		} catch (Exception e) {
			LOGGER.error(ColorConstants.RED + "Error saving config: " + 
				configFile.getName() + ColorConstants.RESET);
			e.printStackTrace();
		}
	}

	/**
	 * Get the config directory
	 */
	public static File getConfigDirectory() {
		return configDirectory;
	}

	/**
	 * Check if a config file exists
	 */
	public static boolean configExists(String fileName) {
		File configFile = new File(configDirectory, fileName);
		boolean exists = configFile.exists();
		
		if (exists) {
			LOGGER.info(ColorConstants.CYAN + "Config file exists: " + fileName + 
				ColorConstants.RESET);
		} else {
			LOGGER.warn(ColorConstants.YELLOW + "Config file not found: " + fileName + 
				ColorConstants.RESET);
		}
		
		return exists;
	}
}