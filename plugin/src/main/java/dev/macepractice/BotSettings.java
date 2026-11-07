package dev.macepractice;

/** Per-player toggles, changed from the menu. */
public final class BotSettings {

    public enum Difficulty {
        EASY(0.20, 0.25, 0.010, 0.25, 0, 7, 0.35),
        NORMAL(0.26, 0.55, 0.025, 0.55, 1, 4, 0.15),
        HARD(0.29, 0.85, 0.045, 0.85, 2, 2, 0.04);

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

    public boolean movement = true;
    public boolean attacks = true;
    public boolean maceCombos = true;
    public boolean shield = true;
    public boolean botInfiniteTotems = true;
    public int botTotems = 10;
    public boolean playerInfiniteTotems = false;
    public Difficulty difficulty = Difficulty.NORMAL;
}
