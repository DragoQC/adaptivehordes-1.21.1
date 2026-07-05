package com.dragoqc.adaptivehordes.config;

import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class ConfigHelpWriter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigHelpWriter() {}

    public static void writeHelpFile() {
        File directory = JsonFileHelper.getConfigDirectory();
        if (directory == null) return;

        File helpFile = new File(directory, ConfigConstants.CONFIG_HELP_FILE);
        try (FileWriter writer = new FileWriter(helpFile)) {
            writer.write(helpText());
            LOGGER.info(ColorConstants.GREEN + "Saved config help: " + helpFile.getName() + ColorConstants.RESET);
        } catch (IOException ex) {
            LOGGER.error(ColorConstants.RED + "Error saving config help: " + helpFile.getName() + ColorConstants.RESET, ex);
        }
    }

    private static String helpText() {
        return """
            # Adaptive Hordes Config Help

            This file explains the JSON files in this folder. The JSON files contain only values used by the mod; this Markdown file contains the descriptions.

            Minecraft timing notes:
            - 20 ticks = 1 second.
            - 24000 ticks = 1 Minecraft day.
            - Day ticks use the 0..23999 range. Around 13000 is early night.

            ## ModConfig.json

            Main horde runtime and wave pacing settings.

            - `enableHordes`: Master switch for automatic wave spawning. Commands and config loading still work when this is false.
            - `waveCheckInterval`: How often each player can be considered for a new wave cycle. `24000` means once per Minecraft day.
            - `liveScanUpdateIntervalTicks`: How often online players are rescanned for gear score while active. Lower values react faster but do more work.
            - `liveScanMaxStaleTicks`: Maximum age for cached player scan data before it is forced to refresh.
            - `baseHordeSize`: Baseline planned mob count before wave `totalMobMultiplier` and player-strength scaling are applied.
            - `waveSpawnWindowStartTick`: Start of the daily spawn window, from `0` to `23999`.
            - `waveSpawnWindowEndTick`: End of the daily spawn window, from `0` to `23999`. If lower than the start, the window wraps past midnight.
            - `maxMobsPerSpawnBatch`: Maximum mobs attempted in one spawn batch.
            - `maxLiveMobsPerPlayer`: Maximum living wave mobs allowed for one player's active wave. Extra planned mobs wait until earlier mobs die.
            - `loadSpawnDelayTicks`: Base delay between spawn batches. Large waves may shorten this delay so the queue can finish during the spawn window.

            ## MobConfig.json

            Runtime behavior for mobs spawned by Adaptive Hordes. These settings only affect wave-spawned mobs tagged by this mod.

            Horde targeting notes:
            - An external living attacker is added above the assigned player in the attacked mob's target queue.
            - An eligible attack alerts all Adaptive Hordes mobs inside `callForHelpRadius`, including mobs from other summoned waves. Further hits during `callForHelpCooldownTicks` affect only the directly attacked mob; the attacker can trigger another nearby alert after the cooldown.
            - Horde mobs do not retaliate against accidental damage from other Adaptive Hordes mobs. Invalid priority targets are removed before targeting returns to the assigned player.

            - `mobDetectionRange`: `FOLLOW_RANGE` applied to spawned mobs. Higher values make mobs acquire and keep targets from farther away.
            - `persistentTargeting`: When true, wave mobs safely pursue their priority target. External attackers temporarily outrank the assigned player, and invalid targets are removed before pursuit returns to the player.
            - `callForHelpRadius`: Radius around an attacked horde mob that receives its external attacker as a priority target. This includes Adaptive Hordes mobs from other summoned waves. Values are clamped to `0.0..32.0`; `0.0` limits retaliation to the attacked mob.
            - `callForHelpCooldownTicks`: Cooldown per external attacker before it can alert nearby horde mobs again. `80` ticks is 4 seconds; `0` allows every eligible hit to alert nearby mobs.
            - `sunlightImmunity`: When true, wave mobs stop burning in sunlight. Useful for daytime hordes or long events.
            - `sizeMultiplier`: `SCALE` attribute applied to spawned mobs. `1.0` means vanilla size. Very large values make valid spawn positions harder to find.
            - `waveMobLifetimeTicks`: Maximum lifetime before a wave mob is cleaned up. `24000` means one Minecraft day.
            - `stuckCheckIntervalTicks`: How often movement progress is checked for stuck cleanup.
            - `maxStuckTicks`: How long a mob may remain stuck or inside blocks before cleanup.
            - `maxDistanceFromTarget`: Wave mobs farther than this many blocks from their assigned player are cleaned up.
            - `spawnMinDistance`: Minimum horizontal spawn radius around the target player. Keep this outside normal melee range.
            - `spawnMaxDistance`: Maximum horizontal spawn radius around the target player. Avoid huge values because vanilla hostile mobs despawn when very far away.
            - `spawnPositionAttempts`: How many random positions are tested per mob. Higher values improve success in rough terrain but cost more CPU.

            ## ScalingConfig.json

            Player power scoring settings used by wave selection and optional mob health scaling.

            - `armorPointValue`: Gear score points per armor point.
            - `enchantmentValue`: Gear score points per enchanted item.
            - `weaponDamageValue`: Gear score points per weapon damage unit.
            - `vanillaBowBaseDamage`: Base ranged threat value for bows before enchantment bonuses.
            - `vanillaCrossbowBaseDamage`: Base ranged threat value for crossbows before enchantment bonuses.
            - `moddedRangedFallbackDamage`: Fallback threat value for ranged-like modded items when no better signal exists.
            - `bowPowerLevelBonus`: Extra ranged threat per Power enchantment level on bows.
            - `bowFlameBonus`: Flat ranged threat bonus if a bow has Flame.
            - `crossbowPiercingLevelBonus`: Extra ranged threat per Piercing enchantment level.
            - `crossbowMultishotBonus`: Flat ranged threat bonus if a crossbow has Multishot.
            - `crossbowQuickChargeLevelBonus`: Extra ranged threat per Quick Charge enchantment level.
            - `weaponPowerHealthScalingEnabled`: Adds a health bonus to wave mobs based on the target player's best weapon power.
            - `weaponPowerHealthBonusMin`: Minimum health bonus ratio when weapon-power health scaling is active. `0.05` means +5%.
            - `weaponPowerHealthBonusMax`: Maximum health bonus ratio. `0.10` means +10%.
            - `weaponPowerForMaxHealthBonus`: Weapon power value that reaches `weaponPowerHealthBonusMax`.
            - `rangedWeaponOverrides`: Map of `itemId -> exact ranged threat value`. Example: `"examplemod:longbow": 12.0`.

            ## WeaponOverrides.json

            Manual weapon overrides for modded items that do not expose reliable damage data.

            Each entry in `overrides` has:
            - `itemId`: Exact item id, such as `minecraft:diamond_sword`.
            - `ranged`: `true` for ranged scoring, `false` for melee scoring.
            - `damage`: Manual damage or threat value used in gear score.

            Built-in compatibility takes precedence for Avaritia's `avaritia:infinity_sword`, treating it as `9999` melee power because its execution-style attack is not exposed as normal attack damage.

            ## IgnoreConfig.json

            Players listed here are skipped by automatic wave spawning.

            - `ignoredPlayerUuids`: Set of player UUID strings. Use `/adaptivehorde ignore <player>` and `/adaptivehorde unignore <player>` to manage this safely in game.

            ## waves.json

            Defines wave templates, weighted mob composition, dimensions, and custom drops.

            Per wave:
            - `name`: Unique wave name used by commands and runtime.
            - `displayName`: Player-facing wave name used for boss bars and messages.
            - `waveSpawningMessage`: Message shown when this wave starts. Supports `{wave}`, `{player}`, and `{count}`.
            - `dimensions`: Allowed dimensions. Empty means all dimensions. Example: `minecraft:overworld`.
            - `strengthRequirement`: Minimum player gear score required for wave eligibility.
            - `waveSpawnChance`: `0.0..1.0` chance weight among eligible waves.
            - `totalMobMultiplier`: Scales total mobs planned for this wave.
            - `waveDrops`: Optional drops applied to every mob in this wave.
            - `waveContent`: List of mob entries for this wave.

            Per mob in `waveContent`:
            - `entityId`: Valid entity id to spawn, vanilla or modded.
            - `baseHealth`: `MAX_HEALTH` base applied to spawned mob.
            - `baseDamage`: `ATTACK_DAMAGE` base applied to spawned mob.
            - `baseSpeed`: `MOVEMENT_SPEED` base applied to spawned mob.
            - `ranged`: Metadata used by targeting logic so ranged mobs keep more vanilla combat distance.
            - `presenceWeight`: Relative selection weight for this mob in the wave.
            - `amountMultiplier`: Additional per-mob amount weight multiplier.
            - `spawnChanceMin`: Minimum per-spawn-cycle chance for this mob to be active.
            - `spawnChanceMax`: Maximum per-spawn-cycle chance for this mob to be active.
            - `baby`: Tries to spawn as a baby variant when the mob type supports it.
            - `randomArmorChance`: `0.0..1.0` chance this mob gets random armor pieces.
            - `randomArmorMaxPieces`: `1..4` max random armor pieces equipped when the chance passes.
            - `dropsMode`: `ADD` keeps vanilla drops and adds configured drops. `OVERRIDE` clears vanilla drops first.
            - `drops`: Per-mob custom drops for wave-spawned mobs only.

            Drop entries:
            - `itemId`: Valid item id.
            - `minCount` and `maxCount`: Inclusive random attempt count range.
            - `chance`: `0.0..1.0` chance per attempt. Each attempt rolls independently.

            Useful commands:
            - `/adaptivehorde reload`: Reload JSON configs from disk.
            - `/adaptivehorde waves`: List waves.
            - `/adaptivehorde wave info <name>`: Inspect one wave.
            - `/adaptivehorde spawn wave <name> [target]`: Manually test a wave.
            - `/adaptivehorde debug plans`: Inspect active wave plans.
            """;
    }
}
