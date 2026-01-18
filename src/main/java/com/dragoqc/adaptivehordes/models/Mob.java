package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class Mob {
    public String name;
    public String entityId;      // "minecraft:zombie"
    public int baseHealth;
    public int baseDamage;
    public float baseSpeed;
    public boolean ranged;
    public List<Drop> drops = new ArrayList<>();

    public Mob addDrop(Drop drop) {
        this.drops.add(drop);
        return this;
    }
		public Mob() {}
}
