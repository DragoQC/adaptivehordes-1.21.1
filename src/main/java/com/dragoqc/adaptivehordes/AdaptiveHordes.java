package com.dragoqc.adaptivehordes;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import com.dragoqc.adaptivehordes.constants.*;
import com.dragoqc.adaptivehordes.jsonfilehelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.models.*;

import net.neoforged.neoforge.common.NeoForge;
import com.dragoqc.adaptivehordes.playerscannerscheduler.PlayerScannerScheduler;

@Mod(AdaptiveHordes.MODID)
public class AdaptiveHordes {
	public static final String MODID = "adaptivehordes";
	public static final Logger LOGGER = LogUtils.getLogger();

	// Config instances
	public static DefaultModConfig modConfig;
	public static DefaultScalingConfig scalingConfig;
	public static DefaultMobConfig mobConfig;

	public AdaptiveHordes(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info(ColorConstants.GREEN + "Adaptive Horde is loading..." + ColorConstants.RESET);

		// Initialize config system
		JsonFileHelper.initializeConfigFolder();

		// Load all configs
		modConfig = JsonFileHelper.loadOrCreate(ConfigConstants.MOD_CONFIG_FILE, DefaultModConfig.class);
		scalingConfig = JsonFileHelper.loadOrCreate(ConfigConstants.SCALING_CONFIG_FILE, DefaultScalingConfig.class);
		mobConfig = JsonFileHelper.loadOrCreate(ConfigConstants.MOB_CONFIG_FILE, DefaultMobConfig.class);

		// Register the scheduler (VERY IMPORTANT)
		NeoForge.EVENT_BUS.register(PlayerScannerScheduler.class);

		LOGGER.info(ColorConstants.CYAN + "All configs loaded successfully!" + ColorConstants.RESET);

		if (modConfig.enableHordes) {
			LOGGER.info(ColorConstants.GREEN + "Hordes are ENABLED" + ColorConstants.RESET);
		} else {
			LOGGER.info(ColorConstants.YELLOW + "Hordes are DISABLED" + ColorConstants.RESET);
		}
	}
}