package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.models.Drop;
import com.dragoqc.adaptivehordes.models.Mob;
import com.dragoqc.adaptivehordes.models.Wave;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;
import java.util.List;

public final class MobWaveDropsHandler {
    private MobWaveDropsHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MobWaveSpawner.isWaveSpawnedMob(entity)) return;

        String waveName = entity.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_NAME);
        String taggedEntityId = entity.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_MOB_ENTITY_ID);
        String mobName = entity.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_MOB_NAME);

        if (waveName == null || waveName.isBlank()) {
            return;
        }

        Wave wave = MobWave.findWaveByName(waveName);
        if (wave == null) {
            AdaptiveHordes.LOGGER.warn(ColorConstants.YELLOW +
                "[waves.json] Drop handling: wave not found for tagged mob: " + waveName +
                ColorConstants.RESET);
            return;
        }

        String realEntityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String lookupEntityId = (taggedEntityId == null || taggedEntityId.isBlank()) ? realEntityId : taggedEntityId;
        Mob mob = MobWave.findMobInWave(wave, lookupEntityId, mobName);
        if (mob == null) {
            // Fallback lookup by runtime entity id.
            mob = MobWave.findMobInWave(wave, realEntityId, null);
        }
        if (mob == null) return;

        if ("OVERRIDE".equals(MobWave.normalizeDropMode(mob.dropsMode))) {
            event.getDrops().clear();
        }

        applyConfiguredDrops(entity, event.getDrops(), wave.waveDrops);
        applyConfiguredDrops(entity, event.getDrops(), mob.drops);
    }

    private static void applyConfiguredDrops(LivingEntity entity, Collection<ItemEntity> outDrops, List<Drop> configuredDrops) {
        if (configuredDrops == null || configuredDrops.isEmpty()) return;

        for (Drop drop : configuredDrops) {
            if (drop == null) continue;
            ResourceLocation itemKey = ResourceLocation.tryParse(drop.itemId);
            if (itemKey == null || !BuiltInRegistries.ITEM.containsKey(itemKey)) continue;

            int attempts = getRandomAmount(entity, drop.minCount, drop.maxCount);
            int successful = rollSuccessfulItems(entity, attempts, drop.chance);
            if (successful <= 0) continue;

            Item item = BuiltInRegistries.ITEM.get(itemKey);
            spawnStacks(entity, outDrops, item, successful);
        }
    }

    private static int getRandomAmount(LivingEntity entity, int minCount, int maxCount) {
        int min = Math.max(0, minCount);
        int max = Math.max(0, maxCount);
        if (max < min) {
            int t = min;
            min = max;
            max = t;
        }
        if (max == min) return min;
        return min + entity.getRandom().nextInt(max - min + 1);
    }

    private static int rollSuccessfulItems(LivingEntity entity, int attempts, float chance) {
        int success = 0;
        float c = (float) Math.max(0.0, Math.min(1.0, chance));
        for (int i = 0; i < attempts; i++) {
            if (entity.getRandom().nextFloat() <= c) success++;
        }
        return success;
    }

    private static void spawnStacks(LivingEntity entity, Collection<ItemEntity> outDrops, Item item, int count) {
        int remaining = count;
        int maxStack = item.getDefaultMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, stackSize);
            outDrops.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
            remaining -= stackSize;
        }
    }
}
