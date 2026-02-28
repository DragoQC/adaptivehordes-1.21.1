package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class Wave {
    public String name = "wave";
    public String displayName = "Wave";
    public String waveSpawningMessage = "A horde is spawning: {wave} targeting {player}!";
    public List<String> dimensions = new ArrayList<>(); // empty = all dimensions
    public int strengthRequirement = 0; // min player strength for this wave to be eligible
    public double waveSpawnChance = 1.0; // 0.0..1.0
    public double totalMobMultiplier = 1.0; // multiplies backend-computed total mob count
    public List<Mob> waveContent = new ArrayList<>();
    public List<Drop> waveDrops = null;     // null => use mob drops
}
