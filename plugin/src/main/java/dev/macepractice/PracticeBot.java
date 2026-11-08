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
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.title.Title;
import java.time.Duration;

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
    private int countdown;
    private long roundStart;
    private int combo;
    private int lastComboTick;

    // ---- crystal pvp ----
    private static final int[] CRYSTAL_CD = {50, 28, 16, 9};
    private static final int[] CRYSTAL_DELAY = {8, 4, 2, 1};
    private static final int[] ANCHOR_CD = {160, 90, 60, 40};
    private static final int[] ANCHOR_FUSE = {10, 6, 4, 3};
    private int crystalCd = 40;
    private int anchorCd = 80;
    private int gappleCd;
    private EnderCrystal pendingCrystal;
    private int crystalExplodeAt;
    private Block anchorBlock;
    private int anchorStage;
    private boolean exploding;
    private Location explosionCenter;

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
        Location loc = arena().botSpawn(w);
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
            eq.setHelmet(Kit.unbreakable(Kit.helmet()));
            eq.setChestplate(Kit.unbreakable(Kit.chestplate()));
            eq.setLeggings(Kit.unbreakable(Kit.leggings()));
            eq.setBoots(Kit.unbreakable(Kit.boots()));
            eq.setItemInMainHand(Kit.sword());
            eq.setItemInOffHand(Kit.totem());
        });
        applySkin();
        resetState();
    }

    public void reset() {
        if (entity == null || !entity.isValid()) {
            spawn();
            return;
        }
        entity.teleport(arena().botSpawn(entity.getWorld()));
        entity.setVelocity(new Vector());
        entity.setFireTicks(0);
        entity.setFallDistance(0);
        entity.setAbsorptionAmount(0);
        entity.clearActivePotionEffects();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        entity.setHealth(max != null ? max.getValue() : 20.0);
        entity.getEquipment().setItemInOffHand(Kit.totem());
        holdMain(Kit.sword());
        applySkin();
        resetState();
    }

    public void applySkin() {
        if (entity == null) return;
        if (s.skin == BotSettings.Skin.MINE) {
            entity.setProfile(ResolvableProfile.resolvableProfile(owner.getPlayerProfile()));
        } else {
            entity.setProfile(Mannequin.defaultProfile());
        }
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
        combo = 0;
        crystalCd = 40;
        anchorCd = 80;
        gappleCd = 0;
        if (pendingCrystal != null) pendingCrystal.remove();
        pendingCrystal = null;
        anchorBlock = null;
        countdown = s.countdown ? 60 : 0;
        roundStart = System.currentTimeMillis() + countdown * 50L;
        if (countdown == 0) {
            owner.showTitle(Title.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(300))));
        }
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
        s.popsForced++;
        owner.sendActionBar(Component.text("Bot popped! (" + pops + ")", NamedTextColor.GOLD));
        owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
    }

    public void onDeath() {
        respawnIn = RESPAWN_DELAY;
        long ms = roundMs();
        s.wins++;
        long[] best = s.bestMs[s.mode.ordinal()];
        int d = s.difficulty.ordinal();
        boolean record = best[d] == 0 || ms < best[d];
        if (record) {
            best[d] = ms;
        }
        plugin.leaderboard().submit(owner.getName(), owner.getUniqueId().toString(), s.mode, s.difficulty, ms);
        plugin.leaderboard().render(plugin.arenaWorld());
        owner.showTitle(Title.title(
                Component.text("You won!", NamedTextColor.GREEN),
                Component.text(BotSettings.formatTime(ms) + (record ? "  NEW BEST!" : "") + "  •  " + pops + " pops",
                        record ? NamedTextColor.GOLD : NamedTextColor.GRAY)));
        Effects.victory(plugin, entity.getLocation(), owner);
    }

    /** Owner died: count the loss. */
    public void onOwnerDeath() {
        s.losses++;
        owner.sendMessage(Component.text("Bot won. It popped " + pops + " totems this round.", NamedTextColor.RED));
    }

    /** You landed a hit on the bot. */
    public void onOwnerHit() {
        if (tick - lastComboTick > 60) combo = 0;
        combo++;
        lastComboTick = tick;
        if (combo > s.maxCombo) s.maxCombo = combo;
        if (combo >= 2) {
            owner.sendActionBar(Component.text("x" + combo + " COMBO", combo >= 5 ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
            owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, Math.min(2f, 0.6f + combo * 0.1f));
        }
    }

    /** The bot hit you: your combo is over. */
    public void onBotHitOwner() {
        if (combo >= 3) {
            owner.sendActionBar(Component.text("Combo broken at x" + combo, NamedTextColor.GRAY));
        }
        combo = 0;
    }

    public int combo() {
        return tick - lastComboTick > 60 ? 0 : combo;
    }

    public int totemsLeft() {
        return Math.max(0, totemsLeft);
    }

    public long roundMs() {
        return Math.max(0, System.currentTimeMillis() - roundStart);
    }

    public Location explosionCenter() {
        return exploding ? explosionCenter : null;
    }

    public boolean isExploding() {
        return exploding;
    }

    private Arena arena() {
        return Arena.of(s.mode);
    }

    public boolean inCountdown() {
        return countdown > 0;
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
        if (!arena().inside(me) || me.getY() > 400) {
            entity.teleport(arena().botSpawn(me.getWorld()));
            entity.setVelocity(new Vector());
            entity.setFallDistance(0);
            return;
        }

        if (countdown > 0) {
            countdown--;
            if (countdown % 20 == 0 || countdown == 59) {
                int n = (countdown + 19) / 20;
                if (countdown == 0) {
                    owner.showTitle(Title.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(300))));
                    owner.playSound(owner.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                } else {
                    owner.showTitle(Title.title(Component.text(String.valueOf(n), NamedTextColor.GOLD), Component.text(s.difficulty.name(), NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                    owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
            }
            entity.lookAt(owner.getEyeLocation().getX(), owner.getEyeLocation().getY(), owner.getEyeLocation().getZ(), LookAnchor.EYES);
            idle();
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
                && arena().inside(owner.getLocation());

        if (!engage) {
            if (state == State.BLOCK) stopBlocking();
            state = State.FIGHT;
            idle();
            return;
        }

        entity.lookAt(owner.getEyeLocation().getX(), owner.getEyeLocation().getY(), owner.getEyeLocation().getZ(), LookAnchor.EYES);
        entity.setBodyYaw(entity.getYaw());

        if (s.mode == BotSettings.Mode.CRYSTAL) {
            crystalFight();
            return;
        }
        switch (state) {
            case FIGHT -> fight();
            case COMBO -> maceCombo();
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

    private void maceCombo() {
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

    // ---- crystal pvp --------------------------------------------------------------------

    private void crystalFight() {
        int d = s.difficulty.ordinal();
        if (crystalCd > 0) crystalCd--;
        if (anchorCd > 0) anchorCd--;
        if (gappleCd > 0) gappleCd--;
        tickPending();

        Location me = entity.getLocation();
        Vector flat = owner.getLocation().toVector().subtract(me.toVector()).setY(0);
        double dist = flat.length();
        Vector fwd = dist > 0.001 ? flat.clone().normalize() : new Vector(0, 0, 1);
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX()).multiply(strafeDir);

        if (gappleCd == 0 && entity.getHealth() < 12) {
            eatGapple();
        }

        if (s.movement) {
            if (--strafeSwitch <= 0) {
                strafeDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                strafeSwitch = 15 + ThreadLocalRandom.current().nextInt(30);
            }
            double speed = s.difficulty.speed;
            Vector desired;
            if (dist > 4.5) {
                desired = fwd.clone().multiply(speed).add(side.clone().multiply(speed * 0.2));
            } else if (dist < 2.3) {
                desired = fwd.clone().multiply(-speed * 0.6).add(side.clone().multiply(speed * 0.5));
            } else {
                desired = side.clone().multiply(speed * 0.7).add(fwd.clone().multiply(speed * 0.1));
            }
            steer(desired, entity.isOnGround() ? 1.0 : 0.08);
            // Hop out of craters / up ledges.
            if (entity.isOnGround() && knockbackTicks == 0 && blockedAhead(desired)) {
                Vector v = entity.getVelocity();
                v.setY(0.42);
                entity.setVelocity(v);
            }
        } else {
            idle();
        }

        if (!s.attacks || pendingCrystal != null || anchorBlock != null) {
            return;
        }
        double reach = reachTo(owner);
        if (reach <= REACH && attackCd == 0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
            boolean falling = !entity.isOnGround() && entity.getVelocity().getY() < -0.05;
            swordHit(falling);
            return;
        }
        if (dist <= 5.5 && crystalCd == 0) {
            if (anchorCd == 0 && ThreadLocalRandom.current().nextDouble() < 0.35 && startAnchor()) {
                return;
            }
            startCrystal(d);
        }
    }

    private boolean blockedAhead(Vector desired) {
        if (desired.lengthSquared() < 1e-4) return false;
        Location ahead = entity.getLocation().add(desired.clone().normalize().multiply(0.7));
        return ahead.getBlock().getType().isSolid();
    }

    /** Highest solid block under the target's feet (within 4 blocks), or null when they're high in the air. */
    private int groundY() {
        Location l = owner.getLocation();
        for (int dy = 0; dy <= 4; dy++) {
            Block b = l.clone().subtract(0, dy + 0.01, 0).getBlock();
            if (b.getType().isSolid()) return b.getY();
        }
        return Integer.MIN_VALUE;
    }

    /** Best spot for a crystal: on the ground right next to you, on the side facing the bot. */
    private Block findCrystalBase() {
        int gy = groundY();
        if (gy == Integer.MIN_VALUE) return null;
        Location ol = owner.getLocation();
        int px = ol.getBlockX(), pz = ol.getBlockZ();
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        Location eye = entity.getEyeLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block base = entity.getWorld().getBlockAt(px + dx, gy, pz + dz);
                if (!arena().buildable(base.getLocation())) continue;
                if (!base.getRelative(0, 1, 0).isPassable() || !base.getRelative(0, 2, 0).isPassable()) continue;
                Material m = base.getType();
                if (m == Material.BARRIER || m == Material.REINFORCED_DEEPSLATE) continue;
                double dd = base.getLocation().add(0.5, 1, 0.5).distanceSquared(eye);
                if (dd < bestDist && dd <= 5.5 * 5.5) {
                    bestDist = dd;
                    best = base;
                }
            }
        }
        return best;
    }

    private void startCrystal(int d) {
        Block base = findCrystalBase();
        if (base == null) {
            crystalCd = 5;
            return;
        }
        World w = entity.getWorld();
        if (base.getType() != Material.OBSIDIAN && base.getType() != Material.BEDROCK) {
            holdMain(new ItemStack(Material.OBSIDIAN));
            base.setType(Material.OBSIDIAN);
            w.playSound(base.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.8f);
        }
        holdMain(new ItemStack(Material.END_CRYSTAL));
        entity.swingMainHand();
        pendingCrystal = w.spawn(base.getLocation().add(0.5, 1, 0.5), EnderCrystal.class, c -> {
            c.setShowingBottom(false);
            c.setPersistent(false);
        });
        crystalExplodeAt = tick + CRYSTAL_DELAY[d];
        crystalCd = CRYSTAL_CD[d] + ThreadLocalRandom.current().nextInt(CRYSTAL_CD[d] / 3 + 1);
    }

    private boolean startAnchor() {
        int gy = groundY();
        if (gy == Integer.MIN_VALUE) return false;
        Location ol = owner.getLocation();
        Location eye = entity.getEyeLocation();
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            Block b = entity.getWorld().getBlockAt(ol.getBlockX() + dir[0], gy + 1, ol.getBlockZ() + dir[1]);
            if (!arena().buildable(b.getLocation()) || !b.getType().isAir()) continue;
            double dd = b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(eye);
            if (dd < bestDist && dd <= 5.5 * 5.5) {
                bestDist = dd;
                best = b;
            }
        }
        if (best == null) return false;
        holdMain(new ItemStack(Material.RESPAWN_ANCHOR));
        entity.swingMainHand();
        best.setType(Material.RESPAWN_ANCHOR);
        entity.getWorld().playSound(best.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.8f);
        anchorBlock = best;
        anchorStage = 0;
        anchorCd = ANCHOR_CD[s.difficulty.ordinal()];
        crystalCd = Math.max(crystalCd, 6);
        return true;
    }

    private void tickPending() {
        if (pendingCrystal != null && tick >= crystalExplodeAt) {
            EnderCrystal c = pendingCrystal;
            pendingCrystal = null;
            if (c.isValid() && ThreadLocalRandom.current().nextDouble() >= s.difficulty.missChance) {
                Location l = c.getLocation();
                c.remove();
                entity.swingMainHand();
                explode(l, 6f, false);
            }
            holdMain(Kit.sword());
        }
        if (anchorBlock != null) {
            anchorStage++;
            if (anchorBlock.getType() != Material.RESPAWN_ANCHOR) {
                anchorBlock = null; // you broke it
                holdMain(Kit.sword());
                return;
            }
            if (anchorStage == 2) {
                holdMain(new ItemStack(Material.GLOWSTONE));
                entity.swingMainHand();
                if (anchorBlock.getBlockData() instanceof RespawnAnchor ra) {
                    ra.setCharges(1);
                    anchorBlock.setBlockData(ra, false);
                }
                entity.getWorld().playSound(anchorBlock.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1f);
            }
            if (anchorStage >= 2 + ANCHOR_FUSE[s.difficulty.ordinal()]) {
                Location l = anchorBlock.getLocation().add(0.5, 0.5, 0.5);
                anchorBlock.setType(Material.AIR);
                anchorBlock = null;
                entity.swingMainHand();
                explode(l, 5f, true);
                holdMain(Kit.sword());
            }
        }
    }

    private void explode(Location l, float power, boolean fire) {
        exploding = true;
        explosionCenter = l;
        try {
            l.getWorld().createExplosion(l, power, fire, true, entity);
        } finally {
            exploding = false;
        }
    }

    private void eatGapple() {
        gappleCd = 300;
        holdMain(new ItemStack(Material.GOLDEN_APPLE));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 0));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (entity != null && entity.isValid() && pendingCrystal == null && anchorBlock == null) holdMain(Kit.sword());
        }, 10L);
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
        double hp = entity.getHealth() + entity.getAbsorptionAmount();
        String totems = s.botInfiniteTotems ? "∞" : String.valueOf(Math.max(0, totemsLeft));
        entity.setDescription(Component.text()
                .append(Component.text(String.format("%.1f❤", hp / 2.0), NamedTextColor.RED))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("⛨ " + totems, NamedTextColor.GOLD))
                .append(Component.text("  " + s.difficulty.name(), NamedTextColor.DARK_GRAY))
                .build());
    }
}
