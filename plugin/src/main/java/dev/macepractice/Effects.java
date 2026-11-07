package dev.macepractice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.ThreadLocalRandom;

/** Damage numbers, win celebration and other eye candy. */
public final class Effects {

    public static final String HARMLESS = "macepractice_harmless";

    private Effects() {
    }

    /** Floating "-4.5" that rises and disappears. Big hits are gold and bold. */
    public static void damageNumber(MacePractice plugin, Location at, double hearts, boolean big) {
        World w = at.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location spawn = at.clone().add(r.nextDouble(-0.4, 0.4), 0.2, r.nextDouble(-0.4, 0.4));
        TextColor color = big ? NamedTextColor.GOLD : hearts >= 3 ? NamedTextColor.RED : NamedTextColor.WHITE;
        Component text = Component.text(String.format("-%.1f❤", hearts), color);
        if (big) text = text.decorate(TextDecoration.BOLD);
        final Component finalText = text;
        TextDisplay d = w.spawn(spawn, TextDisplay.class, t -> {
            t.text(finalText);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setPersistent(false);
            t.setTeleportDuration(20);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) d.teleport(spawn.clone().add(0, 1.2, 0));
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 24L);
    }

    public static void victory(MacePractice plugin, Location at, Player winner) {
        World w = at.getWorld();
        w.strikeLightningEffect(at);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 120, 0.5, 1, 0.5, 0.6);
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        for (int i = 0; i < 3; i++) {
            int delay = i * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Firework fw = w.spawn(at.clone().add(0, 1, 0), Firework.class, f -> {
                    f.setMetadata(HARMLESS, new FixedMetadataValue(plugin, true));
                    FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.fromRGB(0xFFD24A), Color.fromRGB(0x5CE1E6))
                            .withFade(Color.WHITE).withFlicker().build());
                    meta.setPower(1);
                    f.setFireworkMeta(meta);
                });
                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 12L);
            }, delay);
        }
    }
}
