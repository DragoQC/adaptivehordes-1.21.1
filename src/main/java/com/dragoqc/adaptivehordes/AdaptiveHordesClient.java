package com.dragoqc.adaptivehordes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.dragoqc.adaptivehordes.models.WeaponPower;
import com.dragoqc.adaptivehordes.playerscanner.PlayerScanner;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = AdaptiveHordes.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AdaptiveHordes.MODID, value = Dist.CLIENT)
public class AdaptiveHordesClient {
    public AdaptiveHordesClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        AdaptiveHordes.LOGGER.info("HELLO FROM CLIENT SETUP");
        AdaptiveHordes.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onInventoryScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || AdaptiveHordes.scalingConfig == null) return;

        int gearScore = computeClientGearScore(player);
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();

        // Inventory player model area (rough bounds used by vanilla inventory layout).
        int modelLeft = left + 26;
        int modelTop = top + 8;
        int modelRight = left + 76;
        int modelBottom = top + 78;

        if (mouseX >= modelLeft && mouseX <= modelRight && mouseY >= modelTop && mouseY <= modelBottom) {
            event.getGuiGraphics().renderTooltip(
                mc.font,
                Component.literal("Gear Score: " + gearScore),
                mouseX,
                mouseY
            );
        }
    }

    private static int computeClientGearScore(LocalPlayer player) {
        WeaponPower power = PlayerScanner.calculateWeaponPowers(player);
        int armor = (int) player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double health = player.getMaxHealth();
        int enchantCount = countEnchantedItems(player);

        return (int) (
            (armor * AdaptiveHordes.scalingConfig.armorPointValue) +
            (enchantCount * AdaptiveHordes.scalingConfig.enchantmentValue) +
            (power.meleePower * AdaptiveHordes.scalingConfig.weaponDamageValue) +
            (power.rangedPower * AdaptiveHordes.scalingConfig.weaponDamageValue * 0.8) +
            (toughness * 5) +
            (health * 2)
        );
    }

    private static int countEnchantedItems(LocalPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && EnchantmentHelper.hasAnyEnchantments(stack)) count++;
        }
        return count;
    }
}
