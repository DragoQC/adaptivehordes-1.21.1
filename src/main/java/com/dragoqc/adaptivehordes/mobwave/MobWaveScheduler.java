package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.commands.AdaptiveHordesCommands;
import com.dragoqc.adaptivehordes.playerscanner.PlayerScanner;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.Wave;
import com.dragoqc.adaptivehordes.network.WaveHudUpdatePayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobWaveScheduler {
    private static final int MAX_BLOCKED_SPAWN_BATCHES = 8;

    private static final String[] WAVE_VICTORY_MESSAGES = new String[] {
        "You survived the night.",
        "You vanquished your enemies.",
        "The horde has fallen.",
        "Night ends with your victory."
    };

    // Track per-player cycle queueing so one wave plan is created per cycle.
    private static final Map<UUID, Long> queuedCycleByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, SpawnPlan> ACTIVE_SPAWN_PLANS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {

        Level lvl = event.getLevel();
        if (!(lvl instanceof ServerLevel level)) return;

        // Prevent running once per dimension (Nether/End)
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        if (!AdaptiveHordes.modConfig.enableHordes) {
            hideAllWaveHuds(level.getServer());
            queuedCycleByPlayer.clear();
            ACTIVE_SPAWN_PLANS.clear();
            return;
        }

        long time = level.getDayTime();
        int interval = AdaptiveHordes.modConfig.waveCheckInterval;
        long cycle = Math.floorDiv(time, Math.max(1, interval));

        MinecraftServer server = level.getServer();
        if (server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        // Ensure waves are loaded (will create default waves.json if missing)
        MobWave.getAll();

        processQueuedSpawns(level, server);

        if (isInSpawnWindow(level)) {
            runSpawn(level, players, cycle);
        }
    }

    private static void runSpawn(ServerLevel level, List<ServerPlayer> players, long cycle) {
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            Long lastQueuedCycle = queuedCycleByPlayer.get(id);
            if (lastQueuedCycle != null && lastQueuedCycle.longValue() == cycle) continue;
            if (ACTIVE_SPAWN_PLANS.containsKey(id)) continue;

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
                removePlan(player.getUUID());
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

            startWave(level.getServer(), player, wave, scan, perDimensionWave, false, true);
            queuedCycleByPlayer.put(player.getUUID(), cycle);
        }
    }

    public static int startWave(MinecraftServer server, ServerPlayer player, Wave wave, PlayerScanResult scan, boolean ignoreSpawnWindow) {
        Map<String, String> wavesByDimension = new HashMap<>();
        if (wave != null && wave.name != null && !wave.name.isBlank()) {
            wavesByDimension.put("*", wave.name);
        }
        return startWave(server, player, wave, scan, wavesByDimension, ignoreSpawnWindow, true);
    }

    private static int startWave(
        MinecraftServer server,
        ServerPlayer player,
        Wave wave,
        PlayerScanResult scan,
        Map<String, String> wavesByDimension,
        boolean ignoreSpawnWindow,
        boolean announce
    ) {
        if (server == null || player == null || wave == null) return 0;

        int totalMobs = MobWaveSpawner.estimateWaveMobCount(AdaptiveHordes.modConfig.baseHordeSize, wave, scan);
        long now = (server.overworld() == null) ? player.serverLevel().getGameTime() : server.overworld().getGameTime();
        SpawnPlan plan = new SpawnPlan(
            player.getUUID(),
            wave.name,
            wave.displayName,
            totalMobs,
            UUID.randomUUID(),
            now,
            wavesByDimension,
            ignoreSpawnWindow
        );
        removePlan(player.getUUID(), server, true);
        ACTIVE_SPAWN_PLANS.put(player.getUUID(), plan);
        sendWaveHudUpdate(server, plan, true);

        if (announce) {
            broadcastWaveSpawningMessage(server, wave, player, totalMobs);
        }
        return totalMobs;
    }

    private static void processQueuedSpawns(ServerLevel level, MinecraftServer server) {
        if (ACTIVE_SPAWN_PLANS.isEmpty()) return;

        long now = level.getGameTime();
        boolean spawnWindowOpen = isInSpawnWindow(level);
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            if (now < plan.nextSpawnGameTime) continue;
            if (plan.remaining <= 0) {
                double healthForBar = updateBossBar(server, plan, true);
                if (healthForBar <= 0.0D || isWaveFinished(server, plan, now)) {
                    finishPlan(server, plan, null);
                } else {
                    plan.nextSpawnGameTime = now + 20L;
                }
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
            if (player == null || !player.isAlive()) {
                removePlan(plan.playerId, server, false);
                continue;
            }
            if (isIgnored(player)) {
                removePlan(plan.playerId, server, false);
                continue;
            }

            if (!plan.ignoreSpawnWindow && !spawnWindowOpen) {
                if (plan.bossBar != null) {
                    updateBossBar(server, plan, true);
                }
                continue;
            }

            PlayerScanResult scan = PlayerScanner.ensurePlayerData(player, now);
            String dimensionId = player.serverLevel().dimension().location().toString();
            int strength = (scan != null) ? scan.gearScore : 0;
            Wave wave = pickAssignedWaveForDimension(plan.wavesByDimension, strength, dimensionId);
            if (wave == null) {
                plan.nextSpawnGameTime = now + 100;
                continue;
            }

            int maxBatch = Math.max(1, AdaptiveHordes.modConfig.maxMobsPerSpawnBatch);
            int liveCap = Math.max(1, AdaptiveHordes.modConfig.maxLiveMobsPerPlayer);
            int alive = countAliveMobsForSpawnId(server, plan.spawnId, now, plan);
            int liveCapacity = Math.max(0, liveCap - alive);
            if (liveCapacity <= 0) {
                plan.blockedSpawnBatches = 0;
                updateBossBar(server, plan, false);
                sendWaveHudUpdate(server, plan, false);
                plan.nextSpawnGameTime = now + Math.max(20L, computeAdaptiveDelayTicks(level, plan, maxBatch));
                continue;
            }

            int batchSize = Math.min(Math.min(maxBatch, liveCapacity), plan.remaining);
            int spawned = MobWaveSpawner.spawnWaveBatch(player.serverLevel(), player, wave, batchSize, plan.spawnId, scan);
            if (spawned > 0 && alive <= 0) {
                plan.activeBarHealthUnits = 0.0D;
            }
            plan.spawnedSuccessful += Math.max(0, spawned);
            plan.activeBarHealthUnits += Math.max(0, spawned);
            plan.remaining = Math.max(0, plan.remaining - Math.max(0, spawned));
            plan.lastAliveCount = Math.max(0, alive + Math.max(0, spawned));
            plan.lastAliveSampleGameTime = now;
            plan.lastHealthSampleGameTime = Long.MIN_VALUE;
            plan.waveName = wave.name;
            plan.waveDisplayName = (wave.displayName == null || wave.displayName.isBlank()) ? wave.name : wave.displayName;

            if (spawned > 0) {
                plan.blockedSpawnBatches = 0;
            } else {
                plan.blockedSpawnBatches++;
            }

            if (spawned > 0) {
                attachBossBar(plan, player);
                updateBossBar(server, plan, true);
                sendWaveHudUpdate(server, plan, true);
            }

            AdaptiveHordes.LOGGER.info(ColorConstants.CYAN +
                "[MobWaveScheduler] Spawn batch -> player=" + player.getName().getString() +
                " | wave=" + plan.waveName +
                " | batch=" + batchSize +
                " | spawned=" + spawned +
                " | alive=" + alive +
                " | liveCap=" + liveCap +
                " | remaining=" + plan.remaining +
                ColorConstants.RESET
            );

            if (spawned <= 0 && plan.blockedSpawnBatches >= MAX_BLOCKED_SPAWN_BATCHES) {
                AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                    "[MobWaveScheduler] Ending blocked wave queue for " + player.getName().getString() +
                    " | wave=" + plan.waveName +
                    " | failedBatches=" + plan.blockedSpawnBatches +
                    " | remaining=" + plan.remaining +
                    ColorConstants.RESET
                );
                plan.remaining = 0;
            }

            if (plan.remaining <= 0) {
                if (isWaveFinished(server, plan, now)) {
                    finishPlan(server, plan, player);
                } else {
                    sendWaveHudUpdate(server, plan, true);
                    plan.nextSpawnGameTime = now + 20L;
                }
                continue;
            }

            double healthForBar = updateBossBar(server, plan, false);
            sendWaveHudUpdate(server, plan, false);
            if (plan.remaining <= 0 && healthForBar <= 0.0D) {
                finishPlan(server, plan, player);
                continue;
            }
            int delay = computeAdaptiveDelayTicks(level, plan, maxBatch);
            if (spawned <= 0) {
                delay = Math.max(delay, Math.min(200, AdaptiveHordes.modConfig.loadSpawnDelayTicks));
            }
            plan.nextSpawnGameTime = now + delay;
        }
    }

    private static int computeAdaptiveDelayTicks(ServerLevel level, SpawnPlan plan, int maxBatch) {
        int baseDelay = Math.max(1, AdaptiveHordes.modConfig.loadSpawnDelayTicks);
        int total = Math.max(1, plan.totalPlanned);
        int reference = Math.max(1, AdaptiveHordes.modConfig.baseHordeSize * 5); // default 10 -> 50
        int dynamicDelay = (int) Math.round((double) baseDelay * ((double) reference / (double) total));
        dynamicDelay = Math.max(1, Math.min(baseDelay, dynamicDelay));

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
            .replace("{wave}", (wave.displayName == null || wave.displayName.isBlank()) ? wave.name : wave.displayName)
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

    public static List<String> getActivePlanDebugLines(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        long now = (server == null || server.overworld() == null) ? 0L : server.overworld().getGameTime();
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            String playerLabel = plan.playerId.toString();
            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(plan.playerId);
                if (p != null) playerLabel = p.getName().getString();
            }
            long nextIn = Math.max(0L, plan.nextSpawnGameTime - now);
            int alive = countAliveMobsForSpawnId(server, plan.spawnId, now, plan);
            lines.add(
                "player=" + playerLabel +
                " wave=" + plan.waveName +
                " remaining=" + plan.remaining + "/" + plan.totalPlanned +
                " alive=" + alive +
                " nextIn=" + nextIn + "t"
            );
        }
        return lines;
    }

    public static void refreshBossBarForSpawnId(MinecraftServer server, String spawnIdRaw) {
        if (server == null || spawnIdRaw == null || spawnIdRaw.isBlank()) return;
        UUID spawnId;
        try {
            spawnId = UUID.fromString(spawnIdRaw);
        } catch (Exception ex) {
            return;
        }

        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            if (!spawnId.equals(plan.spawnId)) continue;
            double healthForBar = updateBossBar(server, plan, true);
            sendWaveHudUpdate(server, plan, true);
            if (plan.remaining <= 0 && healthForBar <= 0.0D) {
                finishPlan(server, plan, null);
            }
            break;
        }
    }

    @SubscribeEvent
    public static void onWaveMobDamaged(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        if (!MobWaveSpawner.isWaveSpawnedMob(entity)) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        String spawnId = entity.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_SPAWN_ID);
        refreshBossBarForSpawnId(serverLevel.getServer(), spawnId);
    }

    public static void refreshBossBarVisibilityForPlayer(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            if (!playerId.equals(plan.playerId)) continue;
            if (player != null && !isBossBarHidden(player) && plan.bossBar == null) {
                WaveHealthSample sample = forceWaveHealthSample(server, plan, (server.overworld() == null) ? 0L : server.overworld().getGameTime());
                if (sample.aliveCount > 0) {
                    attachBossBar(plan, player);
                }
            }
            double healthForBar = updateBossBar(server, plan, true);
            sendWaveHudUpdate(server, plan, true);
            if (plan.remaining <= 0 && healthForBar <= 0.0D) {
                finishPlan(server, plan, player);
            }
            break;
        }
    }

    public static int clearActivePlans(MinecraftServer server, String waveNameFilter) {
        int removed = 0;
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            if (plan == null) continue;
            if (waveNameFilter != null && !waveNameFilter.isBlank() && !waveNameFilter.equals(plan.waveName)) continue;
            removePlan(plan.playerId, server, false);
            removed++;
        }
        return removed;
    }

    private static void attachBossBar(SpawnPlan plan, ServerPlayer player) {
        if (plan == null || player == null) return;
        if (isBossBarHidden(player)) {
            if (plan.bossBar != null) {
                plan.bossBar.removeAllPlayers();
            }
            return;
        }
        if (plan.bossBar == null) {
            String display = (plan.waveDisplayName == null || plan.waveDisplayName.isBlank()) ? plan.waveName : plan.waveDisplayName;
            plan.bossBar = new ServerBossEvent(
                Component.literal("Wave " + display + " | Active Enemies: 0"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
            );
            plan.bossBar.setDarkenScreen(false);
            plan.bossBar.setPlayBossMusic(false);
            plan.bossBar.setCreateWorldFog(false);
            plan.bossBar.setProgress(0.0f);
        }
        plan.bossBar.removeAllPlayers();
        plan.bossBar.addPlayer(player);
    }

    private static double updateBossBar(MinecraftServer server, SpawnPlan plan, boolean forceSample) {
        if (server == null || plan == null) return 0.0D;
        long now = (server.overworld() == null) ? 0L : server.overworld().getGameTime();
        WaveHealthSample sample = forceSample ? forceWaveHealthSample(server, plan, now) : getCachedWaveHealthSample(server, plan, now);
        if (plan.bossBar == null) return sample.remainingHealthUnits;

        ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
        if (player != null && isBossBarHidden(player)) {
            plan.bossBar.removeAllPlayers();
            return sample.remainingHealthUnits;
        }
        if (sample.aliveCount <= 0) {
            plan.activeBarHealthUnits = 0.0D;
            plan.bossBar.setProgress(0.0F);
            plan.bossBar.removeAllPlayers();
            return 0.0D;
        }

        double maxHealthUnits = Math.max(0.0D, plan.activeBarHealthUnits);
        float progress = maxHealthUnits <= 0.0D ? 0.0F : (float) (sample.remainingHealthUnits / maxHealthUnits);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        plan.bossBar.setProgress(progress);
        String display = (plan.waveDisplayName == null || plan.waveDisplayName.isBlank()) ? plan.waveName : plan.waveDisplayName;
        if (display == null || display.isBlank()) {
            display = "Wave";
        }
        plan.bossBar.setName(Component.literal(
            "Wave " + display + " | Active Enemies: " + sample.aliveCount
        ));
        if (player != null) {
            plan.bossBar.removeAllPlayers();
            plan.bossBar.addPlayer(player);
        }
        return sample.remainingHealthUnits;
    }

    private static boolean isBossBarHidden(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(AdaptiveHordesCommands.TAG_DISABLE_WAVE_BOSSBAR);
    }

    private static int forceAliveSample(MinecraftServer server, SpawnPlan plan, long now) {
        return forceWaveHealthSample(server, plan, now).aliveCount;
    }

    private static WaveHealthSample getCachedWaveHealthSample(MinecraftServer server, SpawnPlan plan, long now) {
        if (server == null || plan == null) return WaveHealthSample.EMPTY;
        if ((now - plan.lastHealthSampleGameTime) < 10L && plan.lastHealthSample != null) {
            return plan.lastHealthSample;
        }
        return forceWaveHealthSample(server, plan, now);
    }

    private static WaveHealthSample forceWaveHealthSample(MinecraftServer server, SpawnPlan plan, long now) {
        int alive = 0;
        double remainingHealthUnits = 0.0D;
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof LivingEntity living)) continue;
                    if (!MobWaveSpawner.isWaveSpawnedMob(living)) continue;
                    String sid = living.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_SPAWN_ID);
                    if (!plan.spawnId.toString().equals(sid)) continue;
                    if (!living.isAlive()) continue;
                    alive++;
                    double maxHealth = Math.max(1.0D, living.getMaxHealth());
                    double health = Math.max(0.0D, living.getHealth());
                    remainingHealthUnits += Math.max(0.0D, Math.min(1.0D, health / maxHealth));
                }
            }
        }
        WaveHealthSample sample = new WaveHealthSample(alive, remainingHealthUnits);
        plan.lastAliveCount = alive;
        plan.lastAliveSampleGameTime = now;
        plan.lastHealthSample = sample;
        plan.lastHealthSampleGameTime = now;
        return sample;
    }

    private static int countAliveMobsForSpawnId(MinecraftServer server, UUID spawnId, long now, SpawnPlan plan) {
        if (server == null || spawnId == null || plan == null) return 0;
        if ((now - plan.lastAliveSampleGameTime) < 20L && plan.lastAliveCount >= 0) {
            return plan.lastAliveCount;
        }
        return forceAliveSample(server, plan, now);
    }

    private static boolean isWaveFinished(MinecraftServer server, SpawnPlan plan, long now) {
        int alive = countAliveMobsForSpawnId(server, plan.spawnId, now, plan);
        return plan.remaining <= 0 && alive <= 0;
    }

    private static void finishPlan(MinecraftServer server, SpawnPlan plan, ServerPlayer player) {
        if (server != null) {
            updateBossBar(server, plan, true);
            sendWaveHudInactive(server, plan);
        }
        if (plan != null && plan.bossBar != null) {
            plan.bossBar.setProgress(0.0f);
            plan.bossBar.removeAllPlayers();
        }
        ACTIVE_SPAWN_PLANS.remove(plan.playerId);
        if (player == null && server != null) {
            player = server.getPlayerList().getPlayer(plan.playerId);
        }
        if (player != null && plan != null && plan.spawnedSuccessful > 0) {
            broadcastWaveCompletedMessage(player);
        }
        MobWaveRuntimeController.clearCallForHelpCooldownsIfNoActiveHorde(server);
    }

    private static void removePlan(UUID playerId) {
        removePlan(playerId, null, false);
    }

    private static void removePlan(UUID playerId, MinecraftServer server, boolean discardMobs) {
        if (playerId == null) return;
        SpawnPlan removed = ACTIVE_SPAWN_PLANS.remove(playerId);
        if (removed == null) return;
        if (server != null) {
            sendWaveHudInactive(server, removed);
        }
        if (removed.bossBar != null) {
            removed.bossBar.removeAllPlayers();
        }
        if (discardMobs && server != null) {
            discardMobsForSpawnId(server, removed.spawnId);
        }
        MobWaveRuntimeController.clearCallForHelpCooldownsIfNoActiveHorde(server);
    }

    private static void discardMobsForSpawnId(MinecraftServer server, UUID spawnId) {
        if (server == null || spawnId == null) return;
        String spawnIdRaw = spawnId.toString();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (!MobWaveSpawner.isWaveSpawnedMob(living)) continue;
                String sid = living.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_SPAWN_ID);
                if (!spawnIdRaw.equals(sid)) continue;
                living.discard();
            }
        }
    }

    private static void sendWaveHudUpdate(MinecraftServer server, SpawnPlan plan, boolean forceSample) {
        if (server == null || plan == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
        if (player == null) return;
        long now = (server.overworld() == null) ? 0L : server.overworld().getGameTime();
        WaveHealthSample sample = forceSample ? forceWaveHealthSample(server, plan, now) : getCachedWaveHealthSample(server, plan, now);
        String display = (plan.waveDisplayName == null || plan.waveDisplayName.isBlank()) ? plan.waveName : plan.waveDisplayName;
        if (plan.hasSentHudState
            && plan.lastHudTotalPlanned == plan.totalPlanned
            && plan.lastHudRemainingUnspawned == plan.remaining
            && plan.lastHudAliveSpawned == sample.aliveCount
            && display.equals(plan.lastHudDisplayName)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new WaveHudUpdatePayload(
            true,
            display,
            plan.totalPlanned,
            plan.remaining,
            sample.aliveCount
        ));
        plan.hasSentHudState = true;
        plan.lastHudDisplayName = display;
        plan.lastHudTotalPlanned = plan.totalPlanned;
        plan.lastHudRemainingUnspawned = plan.remaining;
        plan.lastHudAliveSpawned = sample.aliveCount;
    }

    private static void sendWaveHudInactive(MinecraftServer server, SpawnPlan plan) {
        if (server == null || plan == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, WaveHudUpdatePayload.inactive());
        }
        plan.hasSentHudState = false;
        plan.lastHudDisplayName = "";
        plan.lastHudTotalPlanned = -1;
        plan.lastHudRemainingUnspawned = -1;
        plan.lastHudAliveSpawned = -1;
    }

    private static void hideAllWaveHuds(MinecraftServer server) {
        if (server == null) return;
        for (SpawnPlan plan : ACTIVE_SPAWN_PLANS.values()) {
            sendWaveHudInactive(server, plan);
            if (plan != null && plan.bossBar != null) {
                plan.bossBar.removeAllPlayers();
            }
        }
    }

    private static final class SpawnPlan {
        private final UUID playerId;
        private String waveName;
        private String waveDisplayName;
        private final int totalPlanned;
        private final UUID spawnId;
        private final Map<String, String> wavesByDimension;
        private final boolean ignoreSpawnWindow;
        private ServerBossEvent bossBar;
        private int spawnedSuccessful;
        private int lastAliveCount;
        private long lastAliveSampleGameTime;
        private WaveHealthSample lastHealthSample;
        private long lastHealthSampleGameTime;
        private boolean hasSentHudState;
        private String lastHudDisplayName;
        private int lastHudTotalPlanned;
        private int lastHudRemainingUnspawned;
        private int lastHudAliveSpawned;
        private double activeBarHealthUnits;
        private int remaining;
        private long nextSpawnGameTime;
        private int blockedSpawnBatches;

        private SpawnPlan(
            UUID playerId,
            String waveName,
            String waveDisplayName,
            int totalPlanned,
            UUID spawnId,
            long now,
            Map<String, String> wavesByDimension,
            boolean ignoreSpawnWindow
        ) {
            this.playerId = playerId;
            this.waveName = waveName;
            this.waveDisplayName = waveDisplayName;
            this.totalPlanned = Math.max(1, totalPlanned);
            this.remaining = this.totalPlanned;
            this.spawnId = spawnId;
            this.nextSpawnGameTime = now;
            this.wavesByDimension = (wavesByDimension == null) ? new HashMap<>() : new HashMap<>(wavesByDimension);
            this.ignoreSpawnWindow = ignoreSpawnWindow;
            this.spawnedSuccessful = 0;
            this.lastAliveCount = -1;
            this.lastAliveSampleGameTime = Long.MIN_VALUE;
            this.lastHealthSample = WaveHealthSample.EMPTY;
            this.lastHealthSampleGameTime = Long.MIN_VALUE;
            this.hasSentHudState = false;
            this.lastHudDisplayName = "";
            this.lastHudTotalPlanned = -1;
            this.lastHudRemainingUnspawned = -1;
            this.lastHudAliveSpawned = -1;
            this.activeBarHealthUnits = 0.0D;
            this.blockedSpawnBatches = 0;
        }
    }

    private static final class WaveHealthSample {
        private static final WaveHealthSample EMPTY = new WaveHealthSample(0, 0.0D);

        private final int aliveCount;
        private final double remainingHealthUnits;

        private WaveHealthSample(int aliveCount, double remainingHealthUnits) {
            this.aliveCount = Math.max(0, aliveCount);
            this.remainingHealthUnits = Math.max(0.0D, remainingHealthUnits);
        }
    }
}
