package com.dragoqc.adaptivehordes.scheduler;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.scanner.PlayerScanner;
import com.dragoqc.adaptivehordes.constants.ColorConstants;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class PlayerScanScheduler {

    private static boolean scanExecutedThisCycle = false;

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {

        Level lvl = event.getLevel();
        if (!(lvl instanceof ServerLevel level)) return; // Only process server-level worlds


        long time = level.getDayTime(); // total world ticks
        long dayTick = time % 24000L;

        int delay = AdaptiveHordes.modConfig.playerScanDelay;
        int interval = AdaptiveHordes.modConfig.playerScanInterval;

        // Only scan on configured "interval days"
        if ((time % interval) != delay) {
            scanExecutedThisCycle = false;
            return;
        }

        // Ensure only one scan per cycle
        if (scanExecutedThisCycle) return;
        scanExecutedThisCycle = true;

        // Log
        AdaptiveHordes.LOGGER.info(ColorConstants.CYAN +
                "Running scheduled player scan at overworld tick " + dayTick +
                ColorConstants.RESET
        );

        // Get server
        MinecraftServer server = level.getServer();
        if (server == null) return;

        // SCAN ALL PLAYERS IN ALL DIMENSIONS
        PlayerScanner.clearCache();

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						PlayerScanner.scanPlayer(player);
				}

				PlayerScanner.saveAll();

        AdaptiveHordes.LOGGER.info(ColorConstants.GREEN +
                "Player scanning complete." +
                ColorConstants.RESET
        );
    }
}
