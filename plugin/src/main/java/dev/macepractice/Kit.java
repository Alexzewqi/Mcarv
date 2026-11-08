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

    public static ItemStack pickaxe() {
        return ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    public static ItemStack axe() {
        return ench(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5,
                Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
    }

    /** Bot armor never breaks (crystals shred durability). */
    public static ItemStack unbreakable(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
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

    /** Default layout, in {@link PlayerInventory#getContents()} order: 0-35 storage, 36-39 boots..helmet, 40 offhand. */
    private static String[] defaultLayout(BotSettings.Mode mode) {
        if (mode == BotSettings.Mode.CRYSTAL) {
            String[] l = defaultLayout(BotSettings.Mode.MACE);
            String[] hotbar = {"NETHERITE_SWORD", "END_CRYSTAL", "OBSIDIAN", "RESPAWN_ANCHOR", "GLOWSTONE",
                    "GOLDEN_APPLE", "ENDER_PEARL", "NETHERITE_PICKAXE", "TOTEM_OF_UNDYING"};
            System.arraycopy(hotbar, 0, l, 0, 9);
            String[] spare = {"EXPERIENCE_BOTTLE", "EXPERIENCE_BOTTLE", "EXPERIENCE_BOTTLE", "END_CRYSTAL", "END_CRYSTAL",
                    "OBSIDIAN", "OBSIDIAN", "RESPAWN_ANCHOR", "GLOWSTONE", "GOLDEN_APPLE", "NETHERITE_AXE", "COOKED_BEEF",
                    "ENDER_PEARL"};
            System.arraycopy(spare, 0, l, 10, spare.length);
            for (int i = 10 + spare.length; i <= 35; i++) l[i] = "TOTEM_OF_UNDYING";
            return l;
        }
        String[] l = new String[41];
        java.util.Arrays.fill(l, "TOTEM_OF_UNDYING");
        // Hotbar: sword, wind charges, pearls, steak, spear, shield, elytra (in place of the breach mace),
        // density/wind-burst mace, totem.
        String[] hotbar = {"NETHERITE_SWORD", "WIND_CHARGE", "ENDER_PEARL", "COOKED_BEEF", "DIAMOND_SPEAR",
                "SHIELD", "ELYTRA", "MACE", "TOTEM_OF_UNDYING"};
        System.arraycopy(hotbar, 0, l, 0, 9);
        l[SETTINGS_SLOT] = "NETHER_STAR";
        l[10] = "WIND_CHARGE";
        l[11] = "ENDER_PEARL";
        l[36] = "NETHERITE_BOOTS";
        l[37] = "NETHERITE_LEGGINGS";
        l[38] = "NETHERITE_CHESTPLATE";
        l[39] = "NETHERITE_HELMET";
        l[40] = "TOTEM_OF_UNDYING";
        return l;
    }

    /** A fresh, full kit item for a layout entry. */
    private static ItemStack fromId(String id) {
        if (id == null) return null;
        return switch (id) {
            case "NETHERITE_SWORD" -> sword();
            case "WIND_CHARGE" -> new ItemStack(Material.WIND_CHARGE, 64);
            case "ENDER_PEARL" -> new ItemStack(Material.ENDER_PEARL, 16);
            case "COOKED_BEEF" -> new ItemStack(Material.COOKED_BEEF, 64);
            case "DIAMOND_SPEAR" -> spear();
            case "SHIELD" -> shield();
            case "ELYTRA" -> elytra();
            case "MACE" -> densityMace();
            case "TOTEM_OF_UNDYING" -> totem();
            case "NETHER_STAR" -> settingsItem();
            case "END_CRYSTAL" -> new ItemStack(Material.END_CRYSTAL, 64);
            case "OBSIDIAN" -> new ItemStack(Material.OBSIDIAN, 64);
            case "RESPAWN_ANCHOR" -> new ItemStack(Material.RESPAWN_ANCHOR, 64);
            case "GLOWSTONE" -> new ItemStack(Material.GLOWSTONE, 64);
            case "GOLDEN_APPLE" -> new ItemStack(Material.GOLDEN_APPLE, 64);
            case "EXPERIENCE_BOTTLE" -> new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
            case "NETHERITE_PICKAXE" -> pickaxe();
            case "NETHERITE_AXE" -> axe();
            case "NETHERITE_HELMET" -> helmet();
            case "NETHERITE_CHESTPLATE" -> chestplate();
            case "NETHERITE_LEGGINGS" -> leggings();
            case "NETHERITE_BOOTS" -> boots();
            default -> null;
        };
    }

    /** Remembers where you put every item; empty slots stay empty. */
    public static void saveLayout(Player p, BotSettings s) {
        ItemStack[] c = p.getInventory().getContents();
        String[] l = new String[41];
        for (int i = 0; i < 41 && i < c.length; i++) {
            l[i] = c[i] == null || c[i].getType().isAir() ? "AIR"
                    : isSettingsItem(c[i]) ? "NETHER_STAR" : c[i].getType().name();
        }
        s.setLayout(s.mode, l);
    }

    public static void give(Player p, BotSettings s) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        String[] l = s.layoutFor(s.mode) != null ? s.layoutFor(s.mode) : defaultLayout(s.mode);
        ItemStack[] contents = new ItemStack[41];
        for (int i = 0; i < 41; i++) {
            contents[i] = fromId(l[i]);
        }
        inv.setContents(contents);
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
        p.setCooldown(Material.GOLDEN_APPLE, 0);
    }
}
