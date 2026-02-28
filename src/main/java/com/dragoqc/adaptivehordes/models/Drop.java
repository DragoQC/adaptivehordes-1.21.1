package com.dragoqc.adaptivehordes.models;

public class Drop {
	public String itemId; // "minecraft:iron_ingot"
	public int minCount;
	public int maxCount;
	public float chance; // 0.0 to 1.0

	public Drop() {}

	public Drop(String itemId, int minCount, int maxCount, float chance) {
		this.itemId = itemId;
		this.minCount = minCount;
		this.maxCount = maxCount;
		this.chance = chance;
	}
}
