package com.dragoqc.adaptivehordes;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import com.dragoqc.adaptivehordes.config.*;
import com.dragoqc.adaptivehordes.constants.*;

@Mod(AdaptiveHordes.MODID)
public class AdaptiveHordes {
    public static final String MODID = "adaptivehordes";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    
    // Config instances
    public static DefaultHordeConfig hordeConfig;
    public static DefaultScalingConfig scalingConfig;
    public static DefaultMobConfig mobConfig;

    public AdaptiveHordes(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info(ColorConstants.GREEN + "Adaptive Horde is loading..." + ColorConstants.RESET);
        
        // Initialize config system
        ConfigHelper.initialize();
        
        // Load all configs
        hordeConfig = ConfigHelper.loadOrCreate(ConfigConstants.HORDE_CONFIG, DefaultHordeConfig.class);
        scalingConfig = ConfigHelper.loadOrCreate(ConfigConstants.SCALING_CONFIG, DefaultScalingConfig.class);
        mobConfig = ConfigHelper.loadOrCreate(ConfigConstants.MOB_CONFIG, DefaultMobConfig.class);
        
        LOGGER.info(ColorConstants.CYAN + "All configs loaded successfully!" + ColorConstants.RESET);
        
        if (hordeConfig.enableHordes) {
            LOGGER.info(ColorConstants.GREEN + "Hordes are ENABLED" + ColorConstants.RESET);
        } else {
            LOGGER.info(ColorConstants.YELLOW + "Hordes are DISABLED" + ColorConstants.RESET);
        }
    }
}