package dev.macepractice;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A flat, square, open-sky arena floating in the void, walled with barriers up to build height. */
public final class Arena {

    public static final int FLOOR_Y = 100;
    public static final int RADIUS = 30;

    /** All themes are perfectly flat and use full blocks with normal friction (no ice, no soul sand). */
    public enum Theme {
        DEEPSLATE("Deepslate", Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.POLISHED_BLACKSTONE,
                Material.CRYING_OBSIDIAN, Material.SEA_LANTERN, Material.POLISHED_BLACKSTONE_BRICKS, Material.TINTED_GLASS),
        NETHER("Nether", Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.POLISHED_BLACKSTONE,
                Material.CRYING_OBSIDIAN, Material.SHROOMLIGHT, Material.CHISELED_NETHER_BRICKS, Material.RED_STAINED_GLASS),
        END("End", Material.END_STONE_BRICKS, Material.END_STONE, Material.PURPUR_BLOCK,
                Material.OBSIDIAN, Material.PEARLESCENT_FROGLIGHT, Material.PURPUR_PILLAR, Material.MAGENTA_STAINED_GLASS),
        SNOW("Snow", Material.SNOW_BLOCK, Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.DIAMOND_BLOCK, Material.SEA_LANTERN, Material.CALCITE, Material.LIGHT_BLUE_STAINED_GLASS),
        DESERT("Desert", Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.ORANGE_TERRACOTTA,
                Material.GOLD_BLOCK, Material.OCHRE_FROGLIGHT, Material.CHISELED_SANDSTONE, Material.ORANGE_STAINED_GLASS),
        COPPER("Copper", Material.CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER,
                Material.WAXED_COPPER_BLOCK, Material.VERDANT_FROGLIGHT, Material.WAXED_COPPER_BLOCK, Material.CYAN_STAINED_GLASS),
        GRASS("Grass", Material.MOSS_BLOCK, Material.GREEN_CONCRETE_POWDER, Material.STRIPPED_OAK_WOOD,
                Material.EMERALD_BLOCK, Material.GLOWSTONE, Material.MOSSY_STONE_BRICKS, Material.LIME_STAINED_GLASS);

        final String label;
        final Material floorA, floorB, ring, center, light, wall, trim;

        Theme(String label, Material floorA, Material floorB, Material ring, Material center, Material light,
              Material wall, Material trim) {
            this.label = label;
            this.floorA = floorA;
            this.floorB = floorB;
            this.ring = ring;
            this.center = center;
            this.light = light;
            this.wall = wall;
            this.trim = trim;
        }

        public Theme next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Arena() {
    }

    public static Location playerSpawn(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, -10.5, 0f, 0f);
    }

    public static Location botSpawn(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, 10.5, 180f, 0f);
    }

    public static Location leaderboardSpot(World w) {
        return new Location(w, 0.5, FLOOR_Y + 6.5, RADIUS - 1.5);
    }

    public static boolean inside(Location l) {
        return Math.abs(l.getX()) < RADIUS && Math.abs(l.getZ()) < RADIUS && l.getY() > FLOOR_Y - 3;
    }

    /** Builds the arena in the given theme. Only blocks that differ are changed, so re-runs are cheap. */
    public static void build(World w, Theme t) {
        int top = w.getMaxHeight() - 1;
        boolean barriersDone = w.getBlockAt(RADIUS, top, RADIUS).getType() == Material.BARRIER;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                set(w, x, FLOOR_Y - 1, z, Material.BEDROCK);
                set(w, x, FLOOR_Y, z, floor(t, x, z));
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                if (edge) {
                    boolean post = (x + RADIUS) % 10 == 0 && (z + RADIUS) % 10 == 0
                            || Math.abs(x) == RADIUS && Math.abs(z) == RADIUS;
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                        set(w, x, y, z, post && y == FLOOR_Y + 3 ? t.light : t.wall);
                    }
                    set(w, x, FLOOR_Y + 4, z, t.trim);
                    if (!barriersDone) {
                        for (int y = FLOOR_Y + 5; y <= top; y++) {
                            set(w, x, y, z, Material.BARRIER);
                        }
                    }
                }
            }
        }
    }

    private static Material floor(Theme t, int x, int z) {
        double d = Math.sqrt(x * x + z * z);
        if (d < 1.5) {
            return t.center;
        }
        if (Math.abs(d - 8) < 0.5 || Math.abs(d - 18) < 0.5) {
            return t.ring;
        }
        // Spawn markers for player (north) and bot (south).
        if (x == 0 && Math.abs(z) == 10) {
            return t.center;
        }
        if (Math.abs(x) % 6 == 0 && Math.abs(z) % 6 == 0) {
            return t.light;
        }
        return ((x + z) & 1) == 0 ? t.floorA : t.floorB;
    }

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }
}
