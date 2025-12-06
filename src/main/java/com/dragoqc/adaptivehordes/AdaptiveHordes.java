package com.dragoqc.adaptivehordes;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import com.dragoqc.adaptivehordes.config.*;
import com.dragoqc.adaptivehordes.constants.*;
import com.dragoqc.adaptivehordes.models.*;

import net.neoforged.neoforge.common.NeoForge;
import com.dragoqc.adaptivehordes.scheduler.PlayerScanScheduler;

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
		ConfigHelper.initialize();

		// Load all configs
		modConfig = ConfigHelper.loadOrCreate(ConfigConstants.MOD_CONFIG_FILE, DefaultModConfig.class);
		scalingConfig = ConfigHelper.loadOrCreate(ConfigConstants.SCALING_CONFIG_FILE, DefaultScalingConfig.class);
		mobConfig = ConfigHelper.loadOrCreate(ConfigConstants.MOB_CONFIG_FILE, DefaultMobConfig.class);

		// Register the scheduler (VERY IMPORTANT)
		NeoForge.EVENT_BUS.register(PlayerScanScheduler.class);

		LOGGER.info(ColorConstants.CYAN + "All configs loaded successfully!" + ColorConstants.RESET);

		if (modConfig.enableHordes) {
			LOGGER.info(ColorConstants.GREEN + "Hordes are ENABLED" + ColorConstants.RESET);
		} else {
			LOGGER.info(ColorConstants.YELLOW + "Hordes are DISABLED" + ColorConstants.RESET);
		}
	}
}