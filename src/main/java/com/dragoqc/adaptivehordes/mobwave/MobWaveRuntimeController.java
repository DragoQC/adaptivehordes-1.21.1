package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Optional;
import java.util.UUID;

public final class MobWaveRuntimeController {
    private MobWaveRuntimeController() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity raw = event.getEntity();
        if (!(raw instanceof Mob mob)) return;
        if (raw.level().isClientSide) return;
        if (!MobWaveSpawner.isWaveSpawnedMob(mob)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        CompoundTag tag = mob.getPersistentData();
        long now = level.getGameTime();
        if (isExpired(now, tag)) {
            discardAndRefresh(level, mob, tag);
            return;
        }

        if (isTooFarFromAssignedTarget(level, mob, tag)) {
            discardAndRefresh(level, mob, tag);
            return;
        }

        if (AdaptiveHordes.mobConfig.sunlightImmunity && mob.isOnFire()) {
            mob.clearFire();
        }

        updateStuckState(now, mob, tag);
        if (tag.getInt(MobWaveSpawner.TAG_STUCK_TICKS) >= Math.max(40, AdaptiveHordes.mobConfig.maxStuckTicks)) {
            discardAndRefresh(level, mob, tag);
            return;
        }

        if (AdaptiveHordes.mobConfig.persistentTargeting) {
            ServerPlayer preferred = resolvePreferredTarget(level, tag);
            if (preferred != null && preferred.isAlive()) {
                boolean isRanged = tag.getBoolean(MobWaveSpawner.TAG_WAVE_MOB_RANGED);
                boolean targetMismatch = mob.getTarget() == null || !preferred.getUUID().equals(mob.getTarget().getUUID());

                // Ranged mobs keep vanilla ranged AI and combat distance.
                if (isRanged) {
                    if (targetMismatch && (now % 10L) == 0L) {
                        MobWaveSpawner.forceWaveTarget(mob, preferred);
                    }
                } else {
                    // Melee mobs should keep hard focus and push pathing toward target.
                    if (targetMismatch || (now % 5L) == 0L) {
                        MobWaveSpawner.forceWaveTarget(mob, preferred);
                    }
                    if (mob instanceof PathfinderMob pathfinderMob && (now % 20L) == 0L) {
                        pathfinderMob.getNavigation().moveTo(preferred, 1.1D);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        if (!MobWaveSpawner.isWaveSpawnedMob(mob)) return;

        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof Player attacker)) return;

        CompoundTag tag = mob.getPersistentData();
        UUID primary = parseUuid(tag.getString(MobWaveSpawner.TAG_PRIMARY_TARGET_UUID)).orElse(null);
        if (primary == null) return;

        if (attacker.getUUID().equals(primary)) {
            tag.remove(MobWaveSpawner.TAG_OVERRIDE_TARGET_UUID);
        } else {
            tag.putString(MobWaveSpawner.TAG_OVERRIDE_TARGET_UUID, attacker.getUUID().toString());
            if (attacker instanceof ServerPlayer player) {
                MobWaveSpawner.forceWaveTarget(mob, player);
            }
        }
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        if (!MobWaveSpawner.isWaveSpawnedMob(mob)) return;
        if (!AdaptiveHordes.mobConfig.persistentTargeting) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        ServerPlayer preferred = resolvePreferredTarget(level, mob.getPersistentData());
        if (preferred == null || !preferred.isAlive()) return;

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null || !preferred.getUUID().equals(newTarget.getUUID())) {
            event.setNewAboutToBeSetTarget(preferred);
            MobWaveSpawner.forceWaveTarget(mob, preferred);
        }
    }

    private static boolean isExpired(long now, CompoundTag tag) {
        long spawnTick = tag.getLong(MobWaveSpawner.TAG_SPAWN_GAME_TIME);
        int ttl = Math.max(200, AdaptiveHordes.mobConfig.waveMobLifetimeTicks);
        return (now - spawnTick) > ttl;
    }

    private static boolean isTooFarFromAssignedTarget(ServerLevel level, Mob mob, CompoundTag tag) {
        ServerPlayer preferred = resolvePreferredTarget(level, tag);
        if (preferred == null || !preferred.isAlive()) return false;
        double maxDist = Math.max(32, AdaptiveHordes.mobConfig.maxDistanceFromTarget);
        return mob.distanceToSqr(preferred) > (maxDist * maxDist);
    }

    private static void updateStuckState(long now, Mob mob, CompoundTag tag) {
        int interval = Math.max(10, AdaptiveHordes.mobConfig.stuckCheckIntervalTicks);
        long lastCheck = tag.getLong(MobWaveSpawner.TAG_LAST_STUCK_CHECK_GAME_TIME);
        if ((now - lastCheck) < interval) return;

        double lastX = tag.getDouble(MobWaveSpawner.TAG_LAST_POS_X);
        double lastY = tag.getDouble(MobWaveSpawner.TAG_LAST_POS_Y);
        double lastZ = tag.getDouble(MobWaveSpawner.TAG_LAST_POS_Z);
        double moved = mob.distanceToSqr(lastX, lastY, lastZ);

        int stuckTicks = tag.getInt(MobWaveSpawner.TAG_STUCK_TICKS);
        if (moved < 0.25 || mob.isInWall()) {
            stuckTicks += interval;
        } else {
            stuckTicks = Math.max(0, stuckTicks - interval);
        }

        tag.putInt(MobWaveSpawner.TAG_STUCK_TICKS, stuckTicks);
        tag.putLong(MobWaveSpawner.TAG_LAST_STUCK_CHECK_GAME_TIME, now);
        tag.putDouble(MobWaveSpawner.TAG_LAST_POS_X, mob.getX());
        tag.putDouble(MobWaveSpawner.TAG_LAST_POS_Y, mob.getY());
        tag.putDouble(MobWaveSpawner.TAG_LAST_POS_Z, mob.getZ());
    }

    private static ServerPlayer resolvePreferredTarget(ServerLevel level, CompoundTag tag) {
        UUID override = parseUuid(tag.getString(MobWaveSpawner.TAG_OVERRIDE_TARGET_UUID)).orElse(null);
        if (override != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(override);
            if (p != null) return p;
        }

        UUID primary = parseUuid(tag.getString(MobWaveSpawner.TAG_PRIMARY_TARGET_UUID)).orElse(null);
        if (primary == null) {
            AdaptiveHordes.LOGGER.debug(ColorConstants.YELLOW + "[MobWave] Missing primary target UUID on wave mob." + ColorConstants.RESET);
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(primary);
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static void discardAndRefresh(ServerLevel level, Mob mob, CompoundTag tag) {
        String spawnId = tag.getString(MobWaveSpawner.TAG_WAVE_SPAWN_ID);
        mob.discard();
        MobWaveScheduler.refreshBossBarForSpawnId(level.getServer(), spawnId);
    }
}
