package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MobWaveRuntimeController {
    private static final int MAX_PRIORITY_TARGETS = 8;
    private static final int MELEE_TARGET_REFRESH_TICKS = 5;
    private static final int RANGED_TARGET_REFRESH_TICKS = 10;
    private static final int PURSUIT_PATH_REFRESH_TICKS = 20;
    private static final int FALLBACK_PURSUIT_RADIUS = 12;
    private static final int FALLBACK_PURSUIT_VERTICAL_RANGE = 6;
    private static final double MELEE_PURSUIT_SPEED = 1.1D;
    private static final double RANGED_PURSUIT_SPEED = 1.0D;
    private static final double FALLBACK_PURSUIT_ANGLE = Math.PI / 2.0D;
    private static final double MAX_CALL_FOR_HELP_RADIUS = 32.0D;
    private static final Map<UUID, Long> CALL_FOR_HELP_COOLDOWNS = new HashMap<>();

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

        if (!AdaptiveHordes.mobConfig.persistentTargeting && MobWaveSpawner.isWaveSpawnedMob(mob.getTarget())) {
            mob.setTarget(null);
        }

        if (AdaptiveHordes.mobConfig.persistentTargeting) {
            LivingEntity preferred = resolvePreferredTarget(level, mob);
            if (preferred != null && preferred.isAlive()) {
                boolean isRanged = tag.getBoolean(MobWaveSpawner.TAG_WAVE_MOB_RANGED);
                boolean targetMismatch = mob.getTarget() == null || !preferred.getUUID().equals(mob.getTarget().getUUID());
                int targetRefreshTicks = isRanged ? RANGED_TARGET_REFRESH_TICKS : MELEE_TARGET_REFRESH_TICKS;

                if (targetMismatch || isScheduledTick(now, mob, targetRefreshTicks)) {
                    MobWaveSpawner.forceWaveTarget(mob, preferred);
                }

                if (mob instanceof PathfinderMob pathfinderMob) {
                    updateRaidPursuit(now, pathfinderMob, preferred, isRanged, targetMismatch);
                }
            } else if (mob.getTarget() != null && MobWaveSpawner.isWaveSpawnedMob(mob.getTarget())) {
                mob.setTarget(null);
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
        if (!(attackerEntity instanceof LivingEntity attacker)) return;
        if (attacker == mob || MobWaveSpawner.isWaveSpawnedMob(attacker)) return;
        if (!AdaptiveHordes.mobConfig.persistentTargeting) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        LivingEntity previousPreferredTarget = resolvePreferredTarget(level, mob);
        ServerPlayer assignedPlayer = resolveAssignedPlayer(level, mob.getPersistentData());
        boolean assignedPlayerAttack = assignedPlayer != null && assignedPlayer.getUUID().equals(attacker.getUUID());

        pushPriorityTarget(mob, attacker);
        LivingEntity preferred = resolvePreferredTarget(level, mob);
        if (preferred != null) {
            MobWaveSpawner.forceWaveTarget(mob, preferred);
        }

        double callRadius = getCallForHelpRadius();
        if (callRadius > 0.0D && tryStartCallForHelpCooldown(attacker.getUUID(), level.getGameTime())) {
            callForHelp(level, mob, attacker, callRadius, previousPreferredTarget, assignedPlayerAttack);
        }
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        if (!MobWaveSpawner.isWaveSpawnedMob(mob)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        boolean targetingHordeMob = newTarget != null && MobWaveSpawner.isWaveSpawnedMob(newTarget);
        if (!AdaptiveHordes.mobConfig.persistentTargeting) {
            if (targetingHordeMob) {
                event.setCanceled(true);
            }
            return;
        }

        LivingEntity preferred = resolvePreferredTarget(level, mob);
        if (preferred == null || !preferred.isAlive()) {
            if (targetingHordeMob) {
                event.setCanceled(true);
            }
            return;
        }

        if (newTarget == null || !preferred.getUUID().equals(newTarget.getUUID())) {
            event.setNewAboutToBeSetTarget(preferred);
        }
    }

    private static boolean isExpired(long now, CompoundTag tag) {
        long spawnTick = tag.getLong(MobWaveSpawner.TAG_SPAWN_GAME_TIME);
        int ttl = Math.max(200, AdaptiveHordes.mobConfig.waveMobLifetimeTicks);
        return (now - spawnTick) > ttl;
    }

    private static boolean isTooFarFromAssignedTarget(ServerLevel level, Mob mob, CompoundTag tag) {
        ServerPlayer assignedPlayer = resolveAssignedPlayer(level, tag);
        if (assignedPlayer == null || !assignedPlayer.isAlive()) return false;
        double maxDist = Math.max(32, AdaptiveHordes.mobConfig.maxDistanceFromTarget);
        return mob.distanceToSqr(assignedPlayer) > (maxDist * maxDist);
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

    private static void updateRaidPursuit(
        long now,
        PathfinderMob mob,
        LivingEntity preferred,
        boolean isRanged,
        boolean targetMismatch
    ) {
        if (hasRunningMovementGoal(mob)) return;

        PathNavigation navigation = mob.getNavigation();
        Path currentPath = navigation.getPath();
        boolean hasLineOfSight = mob.getSensing().hasLineOfSight(preferred);
        boolean hasReachablePath = currentPath != null && !currentPath.isDone() && currentPath.canReach();

        // Ranged combat AI should retain its preferred distance once it can see the player.
        if (isRanged && hasLineOfSight) return;

        // Melee AI already owns a useful route. Recalculate only when sight or pathing is lost.
        if (!isRanged && hasLineOfSight && hasReachablePath) return;
        if (!targetMismatch && !isScheduledTick(now, mob, PURSUIT_PATH_REFRESH_TICKS)) return;

        int pathAccuracy = isRanged ? 4 : 1;
        Path pursuitPath = navigation.createPath(preferred, pathAccuracy);
        double pursuitSpeed = isRanged ? RANGED_PURSUIT_SPEED : MELEE_PURSUIT_SPEED;
        if (pursuitPath != null && pursuitPath.getNodeCount() > 0) {
            navigation.moveTo(pursuitPath, pursuitSpeed);
            return;
        }

        if (!(navigation instanceof GroundPathNavigation)) return;

        Vec3 fallbackPosition = DefaultRandomPos.getPosTowards(
            mob,
            FALLBACK_PURSUIT_RADIUS,
            FALLBACK_PURSUIT_VERTICAL_RANGE,
            preferred.position(),
            FALLBACK_PURSUIT_ANGLE
        );
        if (fallbackPosition != null) {
            navigation.moveTo(fallbackPosition.x, fallbackPosition.y, fallbackPosition.z, pursuitSpeed);
        }
    }

    private static boolean hasRunningMovementGoal(Mob mob) {
        for (WrappedGoal wrappedGoal : mob.goalSelector.getAvailableGoals()) {
            if (!wrappedGoal.isRunning()) continue;
            if (wrappedGoal.getFlags().contains(Goal.Flag.MOVE)
                || wrappedGoal.getFlags().contains(Goal.Flag.JUMP)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScheduledTick(long now, Mob mob, int intervalTicks) {
        int safeInterval = Math.max(1, intervalTicks);
        return Math.floorMod(now + mob.getId(), (long)safeInterval) == 0L;
    }

    private static void callForHelp(
        ServerLevel level,
        Mob victim,
        LivingEntity attacker,
        double radius,
        LivingEntity victimPreviousTarget,
        boolean assignedPlayerAttack
    ) {
        double radiusSquared = radius * radius;
        List<Mob> nearbyMobs = level.getEntitiesOfClass(
            Mob.class,
            victim.getBoundingBox().inflate(radius),
            candidate -> candidate.isAlive()
                && !candidate.isRemoved()
                && MobWaveSpawner.isWaveSpawnedMob(candidate)
                && candidate.distanceToSqr(victim) <= radiusSquared
        );
        Map<UUID, List<ResponderFocus>> splitGroups = new HashMap<>();

        for (Mob nearbyMob : nearbyMobs) {
            LivingEntity currentTarget = nearbyMob == victim
                ? victimPreviousTarget
                : resolvePreferredTarget(level, nearbyMob);
            if (currentTarget != null && currentTarget.getUUID().equals(attacker.getUUID())) continue;

            boolean focusedOnAssignedPlayer = isAssignedPlayerTarget(nearbyMob, currentTarget);
            if (!assignedPlayerAttack && (currentTarget == null || focusedOnAssignedPlayer)) {
                promoteAndRetarget(nearbyMob, attacker);
                continue;
            }

            UUID focusUuid = currentTarget == null ? null : currentTarget.getUUID();
            splitGroups.computeIfAbsent(focusUuid, ignored -> new ArrayList<>())
                .add(new ResponderFocus(nearbyMob, currentTarget));
        }

        for (List<ResponderFocus> splitGroup : splitGroups.values()) {
            splitThreatGroup(splitGroup, victim, attacker);
        }
    }

    private static void splitThreatGroup(List<ResponderFocus> splitGroup, Mob victim, LivingEntity attacker) {
        if (splitGroup.isEmpty()) return;

        splitGroup.sort(Comparator.comparing(focus -> focus.mob().getUUID()));
        int promoteCount = (splitGroup.size() + 1) / 2;
        int promoted = 0;

        for (ResponderFocus focus : splitGroup) {
            if (focus.mob() != victim) continue;
            promoteAndRetarget(focus.mob(), attacker);
            promoted++;
            break;
        }

        for (ResponderFocus focus : splitGroup) {
            if (focus.mob() == victim) continue;
            if (promoted < promoteCount) {
                promoteAndRetarget(focus.mob(), attacker);
                promoted++;
            } else if (focus.currentTarget() != null) {
                queuePriorityTargetBelowCurrent(focus.mob(), focus.currentTarget(), attacker);
            }
        }
    }

    private static void promoteAndRetarget(Mob mob, LivingEntity target) {
        pushPriorityTarget(mob, target);
        MobWaveSpawner.forceWaveTarget(mob, target);
    }

    private static boolean isAssignedPlayerTarget(Mob mob, LivingEntity target) {
        if (target == null) return false;
        UUID assignedPlayerUuid = parseUuid(
            mob.getPersistentData().getString(MobWaveSpawner.TAG_PRIMARY_TARGET_UUID)
        ).orElse(null);
        return target.getUUID().equals(assignedPlayerUuid);
    }

    private static double getCallForHelpRadius() {
        return Math.max(0.0D, Math.min(MAX_CALL_FOR_HELP_RADIUS, AdaptiveHordes.mobConfig.callForHelpRadius));
    }

    private static boolean tryStartCallForHelpCooldown(UUID attackerUuid, long now) {
        long nextAllowedTick = CALL_FOR_HELP_COOLDOWNS.getOrDefault(attackerUuid, Long.MIN_VALUE);
        if (now < nextAllowedTick) return false;

        int cooldownTicks = Math.max(0, AdaptiveHordes.mobConfig.callForHelpCooldownTicks);
        if (cooldownTicks == 0) {
            CALL_FOR_HELP_COOLDOWNS.remove(attackerUuid);
        } else {
            CALL_FOR_HELP_COOLDOWNS.put(attackerUuid, now + cooldownTicks);
        }
        return true;
    }

    private static void pushPriorityTarget(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !target.isAlive()) return;
        if (MobWaveSpawner.isWaveSpawnedMob(target)) return;

        CompoundTag tag = mob.getPersistentData();
        String targetUuid = target.getUUID().toString();
        ListTag currentTargets = tag.getList(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, Tag.TAG_STRING);
        ListTag updatedTargets = new ListTag();
        Set<String> seenTargets = new HashSet<>();

        updatedTargets.add(StringTag.valueOf(targetUuid));
        seenTargets.add(targetUuid);
        for (int index = 0; index < currentTargets.size() && updatedTargets.size() < MAX_PRIORITY_TARGETS; index++) {
            String queuedUuid = currentTargets.getString(index);
            if (queuedUuid.isBlank() || !seenTargets.add(queuedUuid)) continue;
            if (parseUuid(queuedUuid).isEmpty()) continue;
            updatedTargets.add(StringTag.valueOf(queuedUuid));
        }

        tag.put(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, updatedTargets);
    }

    private static void queuePriorityTargetBelowCurrent(Mob mob, LivingEntity currentTarget, LivingEntity queuedTarget) {
        if (mob == null || currentTarget == null || queuedTarget == null) return;
        if (!currentTarget.isAlive() || !queuedTarget.isAlive()) return;
        if (MobWaveSpawner.isWaveSpawnedMob(currentTarget) || MobWaveSpawner.isWaveSpawnedMob(queuedTarget)) return;

        CompoundTag tag = mob.getPersistentData();
        String currentTargetUuid = currentTarget.getUUID().toString();
        String queuedTargetUuid = queuedTarget.getUUID().toString();
        ListTag currentTargets = tag.getList(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, Tag.TAG_STRING);
        ListTag updatedTargets = new ListTag();
        Set<String> seenTargets = new HashSet<>();

        updatedTargets.add(StringTag.valueOf(currentTargetUuid));
        seenTargets.add(currentTargetUuid);
        if (updatedTargets.size() < MAX_PRIORITY_TARGETS && seenTargets.add(queuedTargetUuid)) {
            updatedTargets.add(StringTag.valueOf(queuedTargetUuid));
        }

        for (int index = 0; index < currentTargets.size() && updatedTargets.size() < MAX_PRIORITY_TARGETS; index++) {
            String existingUuid = currentTargets.getString(index);
            if (existingUuid.isBlank() || !seenTargets.add(existingUuid)) continue;
            if (parseUuid(existingUuid).isEmpty()) continue;
            updatedTargets.add(StringTag.valueOf(existingUuid));
        }

        tag.put(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, updatedTargets);
    }

    private static LivingEntity resolvePreferredTarget(ServerLevel level, Mob mob) {
        LivingEntity priorityTarget = resolvePriorityTarget(level, mob);
        if (priorityTarget != null) return priorityTarget;
        return resolveAssignedPlayer(level, mob.getPersistentData());
    }

    private static LivingEntity resolvePriorityTarget(ServerLevel level, Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, Tag.TAG_LIST)) return null;

        ListTag queuedTargets = tag.getList(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS, Tag.TAG_STRING);
        while (!queuedTargets.isEmpty()) {
            String queuedUuid = queuedTargets.getString(0);
            UUID targetUuid = parseUuid(queuedUuid).orElse(null);
            Entity queuedEntity = targetUuid == null ? null : level.getEntity(targetUuid);
            if (queuedEntity instanceof LivingEntity livingTarget
                && livingTarget != mob
                && livingTarget.isAlive()
                && !livingTarget.isRemoved()
                && !MobWaveSpawner.isWaveSpawnedMob(livingTarget)) {
                return livingTarget;
            }

            queuedTargets.remove(0);
            if (targetUuid != null) {
                CALL_FOR_HELP_COOLDOWNS.remove(targetUuid);
            }
        }

        tag.remove(MobWaveSpawner.TAG_PRIORITY_TARGET_UUIDS);
        return null;
    }

    private static ServerPlayer resolveAssignedPlayer(ServerLevel level, CompoundTag tag) {
        UUID primary = parseUuid(tag.getString(MobWaveSpawner.TAG_PRIMARY_TARGET_UUID)).orElse(null);

        if (primary == null) {
            AdaptiveHordes.LOGGER.debug(ColorConstants.YELLOW + "[MobWave] Missing primary target UUID on wave mob." + ColorConstants.RESET);
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(primary);
    }

    public static void clearCallForHelpCooldownsIfNoActiveHorde(MinecraftServer server) {
        if (server == null) return;
        if (CALL_FOR_HELP_COOLDOWNS.isEmpty()) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity livingEntity
                    && livingEntity.isAlive()
                    && !livingEntity.isRemoved()
                    && MobWaveSpawner.isWaveSpawnedMob(livingEntity)) {
                    return;
                }
            }
        }

        CALL_FOR_HELP_COOLDOWNS.clear();
    }

    static void clearCallForHelpCooldowns() {
        CALL_FOR_HELP_COOLDOWNS.clear();
    }

    private record ResponderFocus(Mob mob, LivingEntity currentTarget) {}

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
        clearCallForHelpCooldownsIfNoActiveHorde(level.getServer());
    }
}
