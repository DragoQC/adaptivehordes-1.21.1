package com.dragoqc.adaptivehordes.models;

import java.util.HashMap;
import java.util.Map;

public class DefaultScalingConfig {
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
    
    public DefaultScalingConfig() {}
}
