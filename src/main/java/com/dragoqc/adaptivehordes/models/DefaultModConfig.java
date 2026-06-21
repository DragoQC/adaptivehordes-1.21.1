package com.dragoqc.adaptivehordes.models;

public class DefaultModConfig {
	public boolean enableHordes = true;
	public int waveCheckInterval = 24000;
	public int liveScanUpdateIntervalTicks = 40;
	public int liveScanMaxStaleTicks = 200;
	public int baseHordeSize = 10;
	public int waveSpawnWindowStartTick = 13000;
	public int waveSpawnWindowEndTick = 22000;
	public int maxMobsPerSpawnBatch = 8;
	public int maxLiveMobsPerPlayer = 24;
	public int loadSpawnDelayTicks = 500;

	// No-arg constructor required for Gson
	public DefaultModConfig() {}
}
