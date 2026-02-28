package com.dragoqc.adaptivehordes.models;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultModConfig {
	public String _about = "Main adaptivehorde behavior and spawn pacing settings.";
	public Map<String, String> _keyInfo = new LinkedHashMap<>();

	public boolean enableHordes = true;
	public int waveAnnounceDelay = 6000;
	public int waveSpawnDelay = 6050; 
	public int waveCheckInterval = 24000;
	public int liveScanUpdateIntervalTicks = 40;
	public int liveScanMaxStaleTicks = 200;
	public int baseHordeSize = 10;
	public int waveSpawnWindowStartTick = 13000;
	public int waveSpawnWindowEndTick = 22000;
	public int maxMobsPerSpawnBatch = 8;
	public int lowLoadMobCountReference = 50;
	public int highLoadMobCountReference = 5000;
	public int lowLoadSpawnDelayTicks = 500;
	public int highLoadSpawnDelayTicks = 5;

	// No-arg constructor required for Gson
	public DefaultModConfig() {
		_keyInfo.put("enableHordes", "true/false: master switch for the whole wave system.");
		_keyInfo.put("waveAnnounceDelay", "Tick offset inside waveCheckInterval when warning message is sent.");
		_keyInfo.put("waveSpawnDelay", "Tick offset inside waveCheckInterval when wave queue is created.");
		_keyInfo.put("waveCheckInterval", "How often a new wave cycle happens. 24000 = one Minecraft day.");
		_keyInfo.put("liveScanUpdateIntervalTicks", "Live in-memory scan check interval per player. Lower = more reactive.");
		_keyInfo.put("liveScanMaxStaleTicks", "Forces refresh if no rescan happened for this many ticks.");
		_keyInfo.put("baseHordeSize", "Baseline mob count before wave/scaling multipliers.");
		_keyInfo.put("waveSpawnWindowStartTick", "Start tick of allowed spawn window (0..23999).");
		_keyInfo.put("waveSpawnWindowEndTick", "End tick of allowed spawn window (0..23999).");
		_keyInfo.put("maxMobsPerSpawnBatch", "Maximum mobs spawned per queue batch.");
		_keyInfo.put("lowLoadMobCountReference", "Reference wave size considered 'small'.");
		_keyInfo.put("highLoadMobCountReference", "Reference wave size considered 'huge'.");
		_keyInfo.put("lowLoadSpawnDelayTicks", "Delay between batches when wave size is around lowLoadMobCountReference.");
		_keyInfo.put("highLoadSpawnDelayTicks", "Delay between batches when wave size is around highLoadMobCountReference.");
	}
}
