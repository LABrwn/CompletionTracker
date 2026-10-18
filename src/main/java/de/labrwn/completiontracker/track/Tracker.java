package de.labrwn.completiontracker.track;

import de.labrwn.completiontracker.data.Entry;
import de.labrwn.completiontracker.data.PlayerProgress;
import de.labrwn.completiontracker.data.ProgressStore;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;


/** Watches the game and records new items, kills, variants and advancements. */
public final class Tracker {
	private static int tickCounter;

	private Tracker() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ProgressStore.unload(server, handler.getPlayer().getUUID()));
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> ProgressStore.saveAll(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(ProgressStore::saveAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ProgressStore.clear());

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player) {
				MinecraftServer server = player.level().getServer();
				PlayerProgress p = ProgressStore.get(server, player.getUUID());
				int day = Days.today(server);
				String type = EntityType.getKey(entity.getType()).toString();
				Entry kill = p.kills.computeIfAbsent(type, k -> new Entry(day, false, "kill"));
				kill.count++;
				String variant = Variants.of(entity);
				if (variant != null) {
					Entry v = p.variants.computeIfAbsent(type + "/" + variant, k -> new Entry(day, false, "kill"));
					v.count++;
				}
				p.dirty = true;
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter % 20 != 0) return; // once per second
			boolean checkAdvancements = tickCounter % 100 == 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerProgress p = ProgressStore.get(server, player.getUUID());
				int day = Days.today(server);
				scanPlayer(player, p, day);
				if (checkAdvancements) scanAdvancements(server, player, p, day);
			}
		});
	}

	private static void onJoin(MinecraftServer server, ServerPlayer player) {
		boolean firstTime = !ProgressStore.exists(server, player.getUUID());
		PlayerProgress p = ProgressStore.get(server, player.getUUID());
		if (firstTime) {
			RetroImport.run(server, player, p);
			p.dirty = true;
			ProgressStore.save(server, player.getUUID());
		}
	}

	/** Inventory, ender chest, and the container the player has open. */
	private static void scanPlayer(ServerPlayer player, PlayerProgress p, int day) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) note(p, inv.getItem(i), day, "inventory");
		ItemStack carried = player.containerMenu.getCarried();
		note(p, carried, day, "inventory");
		if (player.containerMenu != player.inventoryMenu) {
			for (Slot slot : player.containerMenu.slots) {
				if (slot.container != inv) note(p, slot.getItem(), day, "container");
			}
		}
	}

	public static void note(PlayerProgress p, ItemStack stack, int day, String source) {
		if (stack == null || stack.isEmpty()) return;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (!p.items.containsKey(id)) {
			p.items.put(id, new Entry(day, false, source));
			p.dirty = true;
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container != null) container.nonEmptyItemCopyStream().forEach(s -> note(p, s, day, source));
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) bundle.itemCopies().forEach(s -> note(p, s, day, source));
	}

	private static void scanAdvancements(MinecraftServer server, ServerPlayer player, PlayerProgress p, int day) {
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			if (holder.value().display().isEmpty()) continue; // recipes and hidden helpers
			String id = holder.id().toString();
			if (p.advancements.containsKey(id)) continue;
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			if (progress.isDone()) {
				p.advancements.put(id, new Entry(day, false, "advancement"));
				p.dirty = true;
			}
		}
	}
}
