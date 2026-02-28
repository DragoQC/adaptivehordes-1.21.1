🌊 Adaptive Horde

Dynamic mob hordes that scale with your power

📖 Overview
Adaptive Horde is a server-side mod that brings true progression-based difficulty to Minecraft. Instead of relying on arbitrary day counts or manual difficulty settings, the mod analyzes each player's gear and dynamically spawns hordes of mobs that match their power level.
The better your equipment, the stronger and more numerous the enemies become. No more steamrolling through the night once you get diamond armor - the world fights back!

✨ Features
🎯 Gear-Based Scaling

Calculates a "threat level" based on your armor, weapons, and enchantments
More powerful gear = larger, stronger hordes
Each player is tracked individually - no punishing new players!

🧟 Universal Mob Support

Works with ANY mob from ANY mod
Vanilla zombies, skeletons, creepers
Modded mobs automatically supported
No configuration needed for mod compatibility

🌙 Customizable Horde System

Configure when hordes spawn (every night, every X days, random chance)
Set base horde sizes and scaling multipliers
Adjust mob detection ranges
Enable/disable persistent mob targeting

⚙️ Advanced Mob Customization

Make zombies immune to sunlight during hordes
Scale mob size and attributes
Persistent targeting - mobs never lose focus on their target
Increased detection range for challenging gameplay

📊 Per-Player Difficulty

Each player's gear is tracked separately
Multiplayer-friendly: experienced players face tougher mobs, new players start easier
Server-side processing ensures consistent behavior

## Editable-first workflow

Most balancing can now be done through JSON files instead of Java edits.

1. Start the game/server once so default configs are generated in:
`config/adaptivehordes/`
2. Edit these files:
- `ModConfig.json` (timing, enable/disable, base size)
- `ScalingConfig.json` (gear score weights + ranged weapon estimation)
- `MobConfig.json` (mob behavior toggles)
- `waves.json` (wave definitions, chance, multipliers, mob compositions)
3. In game/server console, run:
`/adaptivehorde reload`

This reloads config files from disk without restarting Minecraft.

Admin commands (all start with `adaptivehorde`):
- `/adaptivehorde reload`
- `/adaptivehorde waves`
- `/adaptivehorde wave info <name>`
- `/adaptivehorde spawn wave <name> [target]`
- `/adaptivehorde wave clear [name]`
- `/adaptivehorde wave resetdefaults`
- `/adaptivehorde scan player` (scan all online players)
- `/adaptivehorde scan player <target>`
- `/adaptivehorde ignore <target>`
- `/adaptivehorde unignore <target>`
- `/adaptivehorde ignore list`
- `/adaptivehorde delete config sure sure sure`
- `/adaptivehorde delete player scans sure sure sure`

Player command (no admin required):
- `/adaptivehorde announcements on|off|toggle`

Default waves now include:
- `undead`
- `crawley`
- `boomboom`
- `raider`
- `infernal_brutes`
- `nether_burn`
- `end_stalkers`
- `apex_warden`

Waves can be dimension-specific using `waves[].dimensions`.
- `minecraft:overworld`
- `minecraft:the_nether`
- `minecraft:the_end`
- empty list means all dimensions

Per-mob spawning extras in `waves.json`:
- `name` and `nameVisible`
- `baby` (`true/false`)
- `mountEntityId` (any living entity id, example `minecraft:bat`)

Per-wave message in `waves.json`:
- `waveSpawningMessage` supports `{wave}`, `{player}`, `{count}` placeholders.
- message is shown to the targeted player only and respects their announcement on/off toggle.

Wave spawn pacing is configurable in `ModConfig.json`:
- `waveSpawnWindowStartTick` and `waveSpawnWindowEndTick` (default `13000..22000`)
- `maxMobsPerSpawnBatch`
- `lowLoadMobCountReference` / `highLoadMobCountReference`
- `lowLoadSpawnDelayTicks` / `highLoadSpawnDelayTicks`

This spreads spawning during the configured night window instead of spawning all mobs at once.

Weapon-power health scaling in `ScalingConfig.json`:
- `weaponPowerHealthScalingEnabled`
- `weaponPowerHealthBonusMin`
- `weaponPowerHealthBonusMax`
- `weaponPowerForMaxHealthBonus`

`ScalingConfig.json` supports `rangedWeaponOverrides` so you can define exact power for modded bows/weapons by item id.

`waves.json` supports:
- `waveSpawnChance` (0.0 to 1.0) per wave
- `totalMobMultiplier` per wave
- per mob: `presenceWeight`, `amountMultiplier`, `spawnChanceMin`, `spawnChanceMax`, `dropsMode`

On load, waves are validated. Invalid values are repaired and logged to console with colored warnings/errors.

Wave spawning/runtime controls in `MobConfig.json`:
- `waveMobLifetimeTicks`
- `stuckCheckIntervalTicks`
- `maxStuckTicks`
- `maxDistanceFromTarget`
- `spawnMinDistance`
- `spawnMaxDistance`
- `spawnPositionAttempts`

Wave mobs are tagged with persistent data and cleaned up automatically if expired or stuck.

Drop behavior for wave mobs:
- `dropsMode: "ADD"` keeps vanilla drops and adds configured drops.
- `dropsMode: "OVERRIDE"` removes vanilla drops and only uses configured drops.
- This applies only to mobs tagged as wave-spawned by the wave system.

Configured drop chance is rolled per item attempt:
- if a drop has `minCount=0`, `maxCount=9`, `chance=0.5`, it first picks an amount in [0..9], then each unit rolls 50% independently.

## Developer check

To verify Java compiles:
`bash gradlew compileJava -q`
