package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Floating board over the south wall: fastest bot kills per difficulty. */
public final class Leaderboard {

    record Entry(String name, String uuid, BotSettings.Difficulty diff, long ms) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private TextDisplay display;

    public void load(YamlConfiguration data) {
        entries.clear();
        for (Map<?, ?> m : data.getMapList("leaderboard")) {
            try {
                entries.add(new Entry(String.valueOf(m.get("name")), String.valueOf(m.get("uuid")),
                        BotSettings.Difficulty.valueOf(String.valueOf(m.get("diff"))),
                        Long.parseLong(String.valueOf(m.get("ms")))));
            } catch (Exception ignored) {
            }
        }
    }

    public void save(YamlConfiguration data) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(Map.of("name", e.name, "uuid", e.uuid, "diff", e.diff.name(), "ms", e.ms));
        }
        data.set("leaderboard", out);
    }

    /** Records a kill time; keeps only each player's best per difficulty. */
    public void submit(String name, String uuid, BotSettings.Difficulty diff, long ms) {
        boolean hasBetter = entries.stream().anyMatch(e -> e.uuid.equals(uuid) && e.diff == diff && e.ms <= ms);
        if (hasBetter) {
            return;
        }
        entries.removeIf(e -> e.uuid.equals(uuid) && e.diff == diff);
        entries.add(new Entry(name, uuid, diff, ms));
    }

    public void render(World w) {
        if (display == null || !display.isValid()) {
            display = w.spawn(Arena.leaderboardSpot(w), TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setPersistent(false);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setLineWidth(260);
            });
        }
        TextComponent.Builder b = Component.text()
                .append(Component.text("⚔ FASTEST BOT KILLS ⚔", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("turn off Bot Infinite Totems to compete", NamedTextColor.DARK_GRAY));
        BotSettings.Difficulty[] shown = {BotSettings.Difficulty.INSANE, BotSettings.Difficulty.HARD, BotSettings.Difficulty.NORMAL};
        for (BotSettings.Difficulty d : shown) {
            b.append(Component.newline()).append(Component.newline())
                    .append(Component.text(d.name(), color(d)).decorate(TextDecoration.BOLD));
            List<Entry> top = entries.stream().filter(e -> e.diff == d)
                    .sorted(Comparator.comparingLong(Entry::ms)).limit(3).toList();
            if (top.isEmpty()) {
                b.append(Component.newline()).append(Component.text("no kills yet", NamedTextColor.GRAY));
            }
            int rank = 1;
            for (Entry e : top) {
                b.append(Component.newline())
                        .append(Component.text("#" + rank++ + " ", NamedTextColor.YELLOW))
                        .append(Component.text(e.name + "  ", NamedTextColor.WHITE))
                        .append(Component.text(BotSettings.formatTime(e.ms), NamedTextColor.AQUA));
            }
        }
        display.text(b.build());
    }

    public void remove() {
        if (display != null) display.remove();
    }

    private static NamedTextColor color(BotSettings.Difficulty d) {
        return switch (d) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case INSANE -> NamedTextColor.DARK_PURPLE;
        };
    }
}
