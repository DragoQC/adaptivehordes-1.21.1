package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class DefaultWaveConfig {
    public List<Wave> waves = new ArrayList<>();

    // No-arg constructor required for Gson
    public DefaultWaveConfig() {
        waves.add(wave("undead", "minecraft:overworld", 100, List.of(
            armor(mob("minecraft:zombie", 20, 4, 0.23f, false, 1.2, 1.0, 0.65, 1.00), 0.20, 2)
                .addDrop(drop("minecraft:rotten_flesh", 1, 4, 0.60f)),
            armor(mob("minecraft:skeleton", 20, 4, 0.25f, true, 1.0, 1.0, 0.45, 0.90), 0.18, 2)
                .addDrop(drop("minecraft:bone", 1, 4, 0.58f)),
            mob("minecraft:drowned", 24, 5, 0.23f, false, 0.7, 1.0, 0.25, 0.70)
                .addDrop(drop("minecraft:copper_ingot", 0, 2, 0.35f))
        ), 1.00, 1.00));

        waves.add(wave("crawley", "minecraft:overworld", 220, List.of(
            mob("minecraft:spider", 18, 4, 0.31f, false, 1.2, 1.0, 0.60, 1.00)
                .addDrop(drop("minecraft:string", 1, 4, 0.62f)),
            mob("minecraft:cave_spider", 18, 4, 0.34f, false, 1.0, 1.0, 0.50, 0.90)
                .addDrop(drop("minecraft:spider_eye", 0, 2, 0.45f)),
            mob("minecraft:silverfish", 10, 2, 0.38f, false, 0.8, 1.1, 0.35, 0.80)
        ), 0.90, 1.15));

        waves.add(wave("boomboom", "minecraft:overworld", 340, List.of(
            mob("minecraft:creeper", 20, 0, 0.25f, false, 1.4, 1.2, 0.65, 1.00)
                .addDrop(drop("minecraft:gunpowder", 1, 6, 0.72f))
                .addDrop(drop("minecraft:tnt", 0, 1, 0.20f)),
            mob("minecraft:creeper", 22, 0, 0.27f, false, 1.15, 1.0, 0.55, 0.95)
                .addDrop(drop("minecraft:gunpowder", 0, 4, 0.58f)),
            mob("minecraft:zombie", 22, 5, 0.31f, false, 0.8, 0.9, 0.30, 0.70, true)
                .addDrop(drop("minecraft:gunpowder", 0, 2, 0.35f)),
            mob("minecraft:skeleton", 20, 4, 0.29f, true, 0.75, 0.95, 0.30, 0.75)
                .addDrop(drop("minecraft:arrow", 0, 4, 0.45f))
        ), 0.78, 1.30));

        waves.add(wave("raider", "minecraft:overworld", 480, List.of(
            mob("minecraft:pillager", 24, 7, 0.25f, true, 1.1, 1.0, 0.55, 0.95),
            mob("minecraft:vindicator", 28, 9, 0.27f, false, 0.9, 1.0, 0.40, 0.85)
                .addDrop(drop("minecraft:emerald", 0, 2, 0.35f)),
            mob("minecraft:witch", 26, 0, 0.25f, true, 0.6, 0.9, 0.25, 0.60)
        ), 0.66, 1.55));

        waves.add(wave("infernal-brutes", "minecraft:the_nether", 620, List.of(
            armor(mob("minecraft:piglin_brute", 38, 10, 0.28f, false, 1.0, 1.2, 0.55, 0.95), 0.35, 3)
                .addDrop(drop("minecraft:gold_ingot", 0, 2, 0.30f)),
            mob("minecraft:hoglin", 44, 11, 0.30f, false, 0.9, 1.2, 0.45, 0.85),
            armor(mob("minecraft:piglin", 24, 6, 0.31f, false, 0.7, 1.0, 0.25, 0.65), 0.25, 3)
        ), 0.52, 1.85));

        waves.add(wave("nether-burn", "minecraft:the_nether", 760, List.of(
            mob("minecraft:blaze", 28, 7, 0.23f, true, 1.0, 1.0, 0.50, 0.90)
                .addDrop(drop("minecraft:blaze_rod", 0, 2, 0.30f)),
            mob("minecraft:wither_skeleton", 30, 8, 0.26f, false, 0.9, 1.0, 0.45, 0.85),
            mob("minecraft:magma_cube", 26, 6, 0.24f, false, 0.8, 1.0, 0.35, 0.75)
        ), 0.40, 2.00));

        waves.add(wave("end-stalkers", "minecraft:the_end", 920, List.of(
            mob("minecraft:enderman", 40, 9, 0.30f, false, 1.1, 1.0, 0.60, 1.00)
                .addDrop(drop("minecraft:ender_pearl", 0, 2, 0.40f)),
            mob("minecraft:endermite", 8, 2, 0.35f, false, 0.9, 1.1, 0.45, 0.85),
            mob("minecraft:phantom", 24, 6, 0.34f, true, 0.85, 1.0, 0.40, 0.80)
                .addDrop(drop("minecraft:phantom_membrane", 0, 2, 0.36f))
        ), 0.30, 2.20));

        waves.add(wave("zombie-legion", "minecraft:overworld", 160, List.of(
            mob("minecraft:zombie", 24, 5, 0.24f, false, 1.2, 1.1, 0.65, 1.00),
            mob("minecraft:husk", 24, 5, 0.25f, false, 0.8, 1.0, 0.45, 0.85),
            mob("minecraft:zombie", 18, 4, 0.33f, false, 0.5, 0.8, 0.20, 0.60, true)
        ), 0.92, 1.10));

        waves.add(wave("spider-brood", "minecraft:overworld", 260, List.of(
            mob("minecraft:spider", 20, 5, 0.31f, false, 1.1, 1.0, 0.60, 1.00),
            mob("minecraft:cave_spider", 20, 5, 0.34f, false, 1.0, 1.0, 0.55, 0.95),
            mob("minecraft:endermite", 10, 2, 0.36f, false, 0.5, 0.9, 0.25, 0.70)
        ), 0.85, 1.20));

        waves.add(wave("silk-sting-swarm", "minecraft:overworld", 320, List.of(
            mob("minecraft:spider", 22, 5, 0.32f, false, 1.1, 1.0, 0.50, 0.95),
            mob("minecraft:cave_spider", 22, 6, 0.35f, false, 1.0, 1.0, 0.55, 1.00),
            mob("minecraft:silverfish", 12, 3, 0.40f, false, 0.9, 1.2, 0.45, 0.90)
        ), 0.80, 1.35));

        waves.add(wave("creeper-storm", "minecraft:overworld", 380, List.of(
            mob("minecraft:creeper", 24, 0, 0.27f, false, 1.3, 1.1, 0.60, 1.00)
                .addDrop(drop("minecraft:gunpowder", 1, 6, 0.70f)),
            mob("minecraft:creeper", 28, 0, 0.26f, false, 0.8, 0.9, 0.35, 0.75)
                .addDrop(drop("minecraft:tnt", 0, 1, 0.18f)),
            mob("minecraft:skeleton", 22, 5, 0.29f, true, 0.5, 0.9, 0.20, 0.60)
        ), 0.74, 1.45));

        waves.add(wave("drowned-tide", "minecraft:overworld", 430, List.of(
            mob("minecraft:drowned", 30, 7, 0.24f, false, 1.1, 1.0, 0.55, 0.95),
            mob("minecraft:zombie", 26, 6, 0.24f, false, 0.8, 1.0, 0.35, 0.80),
            mob("minecraft:skeleton", 22, 5, 0.26f, true, 0.6, 0.9, 0.25, 0.65)
        ), 0.70, 1.55));

        waves.add(wave("frost-bone", "minecraft:overworld", 520, List.of(
            mob("minecraft:stray", 28, 7, 0.25f, true, 1.1, 1.0, 0.55, 0.95),
            mob("minecraft:skeleton", 24, 6, 0.26f, true, 0.9, 1.0, 0.45, 0.85),
            mob("minecraft:zombie", 30, 7, 0.24f, false, 0.6, 0.95, 0.25, 0.65)
        ), 0.62, 1.70));

        waves.add(wave("skeleton-breeze", "minecraft:overworld", 580, List.of(
            mob("minecraft:breeze", 28, 7, 0.30f, true, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:skeleton", 26, 7, 0.27f, true, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:stray", 28, 7, 0.26f, true, 0.8, 0.95, 0.35, 0.75)
        ), 0.58, 1.85));

        waves.add(wave("raider-siege", "minecraft:overworld", 700, List.of(
            mob("minecraft:pillager", 28, 8, 0.26f, true, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:vindicator", 34, 11, 0.28f, false, 0.9, 1.0, 0.45, 0.85),
            mob("minecraft:evoker", 30, 0, 0.24f, true, 0.5, 0.8, 0.20, 0.55)
        ), 0.46, 2.00));

        waves.add(wave("overworld-piglin-remnants", "minecraft:overworld", 780, List.of(
            mob("minecraft:zombified_piglin", 32, 9, 0.29f, false, 1.1, 1.0, 0.55, 0.95),
            mob("minecraft:zombie", 30, 8, 0.25f, false, 0.7, 0.95, 0.30, 0.70),
            mob("minecraft:blaze", 30, 8, 0.24f, true, 0.5, 0.85, 0.20, 0.55)
        ), 0.38, 2.15));

        waves.add(wave("nether-skirmish", "minecraft:the_nether", 680, List.of(
            mob("minecraft:piglin", 28, 8, 0.30f, false, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:hoglin", 38, 10, 0.30f, false, 0.9, 1.1, 0.45, 0.85),
            mob("minecraft:zombified_piglin", 30, 8, 0.29f, false, 0.8, 1.0, 0.35, 0.75)
        ), 0.49, 1.95));

        waves.add(wave("lava-legion", "minecraft:the_nether", 860, List.of(
            mob("minecraft:blaze", 34, 9, 0.24f, true, 1.1, 1.0, 0.55, 0.95),
            mob("minecraft:magma_cube", 34, 8, 0.25f, false, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:ghast", 26, 10, 0.24f, true, 0.5, 0.8, 0.20, 0.55)
        ), 0.34, 2.20));

        waves.add(wave("brute-assault", "minecraft:the_nether", 980, List.of(
            armor(mob("minecraft:piglin_brute", 46, 13, 0.30f, false, 1.0, 1.1, 0.55, 0.95), 0.45, 4),
            mob("minecraft:hoglin", 48, 12, 0.31f, false, 0.9, 1.1, 0.50, 0.90),
            mob("minecraft:blaze", 34, 9, 0.24f, true, 0.6, 0.9, 0.25, 0.65)
        ), 0.28, 2.35));

        waves.add(wave("soul-forge", "minecraft:the_nether", 1120, List.of(
            mob("minecraft:wither_skeleton", 38, 11, 0.28f, false, 1.0, 1.0, 0.55, 0.95),
            mob("minecraft:skeleton", 30, 8, 0.27f, true, 0.8, 0.95, 0.40, 0.80),
            mob("minecraft:blaze", 34, 9, 0.24f, true, 0.7, 0.9, 0.30, 0.70)
        ), 0.20, 2.45));

        waves.add(wave("end-swarm", "minecraft:the_end", 1050, List.of(
            mob("minecraft:endermite", 12, 3, 0.38f, false, 1.2, 1.2, 0.60, 1.00),
            mob("minecraft:enderman", 42, 10, 0.31f, false, 0.9, 1.0, 0.45, 0.85),
            mob("minecraft:phantom", 26, 7, 0.34f, true, 0.8, 1.0, 0.35, 0.75)
        ), 0.24, 2.30));

        waves.add(wave("void-hunters", "minecraft:the_end", 1180, List.of(
            mob("minecraft:enderman", 44, 11, 0.31f, false, 1.0, 1.0, 0.55, 0.95),
            mob("minecraft:shulker", 42, 0, 0.20f, true, 0.8, 0.9, 0.35, 0.70),
            mob("minecraft:phantom", 28, 8, 0.35f, true, 0.7, 0.95, 0.30, 0.70)
        ), 0.18, 2.50));

        waves.add(wave("end-zombie-exiles", "minecraft:the_end", 1260, List.of(
            mob("minecraft:zombie", 36, 10, 0.27f, false, 1.0, 1.0, 0.50, 0.90),
            mob("minecraft:husk", 38, 10, 0.28f, false, 0.9, 1.0, 0.45, 0.85),
            mob("minecraft:enderman", 46, 11, 0.31f, false, 0.7, 0.95, 0.30, 0.70)
        ), 0.13, 2.55));

        waves.add(wave("phantom-sky", "minecraft:overworld", 1320, List.of(
            mob("minecraft:phantom", 34, 9, 0.36f, true, 1.1, 1.1, 0.60, 1.00),
            mob("minecraft:stray", 30, 8, 0.27f, true, 0.8, 0.95, 0.35, 0.75)
        ), 0.12, 2.40));

        waves.add(wave("apex-warden", "minecraft:overworld", 1500, List.of(
            mob("minecraft:warden", 220, 30, 0.30f, false, 1.0, 1.0, 0.20, 0.45)
                .addDrop(drop("minecraft:echo_shard", 1, 4, 0.60f))
        ), 0.08, 2.60));
    }

    private static Wave wave(String name, String dimensionId, int strength, List<Mob> content, double waveSpawnChance, double totalMobMultiplier) {
        Wave w = new Wave();
        w.name = name;
        w.displayName = toDisplayName(name);
        w.waveSpawningMessage = "Wave " + name + " is spawning for {player} ({count} mobs planned)";
        w.dimensions.add(dimensionId);
        w.strengthRequirement = strength;
        w.waveSpawnChance = waveSpawnChance;
        w.totalMobMultiplier = totalMobMultiplier;
        w.waveContent = new ArrayList<>(content);
        w.waveDrops = null; // default
        return w;
    }

    private static Mob mob(
        String entityId,
        int baseHealth,
        int baseDamage,
        float baseSpeed,
        boolean ranged,
        double presenceWeight,
        double amountMultiplier,
        double spawnChanceMin,
        double spawnChanceMax
    ) {
        return mob(entityId, baseHealth, baseDamage, baseSpeed, ranged, presenceWeight, amountMultiplier, spawnChanceMin, spawnChanceMax, false);
    }

    private static Mob mob(
        String entityId,
        int baseHealth,
        int baseDamage,
        float baseSpeed,
        boolean ranged,
        double presenceWeight,
        double amountMultiplier,
        double spawnChanceMin,
        double spawnChanceMax,
        boolean baby
    ) {
        Mob mob = new Mob(entityId, baseHealth, baseDamage, baseSpeed, ranged);
        mob.presenceWeight = presenceWeight;
        mob.amountMultiplier = amountMultiplier;
        mob.spawnChanceMin = spawnChanceMin;
        mob.spawnChanceMax = spawnChanceMax;
        mob.baby = baby;
        return mob;
    }

    private static Drop drop(String itemId, int min, int max, float chance) {
        return new Drop(itemId, min, max, chance);
    }

    private static Mob armor(Mob mob, double chance, int maxPieces) {
        mob.randomArmorChance = chance;
        mob.randomArmorMaxPieces = maxPieces;
        return mob;
    }

    private static String toDisplayName(String codeName) {
        if (codeName == null || codeName.isBlank()) return "Wave";
        String[] parts = codeName.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1));
        }
        return out.toString();
    }
}
