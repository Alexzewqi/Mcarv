package dev.macepractice;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A square open-sky arena floating in the void, walled with barriers up to build height. */
public final class Arena {

    public static final int FLOOR_Y = 100;
    public static final int RADIUS = 30;

    private Arena() {
    }

    public static Location playerSpawn(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, -10.5, 0f, 0f);
    }

    public static Location botSpawn(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, 10.5, 180f, 0f);
    }

    public static boolean inside(Location l) {
        return Math.abs(l.getX()) < RADIUS && Math.abs(l.getZ()) < RADIUS && l.getY() > FLOOR_Y - 3;
    }

    public static void build(World w) {
        // Marker block: if the corner wall already exists, the arena was built on a previous start.
        if (w.getBlockAt(RADIUS, FLOOR_Y + 1, RADIUS).getType() == Material.POLISHED_BLACKSTONE_BRICKS
                && w.getBlockAt(0, FLOOR_Y, 0).getType() != Material.AIR) {
            return;
        }
        int top = w.getMaxHeight() - 1;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                set(w, x, FLOOR_Y - 1, z, Material.BEDROCK);
                set(w, x, FLOOR_Y, z, floor(x, z));
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                if (edge) {
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                        set(w, x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                    set(w, x, FLOOR_Y + 4, z, Material.TINTED_GLASS);
                    for (int y = FLOOR_Y + 5; y <= top; y++) {
                        set(w, x, y, z, Material.BARRIER);
                    }
                }
            }
        }
    }

    private static Material floor(int x, int z) {
        double d = Math.sqrt(x * x + z * z);
        if (d < 1.5) {
            return Material.CRYING_OBSIDIAN;
        }
        if (Math.abs(d - 8) < 0.5 || Math.abs(d - 18) < 0.5) {
            return Material.POLISHED_BLACKSTONE;
        }
        if (Math.abs(x) % 6 == 0 && Math.abs(z) % 6 == 0) {
            return Material.SEA_LANTERN;
        }
        return ((x + z) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
    }

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }
}
