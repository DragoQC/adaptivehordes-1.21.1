package com.dragoqc.adaptivehordes.playerscanner;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.constants.ColorConstants;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class PlayerScannerScheduler {

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        Level lvl = event.getLevel();
        if (!(lvl instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return; // run once per server tick cycle

        MinecraftServer server = level.getServer();
        if (server == null) return;

        long now = level.getGameTime();
        int minInterval = Math.max(1, AdaptiveHordes.modConfig.liveScanUpdateIntervalTicks);
        int maxStale = Math.max(minInterval, AdaptiveHordes.modConfig.liveScanMaxStaleTicks);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerScanner.updateLivePlayer(player, now, minInterval, maxStale);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerScanner.forceRescan(player, player.serverLevel().getGameTime());
        AdaptiveHordes.LOGGER.info(ColorConstants.GREEN +
            "[PlayerScanner] Live scan initialized for " + player.getName().getString() +
            ColorConstants.RESET);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerScanner.forceRescan(player, player.serverLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerScanner.forceRescan(player, player.serverLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerScanner.removePlayerData(player.getUUID());
    }
}
