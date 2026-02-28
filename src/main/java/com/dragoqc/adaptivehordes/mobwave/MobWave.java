package com.dragoqc.adaptivehordes.mobwave;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.models.DefaultWaveConfig;
import com.dragoqc.adaptivehordes.models.Drop;
import com.dragoqc.adaptivehordes.models.Mob;
import com.dragoqc.adaptivehordes.models.Wave;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MobWave {

    private static DefaultWaveConfig CACHE = null;

    private MobWave() {}

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    /** Loads (or creates) waves.json using JsonFileHelper and caches it. */
    public static DefaultWaveConfig getConfig() {
        if (CACHE != null) return CACHE;

        CACHE = JsonFileHelper.loadOrCreate(
            ConfigConstants.WAVES_CONFIG_FILE,
            DefaultWaveConfig.class
        );

        // Validate and normalize so malformed data is visible and repaired.
        boolean changed = validateAndNormalize(CACHE);

        // Normalize ordering so selection is deterministic.
        CACHE.waves.sort(Comparator.comparingInt(w -> w.strengthRequirement));

        if (changed) {
            AdaptiveHordes.LOGGER.warn(
                ColorConstants.YELLOW + "[waves.json] Validation repaired invalid values. Saving normalized file." + ColorConstants.RESET
            );
            saveAll();
        }

        return CACHE;
    }

    /** Convenience: returns the wave list. */
    public static List<Wave> getAll() {
        return getConfig().waves;
    }

    /** Force re-read from disk. */
    public static void reload() {
        CACHE = null;
        getConfig();
    }

    /** Save current cached config back to waves.json. */
    public static void saveAll() {
        DefaultWaveConfig cfg = getConfig();
        File file = new File(JsonFileHelper.getConfigDirectory(), ConfigConstants.WAVES_CONFIG_FILE);
        JsonFileHelper.saveConfig(file, cfg);
    }

    /** Replace waves.json content with hardcoded defaults from DefaultWaveConfig. */
    public static void resetToDefaults() {
        CACHE = new DefaultWaveConfig();
        CACHE.waves.sort(Comparator.comparingInt(w -> w.strengthRequirement));
        saveAll();
    }

    /**
     * Pick one eligible wave for a strength:
     * - only waves with strengthRequirement <= strength are eligible
     * - uses waveSpawnChance as weighted chance among eligible waves
     * - if no eligible wave exists, falls back to the lowest requirement wave
     */
    public static Wave pickWaveForStrength(int strength) {
        return pickWaveForStrength(strength, null);
    }

    public static Wave pickWaveForStrength(int strength, String dimensionId) {
        List<Wave> waves = getAll();
        if (waves.isEmpty()) return null;

        List<Wave> eligible = new ArrayList<>();
        for (Wave w : waves) {
            if (w == null) continue;
            if (!isWaveAllowedInDimension(w, dimensionId)) continue;
            if (w.strengthRequirement <= strength) {
                eligible.add(w);
            } else {
                break; // sorted by strengthRequirement
            }
        }

        if (eligible.isEmpty()) {
            return waves.get(0);
        }

        int maxEligibleRequirement = eligible.get(eligible.size() - 1).strengthRequirement;
        double[] adjustedWeights = new double[eligible.size()];
        double totalWeight = 0.0;
        for (int i = 0; i < eligible.size(); i++) {
            Wave w = eligible.get(i);
            double baseWeight = Math.max(0.0, w.waveSpawnChance);
            double hardWaveBias = computeHardWaveBiasMultiplier(strength, w.strengthRequirement, maxEligibleRequirement);
            double adjusted = baseWeight * hardWaveBias;
            adjustedWeights[i] = adjusted;
            totalWeight += adjusted;
        }

        // If all chances are zero, pick the strongest eligible wave.
        if (totalWeight <= 0.0) {
            return eligible.get(eligible.size() - 1);
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0;
        for (int i = 0; i < eligible.size(); i++) {
            Wave w = eligible.get(i);
            cursor += adjustedWeights[i];
            if (roll <= cursor) return w;
        }

        return eligible.get(eligible.size() - 1);
    }

    private static double computeHardWaveBiasMultiplier(int playerStrength, int waveRequirement, int maxEligibleRequirement) {
        int maxReq = Math.max(1, maxEligibleRequirement);
        double strengthRatio = Math.max(0.0, (double) playerStrength / (double) maxReq);

        // No bias while player is not over the top eligible requirement.
        if (strengthRatio <= 1.0) {
            return 1.0;
        }

        // Becomes strong by ratio ~5x and above.
        double biasT = clamp01((strengthRatio - 1.0) / 4.0);
        double reqNorm = Math.max(0.01, (double) Math.max(0, waveRequirement) / (double) maxReq);
        double exponent = 1.0 + (biasT * 6.0); // 1..7
        return Math.max(0.02, Math.pow(reqNorm, exponent));
    }

    /** Returns a safe multiplier for total mobs to spawn for this wave. */
    public static double getTotalMobMultiplier(Wave wave) {
        if (wave == null || !Double.isFinite(wave.totalMobMultiplier) || wave.totalMobMultiplier <= 0.0) {
            return 1.0;
        }
        return wave.totalMobMultiplier;
    }

    /**
     * Picks which mobs are active for this wave spawn cycle.
     * Each mob gets a dynamic chance sampled between spawnChanceMin and spawnChanceMax.
     */
    public static List<Mob> pickActiveMobs(Wave wave) {
        if (wave == null || wave.waveContent == null || wave.waveContent.isEmpty()) {
            return Collections.emptyList();
        }

        List<Mob> active = new ArrayList<>();
        for (Mob mob : wave.waveContent) {
            if (mob == null || mob.presenceWeight <= 0.0) continue;
            double chance = mob.spawnChanceMin + (ThreadLocalRandom.current().nextDouble() * (mob.spawnChanceMax - mob.spawnChanceMin));
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                active.add(mob);
            }
        }

        return active;
    }

    public static Wave findWaveByName(String waveName) {
        if (isBlank(waveName)) return null;
        for (Wave wave : getAll()) {
            if (wave != null && waveName.equals(wave.name)) return wave;
        }
        return null;
    }

    public static Mob findMobInWave(Wave wave, String entityId, String mobName) {
        if (wave == null || wave.waveContent == null) return null;
        for (Mob mob : wave.waveContent) {
            if (mob == null) continue;
            if (entityId != null && !entityId.equals(mob.entityId)) continue;
            if (!isBlank(mobName) && !mobName.equals(mob.name)) continue;
            return mob;
        }
        return null;
    }

    private static boolean validateAndNormalize(DefaultWaveConfig cfg) {
        boolean changed = false;

        if (cfg.waves == null) {
            cfg.waves = new ArrayList<>();
            logWarn("waves", "Missing list; created empty list.");
            changed = true;
        }

        for (int i = 0; i < cfg.waves.size(); i++) {
            Wave wave = cfg.waves.get(i);
            String wavePath = "waves[" + i + "]";

            if (wave == null) {
                cfg.waves.remove(i--);
                logInvalid(wavePath, "Entry is null and was removed.");
                changed = true;
                continue;
            }

            if (isBlank(wave.name)) {
                wave.name = "wave_" + i;
                logWarn(wavePath + ".name", "Missing/blank; set to '" + wave.name + "'.");
                changed = true;
            }
            if (isBlank(wave.waveSpawningMessage)) {
                wave.waveSpawningMessage = "Wave " + wave.name + " is spawning for {player} ({count} mobs planned)";
                logWarn(wavePath + ".waveSpawningMessage", "Missing/blank; set to default template.");
                changed = true;
            }
            if (wave.strengthRequirement < 0) {
                logWarn(wavePath + ".strengthRequirement", "Negative value; clamped to 0.");
                wave.strengthRequirement = 0;
                changed = true;
            }
            if (!Double.isFinite(wave.waveSpawnChance)) {
                logWarn(wavePath + ".waveSpawnChance", "Not a finite number; set to 1.0.");
                wave.waveSpawnChance = 1.0;
                changed = true;
            }
            if (wave.waveSpawnChance < 0.0 || wave.waveSpawnChance > 1.0) {
                logWarn(wavePath + ".waveSpawnChance", "Out of range; clamped to [0.0, 1.0].");
                wave.waveSpawnChance = clamp01(wave.waveSpawnChance);
                changed = true;
            }
            if (!Double.isFinite(wave.totalMobMultiplier) || wave.totalMobMultiplier <= 0.0) {
                logWarn(wavePath + ".totalMobMultiplier", "Must be > 0; set to 1.0.");
                wave.totalMobMultiplier = 1.0;
                changed = true;
            }

        if (wave.waveContent == null) {
                wave.waveContent = new ArrayList<>();
                logWarn(wavePath + ".waveContent", "Missing list; created empty list.");
                changed = true;
            }
            if (wave.dimensions == null) {
                wave.dimensions = new ArrayList<>();
                changed = true;
            }
            for (int d = 0; d < wave.dimensions.size(); d++) {
                String dim = wave.dimensions.get(d);
                if (isBlank(dim)) {
                    wave.dimensions.remove(d--);
                    changed = true;
                    continue;
                }
                ResourceLocation key = ResourceLocation.tryParse(dim);
                if (key == null) {
                    logWarn(wavePath + ".dimensions[" + d + "]", "Invalid dimension id '" + dim + "'; removed.");
                    wave.dimensions.remove(d--);
                    changed = true;
                    continue;
                }
                wave.dimensions.set(d, key.toString());
            }
            if (wave.waveDrops != null) {
                for (int d = 0; d < wave.waveDrops.size(); d++) {
                    Drop drop = wave.waveDrops.get(d);
                    String dropPath = wavePath + ".waveDrops[" + d + "]";
                    if (drop == null) {
                        wave.waveDrops.remove(d--);
                        logInvalid(dropPath, "Drop entry is null and was removed.");
                        changed = true;
                        continue;
                    }
                    if (isBlank(drop.itemId)) {
                        wave.waveDrops.remove(d--);
                        logInvalid(dropPath + ".itemId", "Missing/blank item id; drop was removed.");
                        changed = true;
                        continue;
                    }

                    ResourceLocation dropKey = ResourceLocation.tryParse(drop.itemId);
                    if (dropKey == null || !BuiltInRegistries.ITEM.containsKey(dropKey)) {
                        wave.waveDrops.remove(d--);
                        logInvalid(dropPath + ".itemId", "Unknown item id '" + drop.itemId + "'; drop was removed.");
                        changed = true;
                        continue;
                    }

                    if (drop.minCount < 0) {
                        drop.minCount = 0;
                        logWarn(dropPath + ".minCount", "Negative value; clamped to 0.");
                        changed = true;
                    }
                    if (drop.maxCount < 0) {
                        drop.maxCount = 0;
                        logWarn(dropPath + ".maxCount", "Negative value; clamped to 0.");
                        changed = true;
                    }
                    if (drop.minCount > drop.maxCount) {
                        int t = drop.minCount;
                        drop.minCount = drop.maxCount;
                        drop.maxCount = t;
                        logWarn(dropPath + ".minCount/.maxCount", "minCount > maxCount; values were swapped.");
                        changed = true;
                    }
                    if (!Float.isFinite(drop.chance)) {
                        drop.chance = 0.0f;
                        logWarn(dropPath + ".chance", "Not finite; set to 0.0.");
                        changed = true;
                    }
                    float clampedChance = (float) clamp01(drop.chance);
                    if (clampedChance != drop.chance) {
                        drop.chance = clampedChance;
                        logWarn(dropPath + ".chance", "Out of range; clamped to [0.0, 1.0].");
                        changed = true;
                    }
                }
            }

            for (int j = 0; j < wave.waveContent.size(); j++) {
                Mob mob = wave.waveContent.get(j);
                String mobPath = wavePath + ".waveContent[" + j + "]";

                if (mob == null) {
                    wave.waveContent.remove(j--);
                    logInvalid(mobPath, "Entry is null and was removed.");
                    changed = true;
                    continue;
                }

                if (isBlank(mob.entityId)) {
                    wave.waveContent.remove(j--);
                    logInvalid(mobPath + ".entityId", "Missing/blank entity id; mob was removed.");
                    changed = true;
                    continue;
                }

                ResourceLocation entityKey = ResourceLocation.tryParse(mob.entityId);
                if (entityKey == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityKey)) {
                    wave.waveContent.remove(j--);
                    logInvalid(mobPath + ".entityId", "Unknown entity id '" + mob.entityId + "'; mob was removed.");
                    changed = true;
                    continue;
                }

                if (isBlank(mob.name)) {
                    mob.name = entityKey.toString();
                    logWarn(mobPath + ".name", "Missing/blank; set to entity id.");
                    changed = true;
                }
                if (!Double.isFinite(mob.presenceWeight) || mob.presenceWeight < 0.0) {
                    mob.presenceWeight = 1.0;
                    logWarn(mobPath + ".presenceWeight", "Invalid; set to 1.0.");
                    changed = true;
                }
                if (!Double.isFinite(mob.amountMultiplier) || mob.amountMultiplier <= 0.0) {
                    mob.amountMultiplier = 1.0;
                    logWarn(mobPath + ".amountMultiplier", "Must be > 0; set to 1.0.");
                    changed = true;
                }
                if (!Double.isFinite(mob.spawnChanceMin)) {
                    mob.spawnChanceMin = 0.0;
                    logWarn(mobPath + ".spawnChanceMin", "Not finite; set to 0.0.");
                    changed = true;
                }
                if (!Double.isFinite(mob.spawnChanceMax)) {
                    mob.spawnChanceMax = 1.0;
                    logWarn(mobPath + ".spawnChanceMax", "Not finite; set to 1.0.");
                    changed = true;
                }

                double min = clamp01(mob.spawnChanceMin);
                double max = clamp01(mob.spawnChanceMax);
                if (min != mob.spawnChanceMin || max != mob.spawnChanceMax) {
                    logWarn(mobPath + ".spawnChanceMin/.spawnChanceMax", "Out of range; clamped to [0.0, 1.0].");
                    mob.spawnChanceMin = min;
                    mob.spawnChanceMax = max;
                    changed = true;
                }
                if (mob.spawnChanceMin > mob.spawnChanceMax) {
                    double t = mob.spawnChanceMin;
                    mob.spawnChanceMin = mob.spawnChanceMax;
                    mob.spawnChanceMax = t;
                    logWarn(mobPath + ".spawnChanceMin/.spawnChanceMax", "Min was greater than max; values were swapped.");
                    changed = true;
                }

                String normalizedDropMode = normalizeDropMode(mob.dropsMode);
                if (!normalizedDropMode.equals(mob.dropsMode)) {
                    logWarn(mobPath + ".dropsMode", "Invalid value '" + mob.dropsMode + "'; set to '" + normalizedDropMode + "'.");
                    mob.dropsMode = normalizedDropMode;
                    changed = true;
                }
                if (!isBlank(mob.mountEntityId)) {
                    ResourceLocation mountKey = ResourceLocation.tryParse(mob.mountEntityId);
                    if (mountKey == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(mountKey)) {
                        logWarn(mobPath + ".mountEntityId", "Unknown mount entity id '" + mob.mountEntityId + "'; cleared.");
                        mob.mountEntityId = null;
                        changed = true;
                    } else if (!LivingEntity.class.isAssignableFrom(BuiltInRegistries.ENTITY_TYPE.get(mountKey).getBaseClass())) {
                        logWarn(mobPath + ".mountEntityId", "Mount id '" + mob.mountEntityId + "' is not a living entity; cleared.");
                        mob.mountEntityId = null;
                        changed = true;
                    }
                } else if (mob.mountEntityId != null) {
                    mob.mountEntityId = null;
                    changed = true;
                }
                if (!Double.isFinite(mob.randomArmorChance)) {
                    mob.randomArmorChance = 0.0;
                    logWarn(mobPath + ".randomArmorChance", "Not finite; set to 0.0.");
                    changed = true;
                }
                double armorChance = clamp01(mob.randomArmorChance);
                if (armorChance != mob.randomArmorChance) {
                    mob.randomArmorChance = armorChance;
                    logWarn(mobPath + ".randomArmorChance", "Out of range; clamped to [0.0, 1.0].");
                    changed = true;
                }
                if (mob.randomArmorMaxPieces < 1 || mob.randomArmorMaxPieces > 4) {
                    mob.randomArmorMaxPieces = Math.max(1, Math.min(4, mob.randomArmorMaxPieces));
                    logWarn(mobPath + ".randomArmorMaxPieces", "Out of range; clamped to [1, 4].");
                    changed = true;
                }

                if (mob.drops == null) {
                    mob.drops = new ArrayList<>();
                    changed = true;
                }
                for (int k = 0; k < mob.drops.size(); k++) {
                    Drop drop = mob.drops.get(k);
                    String dropPath = mobPath + ".drops[" + k + "]";
                    if (drop == null) {
                        mob.drops.remove(k--);
                        logInvalid(dropPath, "Drop entry is null and was removed.");
                        changed = true;
                        continue;
                    }
                    if (isBlank(drop.itemId)) {
                        mob.drops.remove(k--);
                        logInvalid(dropPath + ".itemId", "Missing/blank item id; drop was removed.");
                        changed = true;
                        continue;
                    }

                    ResourceLocation dropKey = ResourceLocation.tryParse(drop.itemId);
                    if (dropKey == null || !BuiltInRegistries.ITEM.containsKey(dropKey)) {
                        mob.drops.remove(k--);
                        logInvalid(dropPath + ".itemId", "Unknown item id '" + drop.itemId + "'; drop was removed.");
                        changed = true;
                        continue;
                    }

                    if (drop.minCount < 0) {
                        drop.minCount = 0;
                        logWarn(dropPath + ".minCount", "Negative value; clamped to 0.");
                        changed = true;
                    }
                    if (drop.maxCount < 0) {
                        drop.maxCount = 0;
                        logWarn(dropPath + ".maxCount", "Negative value; clamped to 0.");
                        changed = true;
                    }
                    if (drop.minCount > drop.maxCount) {
                        int t = drop.minCount;
                        drop.minCount = drop.maxCount;
                        drop.maxCount = t;
                        logWarn(dropPath + ".minCount/.maxCount", "minCount > maxCount; values were swapped.");
                        changed = true;
                    }
                    if (!Float.isFinite(drop.chance)) {
                        drop.chance = 0.0f;
                        logWarn(dropPath + ".chance", "Not finite; set to 0.0.");
                        changed = true;
                    }
                    float clampedChance = (float) clamp01(drop.chance);
                    if (clampedChance != drop.chance) {
                        drop.chance = clampedChance;
                        logWarn(dropPath + ".chance", "Out of range; clamped to [0.0, 1.0].");
                        changed = true;
                    }
                }
            }

            if (wave.waveContent.isEmpty()) {
                logInvalid(wavePath + ".waveContent", "No valid mobs remain in this wave.");
            }
        }

        return changed;
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String normalizeDropMode(String mode) {
        if (mode == null) return "ADD";
        String normalized = mode.trim().toUpperCase();
        if ("OVERRIDE".equals(normalized)) return "OVERRIDE";
        return "ADD";
    }

    public static boolean isWaveAllowedInDimension(Wave wave, String dimensionId) {
        if (wave == null) return false;
        if (dimensionId == null || dimensionId.isBlank()) return true;
        if (wave.dimensions == null || wave.dimensions.isEmpty()) return true;
        for (String dim : wave.dimensions) {
            if (dimensionId.equals(dim)) return true;
        }
        return false;
    }

    private static void logInvalid(String keyPath, String reason) {
        AdaptiveHordes.LOGGER.error(
            ColorConstants.RED + "[waves.json] Invalid " + keyPath + " -> " + reason + ColorConstants.RESET
        );
    }

    private static void logWarn(String keyPath, String reason) {
        AdaptiveHordes.LOGGER.warn(
            ColorConstants.YELLOW + "[waves.json] Repaired " + keyPath + " -> " + reason + ColorConstants.RESET
        );
    }
}
