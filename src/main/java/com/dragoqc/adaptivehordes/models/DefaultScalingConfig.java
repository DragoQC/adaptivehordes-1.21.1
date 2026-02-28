package com.dragoqc.adaptivehordes.models;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultScalingConfig {
    public String _about = "Player power scoring settings used by wave selection.";
    public Map<String, String> _keyInfo = new LinkedHashMap<>();

    public int armorPointValue = 10;
    public int enchantmentValue = 5;
    public int weaponDamageValue = 20;

    // Ranged damage estimation knobs (editable in ScalingConfig.json).
    // These values are used for threat scoring, not exact combat simulation.
    public double vanillaBowBaseDamage = 6.0;
    public double vanillaCrossbowBaseDamage = 9.0;
    public double moddedRangedFallbackDamage = 5.0;

    public double bowPowerLevelBonus = 1.0;
    public double bowFlameBonus = 1.0;
    public double crossbowPiercingLevelBonus = 0.35;
    public double crossbowMultishotBonus = 1.0;
    public double crossbowQuickChargeLevelBonus = 0.2;
    public boolean weaponPowerHealthScalingEnabled = true;
    public double weaponPowerHealthBonusMin = 0.05;
    public double weaponPowerHealthBonusMax = 0.10;
    public double weaponPowerForMaxHealthBonus = 30.0;

    // Per-item overrides for modded ranged weapons.
    // Key: item id (example: "modid:my_bow"), Value: estimated ranged power.
    public Map<String, Double> rangedWeaponOverrides = new HashMap<>();
    
    public DefaultScalingConfig() {
        _keyInfo.put("armorPointValue", "Points per armor point.");
        _keyInfo.put("enchantmentValue", "Points per enchanted item.");
        _keyInfo.put("weaponDamageValue", "Points per weapon damage unit.");
        _keyInfo.put("vanillaBowBaseDamage", "Base threat value for bows before enchant bonuses.");
        _keyInfo.put("vanillaCrossbowBaseDamage", "Base threat value for crossbows before enchant bonuses.");
        _keyInfo.put("moddedRangedFallbackDamage", "Fallback threat value for ranged-like modded items when no better signal exists.");
        _keyInfo.put("bowPowerLevelBonus", "Extra threat per Power level on bows.");
        _keyInfo.put("bowFlameBonus", "Flat bonus if bow has Flame.");
        _keyInfo.put("crossbowPiercingLevelBonus", "Extra threat per Piercing level.");
        _keyInfo.put("crossbowMultishotBonus", "Flat bonus if crossbow has Multishot.");
        _keyInfo.put("crossbowQuickChargeLevelBonus", "Extra threat per Quick Charge level.");
        _keyInfo.put("weaponPowerHealthScalingEnabled", "true/false: adds health bonus to wave mobs based on target player's weapon power.");
        _keyInfo.put("weaponPowerHealthBonusMin", "Minimum bonus ratio applied when scaling is active (0.05 = +5%).");
        _keyInfo.put("weaponPowerHealthBonusMax", "Maximum bonus ratio applied (0.10 = +10%).");
        _keyInfo.put("weaponPowerForMaxHealthBonus", "Weapon power value that reaches weaponPowerHealthBonusMax.");
        _keyInfo.put("rangedWeaponOverrides", "Map of itemId -> exact ranged threat value override.");
    }
}
