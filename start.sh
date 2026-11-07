#!/usr/bin/env bash
# Downloads Paper (first run only) and starts the Mace PvP practice server.
#   MC_VERSION=26.2  RAM=2G  ./start.sh
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.2}"
RAM="${RAM:-2G}"
cd "$(dirname "$0")/server"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. On Ubuntu/Debian run:  sudo apt install -y openjdk-25-jre-headless"
  exit 1
fi
JV=$(java -version 2>&1 | grep -oE 'version "[0-9]+' | grep -oE '[0-9]+$')
if [ "${JV:-0}" -lt 25 ]; then
  echo "Minecraft 26.x needs Java 25+ (you have $JV). Run:  sudo apt install -y openjdk-25-jre-headless"
  exit 1
fi

if [ ! -f paper.jar ] || [ "$(cat .paper-version 2>/dev/null)" != "$MC_VERSION" ]; then
  echo "Downloading Paper $MC_VERSION ..."
  URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest" \
        | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$URL" ]; then
    echo "Could not find a Paper build for $MC_VERSION"; exit 1
  fi
  curl -fL -o paper.jar "$URL"
  echo "$MC_VERSION" > .paper-version
fi

if ! grep -qs "eula=true" eula.txt; then
  echo "Minecraft requires you to accept the EULA: https://aka.ms/MinecraftEULA"
  if [ "${EULA:-}" = "true" ]; then
    ans=y
  else
    read -r -p "Do you accept it? [y/N] " ans
  fi
  if [[ "$ans" =~ ^[Yy]$ ]]; then
    echo "eula=true" > eula.txt
  else
    echo "EULA not accepted, exiting."; exit 1
  fi
fi

exec java -Xms"$RAM" -Xmx"$RAM" -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -jar paper.jar --nogui
