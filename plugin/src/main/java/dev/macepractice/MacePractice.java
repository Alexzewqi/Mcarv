package dev.macepractice;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MacePractice extends JavaPlugin {

    private static MacePractice instance;

    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, PracticeBot> bots = new HashMap<>();
    private World arenaWorld;

    public static MacePractice get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getServer().getPluginManager().registerEvents(new Menu(this), this);
        // Drive every bot's AI once per tick.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (PracticeBot bot : bots.values().toArray(new PracticeBot[0])) {
                bot.tick();
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (PracticeBot bot : bots.values()) {
            bot.remove();
        }
        bots.clear();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }

    /** Called once the server has finished starting and worlds are loaded. */
    void setupWorld() {
        arenaWorld = Bukkit.getWorlds().get(0);
        World w = arenaWorld;
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setSpawnLocation(Arena.playerSpawn(w));
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(Arena.RADIUS * 2 + 2);
        // Leftover bots from a crash.
        for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
            m.remove();
        }
        Arena.build(w);
        // Keep the arena ticking (bot physics runs even if you fly off with an elytra).
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                w.setChunkForceLoaded(cx, cz, true);
            }
        }
        getLogger().info("Arena ready.");
    }

    public World arenaWorld() {
        return arenaWorld != null ? arenaWorld : Bukkit.getWorlds().get(0);
    }

    public BotSettings settings(Player p) {
        return settings.computeIfAbsent(p.getUniqueId(), k -> new BotSettings());
    }

    public PracticeBot bot(Player p) {
        return bots.get(p.getUniqueId());
    }

    public PracticeBot botByEntity(Mannequin m) {
        for (PracticeBot b : bots.values()) {
            if (b.entity() != null && b.entity().getUniqueId().equals(m.getUniqueId())) {
                return b;
            }
        }
        return null;
    }

    public void spawnBot(Player p) {
        removeBot(p);
        PracticeBot bot = new PracticeBot(this, p, settings(p));
        bots.put(p.getUniqueId(), bot);
        bot.spawn();
    }

    public void removeBot(Player p) {
        PracticeBot old = bots.remove(p.getUniqueId());
        if (old != null) {
            old.remove();
        }
    }

    /** Full reset of a round: heal + re-kit the player, reset the bot. */
    public void resetRound(Player p) {
        Location spawn = Arena.playerSpawn(arenaWorld());
        p.teleport(spawn);
        Kit.give(p);
        PracticeBot bot = bot(p);
        if (bot == null) {
            spawnBot(p);
        } else {
            bot.reset();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "bot" -> Menu.open(p, this);
            case "kit" -> Kit.give(p);
            case "resetbot" -> resetRound(p);
            default -> {
                return false;
            }
        }
        return true;
    }
}
