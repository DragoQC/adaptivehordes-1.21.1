package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class DefaultWaveConfig {

    public List<MobWave> waves = new ArrayList<>();

    // No-arg constructor required for Gson
    public DefaultWaveConfig() {
        // 6 default waves
        waves.add(wave("wave_100", 100, List.of(
            new Mob("Brute",  "minecraft:zombie",   20, 4, 0.23f, false),
            new Mob("Archer", "minecraft:skeleton", 16, 4, 0.25f, true),
            new Mob("Booms",  "minecraft:creeper",  18, 0, 0.23f, false)
        )));

        waves.add(wave("wave_200", 200, List.of(
            new Mob("Brute",  "minecraft:zombie",   24, 5, 0.23f, false),
            new Mob("Archer", "minecraft:skeleton", 18, 5, 0.25f, true),
            new Mob("Scout",  "minecraft:spider",   16, 4, 0.30f, false)
        )));

        waves.add(wave("wave_300", 300, List.of(
            new Mob("Brute",  "minecraft:husk",     26, 6, 0.23f, false),
            new Mob("Archer", "minecraft:skeleton", 20, 6, 0.25f, true),
            new Mob("Caster", "minecraft:witch",    26, 0, 0.25f, true)
        )));

        waves.add(wave("wave_400", 400, List.of(
            new Mob("Tank",   "minecraft:zombie",   34, 7, 0.22f, false),
            new Mob("Archer", "minecraft:stray",    22, 7, 0.25f, true),
            new Mob("Booms",  "minecraft:creeper",  22, 0, 0.24f, false)
        )));

        waves.add(wave("wave_500", 500, List.of(
            new Mob("Tank",    "minecraft:drowned",   34, 7, 0.23f, false),
            new Mob("Captain", "minecraft:pillager",  24, 8, 0.25f, true),
            new Mob("Runner",  "minecraft:spider",    20, 6, 0.32f, false)
        )));

        waves.add(wave("wave_600", 600, List.of(
            new Mob("Elite",  "minecraft:vindicator", 30, 10, 0.27f, false),
            new Mob("Caster", "minecraft:witch",      30, 0,  0.25f, true),
            new Mob("Booms",  "minecraft:creeper",    24, 0,  0.24f, false)
        )));
    }

    private static MobWave wave(String name, int strength, List<Mob> content) {
        MobWave w = new MobWave();
        w.name = name;
        w.strengthRequirement = strength;
        w.waveContent = new ArrayList<>(content);
        w.waveDrops = null; // default
        return w;
    }
}
