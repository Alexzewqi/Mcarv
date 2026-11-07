package dev.macepractice;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A player-shaped Mannequin driven by a small hand-written combat AI:
 * strafes and sprint/crit-hits with the sword, goes for wind charge + density mace smashes
 * (chaining them with Wind Burst), shields or dodges your mace dives and pearls back when you run.
 */
public final class PracticeBot {

    private enum State { FIGHT, COMBO, BLOCK }

    private static final double REACH = 3.0;
    private static final int SWORD_COOLDOWN = 12;   // 0.625s
    private static final int RESPAWN_DELAY = 60;

    private final MacePractice plugin;
    private final Player owner;
    private final BotSettings s;

    private Mannequin entity;
    private State state = State.FIGHT;
    private int tick;
    private int attackCd;
    private int comboCd = 60;
    private int comboTicks;
    private int chainLeft;
    private int blockTicks;
    private int knockbackTicks;
    private int pearlCd = 100;
    private int reactionWait;
    private int strafeDir = 1;
    private int strafeSwitch;
    private boolean wantsCrit;
    private int respawnIn = -1;
    private int totemsLeft;
    private int pops;
    private boolean popRefillPending;

    public PracticeBot(MacePractice plugin, Player owner, BotSettings settings) {
        this.plugin = plugin;
        this.owner = owner;
        this.s = settings;
    }

    public Mannequin entity() {
        return entity;
    }

    public Player owner() {
        return owner;
    }

    // ---- lifecycle ----------------------------------------------------------------------

    public void spawn() {
        World w = plugin.arenaWorld();
        Location loc = Arena.botSpawn(w);
        entity = w.spawn(loc, Mannequin.class, m -> {
            m.customName(Component.text(owner.getName() + "'s Bot", NamedTextColor.RED));
            m.setCustomNameVisible(true);
            m.setDescription(Component.text("Mace PvP Practice", NamedTextColor.GRAY));
            m.setPersistent(false);
            m.setImmovable(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            AttributeInstance step = m.getAttribute(Attribute.STEP_HEIGHT);
            if (step != null) {
                step.setBaseValue(0.6);
            }
            EntityEquipment eq = m.getEquipment();
            eq.setHelmet(Kit.helmet());
            eq.setChestplate(Kit.chestplate());
            eq.setLeggings(Kit.leggings());
            eq.setBoots(Kit.boots());
            eq.setItemInMainHand(Kit.sword());
            eq.setItemInOffHand(Kit.totem());
        });
        resetState();
    }

    public void reset() {
        if (entity == null || !entity.isValid()) {
            spawn();
            return;
        }
        entity.teleport(Arena.botSpawn(entity.getWorld()));
        entity.setVelocity(new Vector());
        entity.setFireTicks(0);
        entity.setFallDistance(0);
        entity.setAbsorptionAmount(0);
        entity.clearActivePotionEffects();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        entity.setHealth(max != null ? max.getValue() : 20.0);
        entity.getEquipment().setItemInOffHand(Kit.totem());
        holdMain(Kit.sword());
        resetState();
    }

    private void resetState() {
        state = State.FIGHT;
        attackCd = 20;
        comboCd = 60;
        pearlCd = 100;
        knockbackTicks = 0;
        totemsLeft = s.botTotems;
        pops = 0;
        respawnIn = -1;
    }

    public void remove() {
        if (entity != null) {
            entity.remove();
        }
    }

    // ---- events from the listener -------------------------------------------------------

    /** The owner hit us: let vanilla knockback play out before steering again. */
    public void onHurt() {
        knockbackTicks = 8;
    }

    /** A totem popped. Returns a short status line for the owner. */
    public void onTotemPop() {
        pops++;
        if (!s.botInfiniteTotems) {
            totemsLeft--;
        }
        popRefillPending = true;
        owner.sendActionBar(Component.text("Bot popped! (" + pops + ")", NamedTextColor.GOLD));
    }

    public void onDeath() {
        respawnIn = RESPAWN_DELAY;
        owner.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("You won!", NamedTextColor.GREEN),
                Component.text("Bot popped " + pops + " totems", NamedTextColor.GRAY)));
        owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** Owner died: heal up for the next round. */
    public void onOwnerDeath() {
        owner.sendMessage(Component.text("Bot won. It popped " + pops + " totems this round.", NamedTextColor.RED));
    }

    public int pops() {
        return pops;
    }

    // ---- AI -----------------------------------------------------------------------------

    public void tick() {
        tick++;
        if (!owner.isOnline()) {
            return;
        }
        if (entity == null || !entity.isValid() || entity.isDead()) {
            if (respawnIn < 0) {
                respawnIn = RESPAWN_DELAY;
            }
            if (--respawnIn <= 0) {
                plugin.resetRound(owner);
            }
            return;
        }

        if (popRefillPending) {
            popRefillPending = false;
            if (s.botInfiniteTotems || totemsLeft > 0) {
                entity.getEquipment().setItemInOffHand(Kit.totem());
            } else {
                entity.getEquipment().setItemInOffHand(null);
                owner.sendActionBar(Component.text("Bot is out of totems!", NamedTextColor.RED));
            }
        }

        Location me = entity.getLocation();
        if (!Arena.inside(me) || me.getY() > 400) {
            entity.teleport(Arena.botSpawn(me.getWorld()));
            entity.setVelocity(new Vector());
            entity.setFallDistance(0);
            return;
        }

        think();
    }

    private void think() {

        if (attackCd > 0) attackCd--;
        if (comboCd > 0) comboCd--;
        if (pearlCd > 0) pearlCd--;
        if (knockbackTicks > 0) knockbackTicks--;

        // Saturated-player style regen: half a heart every 10 ticks.
        if (tick % 10 == 0) {
            AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
            double mh = max != null ? max.getValue() : 20.0;
            if (entity.getHealth() < mh) {
                entity.setHealth(Math.min(mh, entity.getHealth() + 1.0));
            }
        }

        if (tick % 5 == 0) {
            sendHud();
        }

        boolean engage = owner.getWorld().equals(entity.getWorld()) && !owner.isDead()
                && (owner.getGameMode() == org.bukkit.GameMode.SURVIVAL || owner.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                && Arena.inside(owner.getLocation());

        if (!engage) {
            if (state == State.BLOCK) stopBlocking();
            state = State.FIGHT;
            idle();
            return;
        }

        entity.lookAt(owner.getEyeLocation().getX(), owner.getEyeLocation().getY(), owner.getEyeLocation().getZ(), LookAnchor.EYES);
        entity.setBodyYaw(entity.getYaw());

        switch (state) {
            case FIGHT -> fight();
            case COMBO -> combo();
            case BLOCK -> block();
        }
    }

    private void idle() {
        Vector v = entity.getVelocity();
        if (entity.isOnGround() && knockbackTicks == 0) {
            v.setX(v.getX() * 0.5);
            v.setZ(v.getZ() * 0.5);
            entity.setVelocity(v);
        }
    }

    private void fight() {
        Location me = entity.getLocation();
        Location them = owner.getLocation();
        Vector flat = them.toVector().subtract(me.toVector()).setY(0);
        double dist = flat.length();
        Vector fwd = dist > 0.001 ? flat.clone().normalize() : new Vector(0, 0, 1);
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX()).multiply(strafeDir);

        // --- defence: you're diving at it with a mace ---
        if (incomingDive(dist)) {
            if (ThreadLocalRandom.current().nextDouble() < s.difficulty.reactChance) {
                if (s.shield) {
                    startBlocking();
                    return;
                }
                if (s.movement && entity.isOnGround()) {
                    // Sidestep out of the landing zone.
                    Vector v = side.clone().multiply(0.45).add(fwd.clone().multiply(-0.2));
                    v.setY(0.42);
                    entity.setVelocity(v);
                    knockbackTicks = 6;
                    return;
                }
            }
        }

        // --- offence: wind charge + mace combo ---
        if (s.attacks && s.maceCombos && comboCd == 0 && entity.isOnGround() && dist > 1.5 && dist < 8
                && ThreadLocalRandom.current().nextDouble() < s.difficulty.comboChance) {
            startCombo(fwd, dist);
            return;
        }

        // --- pearl back when you run away ---
        if (s.movement && s.attacks && dist > 16 && pearlCd == 0 && entity.isOnGround()) {
            throwPearl();
        }

        // --- movement ---
        if (s.movement) {
            if (--strafeSwitch <= 0) {
                strafeDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                strafeSwitch = 15 + ThreadLocalRandom.current().nextInt(30);
            }
            double speed = s.difficulty.speed;
            Vector desired;
            if (dist > 3.2) {
                desired = fwd.clone().multiply(speed).add(side.clone().multiply(speed * 0.25));
            } else if (dist < 1.6) {
                desired = fwd.clone().multiply(-speed * 0.6).add(side.clone().multiply(speed * 0.6));
            } else {
                desired = side.clone().multiply(speed * 0.75).add(fwd.clone().multiply(speed * 0.35));
            }
            steer(desired, entity.isOnGround() ? 1.0 : 0.08);
        } else {
            idle();
        }

        // --- melee ---
        if (!s.attacks) {
            return;
        }
        double reach = reachTo(owner);
        if (reach > REACH + 1.5) {
            reactionWait = s.difficulty.reactionDelay;
            wantsCrit = false;
            return;
        }
        if (reactionWait > 0) {
            reactionWait--;
            return;
        }
        if (attackCd <= 3 && entity.isOnGround() && !wantsCrit && s.movement
                && ThreadLocalRandom.current().nextDouble() < s.difficulty.critChance) {
            // Jump now so we are falling when the cooldown is ready -> crit.
            wantsCrit = true;
            Vector v = entity.getVelocity();
            v.setY(0.42);
            entity.setVelocity(v);
        }
        if (attackCd > 0 || reach > REACH) {
            return;
        }
        boolean falling = !entity.isOnGround() && entity.getVelocity().getY() < -0.05;
        if (wantsCrit && !falling && !entity.isOnGround()) {
            return; // still rising, wait for the crit window
        }
        swordHit(falling);
        wantsCrit = false;
    }

    private boolean incomingDive(double flatDist) {
        if (flatDist > 5) return false;
        if (owner.isOnGround()) return false;
        double above = owner.getLocation().getY() - entity.getLocation().getY();
        Material held = owner.getInventory().getItemInMainHand().getType();
        boolean diving = owner.getVelocity().getY() < -0.1 || owner.getFallDistance() > 1.5;
        return above > 2.0 && diving && held == Material.MACE;
    }

    private void steer(Vector desired, double grip) {
        if (knockbackTicks > 0) {
            return;
        }
        Vector v = entity.getVelocity();
        v.setX(v.getX() + (desired.getX() - v.getX()) * grip);
        v.setZ(v.getZ() + (desired.getZ() - v.getZ()) * grip);
        entity.setVelocity(v);
    }

    private void swordHit(boolean crit) {
        holdMain(Kit.sword());
        entity.swingMainHand();
        attackCd = SWORD_COOLDOWN;
        if (ThreadLocalRandom.current().nextDouble() < s.difficulty.missChance) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1f);
            return;
        }
        // Netherite sword (8) + Sharpness V (3), crits x1.5.
        double dmg = 11.0 * (crit ? 1.5 : 1.0);
        boolean blocked = owner.isBlocking();
        owner.damage(dmg, source(DamageType.MOB_ATTACK));
        Location at = owner.getLocation().add(0, 1, 0);
        if (crit) {
            entity.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.3, 0.5, 0.3, 0.2);
            entity.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        } else {
            entity.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
        }
        if (!blocked && s.movement) {
            // Sprint-hit bonus knockback on top of vanilla's.
            Vector dir = owner.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() > 1e-4) {
                owner.setVelocity(owner.getVelocity().add(dir.normalize().multiply(0.35)).setY(Math.max(owner.getVelocity().getY(), 0.36)));
            }
        }
    }

    // ---- mace combo ---------------------------------------------------------------------

    private void startCombo(Vector fwd, double dist) {
        state = State.COMBO;
        comboTicks = 0;
        chainLeft = s.difficulty.maxChain;
        holdMain(new ItemStack(Material.WIND_CHARGE));
        entity.swingMainHand();
        Location feet = entity.getLocation();
        World w = entity.getWorld();
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, feet, 1);
        w.playSound(feet, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        Vector v = fwd.clone().multiply(Math.min(0.45, dist * 0.06));
        v.setY(1.1);
        entity.setVelocity(v);
        entity.setFallDistance(0);
        knockbackTicks = 0;
    }

    private void combo() {
        comboTicks++;
        if (comboTicks == 3) {
            holdMain(Kit.densityMace());
        }
        Location me = entity.getLocation();
        Vector flat = owner.getLocation().toVector().subtract(me.toVector()).setY(0);
        double dist = flat.length();
        Vector v = entity.getVelocity();

        // Air-steer to end up right above you.
        if (knockbackTicks == 0 && dist > 0.3) {
            Vector want = flat.clone().normalize().multiply(Math.min(0.4, dist * 0.15));
            v.setX(v.getX() + (want.getX() - v.getX()) * 0.25);
            v.setZ(v.getZ() + (want.getZ() - v.getZ()) * 0.25);
            entity.setVelocity(v);
        }

        float fall = entity.getFallDistance();
        if (v.getY() < 0 && fall > 1.5 && reachTo(owner) <= REACH + 0.2) {
            maceSmash(fall);
            return;
        }

        if ((comboTicks > 6 && entity.isOnGround()) || comboTicks > 80) {
            endCombo();
        }
    }

    private void maceSmash(float fall) {
        entity.swingMainHand();
        // Mace smash bonus: 4/block for the first 3, 2/block for the next 5, then 1/block.
        double smash = fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
        double density = 0.5 * 5 * fall;
        double dmg = 6 * 1.5 + smash + density;
        boolean blocked = owner.isBlocking();
        owner.damage(dmg, source(DamageType.MACE_SMASH));

        World w = entity.getWorld();
        Location at = owner.getLocation();
        if (!blocked) {
            w.playSound(at, fall > 5 ? Sound.ITEM_MACE_SMASH_GROUND_HEAVY : Sound.ITEM_MACE_SMASH_GROUND, 1f, 1f);
            w.spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.3);
        }

        // Wind Burst III: bounce back up and maybe go again.
        entity.setFallDistance(0);
        w.playSound(entity.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.1f);
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, entity.getLocation(), 1);
        Vector v = entity.getVelocity();
        v.setY(1.0);
        entity.setVelocity(v);
        attackCd = SWORD_COOLDOWN;
        if (chainLeft-- > 0) {
            comboTicks = 3;
        } else {
            endCombo();
            // Still in the air after the bounce; keep the mace out until we land.
            holdMain(Kit.densityMace());
        }
    }

    private void endCombo() {
        state = State.FIGHT;
        holdMain(Kit.sword());
        comboCd = 40 + ThreadLocalRandom.current().nextInt(80) + (2 - s.difficulty.ordinal()) * 40;
    }

    // ---- shield -------------------------------------------------------------------------

    private void startBlocking() {
        state = State.BLOCK;
        blockTicks = 0;
        holdMain(Kit.shield());
        entity.startUsingItem(EquipmentSlot.HAND);
    }

    private void block() {
        blockTicks++;
        if (!entity.hasActiveItem() && blockTicks > 1) {
            entity.startUsingItem(EquipmentSlot.HAND);
        }
        steer(new Vector(), entity.isOnGround() ? 1.0 : 0.05);
        boolean threatOver = owner.isOnGround() || owner.getLocation().getY() < entity.getLocation().getY() + 0.5;
        if ((threatOver && blockTicks > 6) || blockTicks > 40) {
            stopBlocking();
            attackCd = Math.min(attackCd, 2); // punish right after you land
        }
    }

    private void stopBlocking() {
        if (entity.hasActiveItem()) {
            entity.clearActiveItem();
        }
        holdMain(Kit.sword());
        state = State.FIGHT;
    }

    // ---- misc ---------------------------------------------------------------------------

    private void throwPearl() {
        pearlCd = 160;
        holdMain(new ItemStack(Material.ENDER_PEARL));
        entity.swingMainHand();
        Location eye = entity.getEyeLocation();
        Vector to = owner.getLocation().toVector().subtract(eye.toVector());
        double d = to.clone().setY(0).length();
        Vector vel = to.clone().setY(0).normalize().multiply(1.5);
        vel.setY(Math.min(0.9, 0.08 * d + (to.getY() / Math.max(1, d)) * 1.5));
        entity.launchProjectile(EnderPearl.class, vel);
        entity.getWorld().playSound(eye, Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (state == State.FIGHT && entity != null && entity.isValid()) holdMain(Kit.sword());
        }, 4L);
    }

    private void holdMain(ItemStack item) {
        EntityEquipment eq = entity.getEquipment();
        if (eq.getItemInMainHand().getType() != item.getType()) {
            eq.setItemInMainHand(item);
        }
    }

    private DamageSource source(DamageType type) {
        return DamageSource.builder(type).withCausingEntity(entity).withDirectEntity(entity).build();
    }

    /** Distance from our eyes to the closest point of the target's hitbox (how vanilla reach works). */
    private double reachTo(Player p) {
        Location eye = entity.getEyeLocation();
        BoundingBox box = p.getBoundingBox();
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return eye.toVector().distance(new Vector(x, y, z));
    }

    private void sendHud() {
        if (state == State.COMBO && tick % 20 != 0) return;
        double hp = entity.getHealth() + entity.getAbsorptionAmount();
        String totems = s.botInfiniteTotems ? "∞" : String.valueOf(Math.max(0, totemsLeft));
        owner.sendActionBar(Component.text()
                .append(Component.text("Bot ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f❤", hp / 2.0), NamedTextColor.RED))
                .append(Component.text("  Totems: ", NamedTextColor.GRAY))
                .append(Component.text(totems, NamedTextColor.GOLD))
                .append(Component.text("  Pops: ", NamedTextColor.GRAY))
                .append(Component.text(pops, NamedTextColor.YELLOW))
                .append(Component.text("  [" + s.difficulty.name() + (s.movement ? "" : ", frozen") + "]", NamedTextColor.DARK_GRAY))
                .build());
    }
}
