package com.dragoqc.adaptivehordes.models;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultMobConfig {
	public String _about = "Runtime behavior for wave-spawned mobs (targeting, cleanup, spawn placement).";
	public Map<String, String> _keyInfo = new LinkedHashMap<>();

	public int mobDetectionRange = 64;
	public boolean persistentTargeting = true;
	public boolean sunlightImmunity = false;
	public double sizeMultiplier = 1.0;
	public int waveMobLifetimeTicks = 24000;
	public int stuckCheckIntervalTicks = 40;
	public int maxStuckTicks = 600;
	public int maxDistanceFromTarget = 192;
	public int spawnMinDistance = 28;
	public int spawnMaxDistance = 56;
	public int spawnPositionAttempts = 18;
	
	public DefaultMobConfig() {
		_keyInfo.put("mobDetectionRange", "Follow range applied to spawned wave mobs.");
		_keyInfo.put("persistentTargeting", "true/false: force wave mobs to keep chasing assigned target.");
		_keyInfo.put("sunlightImmunity", "true/false: if true, clears fire on wave mobs during daytime.");
		_keyInfo.put("sizeMultiplier", "Applied to Attributes.SCALE for spawned wave mobs. 1.0 = normal size.");
		_keyInfo.put("waveMobLifetimeTicks", "Max lifetime before wave mobs are despawned.");
		_keyInfo.put("stuckCheckIntervalTicks", "How often stuck detection runs.");
		_keyInfo.put("maxStuckTicks", "How long a mob can remain stuck before despawn.");
		_keyInfo.put("maxDistanceFromTarget", "If farther than this from preferred target, despawn.");
		_keyInfo.put("spawnMinDistance", "Minimum spawn radius around target player.");
		_keyInfo.put("spawnMaxDistance", "Maximum spawn radius around target player.");
		_keyInfo.put("spawnPositionAttempts", "Attempts to find valid ground spawn location.");
	}
}
