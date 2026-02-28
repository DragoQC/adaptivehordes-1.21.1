package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class Mob {
    public String entityId;      // "minecraft:zombie"
    public int baseHealth;
    public int baseDamage;
    public float baseSpeed;
    public boolean ranged;
    public double presenceWeight = 1.0; // relative chance in this wave
    public double amountMultiplier = 1.0; // multiplier for this mob amount inside the wave
    public double spawnChanceMin = 0.0; // 0.0..1.0
    public double spawnChanceMax = 1.0; // 0.0..1.0
    public boolean baby = false; // only applies to mobs that support baby variants
    public double randomArmorChance = 0.0; // 0.0..1.0 chance this mob gets random armor pieces
    public int randomArmorMaxPieces = 4; // max random armor pieces to equip when chance passes
    public String dropsMode = "ADD"; // ADD or OVERRIDE (for this wave-spawned mob only)
    public List<Drop> drops = new ArrayList<>();

    public Mob() {}

    public Mob(String entityId, int baseHealth, int baseDamage, float baseSpeed, boolean ranged) {
        this.entityId = entityId;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.ranged = ranged;
    }

    public Mob addDrop(Drop drop) {
        this.drops.add(drop);
        return this;
    }
}
