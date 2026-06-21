package com.dragoqc.adaptivehordes.models;

public class DefaultMobConfig {
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
	
	public DefaultMobConfig() {}
}
