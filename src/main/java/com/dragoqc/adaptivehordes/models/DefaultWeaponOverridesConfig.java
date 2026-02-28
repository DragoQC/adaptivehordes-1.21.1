package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultWeaponOverridesConfig {
    public String _about = "Manual weapon overrides for modded items that do not expose reliable damage data.";
    public Map<String, String> _keyInfo = new LinkedHashMap<>();
    public List<WeaponOverrideEntry> overrides = new ArrayList<>();

    public DefaultWeaponOverridesConfig() {
        _keyInfo.put("overrides[].itemId", "Exact item id (modid:item_name).");
        _keyInfo.put("overrides[].ranged", "true for ranged scoring, false for melee scoring.");
        _keyInfo.put("overrides[].damage", "Manual damage/threat value to use in gear score.");
    }
}
