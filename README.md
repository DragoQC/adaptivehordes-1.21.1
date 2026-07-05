# Adaptive Hordes

Progression-based horde spawning for NeoForge 1.21.1.

Adaptive Hordes evaluates each player's gear score and spawns wave mobs that scale with that player. The mod intentionally uses JSON config files instead of NeoForge TOML config specs so server owners can keep the current editable file layout and existing configs remain repairable.

## Quick Start

1. Run the game or server once to generate configs in `config/adaptivehordes/`.
2. Read `config/adaptivehordes/ConfigHelp.md` for field explanations.
3. Edit the JSON files.
4. Reload in game with `/adaptivehorde reload`.

## Config Files

- `ModConfig.json` - core horde runtime and pacing.
- `ScalingConfig.json` - gear score and weapon scaling rules.
- `MobConfig.json` - spawned mob runtime controls.
- `IgnoreConfig.json` - ignored player UUIDs.
- `WeaponOverrides.json` - manual weapon damage registry.
- `waves.json` - wave catalog and mob entries.
- `ConfigHelp.md` - generated documentation for the JSON files.

### Core Horde Runtime

Important `ModConfig.json` keys:

- `enableHordes`
- `waveCheckInterval`
- `waveSpawnWindowStartTick`
- `waveSpawnWindowEndTick`
- `baseHordeSize`
- `maxMobsPerSpawnBatch`
- `maxLiveMobsPerPlayer`
- `loadSpawnDelayTicks`
- `liveScanUpdateIntervalTicks`
- `liveScanMaxStaleTicks`

### Gear Score + Scaling

Important `ScalingConfig.json` keys:

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
- `rangedWeaponOverrides`

## Horde Targeting And Call-for-Help

- Every horde mob primarily targets the player assigned to it.
- If a golem, wolf, another player, or other living entity attacks a horde mob, that attacker is temporarily placed above the assigned player in the mob's target priority.
- The first attack also alerts every Adaptive Hordes mob within `callForHelpRadius`, including nearby mobs from other summoned waves or assigned players.
- Later attacks from that same entity affect only the directly attacked mob, preventing call-for-help from spreading repeatedly through the horde.
- Horde mobs do not retaliate against accidental damage from other Adaptive Hordes mobs, although the original damage still applies.
- When a priority target dies, unloads, or changes dimension, mobs remove it from their queue and resume the next target, eventually returning to their assigned player.
- Pursuit continues to use safe Minecraft pathfinding and avoids damaging terrain where the mob is vulnerable.

Important `MobConfig.json` keys:

- `persistentTargeting`
- `callForHelpRadius`
- `mobDetectionRange`
- `maxDistanceFromTarget`
- `stuckCheckIntervalTicks`
- `maxStuckTicks`

### Manual Weapon Registry

`WeaponOverrides.json` is used when modded weapons do not expose reliable damage data.

Each entry:

- `itemId`
- `ranged`
- `damage`

Manual overrides are applied before generic weapon detection and can force any item to count as melee or ranged weapon power.

Built-in compatibility takes precedence for Avaritia's `avaritia:infinity_sword`, treating it as `9999` melee power because its execution-style attack is not represented by normal attack-damage attributes.

### Wave Catalog

Per-wave keys in `waves.json`:

- `name`
- `displayName`
- `waveSpawningMessage`
- `dimensions`
- `strengthRequirement`
- `waveSpawnChance`
- `totalMobMultiplier`
- `waveContent`

Per mob entry in `waveContent`:

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
- `dropsMode`
- `drops`

## Commands

All mod commands start with `/adaptivehorde`.

Player commands:

- `/adaptivehorde announcements`
- `/adaptivehorde announcements on`
- `/adaptivehorde announcements off`
- `/adaptivehorde announcements toggle`
- `/adaptivehorde gearscore`

Admin commands require permission level 2:

- `/adaptivehorde reload`
- `/adaptivehorde gearscore all`
- `/adaptivehorde gearscore <target>`
- `/adaptivehorde scan player`
- `/adaptivehorde scan player <target>`
- `/adaptivehorde waves`
- `/adaptivehorde wave info <name>`
- `/adaptivehorde wave clear`
- `/adaptivehorde wave clear <name>`
- `/adaptivehorde wave resetdefaults`
- `/adaptivehorde spawn wave <name>`
- `/adaptivehorde spawn wave <name> <target>`
- `/adaptivehorde debug plans`
- `/adaptivehorde debug player`
- `/adaptivehorde debug player <target>`
- `/adaptivehorde ignore <target>`
- `/adaptivehorde unignore <target>`
- `/adaptivehorde ignore list`
- `/adaptivehorde weaponoverride addheld <damage>`
- `/adaptivehorde weaponoverride addheld <damage> <ranged>`
- `/adaptivehorde weaponoverride removeheld`
- `/adaptivehorde weaponoverride remove <itemId>`
- `/adaptivehorde weaponoverride list`
- `/adaptivehorde weaponoverride reset`

Dangerous reset commands:

- `/adaptivehorde delete config sure sure sure`
- `/adaptivehorde delete player scans sure sure sure`

## HUD And Boss Bar

- The left HUD appears when a tracked wave plan starts and shows full wave progress: remaining enemies over total planned enemies.
- The top vanilla boss bar appears only while spawned wave mobs are alive.
- The boss bar title shows active spawned enemy count.
- The boss bar fill tracks active spawned mob health, so damage lowers the bar and later batches can refill it.
- `/adaptivehorde wave clear` removes active wave mobs, the wave plan, the HUD, and the boss bar.

## Notes

- `/adaptivehorde reload` reloads current files from disk without deleting them.
- Invalid JSON configs are backed up before defaults are recreated.
- `delete config sure sure sure` deletes config files and recreates defaults.
- `weaponoverride reset` deletes `WeaponOverrides.json`, recreates it, and force-rescans online players immediately.
- Wave announcements are stored per player.
- Adaptive Hordes is an independent NeoForge mod and is not affiliated with or endorsed by Mojang or Microsoft.

## Developer Check

```bash
bash gradlew compileJava -q
bash gradlew processResources -q
```
