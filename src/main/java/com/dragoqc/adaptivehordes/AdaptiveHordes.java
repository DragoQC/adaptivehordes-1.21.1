package com.dragoqc.adaptivehordes;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import com.dragoqc.adaptivehordes.commands.AdaptiveHordesCommands;
import com.dragoqc.adaptivehordes.config.ConfigManager;
import com.dragoqc.adaptivehordes.constants.*;
import com.dragoqc.adaptivehordes.models.*;
import com.dragoqc.adaptivehordes.mobwave.MobWaveDropsHandler;
import com.dragoqc.adaptivehordes.mobwave.MobWaveRuntimeController;
import com.dragoqc.adaptivehordes.mobwave.MobWaveScheduler;
import com.dragoqc.adaptivehordes.playerscanner.PlayerScannerScheduler;

import net.neoforged.neoforge.common.NeoForge;

@Mod(AdaptiveHordes.MODID)
public class AdaptiveHordes {
	public static final String MODID = "adaptivehordes";
	public static final Logger LOGGER = LogUtils.getLogger();

	// Config instances
	public static DefaultModConfig modConfig;
	public static DefaultScalingConfig scalingConfig;
	public static DefaultMobConfig mobConfig;
	public static DefaultIgnoreConfig ignoreConfig;
	public static DefaultWeaponOverridesConfig weaponOverridesConfig;

	public AdaptiveHordes(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info(ColorConstants.GREEN + "Adaptive Horde is loading..." + ColorConstants.RESET);

		// Initialize config system and load all JSON-backed configs.
		ConfigManager.initializeAndLoadAll();

		// Register the scheduler (VERY IMPORTANT)
		NeoForge.EVENT_BUS.register(PlayerScannerScheduler.class);
		NeoForge.EVENT_BUS.register(MobWaveScheduler.class);
		NeoForge.EVENT_BUS.register(MobWaveDropsHandler.class);
		NeoForge.EVENT_BUS.register(MobWaveRuntimeController.class);
		NeoForge.EVENT_BUS.addListener(AdaptiveHordesCommands::onRegisterCommands);

		LOGGER.info(ColorConstants.CYAN + "All configs loaded successfully!" + ColorConstants.RESET);

		if (modConfig.enableHordes) {
			LOGGER.info(ColorConstants.GREEN + "Hordes are ENABLED" + ColorConstants.RESET);
		} else {
			LOGGER.info(ColorConstants.YELLOW + "Hordes are DISABLED" + ColorConstants.RESET);
		}
	}
}
