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

## The arena

The plugin builds the arena itself, so there's no map to download. It's a **flat 61×61 floor** floating in the void. It has walls with invisible barriers that go up to build height, so wind charges and elytra can't take you out. You spawn on the north marker and the bot spawns on the south one.
There are **7 themes**, all flat with normal-grip blocks: Deepslate, Nether, End, Snow, Desert, Copper and Grass. Change the theme from the menu.

## Settings menu

To open the menu, sneak and press **F**, run **`/bot`**, or click the **nether star** in your inventory.

**Bot:** Movement · Attacks · Mace Combos · Shields Your Dives · Difficulty (Easy / Normal / Hard / **Insane**) · Infinite Totems · Skin (default or *your own skin*)

**You:** Infinite Totems · Infinite Food · Damage Numbers · Sidebar · Round Countdown · Arena Theme · Stats

**Actions:** Save Kit Layout · Reset Kit Layout · Refill Kit · Reset Round

## Features

- **Kit layout saving:** move your items where you like them, then use `/savekit` or the menu button. You get that layout every round.
- **Sidebar:** bot HP, bot totems, pops this round, round timer, your combo, wins/losses and your best time.
- **Floating damage numbers** on every hit. Big hits, like mace smashes, show in gold.
- **Combo counter:** an `x5 COMBO` popup with a rising sound. Your highest combo is saved.
- **3, 2, 1, FIGHT!** countdown at the start of each round.
- **Leaderboard:** a floating board over the south wall shows the fastest bot kills on Normal, Hard and Insane. Turn off *Bot Infinite Totems* so the bot can die, and race the clock.
- **Win celebration:** lightning, totem particles and fireworks. The fireworks are harmless.
- The bot's **HP and totems show under its name**.
- **Stats are saved** across restarts in `server/plugins/MacePractice/data.yml`. Use `/stats` to see them.

Commands: `/bot`, `/kit`, `/resetbot`, `/savekit`, `/resetkit`, `/stats`.

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
