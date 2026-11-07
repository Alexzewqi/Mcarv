package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Chest GUI with every toggle. */
public final class Menu implements Listener {

    // Row 2: bot behaviour
    private static final int MOVEMENT = 10, ATTACKS = 11, COMBOS = 12, SHIELD = 13, DIFFICULTY = 14,
            BOT_TOTEMS = 15, SKIN = 16;
    // Row 3: you
    private static final int MY_TOTEMS = 19, FOOD = 20, NUMBERS = 21, SIDEBAR = 22, COUNTDOWN = 23,
            THEME = 24, STATS = 25;
    // Row 5: actions
    private static final int SAVE_LAYOUT = 37, RESET_LAYOUT = 38, REFILL = 40, RESET = 42, CLOSE = 43;

    private final MacePractice plugin;

    public Menu(MacePractice plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static void open(Player p, MacePractice plugin) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Component.text("Mace Practice Settings"));
        fill(h.inv, plugin.settings(p), plugin);
        p.openInventory(h.inv);
    }

    private static void fill(Inventory inv, BotSettings s, MacePractice plugin) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        inv.setItem(4, item(Material.WITHER_SKELETON_SKULL, Component.text("Bot", NamedTextColor.RED), List.of(line("How your bot behaves ↓"))));
        inv.setItem(31, item(Material.PLAYER_HEAD, Component.text("You", NamedTextColor.AQUA), List.of(line("Your options ↑  Actions ↓"))));

        inv.setItem(MOVEMENT, toggle(Material.LEATHER_BOOTS, "Bot Movement", s.movement,
                "Off = bot stands still (still faces you,", "takes knockback, and can still hit you)."));
        inv.setItem(ATTACKS, toggle(Material.NETHERITE_SWORD, "Bot Attacks", s.attacks,
                "Off = punching bag mode."));
        inv.setItem(COMBOS, toggle(Material.MACE, "Bot Mace Combos", s.maceCombos,
                "Wind charge up + density mace smash,", "chained with Wind Burst."));
        inv.setItem(SHIELD, toggle(Material.SHIELD, "Bot Shields Your Dives", s.shield,
                "Off = it sidesteps instead."));
        Material diffIcon = switch (s.difficulty) {
            case EASY -> Material.LIME_CONCRETE;
            case NORMAL -> Material.YELLOW_CONCRETE;
            case HARD -> Material.RED_CONCRETE;
            case INSANE -> Material.CRYING_OBSIDIAN;
        };
        inv.setItem(DIFFICULTY, item(diffIcon,
                Component.text("Difficulty: " + s.difficulty.name(), NamedTextColor.GOLD),
                List.of(line("Click to cycle Easy / Normal / Hard / Insane."),
                        line("Speed, crits, combo rate, reactions, misses."))));
        inv.setItem(BOT_TOTEMS, toggle(Material.TOTEM_OF_UNDYING, "Bot Infinite Totems", s.botInfiniteTotems,
                "Off = bot has " + s.botTotems + " totems, then it can die.", "Kill it fast to get on the leaderboard!"));
        inv.setItem(SKIN, item(Material.ARMOR_STAND, Component.text("Bot Skin: " + s.skin.label, NamedTextColor.GOLD),
                List.of(line("Click to cycle: Default / Your skin."))));

        inv.setItem(MY_TOTEMS, toggle(Material.TOTEM_OF_UNDYING, "My Infinite Totems", s.playerInfiniteTotems,
                "Refills the hand your totem popped from."));
        inv.setItem(FOOD, toggle(Material.COOKED_BEEF, "Infinite Food", s.infiniteFood,
                "Hunger never drops."));
        inv.setItem(NUMBERS, toggle(Material.NAME_TAG, "Damage Numbers", s.damageNumbers,
                "Floating numbers on every hit."));
        inv.setItem(SIDEBAR, toggle(Material.OAK_SIGN, "Sidebar", s.sidebar,
                "Bot HP, round timer, combo, your stats."));
        inv.setItem(COUNTDOWN, toggle(Material.CLOCK, "Round Countdown", s.countdown,
                "3, 2, 1, FIGHT! at the start of each round."));
        inv.setItem(THEME, item(Material.FILLED_MAP, Component.text("Arena: " + plugin.theme().label, NamedTextColor.GOLD),
                List.of(line("Click to cycle arena themes (all flat)."), line("Changes it for everyone."))));
        List<Component> stats = new ArrayList<>();
        stats.add(line("Wins: " + s.wins + "   Losses: " + s.losses));
        stats.add(line("Totems popped: " + s.popsForced));
        stats.add(line("Max combo: " + s.maxCombo));
        for (BotSettings.Difficulty d : BotSettings.Difficulty.values()) {
            stats.add(line("Best " + d.name() + ": " + BotSettings.formatTime(s.bestMs[d.ordinal()])));
        }
        inv.setItem(STATS, item(Material.WRITABLE_BOOK, Component.text("Your Stats", NamedTextColor.AQUA), stats));

        inv.setItem(SAVE_LAYOUT, item(Material.LIME_SHULKER_BOX, Component.text("Save Kit Layout", NamedTextColor.GREEN),
                List.of(line("Move your items where you like them,"), line("then click here (or /savekit)."))));
        inv.setItem(RESET_LAYOUT, item(Material.RED_SHULKER_BOX, Component.text("Reset Kit Layout", NamedTextColor.RED),
                List.of(line("Back to the default hotbar."))));
        inv.setItem(REFILL, item(Material.CHEST, Component.text("Refill My Kit", NamedTextColor.AQUA),
                List.of(line("Heals you and gives the full kit back."))));
        inv.setItem(RESET, item(Material.RESPAWN_ANCHOR, Component.text("Reset Round", NamedTextColor.AQUA),
                List.of(line("Respawns the bot and refills your kit."))));
        inv.setItem(CLOSE, item(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        // Nether star in the player's own inventory opens the menu.
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) {
            if (Kit.isSettingsItem(e.getCurrentItem()) || Kit.isSettingsItem(e.getCursor())) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> open(p, plugin));
            }
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        BotSettings s = plugin.settings(p);
        PracticeBot bot = plugin.bot(p);
        switch (e.getRawSlot()) {
            case MOVEMENT -> s.movement = !s.movement;
            case ATTACKS -> s.attacks = !s.attacks;
            case COMBOS -> s.maceCombos = !s.maceCombos;
            case SHIELD -> s.shield = !s.shield;
            case DIFFICULTY -> s.difficulty = s.difficulty.next();
            case BOT_TOTEMS -> {
                s.botInfiniteTotems = !s.botInfiniteTotems;
                plugin.resetRound(p);
            }
            case SKIN -> {
                s.skin = s.skin.next();
                if (bot != null) bot.applySkin();
            }
            case MY_TOTEMS -> s.playerInfiniteTotems = !s.playerInfiniteTotems;
            case FOOD -> s.infiniteFood = !s.infiniteFood;
            case NUMBERS -> s.damageNumbers = !s.damageNumbers;
            case SIDEBAR -> s.sidebar = !s.sidebar;
            case COUNTDOWN -> s.countdown = !s.countdown;
            case THEME -> plugin.setTheme(plugin.theme().next(), p);
            case SAVE_LAYOUT -> {
                Kit.saveLayout(p, s);
                p.sendMessage(Component.text("Kit layout saved!", NamedTextColor.GREEN));
            }
            case RESET_LAYOUT -> {
                s.layout = null;
                Kit.give(p, s);
                p.sendMessage(Component.text("Kit layout reset.", NamedTextColor.YELLOW));
            }
            case REFILL -> {
                Kit.give(p, s);
                p.closeInventory();
            }
            case RESET -> {
                plugin.resetRound(p);
                p.closeInventory();
            }
            case CLOSE -> p.closeInventory();
            default -> {
                return;
            }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
            fill(e.getView().getTopInventory(), s, plugin);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ---- item helpers -------------------------------------------------------------------

    private static ItemStack toggle(Material m, String name, boolean on, String... desc) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(on ? "ON" : "OFF", on ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        for (String d : desc) lore.add(line(d));
        lore.add(line("Click to toggle."));
        ItemStack it = item(m, Component.text(name, on ? NamedTextColor.GREEN : NamedTextColor.RED), lore);
        if (on) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static Component line(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }
}
