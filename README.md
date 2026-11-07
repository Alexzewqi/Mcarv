# Mace PvP Practice Server (Minecraft 26.2, Paper)

A small server with one arena and a practice bot. Every player who joins gets their own bot.

## Run it on a VPS

You need Java 25, because Minecraft 26.x requires it.

```bash
sudo apt update && sudo apt install -y openjdk-25-jre-headless git curl
git clone https://github.com/alexzewqi/mcarv.git
cd mcarv
./start.sh            # first run downloads Paper 26.2 and asks you to accept the Minecraft EULA
```

Options: `RAM=4G ./start.sh` and `EULA=true ./start.sh` (accepts the EULA without asking).
Open port **25565/tcp** in your VPS firewall (`sudo ufw allow 25565/tcp`).
To keep the server running after you log out, start it inside `screen` or `tmux`.

To make yourself op, type `op YourName` in the server console.

## Kit

| Slot | Item |
|---|---|
| 1 | Netherite Sword (Sharpness V, Unbreaking III, Mending) |
| 2 | Wind Charges ×64 |
| 3 | Ender Pearls ×16 |
| 4 | Steak ×64 |
| 5 | Diamond Spear (Lunge III, Sharpness V, Unbreaking III, Mending) |
| 6 | Shield (Unbreaking III, Mending) |
| 7 | Elytra (Unbreaking III, Mending). It takes the Breach IV mace's slot. |
| 8 | Mace (Density V, Wind Burst III, Unbreaking III, Mending) |
| 9 | Totem of Undying |

You also get a totem in your offhand, netherite armor (Protection IV, Unbreaking III, Mending, plus Feather Falling IV on the boots), spare wind charges and pearls, and an inventory full of totems.

## Bot settings menu

To open the menu, sneak and press **F**, run **`/bot`**, or click the **nether star** in your inventory.

- **Bot Movement**: on or off. When it's off the bot stands still, but it still takes knockback, so you can drill combos.
- **Bot Attacks**: on or off. Turn it off to use the bot as a punching bag.
- **Bot Mace Combos**: the bot wind-charges up and smashes you with a density mace, and on harder levels it chains smashes with Wind Burst.
- **Bot Shields Your Dives**: the bot blocks when you dive at it with a mace. When this is off, it sidesteps.
- **Difficulty**: Easy, Normal or Hard. This changes speed, crit rate, combo rate, reactions and how often it misses.
- **Bot Infinite Totems**: when this is off, the bot has 10 totems and then it can die. You get a "You won!" screen and the round resets.
- **My Infinite Totems**: refills a totem in your hand after it pops.
- **Refill My Kit** and **Reset Round**.

Commands: `/bot`, `/kit`, `/resetbot`.

## How the bot plays

The bot looks like a player (it's a Mannequin entity) and wears the same armor. It:
- strafes around you, sprint-hits, and jump-crits with the sword
- uses a wind charge to launch upward, steers above you, and smashes you with the density mace
- raises its shield or sidesteps when you dive at it with a mace, then punishes you after you land
- throws a pearl back at you if you run away
- regenerates like a saturated player and pops totems the normal way

## Build the plugin yourself (optional)

The compiled plugin is already in `server/plugins/MacePractice.jar`. To rebuild it, you need JDK 25:

```bash
cd plugin && ./gradlew build && cp build/libs/MacePractice.jar ../server/plugins/
```
