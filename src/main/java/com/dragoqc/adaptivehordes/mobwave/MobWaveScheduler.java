package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.commands.AdaptiveHordesCommands;
import com.dragoqc.adaptivehordes.playerscanner.PlayerScanner;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.Wave;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobWaveScheduler {
    private static final String[] WAVE_VICTORY_MESSAGES = new String[] {
        "You survived the night.",
        "You vanquished your enemies.",
        "The horde has fallen.",
        "Night ends with your victory."
    };

    // Track which players we already processed for announce/spawn in the current cycle tick
    private static final Set<UUID> announcedThisCycle = new HashSet<>();
    private static final Set<UUID> spawnedThisCycle = new HashSet<>();
    private static final Map<UUID, SpawnPlan> ACTIVE_SPAWN_PLANS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {

        Level lvl = event.getLevel();
        if (!(lvl instanceof ServerLevel level)) return;

        // Prevent running once per dimension (Nether/End)
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        if (!AdaptiveHordes.modConfig.enableHordes) {
            announcedThisCycle.clear();
            spawnedThisCycle.clear();
            ACTIVE_SPAWN_PLANS.clear();
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

        MinecraftServer server = level.getServer();
        if (server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        // Ensure waves are loaded (will create default waves.json if missing)
        MobWave.getAll();

        processQueuedSpawns(level, server);

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

            PlayerScanResult scan = PlayerScanner.ensurePlayerData(player, level.getGameTime());
            if (scan == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No scan data for " + player.getName().getString() +
                    " (did PlayerScanner run yet?)" +
                    ColorConstants.RESET
                );
                continue;
            }
            if (isIgnored(player)) continue;

            String dimensionId = player.serverLevel().dimension().location().toString();
            Wave wave = pickAssignedWaveForDimension(null, scan.gearScore, dimensionId);
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

            // Intentionally no chat pre-announce to avoid mismatch with spawn-time wave selection.
        }
    }

    private static void runSpawn(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (spawnedThisCycle.contains(id)) continue;
            spawnedThisCycle.add(id);

            PlayerScanResult scan = PlayerScanner.ensurePlayerData(player, level.getGameTime());
            if (scan == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No scan data for " + player.getName().getString() +
                    " (did PlayerScanner run yet?)" +
                    ColorConstants.RESET
                );
                continue;
            }
            if (isIgnored(player)) {
                ACTIVE_SPAWN_PLANS.remove(player.getUUID());
                continue;
            }

            String dimensionId = player.serverLevel().dimension().location().toString();
            Map<String, String> perDimensionWave = assignWavesByDimension(scan.gearScore);
            Wave wave = pickAssignedWaveForDimension(perDimensionWave, scan.gearScore, dimensionId);
            if (wave == null) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] No waves configured (waves list empty)." +
                    ColorConstants.RESET
                );
                return;
            }

            AdaptiveHordes.LOGGER.info(ColorConstants.GREEN +
                "[MobWaveScheduler] Queue wave for " + player.getName().getString() +
                " | gearScore=" + scan.gearScore +
                " | wave=" + wave.name +
                " (req=" + wave.strengthRequirement + ")" +
                " | baseHordeSize=" + AdaptiveHordes.modConfig.baseHordeSize +
                ColorConstants.RESET
            );

            int totalMobs = MobWaveSpawner.estimateWaveMobCount(AdaptiveHordes.modConfig.baseHordeSize, wave, scan);
            SpawnPlan plan = new SpawnPlan(
                player.getUUID(),
                wave.name,
                totalMobs,
                UUID.randomUUID(),
                level.getGameTime(),
                perDimensionWave
            );
            ACTIVE_SPAWN_PLANS.put(player.getUUID(), plan);

            broadcastWaveSpawningMessage(level.getServer(), wave, player, totalMobs);
        }
    }

    private static void processQueuedSpawns(ServerLevel level, MinecraftServer server) {
        if (ACTIVE_SPAWN_PLANS.isEmpty()) return;
        if (!isInSpawnWindow(level)) return;

        long now = level.getGameTime();
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            if (now < plan.nextSpawnGameTime) continue;
            if (plan.remaining <= 0) {
                ACTIVE_SPAWN_PLANS.remove(plan.playerId);
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
            if (player == null || !player.isAlive()) {
                ACTIVE_SPAWN_PLANS.remove(plan.playerId);
                continue;
            }
            if (isIgnored(player)) {
                ACTIVE_SPAWN_PLANS.remove(plan.playerId);
                continue;
            }

            String dimensionId = player.serverLevel().dimension().location().toString();
            PlayerScanResult scan = PlayerScanner.ensurePlayerData(player, now);
            int strength = (scan != null) ? scan.gearScore : 0;
            Wave wave = pickAssignedWaveForDimension(plan.wavesByDimension, strength, dimensionId);
            if (wave == null) {
                plan.nextSpawnGameTime = now + 100;
                continue;
            }

            int maxBatch = Math.max(1, AdaptiveHordes.modConfig.maxMobsPerSpawnBatch);
            int batchSize = Math.min(maxBatch, plan.remaining);
            int spawned = MobWaveSpawner.spawnWaveBatch(player.serverLevel(), player, wave, batchSize, plan.spawnId, scan);
            plan.remaining -= batchSize; // consume attempts so queue always progresses
            plan.waveName = wave.name;

            AdaptiveHordes.LOGGER.info(ColorConstants.CYAN +
                "[MobWaveScheduler] Spawn batch -> player=" + player.getName().getString() +
                " | wave=" + plan.waveName +
                " | batch=" + batchSize +
                " | spawned=" + spawned +
                " | remaining=" + plan.remaining +
                ColorConstants.RESET
            );

            if (plan.remaining <= 0) {
                ACTIVE_SPAWN_PLANS.remove(plan.playerId);
                broadcastWaveCompletedMessage(player);
                continue;
            }

            int delay = computeAdaptiveDelayTicks(level, plan, maxBatch);
            plan.nextSpawnGameTime = now + delay;
        }
    }

    private static int computeAdaptiveDelayTicks(ServerLevel level, SpawnPlan plan, int maxBatch) {
        int lowCount = Math.max(1, AdaptiveHordes.modConfig.lowLoadMobCountReference);
        int highCount = Math.max(lowCount + 1, AdaptiveHordes.modConfig.highLoadMobCountReference);
        int highDelay = Math.max(1, AdaptiveHordes.modConfig.lowLoadSpawnDelayTicks);
        int lowDelay = Math.max(1, AdaptiveHordes.modConfig.highLoadSpawnDelayTicks);

        int total = Math.max(1, plan.totalPlanned);
        int dynamicDelay;
        if (total <= lowCount) {
            dynamicDelay = highDelay;
        } else if (total >= highCount) {
            dynamicDelay = lowDelay;
        } else {
            double t = (double) (total - lowCount) / (double) (highCount - lowCount);
            dynamicDelay = (int) Math.round(highDelay + ((lowDelay - highDelay) * t));
        }

        int ticksLeft = ticksRemainingInWindow(level);
        if (ticksLeft <= 0) return Math.max(1, dynamicDelay);

        int batchesLeft = (int) Math.ceil((double) plan.remaining / (double) Math.max(1, maxBatch));
        int maxDelayToFinishWindow = Math.max(1, ticksLeft / Math.max(1, batchesLeft));
        return Math.max(1, Math.min(dynamicDelay, maxDelayToFinishWindow));
    }

    private static boolean isInSpawnWindow(ServerLevel level) {
        int dayTick = (int) (level.getDayTime() % 24000L);
        int start = clampDayTick(AdaptiveHordes.modConfig.waveSpawnWindowStartTick);
        int end = clampDayTick(AdaptiveHordes.modConfig.waveSpawnWindowEndTick);
        if (start <= end) {
            return dayTick >= start && dayTick <= end;
        }
        return dayTick >= start || dayTick <= end;
    }

    private static int ticksRemainingInWindow(ServerLevel level) {
        int dayTick = (int) (level.getDayTime() % 24000L);
        int start = clampDayTick(AdaptiveHordes.modConfig.waveSpawnWindowStartTick);
        int end = clampDayTick(AdaptiveHordes.modConfig.waveSpawnWindowEndTick);
        if (!isInSpawnWindow(level)) return 0;

        if (start <= end) {
            return Math.max(0, end - dayTick);
        }
        if (dayTick >= start) {
            return (24000 - dayTick) + end;
        }
        return Math.max(0, end - dayTick);
    }

    private static int clampDayTick(int tick) {
        if (tick < 0) return 0;
        if (tick > 23999) return 23999;
        return tick;
    }

    private static boolean isIgnored(ServerPlayer player) {
        if (player == null || AdaptiveHordes.ignoreConfig == null || AdaptiveHordes.ignoreConfig.ignoredPlayerUuids == null) {
            return false;
        }
        return AdaptiveHordes.ignoreConfig.ignoredPlayerUuids.contains(player.getUUID().toString());
    }

    private static Map<String, String> assignWavesByDimension(int strength) {
        Map<String, String> assigned = new HashMap<>();
        for (Wave candidate : MobWave.getAll()) {
            if (candidate == null) continue;
            if (candidate.dimensions == null || candidate.dimensions.isEmpty()) {
                Wave allDims = MobWave.pickWaveForStrength(strength, null);
                if (allDims != null && allDims.name != null) {
                    assigned.put("*", allDims.name);
                }
                continue;
            }
            for (String dim : candidate.dimensions) {
                if (dim == null || dim.isBlank() || assigned.containsKey(dim)) continue;
                Wave wave = MobWave.pickWaveForStrength(strength, dim);
                if (wave != null && wave.name != null) {
                    assigned.put(dim, wave.name);
                }
            }
        }
        return assigned;
    }

    private static Wave pickAssignedWaveForDimension(Map<String, String> assigned, int strength, String dimensionId) {
        if (assigned != null && dimensionId != null) {
            String waveName = assigned.get(dimensionId);
            if (waveName == null) {
                waveName = assigned.get("*");
            }
            if (waveName != null) {
                Wave w = MobWave.findWaveByName(waveName);
                if (w != null) return w;
            }
        }
        return MobWave.pickWaveForStrength(strength, dimensionId);
    }

    private static void broadcastWaveSpawningMessage(MinecraftServer server, Wave wave, ServerPlayer targetPlayer, int totalMobs) {
        if (server == null || wave == null || targetPlayer == null) return;
        String template = wave.waveSpawningMessage;
        if (template == null || template.isBlank()) {
            template = "Wave {wave} is spawning for {player} ({count} mobs planned)";
        }

        String message = template
            .replace("{wave}", wave.name)
            .replace("{player}", targetPlayer.getName().getString())
            .replace("{count}", String.valueOf(totalMobs));
        Component component = Component.literal(message).withStyle(ChatFormatting.RED);

        if (targetPlayer.getPersistentData().getBoolean(AdaptiveHordesCommands.TAG_DISABLE_WAVE_ANNOUNCEMENTS)) return;
        targetPlayer.displayClientMessage(component, true); // action bar
    }

    private static void broadcastWaveCompletedMessage(ServerPlayer targetPlayer) {
        if (targetPlayer == null) return;
        if (targetPlayer.getPersistentData().getBoolean(AdaptiveHordesCommands.TAG_DISABLE_WAVE_ANNOUNCEMENTS)) return;
        int idx = targetPlayer.getRandom().nextInt(WAVE_VICTORY_MESSAGES.length);
        Component component = Component.literal(WAVE_VICTORY_MESSAGES[idx]).withStyle(ChatFormatting.DARK_GREEN);
        targetPlayer.displayClientMessage(component, true); // action bar
    }

    private static final class SpawnPlan {
        private final UUID playerId;
        private String waveName;
        private final int totalPlanned;
        private final UUID spawnId;
        private final Map<String, String> wavesByDimension;
        private int remaining;
        private long nextSpawnGameTime;

        private SpawnPlan(UUID playerId, String waveName, int totalPlanned, UUID spawnId, long now, Map<String, String> wavesByDimension) {
            this.playerId = playerId;
            this.waveName = waveName;
            this.totalPlanned = Math.max(1, totalPlanned);
            this.remaining = this.totalPlanned;
            this.spawnId = spawnId;
            this.nextSpawnGameTime = now;
            this.wavesByDimension = (wavesByDimension == null) ? new HashMap<>() : new HashMap<>(wavesByDimension);
        }
    }
}
