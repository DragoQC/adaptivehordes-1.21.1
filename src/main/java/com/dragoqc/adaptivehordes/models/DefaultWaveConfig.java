package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultWaveConfig {
    public String _about = "Defines wave templates and per-mob composition/drop behavior.";
    public Map<String, String> _keyInfo = new LinkedHashMap<>();
    public Map<String, String> _options = new LinkedHashMap<>();

    public List<Wave> waves = new ArrayList<>();

    // No-arg constructor required for Gson
    public DefaultWaveConfig() {
        _keyInfo.put("waves[].name", "Unique wave name used by commands and runtime.");
        _keyInfo.put("waves[].waveSpawningMessage", "Message shown on screen when this wave starts. Supports {wave}, {player}, {count}.");
        _keyInfo.put("waves[].dimensions[]", "Optional allowed dimensions. Empty means all. Example: minecraft:overworld.");
        _keyInfo.put("waves[].strengthRequirement", "Minimum player gear score required for wave eligibility.");
        _keyInfo.put("waves[].waveSpawnChance", "0.0..1.0 chance weight among eligible waves.");
        _keyInfo.put("waves[].totalMobMultiplier", "Scales total mobs planned for this wave.");
        _keyInfo.put("waves[].waveDrops[]", "Optional drops applied to every mob in this wave.");
        _keyInfo.put("waves[].waveContent[].name", "Display/debug label for mob entry.");
        _keyInfo.put("waves[].waveContent[].entityId", "Any valid living entity id to spawn (vanilla or modded).");
        _keyInfo.put("waves[].waveContent[].baseHealth", "MAX_HEALTH base applied to spawned mob.");
        _keyInfo.put("waves[].waveContent[].baseDamage", "ATTACK_DAMAGE base applied to spawned mob.");
        _keyInfo.put("waves[].waveContent[].baseSpeed", "MOVEMENT_SPEED base applied to spawned mob.");
        _keyInfo.put("waves[].waveContent[].ranged", "Metadata flag for your own balancing/UI logic.");
        _keyInfo.put("waves[].waveContent[].presenceWeight", "Relative selection weight for this mob in wave.");
        _keyInfo.put("waves[].waveContent[].amountMultiplier", "Additional per-mob amount weight multiplier.");
        _keyInfo.put("waves[].waveContent[].spawnChanceMin", "Min per-spawn-cycle chance for this mob to be active.");
        _keyInfo.put("waves[].waveContent[].spawnChanceMax", "Max per-spawn-cycle chance for this mob to be active.");
        _keyInfo.put("waves[].waveContent[].nameVisible", "true/false: if true, mob custom name is always visible.");
        _keyInfo.put("waves[].waveContent[].baby", "true/false: try spawning as baby (only works for supported mobs).");
        _keyInfo.put("waves[].waveContent[].mountEntityId", "Optional living entity id used as mount (example: creeper on bat). Also accepts mountEntityID.");
        _keyInfo.put("waves[].waveContent[].randomArmorChance", "0.0..1.0 chance this mob gets random armor pieces.");
        _keyInfo.put("waves[].waveContent[].randomArmorMaxPieces", "1..4 max random armor pieces equipped when chance passes.");
        _keyInfo.put("waves[].waveContent[].dropsMode", "How custom drops combine with vanilla drops.");
        _keyInfo.put("waves[].waveContent[].drops[]", "Per-mob custom drops for wave-spawned mobs only.");
        _keyInfo.put("Drop.itemId", "Valid item id.");
        _keyInfo.put("Drop.minCount/maxCount", "Random attempts sampled in this inclusive range.");
        _keyInfo.put("Drop.chance", "0.0..1.0 chance per attempt (independent rolls).");

        _options.put("dropsMode", "ADD, OVERRIDE");

        waves.add(wave("undead", "minecraft:overworld", 100, List.of(
            armor(mob("Undead Zombie", "minecraft:zombie", 20, 4, 0.23f, false, 1.2, 1.0, 0.65, 1.00), 0.20, 2)
                .addDrop(drop("minecraft:rotten_flesh", 1, 4, 0.60f)),
            armor(mob("Undead Skeleton", "minecraft:skeleton", 20, 4, 0.25f, true, 1.0, 1.0, 0.45, 0.90), 0.18, 2)
                .addDrop(drop("minecraft:bone", 1, 4, 0.58f)),
            mob("Undead Drowned", "minecraft:drowned", 24, 5, 0.23f, false, 0.7, 1.0, 0.25, 0.70)
                .addDrop(drop("minecraft:copper_ingot", 0, 2, 0.35f))
        ), 1.00, 1.00));

        waves.add(wave("crawley", "minecraft:overworld", 220, List.of(
            mob("Crawler Spider", "minecraft:spider", 18, 4, 0.31f, false, 1.2, 1.0, 0.60, 1.00)
                .addDrop(drop("minecraft:string", 1, 4, 0.62f)),
            mob("Crawler Toxic", "minecraft:cave_spider", 18, 4, 0.34f, false, 1.0, 1.0, 0.50, 0.90)
                .addDrop(drop("minecraft:spider_eye", 0, 2, 0.45f)),
            mob("Crawler Swarm", "minecraft:silverfish", 10, 2, 0.38f, false, 0.8, 1.1, 0.35, 0.80)
        ), 0.90, 1.15));

        waves.add(wave("boomboom", "minecraft:overworld", 340, List.of(
            mob("Boom Creeper", "minecraft:creeper", 20, 0, 0.25f, false, 1.4, 1.2, 0.65, 1.00)
                .addDrop(drop("minecraft:gunpowder", 1, 6, 0.72f))
                .addDrop(drop("minecraft:tnt", 0, 1, 0.20f)),
            mob("Boom Bat Rider", "minecraft:creeper", 18, 0, 0.27f, false, 1.15, 1.0, 0.55, 0.95, false, "minecraft:bat")
                .addDrop(drop("minecraft:gunpowder", 0, 4, 0.58f)),
            mob("Bomber Jockey", "minecraft:zombie", 22, 5, 0.31f, false, 0.8, 0.9, 0.30, 0.70, true, "minecraft:bat")
                .addDrop(drop("minecraft:gunpowder", 0, 2, 0.35f)),
            mob("Boom Skeleton Jockey", "minecraft:skeleton", 20, 4, 0.29f, true, 0.75, 0.95, 0.30, 0.75, false, "minecraft:bat")
                .addDrop(drop("minecraft:arrow", 0, 4, 0.45f))
        ), 0.78, 1.30));

        waves.add(wave("raider", "minecraft:overworld", 480, List.of(
            mob("Raider Pillager", "minecraft:pillager", 24, 7, 0.25f, true, 1.1, 1.0, 0.55, 0.95),
            mob("Raider Vindicator", "minecraft:vindicator", 28, 9, 0.27f, false, 0.9, 1.0, 0.40, 0.85)
                .addDrop(drop("minecraft:emerald", 0, 2, 0.35f)),
            mob("Raider Witch", "minecraft:witch", 26, 0, 0.25f, true, 0.6, 0.9, 0.25, 0.60)
        ), 0.66, 1.55));

        waves.add(wave("infernal_brutes", "minecraft:the_nether", 620, List.of(
            armor(mob("Brute Piglin", "minecraft:piglin_brute", 38, 10, 0.28f, false, 1.0, 1.2, 0.55, 0.95), 0.35, 3)
                .addDrop(drop("minecraft:gold_ingot", 0, 2, 0.30f)),
            mob("Hoglin Charger", "minecraft:hoglin", 44, 11, 0.30f, false, 0.9, 1.2, 0.45, 0.85),
            armor(mob("Piglin Jockey", "minecraft:piglin", 24, 6, 0.31f, false, 0.7, 1.0, 0.25, 0.65, false, "minecraft:hoglin"), 0.25, 3)
        ), 0.52, 1.85));

        waves.add(wave("nether_burn", "minecraft:the_nether", 760, List.of(
            mob("Nether Blaze", "minecraft:blaze", 28, 7, 0.23f, true, 1.0, 1.0, 0.50, 0.90)
                .addDrop(drop("minecraft:blaze_rod", 0, 2, 0.30f)),
            mob("Nether Wither Skeleton", "minecraft:wither_skeleton", 30, 8, 0.26f, false, 0.9, 1.0, 0.45, 0.85),
            mob("Nether Magma Cube", "minecraft:magma_cube", 26, 6, 0.24f, false, 0.8, 1.0, 0.35, 0.75)
        ), 0.40, 2.00));

        waves.add(wave("end_stalkers", "minecraft:the_end", 920, List.of(
            mob("End Enderman", "minecraft:enderman", 40, 9, 0.30f, false, 1.1, 1.0, 0.60, 1.00)
                .addDrop(drop("minecraft:ender_pearl", 0, 2, 0.40f)),
            mob("End Endermite", "minecraft:endermite", 8, 2, 0.35f, false, 0.9, 1.1, 0.45, 0.85),
            mob("End Phantom", "minecraft:phantom", 24, 6, 0.34f, true, 0.85, 1.0, 0.40, 0.80)
                .addDrop(drop("minecraft:phantom_membrane", 0, 2, 0.36f))
        ), 0.30, 2.20));

        waves.add(wave("apex_warden", "minecraft:overworld", 1500, List.of(
            mob("Apex Warden", "minecraft:warden", 220, 30, 0.30f, false, 1.0, 1.0, 0.20, 0.45)
                .addDrop(drop("minecraft:echo_shard", 1, 4, 0.60f))
        ), 0.08, 2.60));
    }

    private static Wave wave(String name, String dimensionId, int strength, List<Mob> content, double waveSpawnChance, double totalMobMultiplier) {
        Wave w = new Wave();
        w.name = name;
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
        String name,
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
        return mob(name, entityId, baseHealth, baseDamage, baseSpeed, ranged, presenceWeight, amountMultiplier, spawnChanceMin, spawnChanceMax, false, null);
    }

    private static Mob mob(
        String name,
        String entityId,
        int baseHealth,
        int baseDamage,
        float baseSpeed,
        boolean ranged,
        double presenceWeight,
        double amountMultiplier,
        double spawnChanceMin,
        double spawnChanceMax,
        boolean baby,
        String mountEntityId
    ) {
        Mob mob = new Mob(name, entityId, baseHealth, baseDamage, baseSpeed, ranged);
        mob.presenceWeight = presenceWeight;
        mob.amountMultiplier = amountMultiplier;
        mob.spawnChanceMin = spawnChanceMin;
        mob.spawnChanceMax = spawnChanceMax;
        mob.baby = baby;
        mob.mountEntityId = mountEntityId;
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
}
