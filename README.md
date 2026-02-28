# Adaptive Horde

Progression-based horde spawning for NeoForge 1.21.1.

Adaptive Horde evaluates each player's gear score and spawns wave mobs that scale with that player. It is built to be JSON-first, so server owners can tune behavior without touching Java.

## Quick Start

1. Run the game/server once to generate configs in:
`config/adaptivehordes/`
2. Edit JSON files.
3. Reload in game:
`/adaptivehorde reload`

## Config Files

Current filenames are kept for compatibility. Friendly names are listed for clarity.

- `ModConfig.json` (Core Horde Runtime)
- `ScalingConfig.json` (Gear Score + Scaling Rules)
- `MobConfig.json` (Wave Mob Runtime Controls)
- `IgnoreConfig.json` (Ignored Players List)
- `WeaponOverrides.json` (Manual Weapon Damage Registry)
- `waves.json` (Wave Catalog)

### Core Horde Runtime (`ModConfig.json`)

Main pacing/timing keys:

- `enableHordes`
- `waveCheckInterval`
- `waveSpawnWindowStartTick`
- `waveSpawnWindowEndTick`
- `baseHordeSize`
- `maxMobsPerSpawnBatch`
- `loadSpawnDelayTicks`
- `liveScanUpdateIntervalTicks`
- `liveScanMaxStaleTicks`

### Gear Score + Scaling (`ScalingConfig.json`)

- `armorPointValue`
- `enchantmentValue`
- `weaponDamageValue`
- `vanillaBowBaseDamage`
- `vanillaCrossbowBaseDamage`
- `moddedRangedFallbackDamage`
- `bowPowerLevelBonus`
- `bowFlameBonus`
- `crossbowPiercingLevelBonus`
- `crossbowMultishotBonus`
- `crossbowQuickChargeLevelBonus`
- `weaponPowerHealthScalingEnabled`
- `weaponPowerHealthBonusMin`
- `weaponPowerHealthBonusMax`
- `weaponPowerForMaxHealthBonus`
- `rangedWeaponOverrides` (item id map)
- `rangedWeaponTagOverrides` (item tag map)

### Manual Weapon Registry (`WeaponOverrides.json`)

Used when modded weapons do not expose reliable damage data.

Each entry:

- `itemId`
- `ranged` (`true` or `false`)
- `damage`

These overrides are applied first during scan logic, and can force any item to count as melee or ranged weapon power.

### Wave Catalog (`waves.json`)

Per-wave:

- `name` (code name used by commands/tab completion)
- `displayName` (player-facing name for UI/boss bar)
- `waveSpawningMessage` (`{wave}`, `{player}`, `{count}` supported)
- `dimensions` (`minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`)
- `strengthRequirement`
- `waveSpawnChance`
- `totalMobMultiplier`
- `waveContent`

Per mob entry in `waveContent`:

- `name`
- `entityId`
- `baseHealth`
- `baseDamage`
- `baseSpeed`
- `ranged`
- `presenceWeight`
- `amountMultiplier`
- `spawnChanceMin`
- `spawnChanceMax`
- `baby`
- `randomArmorChance`
- `randomArmorMaxPieces`
- `dropsMode` (`ADD` or `OVERRIDE`)
- `drops`

## Commands

All mod commands start with `/adaptivehorde`.

### Player Commands (no admin permission)

- `/adaptivehorde announcements`
- `/adaptivehorde announcements on`
- `/adaptivehorde announcements off`
- `/adaptivehorde announcements toggle`
- `/adaptivehorde gearscore`

### Admin Commands (permission level 2)

General:

- `/adaptivehorde reload`
- `/adaptivehorde gearscore all`
- `/adaptivehorde gearscore <target>`
- `/adaptivehorde scan player`
- `/adaptivehorde scan player <target>`

Wave management:

- `/adaptivehorde waves`
- `/adaptivehorde wave info <name>`
- `/adaptivehorde wave clear`
- `/adaptivehorde wave clear <name>`
- `/adaptivehorde wave resetdefaults`
- `/adaptivehorde spawn wave <name>`
- `/adaptivehorde spawn wave <name> <target>`

Debug:

- `/adaptivehorde debug plans`
- `/adaptivehorde debug player`
- `/adaptivehorde debug player <target>`

Ignore list:

- `/adaptivehorde ignore <target>`
- `/adaptivehorde unignore <target>`
- `/adaptivehorde ignore list`

Weapon override registry:

- `/adaptivehorde weaponoverride addheld <damage>`
- `/adaptivehorde weaponoverride addheld <damage> <ranged>`
- `/adaptivehorde weaponoverride removeheld`
- `/adaptivehorde weaponoverride remove <itemId>`
- `/adaptivehorde weaponoverride list`
- `/adaptivehorde weaponoverride reset`

Dangerous reset commands:

- `/adaptivehorde delete config sure sure sure`
- `/adaptivehorde delete player scans sure sure sure`

## Notes

- `reload` does not delete files; it reloads current files from disk.
- `delete config sure sure sure` deletes config files and recreates defaults.
- `weaponoverride reset` deletes `WeaponOverrides.json`, recreates it, and force-rescans online players immediately.
- Wave announcements are per-player (toggle stored per player).
- Boss bar appears when wave spawning actually starts (not at pre-compute stage).

## Developer Check

Compile check:

`bash gradlew compileJava -q`
