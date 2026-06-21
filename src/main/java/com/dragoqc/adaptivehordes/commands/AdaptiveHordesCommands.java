package com.dragoqc.adaptivehordes.commands;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.config.ConfigManager;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.mobwave.MobWave;
import com.dragoqc.adaptivehordes.mobwave.MobWaveScheduler;
import com.dragoqc.adaptivehordes.mobwave.MobWaveSpawner;
import com.dragoqc.adaptivehordes.models.Mob;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.Wave;
import com.dragoqc.adaptivehordes.models.WeaponOverrideEntry;
import com.dragoqc.adaptivehordes.playerscanner.PlayerScanner;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.io.File;

public final class AdaptiveHordesCommands {
    public static final String TAG_DISABLE_WAVE_ANNOUNCEMENTS = "adaptivehordes_disable_wave_announcements";
    public static final String TAG_DISABLE_WAVE_BOSSBAR = "adaptivehordes_disable_wave_bossbar";
    private static final SuggestionProvider<CommandSourceStack> WAVE_NAME_SUGGESTIONS = (context, builder) ->
        SharedSuggestionProvider.suggest(getWaveNames(), builder);

    private AdaptiveHordesCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("adaptivehorde")
            // Player-facing utility commands
            .then(Commands.literal("announcements")
                .executes(AdaptiveHordesCommands::announcementStatus)
                .then(Commands.literal("on").executes(AdaptiveHordesCommands::announcementOn))
                .then(Commands.literal("off").executes(AdaptiveHordesCommands::announcementOff))
                .then(Commands.literal("toggle").executes(AdaptiveHordesCommands::announcementToggle)))
            .then(Commands.literal("bossbar")
                .executes(AdaptiveHordesCommands::bossBarStatus)
                .then(Commands.literal("on").executes(AdaptiveHordesCommands::bossBarOn))
                .then(Commands.literal("off").executes(AdaptiveHordesCommands::bossBarOff))
                .then(Commands.literal("toggle").executes(AdaptiveHordesCommands::bossBarToggle)))
            .then(Commands.literal("gearscore")
                .executes(AdaptiveHordesCommands::gearscoreSelf)
                .then(Commands.literal("all")
                    .requires(source -> source.hasPermission(2))
                    .executes(AdaptiveHordesCommands::gearscoreAll))
                .then(Commands.argument("target", EntityArgument.player())
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> gearscoreTarget(context, EntityArgument.getPlayer(context, "target")))))
            // Admin control commands
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> reload(context.getSource())))
            .then(Commands.literal("scan")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("player")
                    .executes(AdaptiveHordesCommands::scanAllPlayers)
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> scanPlayer(context, EntityArgument.getPlayer(context, "target"))))))
            .then(Commands.literal("weaponoverride")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("addheld")
                    .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0.01D))
                        .executes(context -> addHeldWeaponOverride(context, DoubleArgumentType.getDouble(context, "damage"), true))
                        .then(Commands.argument("ranged", BoolArgumentType.bool())
                            .executes(context -> addHeldWeaponOverride(
                                context,
                                DoubleArgumentType.getDouble(context, "damage"),
                                BoolArgumentType.getBool(context, "ranged")
                            )))))
                .then(Commands.literal("removeheld")
                    .executes(AdaptiveHordesCommands::removeHeldWeaponOverride))
                .then(Commands.literal("remove")
                    .then(Commands.argument("itemId", StringArgumentType.word())
                        .executes(context -> removeWeaponOverrideById(context, StringArgumentType.getString(context, "itemId")))))
                .then(Commands.literal("reset")
                    .executes(AdaptiveHordesCommands::resetWeaponOverrides))
                .then(Commands.literal("list")
                    .executes(AdaptiveHordesCommands::listWeaponOverrides)))
            .then(Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("plans")
                    .executes(AdaptiveHordesCommands::debugPlans))
                .then(Commands.literal("player")
                    .executes(AdaptiveHordesCommands::debugPlayerSelf)
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> debugPlayerTarget(context, EntityArgument.getPlayer(context, "target"))))))
            .then(Commands.literal("waves")
                .requires(source -> source.hasPermission(2))
                .executes(AdaptiveHordesCommands::listWaves))
            .then(Commands.literal("wave")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WAVE_NAME_SUGGESTIONS)
                        .executes(AdaptiveHordesCommands::waveInfo)))
                .then(Commands.literal("clear")
                    .executes(context -> clearWaveEnemies(context, null))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WAVE_NAME_SUGGESTIONS)
                        .executes(context -> clearWaveEnemies(context, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("resetdefaults")
                    .executes(AdaptiveHordesCommands::resetDefaultWaves)))
            .then(Commands.literal("spawn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("wave")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WAVE_NAME_SUGGESTIONS)
                        .executes(context -> spawnWaveForSourcePlayer(context, StringArgumentType.getString(context, "name")))
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(context -> spawnWaveForTarget(
                                context,
                                StringArgumentType.getString(context, "name"),
                                EntityArgument.getPlayer(context, "target")
                            ))))))
            .then(Commands.literal("ignore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                    .executes(AdaptiveHordesCommands::listIgnoredPlayers))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(context -> addIgnoredPlayer(context, EntityArgument.getPlayer(context, "target")))))
            .then(Commands.literal("unignore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(context -> removeIgnoredPlayer(context, EntityArgument.getPlayer(context, "target")))))
            .then(Commands.literal("delete")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("config")
                    .then(Commands.literal("sure")
                        .then(Commands.literal("sure")
                            .then(Commands.literal("sure")
                                .executes(AdaptiveHordesCommands::deleteConfigAndReset)))))
                .then(Commands.literal("player")
                    .then(Commands.literal("scans")
                        .then(Commands.literal("sure")
                            .then(Commands.literal("sure")
                                .then(Commands.literal("sure")
                                    .executes(AdaptiveHordesCommands::deletePlayerScans)))))));

        event.getDispatcher().register(root);
    }

    private static int announcementStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        boolean disabled = player.getPersistentData().getBoolean(TAG_DISABLE_WAVE_ANNOUNCEMENTS);
        Component msg = Component.literal("Adaptive Horde: wave announcements are " + (disabled ? "OFF" : "ON"))
            .withStyle(disabled ? ChatFormatting.RED : ChatFormatting.GREEN);
        context.getSource().sendSuccess(() -> msg, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int announcementOn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_ANNOUNCEMENTS, false);
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: wave announcements enabled.").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int announcementOff(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_ANNOUNCEMENTS, true);
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: wave announcements disabled.").withStyle(ChatFormatting.RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int announcementToggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        boolean nowDisabled = !player.getPersistentData().getBoolean(TAG_DISABLE_WAVE_ANNOUNCEMENTS);
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_ANNOUNCEMENTS, nowDisabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: wave announcements are now " + (nowDisabled ? "OFF" : "ON"))
                .withStyle(nowDisabled ? ChatFormatting.RED : ChatFormatting.GREEN),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int bossBarStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        boolean disabled = player.getPersistentData().getBoolean(TAG_DISABLE_WAVE_BOSSBAR);
        Component msg = Component.literal("Adaptive Horde: wave boss bar is " + (disabled ? "OFF" : "ON"))
            .withStyle(disabled ? ChatFormatting.RED : ChatFormatting.GREEN);
        context.getSource().sendSuccess(() -> msg, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bossBarOn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_BOSSBAR, false);
        MobWaveScheduler.refreshBossBarVisibilityForPlayer(context.getSource().getServer(), player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: wave boss bar enabled.").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bossBarOff(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_BOSSBAR, true);
        MobWaveScheduler.refreshBossBarVisibilityForPlayer(context.getSource().getServer(), player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: wave boss bar disabled.").withStyle(ChatFormatting.RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bossBarToggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        boolean nowDisabled = !player.getPersistentData().getBoolean(TAG_DISABLE_WAVE_BOSSBAR);
        player.getPersistentData().putBoolean(TAG_DISABLE_WAVE_BOSSBAR, nowDisabled);
        MobWaveScheduler.refreshBossBarVisibilityForPlayer(context.getSource().getServer(), player.getUUID());
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: wave boss bar is now " + (nowDisabled ? "OFF" : "ON"))
                .withStyle(nowDisabled ? ChatFormatting.RED : ChatFormatting.GREEN),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSourceStack source) {
        try {
            ConfigManager.reloadAll();
            logCommand(source, "reload", "configs");
            source.sendSuccess(() -> Component.literal("Adaptive Hordes: configs reloaded.").withStyle(ChatFormatting.AQUA), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Adaptive Hordes: failed to reload configs. Check logs.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int listWaves(CommandContext<CommandSourceStack> context) {
        List<String> names = getWaveNames();
        if (names.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: no waves found in waves.json").withStyle(ChatFormatting.RED));
            return 0;
        }

        logCommand(context.getSource(), "waves", "listed " + names.size() + " waves");
        context.getSource().sendSuccess(() -> Component.literal("Waves (" + names.size() + "): " + String.join(", ", names)).withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int waveInfo(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Wave wave = MobWave.findWaveByName(name);
        if (wave == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: wave not found: " + name).withStyle(ChatFormatting.RED));
            return 0;
        }

        logCommand(context.getSource(), "wave info", name);
        context.getSource().sendSuccess(
            () -> Component.literal("Wave '" + wave.name + "' req=" + wave.strengthRequirement +
                " waveSpawnChance=" + percent(wave.waveSpawnChance) +
                " totalMobMultiplier=" + format(wave.totalMobMultiplier)).withStyle(ChatFormatting.AQUA),
            false
        );

        if (wave.waveContent == null || wave.waveContent.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  No mobs configured.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        double totalWeight = wave.waveContent.stream()
            .filter(m -> m != null)
            .mapToDouble(m -> Math.max(0.0, m.presenceWeight * m.amountMultiplier))
            .sum();

        for (Mob mob : wave.waveContent) {
            if (mob == null) continue;
            double weight = Math.max(0.0, mob.presenceWeight * mob.amountMultiplier);
            double share = (totalWeight > 0.0) ? (weight / totalWeight) : 0.0;
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "  - " + mob.entityId +
                    " weightShare=" + percent(share) +
                    " spawnRange=" + percent(mob.spawnChanceMin) + ".." + percent(mob.spawnChanceMax) +
                    " armorChance=" + percent(mob.randomArmorChance) +
                    " armorMaxPieces=" + mob.randomArmorMaxPieces +
                    " dropsMode=" + mob.dropsMode
                ).withStyle(ChatFormatting.YELLOW),
                false
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int spawnWaveForSourcePlayer(CommandContext<CommandSourceStack> context, String waveName) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return spawnWaveForTarget(context, waveName, player);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: specify a player when running from console.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int spawnWaveForTarget(CommandContext<CommandSourceStack> context, String waveName, ServerPlayer target) {
        Wave wave = MobWave.findWaveByName(waveName);
        if (wave == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: wave not found: " + waveName).withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerScanResult scan = PlayerScanner.ensurePlayerData(target, target.serverLevel().getGameTime());
        int planned = MobWaveScheduler.startWave(context.getSource().getServer(), target, wave, scan, true);
        logCommand(context.getSource(), "spawn wave", wave.name + " -> " + target.getName().getString() + " (" + planned + " planned)");
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: started wave '" + wave.name + "' for " + target.getName().getString() + " (" + planned + " mobs planned).").withStyle(ChatFormatting.GREEN),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int clearWaveEnemies(CommandContext<CommandSourceStack> context, String waveNameFilter) {
        MinecraftServer server = context.getSource().getServer();
        int removed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (!MobWaveSpawner.isWaveSpawnedMob(living)) continue;

                if (waveNameFilter != null && !waveNameFilter.isBlank()) {
                    String waveName = living.getPersistentData().getString(MobWaveSpawner.TAG_WAVE_NAME);
                    if (!waveNameFilter.equals(waveName)) continue;
                }

                living.discard();
                removed++;
            }
        }

        int clearedPlans = MobWaveScheduler.clearActivePlans(server, waveNameFilter);
        String filterSuffix = (waveNameFilter == null || waveNameFilter.isBlank()) ? "" : " for wave '" + waveNameFilter + "'";
        int finalRemoved = removed;
        logCommand(context.getSource(), "wave clear", finalRemoved + " removed, " + clearedPlans + " active plans cleared" + filterSuffix);
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: cleared " + finalRemoved + " wave enemies and " + clearedPlans + " active wave plans" + filterSuffix + ".").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int scanPlayer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        PlayerScanResult result = PlayerScanner.scanPlayer(target);
        logCommand(context.getSource(), "scan player", target.getName().getString() + " gearScore=" + result.gearScore);
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: scanned " + target.getName().getString() +
                " -> gearScore=" + result.gearScore +
                ", melee=" + format(result.meleePower) +
                ", ranged=" + format(result.arrowPower)).withStyle(ChatFormatting.BLUE),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int scanAllPlayers(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerScanner.scanPlayer(player);
            count++;
        }
        int finalCount = count;
        logCommand(context.getSource(), "scan player", "all online players count=" + count);
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: scanned all online players (" + finalCount + ").").withStyle(ChatFormatting.BLUE),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int gearscoreSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer self = getSelfPlayer(context);
        if (self == null) return 0;
        return sendGearscore(context.getSource(), self, false);
    }

    private static int debugPlayerSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer self = getSelfPlayer(context);
        if (self == null) return 0;
        return debugPlayer(context.getSource(), self);
    }

    private static int debugPlayerTarget(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        return debugPlayer(context.getSource(), target);
    }

    private static int debugPlans(CommandContext<CommandSourceStack> context) {
        List<String> lines = MobWaveScheduler.getActivePlanDebugLines(context.getSource().getServer());
        if (lines.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: no active spawn plans.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde active plans: " + lines.size()).withStyle(ChatFormatting.AQUA), false);
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal("  - " + line).withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int gearscoreTarget(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        return sendGearscore(context.getSource(), target, true);
    }

    private static int gearscoreAll(CommandContext<CommandSourceStack> context) {
        Collection<ServerPlayer> players = context.getSource().getServer().getPlayerList().getPlayers();
        int shown = 0;
        for (ServerPlayer player : players) {
            sendGearscore(context.getSource(), player, false);
            shown++;
        }
        logCommand(context.getSource(), "gearscore all", "count=" + shown);
        return shown > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int sendGearscore(CommandSourceStack source, ServerPlayer target, boolean broadcastToOps) {
        PlayerScanResult result = PlayerScanner.ensurePlayerData(target, target.serverLevel().getGameTime());
        if (result == null) {
            source.sendFailure(Component.literal("Adaptive Horde: could not compute gear score for " + target.getName().getString()).withStyle(ChatFormatting.RED));
            return 0;
        }

        logCommand(source, "gearscore", target.getName().getString() + " gearScore=" + result.gearScore);
        source.sendSuccess(
            () -> Component.literal(
                "Adaptive Horde: " + target.getName().getString() +
                " -> gearScore=" + result.gearScore +
                ", melee=" + format(result.meleePower) +
                ", ranged=" + format(result.arrowPower)
            ).withStyle(ChatFormatting.BLUE),
            broadcastToOps
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int debugPlayer(CommandSourceStack source, ServerPlayer target) {
        PlayerScanResult result = PlayerScanner.ensurePlayerData(target, target.serverLevel().getGameTime());
        if (result == null) {
            source.sendFailure(Component.literal("Adaptive Horde: no live scan for " + target.getName().getString()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String dimensionId = target.serverLevel().dimension().location().toString();
        Wave wave = MobWave.pickWaveForStrength(result.gearScore, dimensionId);
        String waveName = (wave == null || wave.name == null) ? "<none>" : wave.name;
        source.sendSuccess(
            () -> Component.literal(
                "Adaptive Horde debug player=" + target.getName().getString() +
                " dim=" + dimensionId +
                " gearScore=" + result.gearScore +
                " wave=" + waveName
            ).withStyle(ChatFormatting.AQUA),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int addIgnoredPlayer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (AdaptiveHordes.ignoreConfig == null || AdaptiveHordes.ignoreConfig.ignoredPlayerUuids == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: IgnoreConfig is not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }
        AdaptiveHordes.ignoreConfig.ignoredPlayerUuids.add(target.getUUID().toString());
        ConfigManager.saveIgnoreConfig();
        logCommand(context.getSource(), "ignore", target.getName().getString());
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: ignored player " + target.getName().getString()).withStyle(ChatFormatting.GOLD),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int removeIgnoredPlayer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (AdaptiveHordes.ignoreConfig == null || AdaptiveHordes.ignoreConfig.ignoredPlayerUuids == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: IgnoreConfig is not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean removed = AdaptiveHordes.ignoreConfig.ignoredPlayerUuids.remove(target.getUUID().toString());
        ConfigManager.saveIgnoreConfig();
        logCommand(context.getSource(), "unignore", target.getName().getString() + " removed=" + removed);
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: " + (removed ? "unignored " : "not ignored ") + target.getName().getString()).withStyle(ChatFormatting.GOLD),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnoredPlayers(CommandContext<CommandSourceStack> context) {
        if (AdaptiveHordes.ignoreConfig == null || AdaptiveHordes.ignoreConfig.ignoredPlayerUuids == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: IgnoreConfig is not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (AdaptiveHordes.ignoreConfig.ignoredPlayerUuids.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: ignore list is empty.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        String joined = String.join(", ", AdaptiveHordes.ignoreConfig.ignoredPlayerUuids);
        context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde ignored UUIDs: " + joined).withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetDefaultWaves(CommandContext<CommandSourceStack> context) {
        MobWave.resetToDefaults();
        logCommand(context.getSource(), "wave resetdefaults", "waves.json replaced with current default template");
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: waves.json reset to built-in default waves.").withStyle(ChatFormatting.GOLD),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int deleteConfigAndReset(CommandContext<CommandSourceStack> context) {
        File configDir = JsonFileHelper.getConfigDirectory();
        if (configDir == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: config directory is not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }

        deleteIfExists(new File(configDir, ConfigConstants.MOD_CONFIG_FILE));
        deleteIfExists(new File(configDir, ConfigConstants.SCALING_CONFIG_FILE));
        deleteIfExists(new File(configDir, ConfigConstants.MOB_CONFIG_FILE));
        deleteIfExists(new File(configDir, ConfigConstants.IGNORE_CONFIG_FILE));
        deleteIfExists(new File(configDir, ConfigConstants.WEAPON_OVERRIDES_CONFIG_FILE));
        deleteIfExists(new File(configDir, ConfigConstants.WAVES_CONFIG_FILE));

        ConfigManager.reloadAll();
        logCommand(context.getSource(), "delete config", "confirmed x3; recreated defaults from code");
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: config files deleted and reset to code defaults.").withStyle(ChatFormatting.RED),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int addHeldWeaponOverride(CommandContext<CommandSourceStack> context, double damage, boolean ranged) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: hold an item in your main hand.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (AdaptiveHordes.weaponOverridesConfig == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: weapon override config not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (AdaptiveHordes.weaponOverridesConfig.overrides == null) {
            AdaptiveHordes.weaponOverridesConfig.overrides = new java.util.ArrayList<>();
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        WeaponOverrideEntry found = null;
        for (WeaponOverrideEntry entry : AdaptiveHordes.weaponOverridesConfig.overrides) {
            if (entry != null && itemId.equals(entry.itemId)) {
                found = entry;
                break;
            }
        }
        if (found == null) {
            found = new WeaponOverrideEntry();
            found.itemId = itemId;
            AdaptiveHordes.weaponOverridesConfig.overrides.add(found);
        }

        found.ranged = ranged;
        found.damage = damage;
        ConfigManager.saveWeaponOverridesConfig();
        PlayerScanner.forceRescan(player, player.serverLevel().getGameTime());

        logCommand(context.getSource(), "weaponoverride addheld", itemId + " damage=" + format(damage) + " ranged=" + ranged);
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Adaptive Horde: override saved -> " + itemId +
                " damage=" + format(damage) +
                " ranged=" + ranged
            ).withStyle(ChatFormatting.GREEN),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int removeHeldWeaponOverride(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getSelfPlayer(context);
        if (player == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: hold an item in your main hand.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        return removeWeaponOverride(context.getSource(), itemId, player);
    }

    private static int removeWeaponOverrideById(CommandContext<CommandSourceStack> context, String itemId) {
        return removeWeaponOverride(context.getSource(), itemId, getSelfPlayer(context));
    }

    private static int removeWeaponOverride(CommandSourceStack source, String itemId, ServerPlayer actor) {
        if (itemId == null || itemId.isBlank()) {
            source.sendFailure(Component.literal("Adaptive Horde: invalid item id.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (AdaptiveHordes.weaponOverridesConfig == null || AdaptiveHordes.weaponOverridesConfig.overrides == null) {
            source.sendFailure(Component.literal("Adaptive Horde: weapon override config not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean removed = AdaptiveHordes.weaponOverridesConfig.overrides.removeIf(e -> e != null && itemId.equals(e.itemId));
        if (!removed) {
            source.sendFailure(Component.literal("Adaptive Horde: no override found for " + itemId).withStyle(ChatFormatting.RED));
            return 0;
        }

        ConfigManager.saveWeaponOverridesConfig();
        if (actor != null) {
            PlayerScanner.forceRescan(actor, actor.serverLevel().getGameTime());
        }
        logCommand(source, "weaponoverride remove", itemId);
        source.sendSuccess(() -> Component.literal("Adaptive Horde: removed override for " + itemId).withStyle(ChatFormatting.GOLD), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listWeaponOverrides(CommandContext<CommandSourceStack> context) {
        if (AdaptiveHordes.weaponOverridesConfig == null || AdaptiveHordes.weaponOverridesConfig.overrides == null || AdaptiveHordes.weaponOverridesConfig.overrides.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Adaptive Horde: no weapon overrides configured.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde weapon overrides: " + AdaptiveHordes.weaponOverridesConfig.overrides.size()).withStyle(ChatFormatting.AQUA),
            false
        );
        for (WeaponOverrideEntry entry : AdaptiveHordes.weaponOverridesConfig.overrides) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank()) continue;
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "  - " + entry.itemId +
                    " | damage=" + format(entry.damage) +
                    " | ranged=" + entry.ranged
                ).withStyle(ChatFormatting.YELLOW),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int resetWeaponOverrides(CommandContext<CommandSourceStack> context) {
        File configDir = JsonFileHelper.getConfigDirectory();
        if (configDir == null) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: config directory is not initialized.").withStyle(ChatFormatting.RED));
            return 0;
        }

        File weaponOverridesFile = new File(configDir, ConfigConstants.WEAPON_OVERRIDES_CONFIG_FILE);
        if (weaponOverridesFile.exists() && !weaponOverridesFile.delete()) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: could not delete WeaponOverrides.json").withStyle(ChatFormatting.RED));
            return 0;
        }

        ConfigManager.reloadWeaponOverrides();

        MinecraftServer server = context.getSource().getServer();
        if (server != null && server.overworld() != null) {
            long now = server.overworld().getGameTime();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerScanner.forceRescan(player, now);
            }
        }

        logCommand(context.getSource(), "weaponoverride reset", "WeaponOverrides.json deleted and recreated defaults");
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: weapon overrides reset and live scans refreshed.").withStyle(ChatFormatting.GOLD),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int deletePlayerScans(CommandContext<CommandSourceStack> context) {
        PlayerScanner.clearCache();
        logCommand(context.getSource(), "delete player scans", "confirmed x3; player scan live cache cleared");
        context.getSource().sendSuccess(
            () -> Component.literal("Adaptive Horde: player scans deleted.").withStyle(ChatFormatting.RED),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static List<String> getWaveNames() {
        return MobWave.getAll().stream()
            .filter(w -> w != null && w.name != null && !w.name.isBlank())
            .map(w -> w.name)
            .toList();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void logCommand(CommandSourceStack source, String action, String details) {
        String actor = source.getTextName();
        AdaptiveHordes.LOGGER.info(
            ColorConstants.CYAN + "[AdaptiveHorde Cmd]" +
            ColorConstants.GREEN + " actor=" + actor +
            ColorConstants.PURPLE + " action=" + action +
            ColorConstants.YELLOW + " details=" + details +
            ColorConstants.RESET
        );
    }

    private static void deleteIfExists(File file) {
        if (file == null || !file.exists()) return;
        boolean deleted = file.delete();
        if (!deleted) {
            AdaptiveHordes.LOGGER.warn(
                ColorConstants.YELLOW + "[AdaptiveHorde Cmd] Could not delete file: " + file.getAbsolutePath() + ColorConstants.RESET
            );
        }
    }

    private static ServerPlayer getSelfPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return context.getSource().getPlayerOrException();
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Adaptive Horde: this command must be run by a player.").withStyle(ChatFormatting.RED));
            return null;
        }
    }
}
