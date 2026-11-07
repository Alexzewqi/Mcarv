package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public final class Kit {

    public static final int SETTINGS_SLOT = 9;

    private Kit() {
    }

    // ---- items --------------------------------------------------------------------------

    public static ItemStack sword() {
        return ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack spear() {
        return ench(Material.DIAMOND_SPEAR, Enchantment.LUNGE, 3, Enchantment.SHARPNESS, 5,
                Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack shield() {
        return ench(Material.SHIELD, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack densityMace() {
        return ench(Material.MACE, Enchantment.WIND_BURST, 3, Enchantment.DENSITY, 5,
                Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack breachMace() {
        return ench(Material.MACE, Enchantment.BREACH, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack elytra() {
        return ench(Material.ELYTRA, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack totem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    public static ItemStack helmet() {
        return armor(Material.NETHERITE_HELMET);
    }

    public static ItemStack chestplate() {
        return armor(Material.NETHERITE_CHESTPLATE);
    }

    public static ItemStack leggings() {
        return armor(Material.NETHERITE_LEGGINGS);
    }

    public static ItemStack boots() {
        ItemStack b = armor(Material.NETHERITE_BOOTS);
        b.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 4);
        return b;
    }

    private static ItemStack armor(Material m) {
        return ench(m, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    private static ItemStack ench(Material m, Object... pairs) {
        ItemStack it = new ItemStack(m);
        for (int i = 0; i < pairs.length; i += 2) {
            it.addUnsafeEnchantment((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return it;
    }

    public static ItemStack settingsItem() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Bot Settings", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click me in your inventory", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("or sneak + F, or /bot", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(settingsKey(), PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isSettingsItem(ItemStack it) {
        if (it == null || it.getType() != Material.NETHER_STAR || !it.hasItemMeta()) {
            return false;
        }
        return it.getItemMeta().getPersistentDataContainer().has(settingsKey(), PersistentDataType.BYTE);
    }

    private static NamespacedKey settingsKey() {
        return new NamespacedKey(MacePractice.get(), "settings_item");
    }

    // ---- loadout ------------------------------------------------------------------------

    /**
     * Hotbar: sword, wind charges, pearls, steak, spear, shield, elytra (in place of the breach mace),
     * density/wind-burst mace, totem. Offhand totem, the rest of the inventory is spare totems.
     */
    public static void give(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, sword());
        inv.setItem(1, new ItemStack(Material.WIND_CHARGE, 64));
        inv.setItem(2, new ItemStack(Material.ENDER_PEARL, 16));
        inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 64));
        inv.setItem(4, spear());
        inv.setItem(5, shield());
        inv.setItem(6, elytra());
        inv.setItem(7, densityMace());
        inv.setItem(8, totem());
        inv.setItemInOffHand(totem());

        inv.setHelmet(helmet());
        inv.setChestplate(chestplate());
        inv.setLeggings(leggings());
        inv.setBoots(boots());

        inv.setItem(SETTINGS_SLOT, settingsItem());
        inv.setItem(10, new ItemStack(Material.WIND_CHARGE, 64));
        inv.setItem(11, new ItemStack(Material.ENDER_PEARL, 16));
        for (int slot = 12; slot <= 35; slot++) {
            inv.setItem(slot, totem());
        }
        inv.setHeldItemSlot(0);

        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setAbsorptionAmount(0);
        p.setCooldown(Material.ENDER_PEARL, 0);
        p.setCooldown(Material.WIND_CHARGE, 0);
    }
}
