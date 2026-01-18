package com.dragoqc.adaptivehordes.models;

import java.util.ArrayList;
import java.util.List;

public class MobWave {
    public String name;
    public int strengthRequirement;         // 100..600
    public List<Mob> waveContent = new ArrayList<>();
    public List<Drop> waveDrops = null;     // null => use mob drops
}
