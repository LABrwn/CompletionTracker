#!/usr/bin/env bash
# Writes the public signatures of selected Minecraft classes to api-dump/.
# Used while developing: it lets us check method names of the current game version.
set -u
out=api-dump; rm -rf "$out"; mkdir -p "$out"
# Loom splits the game into a common and a client-only jar; use both on the classpath
jars=$(find .gradle/loom-cache "$HOME/.gradle/caches/fabric-loom" -name 'minecraft-*26.3*.jar' 2>/dev/null | grep -vE 'sources|backup' | sort -u)
if [ -z "$jars" ]; then echo "no minecraft jar found" > "$out/ERROR.txt"; exit 0; fi
jar=$(echo "$jars" | paste -sd: -)
echo "$jars" > "$out/JAR.txt"
for j in $jars; do unzip -l "$j" | awk '{print $4}' | grep '\.class$' | sed 's/\.class$//'; done | sort -u > "$out/all-classes.txt"
pkgs=(
  net/minecraft/client/gui/GuiGraphicsExtractor
  net/minecraft/client/gui/Font
  net/minecraft/client/gui/screens/Screen
  net/minecraft/client/gui/components/
  net/minecraft/client/gui/navigation/
  net/minecraft/client/input/
  net/minecraft/client/renderer/RenderPipelines
  net/minecraft/client/multiplayer/ClientAdvancements
  net/minecraft/client/Minecraft
  net/minecraft/client/gui/Gui
  net/minecraft/world/entity/EntityType
  net/minecraft/world/item/Item
  net/minecraft/core/registries/BuiltInRegistries
  net/minecraft/stats/
  net/minecraft/advancements/
  net/minecraft/server/PlayerAdvancements
  net/minecraft/server/level/ServerPlayer
  net/minecraft/world/entity/animal/
  net/minecraft/world/entity/npc/
  net/minecraft/world/entity/monster/
  net/minecraft/world/entity/variant/
  net/minecraft/world/entity/player/Inventory
  net/minecraft/world/entity/LivingEntity
  net/minecraft/world/level/storage/LevelResource
  net/minecraft/world/item/ItemStack
  net/minecraft/core/component/DataComponents
  net/minecraft/world/level/storage/
  net/minecraft/server/level/ServerLevel
  net/minecraft/server/MinecraftServer
  net/minecraft/world/level/Level
  net/minecraft/world/clock/
  net/minecraft/world/entity/Entity
  net/minecraft/world/inventory/AbstractContainerMenu
  net/minecraft/world/inventory/ChestMenu
  net/minecraft/world/item/component/
  net/minecraft/network/chat/Component
  net/minecraft/world/damagesource/DamageSource
  net/minecraft/server/ServerAdvancementManager
  net/minecraft/world/entity/player/Player
  net/minecraft/world/inventory/Slot
  net/minecraft/core/Registry
  net/minecraft/core/DefaultedRegistry
)
for p in "${pkgs[@]}"; do
  name=$(echo "$p" | tr '/' '.' | sed 's/\.$//')
  grep "^$p" "$out/all-classes.txt" | grep -v '\$[0-9]' > /tmp/cls.txt || true
  [ -s /tmp/cls.txt ] || continue
  xargs -a /tmp/cls.txt javap -public -cp "$jar" > "$out/$name.txt" 2>&1 || true
done
ls -la "$out"
