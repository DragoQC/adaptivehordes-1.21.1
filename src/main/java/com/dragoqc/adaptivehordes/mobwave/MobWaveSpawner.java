package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.models.Mob;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.Wave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MobWaveSpawner {
    public static final String TAG_WAVE_SPAWNED = "adaptivehordes_wave_spawned";
    public static final String TAG_WAVE_NAME = "adaptivehordes_wave_name";
    public static final String TAG_WAVE_MOB_ENTITY_ID = "adaptivehordes_wave_mob_entity_id";
    public static final String TAG_WAVE_MOB_NAME = "adaptivehordes_wave_mob_name";
    public static final String TAG_WAVE_MOB_RANGED = "adaptivehordes_wave_mob_ranged";
    public static final String TAG_WAVE_SPAWN_ID = "adaptivehordes_wave_spawn_id";
    public static final String TAG_PRIMARY_TARGET_UUID = "adaptivehordes_primary_target_uuid";
    public static final String TAG_OVERRIDE_TARGET_UUID = "adaptivehordes_override_target_uuid";
    public static final String TAG_SPAWN_GAME_TIME = "adaptivehordes_spawn_game_time";
    public static final String TAG_LAST_POS_X = "adaptivehordes_last_pos_x";
    public static final String TAG_LAST_POS_Y = "adaptivehordes_last_pos_y";
    public static final String TAG_LAST_POS_Z = "adaptivehordes_last_pos_z";
    public static final String TAG_STUCK_TICKS = "adaptivehordes_stuck_ticks";
    public static final String TAG_LAST_STUCK_CHECK_GAME_TIME = "adaptivehordes_last_stuck_check_time";

    private MobWaveSpawner() {}

    /**
     * Tag entities spawned by the wave system so custom drops only affect them.
     */
    public static void markWaveSpawnedMob(net.minecraft.world.entity.LivingEntity entity, String waveName, String mobEntityId, String mobName) {
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean(TAG_WAVE_SPAWNED, true);
        if (waveName != null) tag.putString(TAG_WAVE_NAME, waveName);
        if (mobEntityId != null) tag.putString(TAG_WAVE_MOB_ENTITY_ID, mobEntityId);
        if (mobName != null) tag.putString(TAG_WAVE_MOB_NAME, mobName);
    }

    public static void forceWaveTarget(net.minecraft.world.entity.Mob mob, ServerPlayer targetPlayer) {
        if (mob == null || targetPlayer == null || !targetPlayer.isAlive()) return;
        mob.setTarget(targetPlayer);
        if (mob instanceof NeutralMob neutralMob) {
            neutralMob.setPersistentAngerTarget(targetPlayer.getUUID());
            neutralMob.setRemainingPersistentAngerTime(Math.max(600, neutralMob.getRemainingPersistentAngerTime()));
        }
    }

    public static boolean isWaveSpawnedMob(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getPersistentData().getBoolean(TAG_WAVE_SPAWNED);
    }

    public static int spawnWave(ServerLevel level, ServerPlayer targetPlayer, Wave wave, int baseHordeSize, PlayerScanResult scan) {
        if (level == null || targetPlayer == null || wave == null || wave.waveContent == null || wave.waveContent.isEmpty()) {
            return 0;
        }

        List<Mob> activeMobs = MobWave.pickActiveMobs(wave);
        if (activeMobs.isEmpty()) {
            activeMobs = new ArrayList<>(wave.waveContent);
        }
        activeMobs.removeIf(m -> m == null || m.presenceWeight <= 0.0);
        if (activeMobs.isEmpty()) return 0;

        int totalToSpawn = computeTotalMobCount(baseHordeSize, wave, scan);
        UUID spawnId = UUID.randomUUID();
        int spawned = 0;

        for (int i = 0; i < totalToSpawn; i++) {
            Mob template = weightedPick(activeMobs);
            if (template == null) continue;
            if (spawnSingle(level, targetPlayer, wave, template, spawnId, scan)) {
                spawned++;
            }
        }

        return spawned;
    }

    public static int estimateWaveMobCount(int baseHordeSize, Wave wave, PlayerScanResult scan) {
        return computeTotalMobCount(baseHordeSize, wave, scan);
    }

    public static int spawnWaveBatch(
        ServerLevel level,
        ServerPlayer targetPlayer,
        Wave wave,
        int batchAmount,
        UUID spawnId,
        PlayerScanResult scan
    ) {
        if (level == null || targetPlayer == null || wave == null || batchAmount <= 0) return 0;

        List<Mob> activeMobs = MobWave.pickActiveMobs(wave);
        if (activeMobs.isEmpty()) {
            activeMobs = new ArrayList<>(wave.waveContent);
        }
        activeMobs.removeIf(m -> m == null || m.presenceWeight <= 0.0);
        if (activeMobs.isEmpty()) return 0;

        int spawned = 0;
        for (int i = 0; i < batchAmount; i++) {
            Mob template = weightedPick(activeMobs);
            if (template == null) continue;
            if (spawnSingle(level, targetPlayer, wave, template, spawnId, scan)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static int computeTotalMobCount(int baseHordeSize, Wave wave, PlayerScanResult scan) {
        int base = Math.max(1, baseHordeSize);
        double waveMult = MobWave.getTotalMobMultiplier(wave);
        double strengthMult = 1.0;
        if (scan != null && wave.strengthRequirement > 0) {
            double ratio = (double) scan.gearScore / (double) wave.strengthRequirement;
            strengthMult = Mth.clamp(0.8 + (ratio * 0.6), 0.6, 3.0);
        }
        int total = (int) Math.round(base * waveMult * strengthMult);
        return Math.max(1, total);
    }

    private static Mob weightedPick(List<Mob> mobs) {
        double total = 0.0;
        for (Mob mob : mobs) {
            if (mob == null) continue;
            total += Math.max(0.0, mob.presenceWeight * mob.amountMultiplier);
        }
        if (total <= 0.0) return mobs.get(0);

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0;
        for (Mob mob : mobs) {
            if (mob == null) continue;
            cursor += Math.max(0.0, mob.presenceWeight * mob.amountMultiplier);
            if (roll <= cursor) return mob;
        }
        return mobs.get(0);
    }

    private static boolean spawnSingle(ServerLevel level, ServerPlayer targetPlayer, Wave wave, Mob template, UUID spawnId, PlayerScanResult scan) {
        ResourceLocation key = ResourceLocation.tryParse(template.entityId);
        if (key == null) return false;

        var type = BuiltInRegistries.ENTITY_TYPE.get(key);
        if (type == null) return false;

        Entity entity = type.create(level);
        if (!(entity instanceof PathfinderMob mob)) return false;

        BlockPos spawnPos = findSpawnPos(level, targetPlayer.blockPosition());
        if (spawnPos == null) return false;

        mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);

        applyMobStats(mob, template, wave, scan);
        markWaveSpawnedMob(mob, wave.name, template.entityId, template.name);
        tagRuntimeData(mob, spawnId, targetPlayer);
        mob.getPersistentData().putBoolean(TAG_WAVE_MOB_RANGED, template.ranged);

        mob.setPersistenceRequired();
        forceWaveTarget(mob, targetPlayer);

        if (!level.noCollision(mob)) {
            mob.discard();
            return false;
        }

        if (!level.addFreshEntity(mob)) {
            return false;
        }

        return true;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos center) {
        int minDist = Math.max(4, AdaptiveHordes.mobConfig.spawnMinDistance);
        int maxDist = Math.max(minDist + 1, AdaptiveHordes.mobConfig.spawnMaxDistance);
        int attempts = Math.max(4, AdaptiveHordes.mobConfig.spawnPositionAttempts);

        for (int i = 0; i < attempts; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            double radius = ThreadLocalRandom.current().nextDouble(minDist, maxDist);
            int x = center.getX() + Mth.floor(Math.cos(angle) * radius);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * radius);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));

            if (!level.getWorldBorder().isWithinBounds(top)) continue;
            if (level.getBlockState(top.below()).isAir()) continue;
            if (!level.getBlockState(top).isAir()) continue;
            return top;
        }

        return null;
    }

    private static void applyMobStats(PathfinderMob mob, Mob template, Wave wave, PlayerScanResult scan) {
        if (template.baby && mob instanceof Zombie zombie) {
            zombie.setBaby(true);
        }

        double healthMultiplier = 1.0 + computeWeaponPowerHealthBonus(scan);
        setAttributeBase(mob, Attributes.MAX_HEALTH, Math.max(1.0, template.baseHealth) * healthMultiplier);
        setAttributeBase(mob, Attributes.MOVEMENT_SPEED, Math.max(0.05, template.baseSpeed));
        setAttributeBase(mob, Attributes.FOLLOW_RANGE, Math.max(16.0, AdaptiveHordes.mobConfig.mobDetectionRange));
        setAttributeBase(mob, Attributes.ATTACK_DAMAGE, Math.max(0.0, template.baseDamage));
        setAttributeBase(mob, Attributes.SCALE, Math.max(0.1, AdaptiveHordes.mobConfig.sizeMultiplier));

        mob.setHealth((float) mob.getMaxHealth());
        applyRandomArmor(mob, template, wave);
    }

    private static void applyRandomArmor(PathfinderMob mob, Mob template, Wave wave) {
        double chance = Mth.clamp(template.randomArmorChance, 0.0, 1.0);
        if (chance <= 0.0) return;
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        int maxPieces = Mth.clamp(template.randomArmorMaxPieces, 1, 4);
        int pieces = ThreadLocalRandom.current().nextInt(1, maxPieces + 1);

        List<EquipmentSlot> slots = new ArrayList<>(List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        ));
        Collections.shuffle(slots, ThreadLocalRandom.current());

        int strength = (wave == null) ? 0 : Math.max(0, wave.strengthRequirement);
        for (int i = 0; i < pieces && i < slots.size(); i++) {
            EquipmentSlot slot = slots.get(i);
            if (!mob.getItemBySlot(slot).isEmpty()) continue;
            Item armorItem = pickRandomArmorItem(slot, strength);
            if (armorItem == null) continue;
            mob.setItemSlot(slot, new ItemStack(armorItem));
        }
    }

    private static Item pickRandomArmorItem(EquipmentSlot slot, int waveStrength) {
        int tier;
        if (waveStrength >= 1200) {
            tier = 4;
        } else if (waveStrength >= 800) {
            tier = 3;
        } else if (waveStrength >= 450) {
            tier = 2;
        } else if (waveStrength >= 220) {
            tier = 1;
        } else {
            tier = 0;
        }

        return switch (slot) {
            case HEAD -> pickByTier(tier,
                Items.LEATHER_HELMET,
                Items.CHAINMAIL_HELMET,
                Items.IRON_HELMET,
                Items.DIAMOND_HELMET,
                Items.NETHERITE_HELMET
            );
            case CHEST -> pickByTier(tier,
                Items.LEATHER_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.NETHERITE_CHESTPLATE
            );
            case LEGS -> pickByTier(tier,
                Items.LEATHER_LEGGINGS,
                Items.CHAINMAIL_LEGGINGS,
                Items.IRON_LEGGINGS,
                Items.DIAMOND_LEGGINGS,
                Items.NETHERITE_LEGGINGS
            );
            case FEET -> pickByTier(tier,
                Items.LEATHER_BOOTS,
                Items.CHAINMAIL_BOOTS,
                Items.IRON_BOOTS,
                Items.DIAMOND_BOOTS,
                Items.NETHERITE_BOOTS
            );
            default -> null;
        };
    }

    private static Item pickByTier(int tier, Item t0, Item t1, Item t2, Item t3, Item t4) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (tier <= 0) {
            return (roll < 75) ? t0 : t1;
        }
        if (tier == 1) {
            if (roll < 45) return t0;
            if (roll < 80) return t1;
            return t2;
        }
        if (tier == 2) {
            if (roll < 20) return t1;
            if (roll < 75) return t2;
            return t3;
        }
        if (tier == 3) {
            if (roll < 25) return t2;
            if (roll < 90) return t3;
            return t4;
        }
        if (roll < 15) return t2;
        if (roll < 65) return t3;
        return t4;
    }

    private static void setAttributeBase(PathfinderMob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double base) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(base);
        }
    }

    private static void tagRuntimeData(PathfinderMob mob, UUID spawnId, ServerPlayer targetPlayer) {
        CompoundTag tag = mob.getPersistentData();
        tag.putString(TAG_WAVE_SPAWN_ID, spawnId.toString());
        tag.putString(TAG_PRIMARY_TARGET_UUID, targetPlayer.getUUID().toString());
        tag.putLong(TAG_SPAWN_GAME_TIME, mob.level().getGameTime());
        tag.putDouble(TAG_LAST_POS_X, mob.getX());
        tag.putDouble(TAG_LAST_POS_Y, mob.getY());
        tag.putDouble(TAG_LAST_POS_Z, mob.getZ());
        tag.putLong(TAG_LAST_STUCK_CHECK_GAME_TIME, mob.level().getGameTime());
        tag.putInt(TAG_STUCK_TICKS, 0);
    }

    private static double computeWeaponPowerHealthBonus(PlayerScanResult scan) {
        if (scan == null || AdaptiveHordes.scalingConfig == null) return 0.0;
        if (!AdaptiveHordes.scalingConfig.weaponPowerHealthScalingEnabled) return 0.0;

        double minBonus = Math.max(0.0, AdaptiveHordes.scalingConfig.weaponPowerHealthBonusMin);
        double maxBonus = Math.max(minBonus, AdaptiveHordes.scalingConfig.weaponPowerHealthBonusMax);
        double ref = Math.max(1.0, AdaptiveHordes.scalingConfig.weaponPowerForMaxHealthBonus);
        double weaponPower = Math.max(scan.meleePower, scan.arrowPower);

        double t = Mth.clamp(weaponPower / ref, 0.0, 1.0);
        return minBonus + ((maxBonus - minBonus) * t);
    }
}
