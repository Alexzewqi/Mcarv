package dev.macepractice;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Two flat, square, open-sky arenas floating in the void, walled with barriers up to build height:
 * the mace arena (indestructible, themed) and the crystal arena (grass/dirt/stone that explodes and
 * regenerates every round).
 */
public final class Arena {

    public static final int FLOOR_Y = 100;
    public static final int RADIUS = 30;
    /** Depth of the destructible terrain in the crystal arena. */
    public static final int CRYSTAL_DEPTH = 8;
    /** How high players can build in the crystal arena. */
    public static final int CRYSTAL_BUILD_HEIGHT = 24;

    public static final Arena MACE = new Arena(0, 0);
    public static final Arena CRYSTAL = new Arena(0, 160);

    final int cx;
    final int cz;

    private Arena(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public static Arena of(BotSettings.Mode mode) {
        return mode == BotSettings.Mode.CRYSTAL ? CRYSTAL : MACE;
    }

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

    public Location playerSpawn(World w) {
        return new Location(w, cx + 0.5, FLOOR_Y + 1, cz - 10.5, 0f, 0f);
    }

    public Location botSpawn(World w) {
        return new Location(w, cx + 0.5, FLOOR_Y + 1, cz + 10.5, 180f, 0f);
    }

    public Location leaderboardSpot(World w) {
        return new Location(w, cx + 0.5, FLOOR_Y + 6.5, cz + RADIUS - 1.5);
    }

    public boolean inside(Location l) {
        int depth = this == CRYSTAL ? CRYSTAL_DEPTH + 2 : 3;
        return Math.abs(l.getX() - cx) < RADIUS && Math.abs(l.getZ() - cz) < RADIUS && l.getY() > FLOOR_Y - depth;
    }

    /** Inside the crystal arena's buildable volume (walls and bedrock excluded). */
    public boolean buildable(Location l) {
        return Math.abs(l.getBlockX() - cx) < RADIUS && Math.abs(l.getBlockZ() - cz) < RADIUS
                && l.getBlockY() > FLOOR_Y - CRYSTAL_DEPTH && l.getBlockY() <= FLOOR_Y + CRYSTAL_BUILD_HEIGHT;
    }

    public org.bukkit.util.BoundingBox box() {
        return new org.bukkit.util.BoundingBox(cx - RADIUS, FLOOR_Y - CRYSTAL_DEPTH - 2, cz - RADIUS,
                cx + RADIUS + 1, FLOOR_Y + CRYSTAL_BUILD_HEIGHT + 10, cz + RADIUS + 1);
    }

    /**
     * Builds / regenerates the crystal arena: grass on dirt on stone on bedrock, blast-proof walls.
     * Only blocks that differ are changed, so a regen after a round is cheap.
     */
    public static void buildCrystal(World w) {
        int top = w.getMaxHeight() - 1;
        int ox = CRYSTAL.cx, oz = CRYSTAL.cz;
        boolean barriersDone = w.getBlockAt(ox + RADIUS, top, oz + RADIUS).getType() == Material.BARRIER;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                set(w, ox + x, FLOOR_Y - CRYSTAL_DEPTH - 1, oz + z, Material.BEDROCK);
                for (int y = FLOOR_Y - CRYSTAL_DEPTH; y <= FLOOR_Y + CRYSTAL_BUILD_HEIGHT; y++) {
                    Material m;
                    if (edge) {
                        m = y <= FLOOR_Y + 4 ? (y == FLOOR_Y + 3 && (x + z) % 10 == 0 ? Material.SEA_LANTERN
                                : Material.REINFORCED_DEEPSLATE) : Material.BARRIER;
                    } else if (y == FLOOR_Y) {
                        m = Material.GRASS_BLOCK;
                    } else if (y >= FLOOR_Y - 3 && y < FLOOR_Y) {
                        m = Material.DIRT;
                    } else if (y < FLOOR_Y) {
                        m = Material.STONE;
                    } else {
                        m = Material.AIR;
                    }
                    set(w, ox + x, y, oz + z, m);
                }
                if (edge && !barriersDone) {
                    for (int y = FLOOR_Y + CRYSTAL_BUILD_HEIGHT + 1; y <= top; y++) {
                        set(w, ox + x, y, oz + z, Material.BARRIER);
                    }
                }
            }
        }
        // Clear dropped items, leftover crystals, falling blocks etc. Players, bots and holograms stay.
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(CRYSTAL.box())) {
            if (e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.EnderCrystal
                    || e instanceof org.bukkit.entity.FallingBlock || e instanceof org.bukkit.entity.ExperienceOrb) {
                e.remove();
            }
        }
    }

    /** Builds the mace arena in the given theme. Only blocks that differ are changed, so re-runs are cheap. */
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
