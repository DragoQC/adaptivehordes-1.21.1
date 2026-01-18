package com.dragoqc.adaptivehordes.models;

public class PlayerScanResult {
    // Player identity
    public String name;
    public String gameTime;
		public String gameTimeInHours;
    
    // Combat stats
    public double meleePower;
    public double arrowPower;
    
    // Defense stats
    public int totalArmor;
    public double totalArmorToughness;
    public double totalHealth;
    
    // Additional tracking
    public int enchantmentCount;
    public int gearScore;  // Overall power rating
    
    // No-arg constructor for Gson
    public PlayerScanResult() {}
    
    public PlayerScanResult(String name) {
        this.name = name;
    }
    
    /**
     * Calculate overall gear score based on all stats
     */
    public void calculateGearScore(int armorPointValue, int enchantmentValue, int weaponDamageValue) {
        this.gearScore = (int) (
            (totalArmor * armorPointValue) +
            (enchantmentCount * enchantmentValue) +
            (meleePower * weaponDamageValue) +
            (arrowPower * weaponDamageValue * 0.8) +
            (totalArmorToughness * 5) +
            (totalHealth * 2)
        );
    }
}