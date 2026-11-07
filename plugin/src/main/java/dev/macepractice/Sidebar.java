package dev.macepractice;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/** Per-player sidebar with the round + your stats. */
public final class Sidebar {

    private Sidebar() {
    }

    public static void update(Player p, BotSettings s, PracticeBot bot) {
        if (!s.sidebar) {
            if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        Scoreboard sb = p.getScoreboard();
        if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(sb);
        }
        Objective o = sb.getObjective("mp");
        if (o == null) {
            o = sb.registerNewObjective("mp", Criteria.DUMMY,
                    Component.text("⚔ MACE PRACTICE ⚔", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        if (bot != null && bot.entity() != null && bot.entity().isValid()) {
            double hp = bot.entity().getHealth() + bot.entity().getAbsorptionAmount();
            lines.add(kv("Bot", String.format("%.1f❤", hp / 2), NamedTextColor.RED));
            lines.add(kv("Bot totems", s.botInfiniteTotems ? "∞" : String.valueOf(bot.totemsLeft()), NamedTextColor.GOLD));
            lines.add(kv("Pops this round", String.valueOf(bot.pops()), NamedTextColor.YELLOW));
            lines.add(kv("Round time", BotSettings.formatTime(bot.roundMs()), NamedTextColor.AQUA));
            lines.add(kv("Combo", bot.combo() + " (max " + s.maxCombo + ")", NamedTextColor.LIGHT_PURPLE));
        }
        lines.add(Component.empty());
        lines.add(kv("Difficulty", s.difficulty.name(), NamedTextColor.WHITE));
        lines.add(kv("Wins / Losses", s.wins + " / " + s.losses, NamedTextColor.GREEN));
        lines.add(kv("Totems popped", String.valueOf(s.popsForced), NamedTextColor.GOLD));
        lines.add(kv("Best (" + s.difficulty.name().charAt(0) + ")", BotSettings.formatTime(s.bestMs[s.difficulty.ordinal()]), NamedTextColor.AQUA));
        lines.add(Component.empty());
        lines.add(Component.text("/bot  •  sneak + F", NamedTextColor.DARK_GRAY));

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            var sc = o.getScore("l" + i);
            sc.setScore(score--);
            sc.customName(lines.get(i));
        }
        for (int i = lines.size(); i < 20; i++) {
            sb.resetScores("l" + i);
        }
    }

    private static Component kv(String k, String v, NamedTextColor c) {
        return Component.text(k + ": ", NamedTextColor.GRAY).append(Component.text(v, c));
    }
}
