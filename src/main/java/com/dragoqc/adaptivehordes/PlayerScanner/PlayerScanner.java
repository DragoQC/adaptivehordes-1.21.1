package com.dragoqc.adaptivehordes.PlayerScanner;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.JsonFileHelper.JsonFileHelper;
import com.dragoqc.adaptivehordes.constants.ColorConstants;
import com.dragoqc.adaptivehordes.constants.ConfigConstants;
import com.dragoqc.adaptivehordes.models.PlayerScanResult;
import com.dragoqc.adaptivehordes.models.WeaponPower;
import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;


import org.slf4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    // UUID string -> scan result
    private static final Map<String, PlayerScanResult> CACHE = new HashMap<>();

    // Vanilla-ish baselines when projectile damage isn't expressible via attributes
    private static final double BASE_BOW_POWER = 6.0;
    private static final double BASE_CROSSBOW_POWER = 9.0;

    private PlayerScanner() { }

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    public static void clearCache() {
        CACHE.clear();
    }

    public static PlayerScanResult getPlayerData(UUID playerId) {
        return CACHE.get(playerId.toString());
    }

    /** Scan ONE player and update cache (does not write JSON). */
    public static PlayerScanResult scanPlayer(Player player) {
        PlayerScanResult result = new PlayerScanResult(player.getName().getString());

        WeaponPower power = calculateWeaponPowers(player);
        result.meleePower = power.meleePower;
        result.arrowPower = power.rangedPower;

				result.gameTime = String.valueOf(getGameTime(player));
				result.gameTimeInHours = String.valueOf(ticksToHours(getGameTime(player)));

        result.totalArmor = (int) player.getAttributeValue(Attributes.ARMOR);
        result.totalArmorToughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        result.totalHealth = player.getMaxHealth();

        result.enchantmentCount = countEnchantedItems(player);

        result.calculateGearScore(
            AdaptiveHordes.scalingConfig.armorPointValue,
            AdaptiveHordes.scalingConfig.enchantmentValue,
            AdaptiveHordes.scalingConfig.weaponDamageValue
        );

        CACHE.put(player.getUUID().toString(), result);

        LOGGER.info(ColorConstants.CYAN +
            "Scanned " + result.name +
            " | Gear Score: " + result.gearScore +
            ColorConstants.RESET);

        return result;
    }

    /** Save the FULL cache to adaptivehordes/PlayerScanResult.json (overwrites). */
    public static void saveAll() {
        try {
            File file = new File(
                JsonFileHelper.getConfigDirectory(),
                ConfigConstants.PLAYER_SCAN_RESULT_FILE
            );

            JsonFileHelper.saveConfig(file, CACHE);

            LOGGER.info(ColorConstants.GREEN +
                "Saved PlayerScanResult.json (" + CACHE.size() + " players)" +
                ColorConstants.RESET);

        } catch (Exception e) {
            LOGGER.error(ColorConstants.RED +
                "Failed to save PlayerScanResult.json" +
                ColorConstants.RESET, e);
        }
    }

    // ------------------------------------------------------------------------
    // WEAPON POWER (scan entire inventory)
    // ------------------------------------------------------------------------

    public static WeaponPower calculateWeaponPowers(Player player) {
        final double[] bestMelee = { 1.0 }; // fists baseline
        final double[] bestRanged = { 0.0 };

        forEachPlayerStack(player, stack -> {
            if (stack.isEmpty()) return;

            if (isRangedWeapon(stack)) {
                bestRanged[0] = Math.max(bestRanged[0], getRangedPower(stack));
                return;
            }

            double melee = getMainHandAttackDamage(player,stack);
            if (melee > bestMelee[0]) bestMelee[0] = melee;
        });

        WeaponPower wp = new WeaponPower();
        wp.meleePower = bestMelee[0];
        wp.rangedPower = bestRanged[0];
        return wp;
    }

    /** "Attack Damage" if this stack were held in MAINHAND. */
		private static double getMainHandAttackDamage(Player player, ItemStack stack) {
			if (stack.isEmpty()) return 0.0;

			if (isInfinitySword(stack)) {
				return ConfigConstants.INFINITY_SWORD_DAMAGE;
			}	

			final double[] modDamage = { 0.0 };

			stack.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attr, modifier) -> {
					if (attr.is(Attributes.ATTACK_DAMAGE)) {
							modDamage[0] += modifier.amount();
					}
			});

			if (modDamage[0] <= 0.0) return 0.0;
			return 1.0 + modDamage[0]; // base player damage + modifier
		}
		private static boolean isInfinitySword(ItemStack stack) {
			String id = stack.getItem().toString();
    	return "avaritia:infinity_sword".equals(id);
		}

    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof BowItem) return true;
        if (stack.getItem() instanceof CrossbowItem) return true;

        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW;
    }

    private static double getRangedPower(ItemStack stack) {
        // Otherwise fall back to reasonable vanilla-ish baselines
        if (stack.getItem() instanceof CrossbowItem || stack.getUseAnimation() == UseAnim.CROSSBOW) {
            return BASE_CROSSBOW_POWER;
        }
        if (stack.getItem() instanceof BowItem || stack.getUseAnimation() == UseAnim.BOW) {
            return BASE_BOW_POWER;
        }
        return 0.0;
    }

    // ------------------------------------------------------------------------
    // ENCHANTMENTS
    // ------------------------------------------------------------------------

    /** Counts how many inventory items have at least one enchantment. */
    private static int countEnchantedItems(Player player) {
        int count = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && EnchantmentHelper.hasAnyEnchantments(stack)) {
                count++;
            }
        }

        return count;
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
					return 0L; // Client or fake player
			}
			// PLAY_TIME is stored in ticks
			return serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
		}
		private static int ticksToHours(long ticks) {
			long seconds = ticks / 20;
			long minutes = seconds / 60;
			return (int)(minutes / 60);  // total whole hours
		}

}
