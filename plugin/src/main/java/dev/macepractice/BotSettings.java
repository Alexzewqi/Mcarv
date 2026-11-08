package dev.macepractice;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.List;

/** Per-player toggles + stats, changed from the menu and saved to data.yml. */
public final class BotSettings {

    public enum Difficulty {
        EASY(0.20, 0.25, 0.010, 0.25, 0, 7, 0.35),
        NORMAL(0.26, 0.55, 0.025, 0.55, 1, 4, 0.15),
        HARD(0.29, 0.85, 0.045, 0.85, 2, 2, 0.04),
        INSANE(0.31, 0.97, 0.070, 0.97, 3, 0, 0.0);

        /** Ground speed in blocks/tick (player sprint is ~0.28). */
        final double speed;
        /** Chance to go for a jump-crit instead of a plain hit. */
        final double critChance;
        /** Per-tick chance to start a wind charge + mace combo once its cooldown is ready. */
        final double comboChance;
        /** Chance to react (shield/dodge) when you dive at it with a mace. */
        final double reactChance;
        /** Extra mace smashes chained off Wind Burst. */
        final int maxChain;
        /** Extra ticks of hesitation before swinging. */
        final int reactionDelay;
        /** Chance a swing whiffs. */
        final double missChance;

        Difficulty(double speed, double critChance, double comboChance, double reactChance,
                   int maxChain, int reactionDelay, double missChance) {
            this.speed = speed;
            this.critChance = critChance;
            this.comboChance = comboChance;
            this.reactChance = reactChance;
            this.maxChain = maxChain;
            this.reactionDelay = reactionDelay;
            this.missChance = missChance;
        }

        public Difficulty next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Mode {
        MACE("Mace PvP"), CRYSTAL("Crystal PvP");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Skin {
        DEFAULT("Default"), MINE("Your skin");

        final String label;

        Skin(String label) {
            this.label = label;
        }

        public Skin next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // ---- toggles ----
    public Mode mode = Mode.MACE;
    public boolean movement = true;
    public boolean attacks = true;
    public boolean maceCombos = true;
    public boolean shield = true;
    public boolean botInfiniteTotems = true;
    public int botTotems = 10;
    public boolean playerInfiniteTotems = false;
    public Difficulty difficulty = Difficulty.NORMAL;
    public Skin skin = Skin.DEFAULT;
    public boolean damageNumbers = true;
    public boolean sidebar = true;
    public boolean countdown = true;
    public boolean infiniteFood = false;
    /** Saved kit layouts per mode: material name per inventory slot (41 entries), or null for the default. */
    public String[] layout;
    public String[] crystalLayout;

    // ---- stats ----
    public int wins;
    public int losses;
    public int popsForced;
    public int maxCombo;
    /** Fastest bot kill, [mode][difficulty]. */
    public long[][] bestMs = new long[Mode.values().length][Difficulty.values().length];

    public String[] layoutFor(Mode m) {
        return m == Mode.CRYSTAL ? crystalLayout : layout;
    }

    public void setLayout(Mode m, String[] l) {
        if (m == Mode.CRYSTAL) crystalLayout = l;
        else layout = l;
    }

    public long best() {
        return bestMs[mode.ordinal()][difficulty.ordinal()];
    }

    public void load(ConfigurationSection c) {
        if (c == null) return;
        movement = c.getBoolean("movement", movement);
        attacks = c.getBoolean("attacks", attacks);
        maceCombos = c.getBoolean("maceCombos", maceCombos);
        shield = c.getBoolean("shield", shield);
        botInfiniteTotems = c.getBoolean("botInfiniteTotems", botInfiniteTotems);
        playerInfiniteTotems = c.getBoolean("playerInfiniteTotems", playerInfiniteTotems);
        damageNumbers = c.getBoolean("damageNumbers", damageNumbers);
        sidebar = c.getBoolean("sidebar", sidebar);
        countdown = c.getBoolean("countdown", countdown);
        infiniteFood = c.getBoolean("infiniteFood", infiniteFood);
        try {
            difficulty = Difficulty.valueOf(c.getString("difficulty", difficulty.name()));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            mode = Mode.valueOf(c.getString("mode", mode.name()));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            skin = Skin.valueOf(c.getString("skin", skin.name()));
        } catch (IllegalArgumentException ignored) {
        }
        List<String> l = c.getStringList("layout");
        layout = l.size() == 41 ? l.toArray(new String[0]) : null;
        l = c.getStringList("crystalLayout");
        crystalLayout = l.size() == 41 ? l.toArray(new String[0]) : null;
        wins = c.getInt("wins");
        losses = c.getInt("losses");
        popsForced = c.getInt("popsForced");
        maxCombo = c.getInt("maxCombo");
        for (Difficulty d : Difficulty.values()) {
            bestMs[Mode.MACE.ordinal()][d.ordinal()] = c.getLong("best." + d.name(), 0);
            bestMs[Mode.CRYSTAL.ordinal()][d.ordinal()] = c.getLong("bestCrystal." + d.name(), 0);
        }
    }

    public void save(ConfigurationSection c) {
        c.set("movement", movement);
        c.set("attacks", attacks);
        c.set("maceCombos", maceCombos);
        c.set("shield", shield);
        c.set("botInfiniteTotems", botInfiniteTotems);
        c.set("playerInfiniteTotems", playerInfiniteTotems);
        c.set("damageNumbers", damageNumbers);
        c.set("sidebar", sidebar);
        c.set("countdown", countdown);
        c.set("infiniteFood", infiniteFood);
        c.set("difficulty", difficulty.name());
        c.set("skin", skin.name());
        c.set("mode", mode.name());
        c.set("crystalLayout", crystalLayout == null ? null : Arrays.asList(crystalLayout));
        c.set("layout", layout == null ? null : Arrays.asList(layout));
        c.set("wins", wins);
        c.set("losses", losses);
        c.set("popsForced", popsForced);
        c.set("maxCombo", maxCombo);
        for (Difficulty d : Difficulty.values()) {
            long m = bestMs[Mode.MACE.ordinal()][d.ordinal()];
            long cr = bestMs[Mode.CRYSTAL.ordinal()][d.ordinal()];
            c.set("best." + d.name(), m == 0 ? null : m);
            c.set("bestCrystal." + d.name(), cr == 0 ? null : cr);
        }
    }

    public static String formatTime(long ms) {
        if (ms <= 0) return "-";
        return String.format("%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }
}
