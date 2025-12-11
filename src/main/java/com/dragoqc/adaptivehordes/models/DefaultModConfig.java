package com.dragoqc.adaptivehordes.models;

public class DefaultModConfig {
	public boolean enableHordes = true;
	public int hordeStartDelay = 6000; //Horde annoncement
	public int hordeSpawnDelay = 6050; //Horde actual spawn
	public int hordeCheckInterval = 24000;
	public int playerScanDelay = 6000;
	public int playerScanInterval = 24000;
	public int baseHordeSize = 10;

	// No-arg constructor required for Gson
	public DefaultModConfig() {}
}
