package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class Listeners implements Listener {

    private final MacePractice plugin;

    public Listeners(MacePractice plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        plugin.setupWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            setupPlayer(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setupPlayer(e.getPlayer());
        e.getPlayer().sendMessage(Component.text("Mace PvP practice! ", NamedTextColor.GOLD)
                .append(Component.text("Open bot settings with sneak + F, /bot, or the nether star in your inventory.", NamedTextColor.GRAY)));
    }

    private void setupPlayer(Player p) {
        if (!p.isOp() || p.getGameMode() != GameMode.CREATIVE) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        plugin.resetRound(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.removeBot(e.getPlayer());
    }

    // ---- combat -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mannequin m)) {
            return;
        }
        PracticeBot bot = plugin.botByEntity(m);
        if (bot == null) {
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p && !p.getUniqueId().equals(bot.owner().getUniqueId())) {
            e.setCancelled(true); // only the owner fights their bot
            return;
        }
        bot.onHurt();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Mannequin m) {
            PracticeBot bot = plugin.botByEntity(m);
            if (bot != null) {
                bot.onTotemPop();
            }
            return;
        }
        if (e.getEntity() instanceof Player p && plugin.settings(p).playerInfiniteTotems) {
            EquipmentSlot hand = e.getHand();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || hand == null) return;
                ItemStack in = p.getInventory().getItem(hand);
                if (in == null || in.getType().isAir()) {
                    p.getInventory().setItem(hand, Kit.totem());
                }
            });
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mannequin m)) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        PracticeBot bot = plugin.botByEntity(m);
        if (bot != null) {
            bot.onDeath();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(false);
        e.deathMessage(null);
        PracticeBot bot = plugin.bot(p);
        if (bot != null) {
            bot.onOwnerDeath();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        e.setRespawnLocation(Arena.playerSpawn(plugin.arenaWorld()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.resetRound(p);
        });
    }

    // ---- menu shortcuts / protection ----------------------------------------------------

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (e.getPlayer().isSneaking()) {
            e.setCancelled(true);
            Menu.open(e.getPlayer(), plugin);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (Kit.isSettingsItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    static boolean isSettings(ItemStack it) {
        return it != null && it.getType() == Material.NETHER_STAR && Kit.isSettingsItem(it);
    }
}
