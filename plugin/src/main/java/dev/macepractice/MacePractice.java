package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MacePractice extends JavaPlugin {

    private static MacePractice instance;

    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, PracticeBot> bots = new HashMap<>();
    private final Leaderboard leaderboard = new Leaderboard();
    private World arenaWorld;
    private Arena.Theme theme = Arena.Theme.DEEPSLATE;
    private File dataFile;
    private YamlConfiguration data;

    public static MacePractice get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        try {
            theme = Arena.Theme.valueOf(data.getString("theme", theme.name()));
        } catch (IllegalArgumentException ignored) {
        }
        leaderboard.load(data);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getServer().getPluginManager().registerEvents(new Menu(this), this);
        // Drive every bot's AI once per tick.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (PracticeBot bot : bots.values().toArray(new PracticeBot[0])) {
                bot.tick();
            }
        }, 1L, 1L);
        // Sidebars + autosave.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Sidebar.update(p, settings(p), bot(p));
            }
        }, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 20L * 60, 20L * 60);
    }

    @Override
    public void onDisable() {
        for (PracticeBot bot : bots.values()) {
            bot.remove();
        }
        bots.clear();
        leaderboard.remove();
        saveData();
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
        // Leftovers from a crash.
        for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
            m.remove();
        }
        for (TextDisplay t : w.getEntitiesByClass(TextDisplay.class)) {
            t.remove();
        }
        Arena.build(w, theme);
        // Keep the arena ticking (bot physics runs even if you fly off with an elytra).
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                w.setChunkForceLoaded(cx, cz, true);
            }
        }
        leaderboard.render(w);
        getLogger().info("Arena ready (" + theme.label + " theme).");
    }

    // ---- arena theme --------------------------------------------------------------------

    public Arena.Theme theme() {
        return theme;
    }

    public void setTheme(Arena.Theme t, Player by) {
        theme = t;
        data.set("theme", t.name());
        Arena.build(arenaWorld(), t);
        Bukkit.broadcast(Component.text(by.getName() + " changed the arena to ", NamedTextColor.GRAY)
                .append(Component.text(t.label, NamedTextColor.GOLD)));
    }

    // ---- data ---------------------------------------------------------------------------

    public void saveData() {
        for (Map.Entry<UUID, BotSettings> e : settings.entrySet()) {
            String path = "players." + e.getKey();
            ConfigurationSection c = data.getConfigurationSection(path);
            if (c == null) c = data.createSection(path);
            e.getValue().save(c);
        }
        leaderboard.save(data);
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    public Leaderboard leaderboard() {
        return leaderboard;
    }

    public World arenaWorld() {
        return arenaWorld != null ? arenaWorld : Bukkit.getWorlds().get(0);
    }

    public BotSettings settings(Player p) {
        return settings.computeIfAbsent(p.getUniqueId(), k -> {
            BotSettings s = new BotSettings();
            s.load(data.getConfigurationSection("players." + k));
            return s;
        });
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

    /** Full reset of a round: heal + re-kit the player, reset the bot, start the countdown. */
    public void resetRound(Player p) {
        Location spawn = Arena.playerSpawn(arenaWorld());
        p.teleport(spawn);
        Kit.give(p, settings(p));
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
        BotSettings s = settings(p);
        switch (command.getName().toLowerCase()) {
            case "bot" -> Menu.open(p, this);
            case "kit" -> Kit.give(p, s);
            case "resetbot" -> resetRound(p);
            case "savekit" -> {
                Kit.saveLayout(p, s);
                p.sendMessage(Component.text("Kit layout saved! It's used every time you get your kit.", NamedTextColor.GREEN));
            }
            case "resetkit" -> {
                s.layout = null;
                Kit.give(p, s);
                p.sendMessage(Component.text("Kit layout reset to default.", NamedTextColor.YELLOW));
            }
            case "stats" -> {
                p.sendMessage(Component.text("— Your stats —", NamedTextColor.GOLD));
                p.sendMessage(Component.text("Wins " + s.wins + "  Losses " + s.losses + "  Totems popped " + s.popsForced
                        + "  Max combo " + s.maxCombo, NamedTextColor.GRAY));
                for (BotSettings.Difficulty d : BotSettings.Difficulty.values()) {
                    p.sendMessage(Component.text("Best " + d.name() + ": " + BotSettings.formatTime(s.bestMs[d.ordinal()]), NamedTextColor.AQUA));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
