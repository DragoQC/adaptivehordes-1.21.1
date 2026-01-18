package com.dragoqc.adaptivehordes.MobWaveScheduler;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.MobWave.MobWave;
import com.dragoqc.adaptivehordes.PlayerScanner.PlayerScanner;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.Wave;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MobWaveScheduler {

    // Track which players we already processed for announce/spawn in the current cycle tick
    private static final Set<UUID> announcedThisCycle = new HashSet<>();
    private static final Set<UUID> spawnedThisCycle = new HashSet<>();

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {

        Level lvl = event.getLevel();
        if (!(lvl instanceof ServerLevel level)) return;

        // Prevent running once per dimension (Nether/End)
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        if (!AdaptiveHordes.modConfig.enableHordes) {
            announcedThisCycle.clear();
            spawnedThisCycle.clear();
            return;
        }

        long time = level.getDayTime();

        int interval = AdaptiveHordes.modConfig.waveCheckInterval;
        int announceDelay = AdaptiveHordes.modConfig.waveAnnounceDelay;
        int spawnDelay = AdaptiveHordes.modConfig.waveSpawnDelay;

        boolean isAnnounceTick = (time % interval) == announceDelay;
        boolean isSpawnTick = (time % interval) == spawnDelay;

        // Reset per-cycle state when not on the tick
        if (!isAnnounceTick) announcedThisCycle.clear();
        if (!isSpawnTick) spawnedThisCycle.clear();

        // Nothing to do this tick
        if (!isAnnounceTick && !isSpawnTick) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        // Ensure waves are loaded (will create default waves.json if missing)
        MobWave.getAll();

        if (isAnnounceTick) {
            runAnnounce(level, players);
        }

        if (isSpawnTick) {
            runSpawn(level, players);
        }
    }

    private static void runAnnounce(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (announcedThisCycle.contains(id)) continue;
            announcedThisCycle.add(id);

            PlayerScanResult scan = PlayerScanner.getPlayerData(id);
            if (scan == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No scan data for " + player.getName().getString() +
                    " (did PlayerScanner run yet?)" +
                    ColorConstants.RESET
                );
                continue;
            }

            Wave wave = MobWave.pickWaveForStrength(scan.gearScore);
            if (wave == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No waves configured (waves list empty)." +
                    ColorConstants.RESET
                );
                return;
            }

            AdaptiveHordes.LOGGER.info(ColorConstants.CYAN +
                "[MobWaveScheduler] Announce to " + player.getName().getString() +
                " | gearScore=" + scan.gearScore +
                " | wave=" + wave.name +
                " (req=" + wave.strengthRequirement + ")" +
                ColorConstants.RESET
            );

            // Placeholder: notify player (you can change to titles/bossbars later)
            player.sendSystemMessage(Component.literal(
                "A horde is coming... (Wave: " + wave.name + ", req: " + wave.strengthRequirement + ")"
            ));
        }
    }

    private static void runSpawn(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (spawnedThisCycle.contains(id)) continue;
            spawnedThisCycle.add(id);

            PlayerScanResult scan = PlayerScanner.getPlayerData(id);
            if (scan == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No scan data for " + player.getName().getString() +
                    " (did PlayerScanner run yet?)" +
                    ColorConstants.RESET
                );
                continue;
            }

            Wave wave = MobWave.pickWaveForStrength(scan.gearScore);
            if (wave == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No waves configured (waves list empty)." +
                    ColorConstants.RESET
                );
                return;
            }

            AdaptiveHordes.LOGGER.info(ColorConstants.GREEN +
                "[MobWaveScheduler] Spawn for " + player.getName().getString() +
                " | gearScore=" + scan.gearScore +
                " | wave=" + wave.name +
                " (req=" + wave.strengthRequirement + ")" +
                " | baseHordeSize=" + AdaptiveHordes.modConfig.baseHordeSize +
                ColorConstants.RESET
            );

            // Placeholder for your future spawner:
            // WaveSpawner.spawnWave(level, player, wave, AdaptiveHordes.modConfig.baseHordeSize);

            player.sendSystemMessage(Component.literal(
                "Spawning your wave now: " + wave.name
            ));
        }
    }
}
