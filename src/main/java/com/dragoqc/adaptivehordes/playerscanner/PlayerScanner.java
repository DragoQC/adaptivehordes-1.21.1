package com.dragoqc.adaptivehordes.playerscanner;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.models.DefaultWeaponOverridesConfig;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.WeaponOverrideEntry;
import com.dragoqc.adaptivehordes.models.WeaponPower;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerScanner {
    private static final String AVARITIA_INFINITY_SWORD_ITEM_ID = "avaritia:infinity_sword";
    private static final double AVARITIA_INFINITY_SWORD_POWER = 9999.0D;

    // UUID string -> scan result
    private static final Map<String, PlayerScanResult> CACHE = new HashMap<>();
    private static final Map<String, Long> SIGNATURE_CACHE = new HashMap<>();
    private static final Map<String, Long> LAST_SCAN_TICK = new HashMap<>();

    private PlayerScanner() { }

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    public static void clearCache() {
        CACHE.clear();
        SIGNATURE_CACHE.clear();
        LAST_SCAN_TICK.clear();
    }

    public static void removePlayerData(UUID playerId) {
        if (playerId == null) return;
        String key = playerId.toString();
        CACHE.remove(key);
        SIGNATURE_CACHE.remove(key);
        LAST_SCAN_TICK.remove(key);
    }

    public static PlayerScanResult getPlayerData(UUID playerId) {
        if (playerId == null) return null;
        return CACHE.get(playerId.toString());
    }

    public static PlayerScanResult ensurePlayerData(ServerPlayer player, long now) {
        if (player == null) return null;
        String key = player.getUUID().toString();
        PlayerScanResult cached = CACHE.get(key);
        if (cached != null) return cached;

        PlayerScanResult scanned = scanPlayer(player);
        SIGNATURE_CACHE.put(key, computePlayerSignature(player));
        LAST_SCAN_TICK.put(key, now);
        return scanned;
    }

    public static void updateLivePlayer(ServerPlayer player, long now, int minIntervalTicks, int maxStaleTicks) {
        if (player == null) return;

        int minInterval = Math.max(1, minIntervalTicks);
        int maxStale = Math.max(minInterval, maxStaleTicks);
        String key = player.getUUID().toString();
        long lastScan = LAST_SCAN_TICK.getOrDefault(key, Long.MIN_VALUE);

        if (lastScan != Long.MIN_VALUE && (now - lastScan) < minInterval) {
            return;
        }

        long signature = computePlayerSignature(player);
        Long previousSignature = SIGNATURE_CACHE.get(key);
        boolean changed = previousSignature == null || previousSignature.longValue() != signature;
        boolean missing = !CACHE.containsKey(key);
        boolean stale = lastScan == Long.MIN_VALUE || (now - lastScan) >= maxStale;
        if (missing || changed || stale) {
            scanPlayer(player);
            SIGNATURE_CACHE.put(key, signature);
            LAST_SCAN_TICK.put(key, now);
        }
    }

    public static void forceRescan(ServerPlayer player, long now) {
        if (player == null) return;
        String key = player.getUUID().toString();
        PlayerScanResult cached = CACHE.get(key);
        long lastTick = LAST_SCAN_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (cached != null && lastTick == now) {
            return;
        }
        scanPlayer(player);
        SIGNATURE_CACHE.put(key, computePlayerSignature(player));
        LAST_SCAN_TICK.put(key, now);
    }

    /** Scan ONE player and update cache (does not write JSON). */
    public static PlayerScanResult scanPlayer(Player player) {
        if (player == null) return null;
        String key = player.getUUID().toString();
        long now = (player instanceof ServerPlayer sp) ? sp.serverLevel().getGameTime() : Long.MIN_VALUE;

        PlayerScanResult cached = CACHE.get(key);
        long lastTick = LAST_SCAN_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (cached != null && now != Long.MIN_VALUE && lastTick == now) {
            return cached;
        }

        PlayerScanResult result = new PlayerScanResult(player.getName().getString());

        WeaponPower power = calculateWeaponPowers(player);
        result.meleePower = power.meleePower;
        result.arrowPower = power.rangedPower;

        long playTime = getGameTime(player);
        result.gameTime = String.valueOf(playTime);
        result.gameTimeInHours = String.valueOf(ticksToHours(playTime));

        result.totalArmor = (int) player.getAttributeValue(Attributes.ARMOR);
        result.totalArmorToughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        result.totalHealth = player.getMaxHealth();

        result.enchantmentCount = countEnchantedItems(player);

        result.calculateGearScore(
            AdaptiveHordes.scalingConfig.armorPointValue,
            AdaptiveHordes.scalingConfig.enchantmentValue,
            AdaptiveHordes.scalingConfig.weaponDamageValue
        );

        CACHE.put(key, result);
        if (now != Long.MIN_VALUE) {
            LAST_SCAN_TICK.put(key, now);
        }

        return result;
    }

    // ------------------------------------------------------------------------
    // WEAPON POWER (scan entire inventory)
    // ------------------------------------------------------------------------

    public static WeaponPower calculateWeaponPowers(Player player) {
        final double[] bestMelee = { 1.0 }; // fists baseline
        final double[] bestRanged = { 0.0 };

        forEachPlayerStack(player, stack -> {
            if (stack.isEmpty()) return;

            if (isAvaritiaInfinitySword(stack)) {
                bestMelee[0] = Math.max(bestMelee[0], AVARITIA_INFINITY_SWORD_POWER);
                return;
            }

            WeaponOverrideEntry override = getManualWeaponOverride(stack);
            if (override != null) {
                if (override.ranged) {
                    bestRanged[0] = Math.max(bestRanged[0], override.damage);
                } else {
                    bestMelee[0] = Math.max(bestMelee[0], override.damage);
                }
                return;
            }

            if (isRangedWeapon(stack)) {
                bestRanged[0] = Math.max(bestRanged[0], getRangedPower(player, stack));
                return;
            }

            double melee = getMainHandAttackDamage(player, stack);
            if (melee > bestMelee[0]) bestMelee[0] = melee;
        });

        WeaponPower wp = new WeaponPower();
        wp.meleePower = bestMelee[0];
        wp.rangedPower = bestRanged[0];
        return wp;
    }

    private static boolean isAvaritiaInfinitySword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return AVARITIA_INFINITY_SWORD_ITEM_ID.equals(itemId);
    }

    private static WeaponOverrideEntry getManualWeaponOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        DefaultWeaponOverridesConfig cfg = AdaptiveHordes.weaponOverridesConfig;
        if (cfg == null || cfg.overrides == null || cfg.overrides.isEmpty()) return null;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId == null || itemId.isBlank()) return null;

        for (WeaponOverrideEntry entry : cfg.overrides) {
            if (entry == null) continue;
            if (entry.itemId == null || entry.itemId.isBlank()) continue;
            if (!itemId.equals(entry.itemId)) continue;
            if (!Double.isFinite(entry.damage) || entry.damage <= 0.0) continue;
            return entry;
        }
        return null;
    }

    /** "Attack Damage" if this stack were held in MAINHAND. */
    private static double getMainHandAttackDamage(Player player, ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        final double[] modDamage = { 0.0 };

        stack.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attr, modifier) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE)) {
                modDamage[0] += modifier.amount();
            }
        });

        if (modDamage[0] <= 0.0) return 0.0;
        return 1.0 + modDamage[0]; // base player damage + modifier
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof BowItem) return true;
        if (stack.getItem() instanceof CrossbowItem) return true;

        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW;
    }

    private static double getRangedPower(Player player, ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (AdaptiveHordes.scalingConfig.rangedWeaponOverrides != null) {
            Double override = AdaptiveHordes.scalingConfig.rangedWeaponOverrides.get(itemId);
            if (override != null) return override;
        }

        boolean isCrossbow = stack.getItem() instanceof CrossbowItem || stack.getUseAnimation() == UseAnim.CROSSBOW;
        boolean isBow = stack.getItem() instanceof BowItem || stack.getUseAnimation() == UseAnim.BOW;

        if (isBow) {
            int powerLevel = getEnchantmentLevel(player, stack, Enchantments.POWER);
            int flameLevel = getEnchantmentLevel(player, stack, Enchantments.FLAME);

            return AdaptiveHordes.scalingConfig.vanillaBowBaseDamage
                + (powerLevel * AdaptiveHordes.scalingConfig.bowPowerLevelBonus)
                + ((flameLevel > 0) ? AdaptiveHordes.scalingConfig.bowFlameBonus : 0.0);
        }

        if (isCrossbow) {
            int piercingLevel = getEnchantmentLevel(player, stack, Enchantments.PIERCING);
            int multishotLevel = getEnchantmentLevel(player, stack, Enchantments.MULTISHOT);
            int quickChargeLevel = getEnchantmentLevel(player, stack, Enchantments.QUICK_CHARGE);

            return AdaptiveHordes.scalingConfig.vanillaCrossbowBaseDamage
                + (piercingLevel * AdaptiveHordes.scalingConfig.crossbowPiercingLevelBonus)
                + ((multishotLevel > 0) ? AdaptiveHordes.scalingConfig.crossbowMultishotBonus : 0.0)
                + (quickChargeLevel * AdaptiveHordes.scalingConfig.crossbowQuickChargeLevelBonus);
        }

        // Modded ranged items with non-standard behavior: fall back to a configurable value.
        double inferred = getMainHandAttackDamage(player, stack);
        if (inferred > 0.0) return inferred;
        return AdaptiveHordes.scalingConfig.moddedRangedFallbackDamage;
    }

    private static int getEnchantmentLevel(Player player, ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        try {
            Holder<Enchantment> holder = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantmentKey);
            return stack.getEnchantmentLevel(holder);
        } catch (Exception ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------
    // ENCHANTMENTS
    // ------------------------------------------------------------------------

    /** Counts how many equipped, held, and inventory items have at least one enchantment. */
    private static int countEnchantedItems(Player player) {
        final int[] count = { 0 };
        forEachPlayerStack(player, stack -> {
            if (!stack.isEmpty() && EnchantmentHelper.hasAnyEnchantments(stack)) {
                count[0]++;
            }
        });
        return count[0];
    }

    // ------------------------------------------------------------------------
    // INVENTORY ITERATION (items + armor + offhand)
    // ------------------------------------------------------------------------

    private static void forEachPlayerStack(Player player, Consumer<ItemStack> action) {
        for (ItemStack s : player.getInventory().items) action.accept(s);
        for (ItemStack s : player.getInventory().armor) action.accept(s);
        for (ItemStack s : player.getInventory().offhand) action.accept(s);
    }

    // ------------------------------------------------------------------------
    // GAMETIME RETRIEVAL
    // ------------------------------------------------------------------------
    private static long getGameTime(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0L;
        }
        return serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    private static int ticksToHours(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        return (int) (minutes / 60);
    }

    private static long computePlayerSignature(Player player) {
        long hash = 1469598103934665603L; // FNV-1a offset basis
        hash = fnvMix(hash, Double.doubleToLongBits(player.getAttributeValue(Attributes.ARMOR)));
        hash = fnvMix(hash, Double.doubleToLongBits(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
        hash = fnvMix(hash, Double.doubleToLongBits(player.getMaxHealth()));

        for (ItemStack stack : player.getInventory().items) hash = hashItemStack(hash, player, stack);
        for (ItemStack stack : player.getInventory().armor) hash = hashItemStack(hash, player, stack);
        for (ItemStack stack : player.getInventory().offhand) hash = hashItemStack(hash, player, stack);

        return hash;
    }

    private static long hashItemStack(long seed, Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return fnvMix(seed, 0x9E3779B97F4A7C15L);
        }
        long hash = seed;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        hash = fnvMixString(hash, itemId);
        hash = fnvMix(hash, stack.getCount());
        hash = fnvMix(hash, stack.getDamageValue());
        hash = fnvMix(hash, stack.getMaxDamage());
        hash = fnvMix(hash, EnchantmentHelper.hasAnyEnchantments(stack) ? 1 : 0);
        hash = fnvMix(hash, getEnchantmentLevel(player, stack, Enchantments.POWER));
        hash = fnvMix(hash, getEnchantmentLevel(player, stack, Enchantments.FLAME));
        hash = fnvMix(hash, getEnchantmentLevel(player, stack, Enchantments.PIERCING));
        hash = fnvMix(hash, getEnchantmentLevel(player, stack, Enchantments.MULTISHOT));
        hash = fnvMix(hash, getEnchantmentLevel(player, stack, Enchantments.QUICK_CHARGE));
        return hash;
    }

    private static long fnvMix(long hash, long value) {
        hash ^= value;
        hash *= 1099511628211L;
        return hash;
    }

    private static long fnvMixString(long hash, String value) {
        if (value == null) return fnvMix(hash, 0L);
        for (int i = 0; i < value.length(); i++) {
            hash = fnvMix(hash, value.charAt(i));
        }
        return hash;
    }

}
