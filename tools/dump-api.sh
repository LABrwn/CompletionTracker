#!/usr/bin/env bash
# Writes the public signatures of selected Minecraft classes to api-dump/.
# Used while developing: it lets us check method names of the current game version.
set -u
out=api-dump; rm -rf "$out"; mkdir -p "$out"
jar=""
for j in $(find "$HOME/.gradle" .gradle build -name '*.jar' 2>/dev/null | grep -i minecraft | grep -vi sources); do
  if unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/client/Minecraft.class'; then jar="$j"; break; fi
done
if [ -z "$jar" ]; then echo "no minecraft jar found" > "$out/ERROR.txt"; exit 0; fi
echo "$jar" > "$out/JAR.txt"
unzip -l "$jar" | awk '{print $4}' | grep '\.class$' | sed 's/\.class$//' > "$out/all-classes.txt"
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
)
for p in "${pkgs[@]}"; do
  name=$(echo "$p" | tr '/' '.' | sed 's/\.$//')
  grep "^$p" "$out/all-classes.txt" | grep -v '\$[0-9]' > /tmp/cls.txt || true
  [ -s /tmp/cls.txt ] || continue
  xargs -a /tmp/cls.txt javap -public -cp "$jar" > "$out/$name.txt" 2>&1 || true
done
ls -la "$out"
