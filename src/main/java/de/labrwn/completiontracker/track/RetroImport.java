package de.labrwn.completiontracker.track;

import de.labrwn.completiontracker.CompletionTracker;
import de.labrwn.completiontracker.data.Entry;
import de.labrwn.completiontracker.data.PlayerProgress;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs once per player and world: reads what Minecraft already knows (statistics, advancements,
 * inventory, ender chest) so an existing world does not start at zero.
 *
 * Minecraft does not store on which game day something happened. The real-time timestamps in the
 * advancement data are turned into an estimated day by {@link DayEstimator}.
 */
public final class RetroImport {
	private RetroImport() {
	}

	public static void run(MinecraftServer server, ServerPlayer player, PlayerProgress p) {
		long start = System.currentTimeMillis();
		int today = Days.today(server);
		p.installedDay = today;
		ServerStatsCounter stats = player.getStats();

		// Timestamps: when an item was first held (recipe unlocks "has_<item>"), first kills, foods eaten
		Map<String, Instant> firstItem = new HashMap<>();
		Map<String, Instant> firstKill = new HashMap<>();
		List<Instant> allStamps = new ArrayList<>();
		Map<String, Instant> advancementDone = new HashMap<>();
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			String advId = holder.id().getPath();
			Instant latest = null;
			for (String criterion : progress.getCompletedCriteria()) {
				CriterionProgress cp = progress.getCriterion(criterion);
				if (cp == null || cp.getObtained() == null) continue;
				Instant t = cp.getObtained();
				allStamps.add(t);
				if (latest == null || t.isAfter(latest)) latest = t;
				if (criterion.startsWith("has_")) earliest(firstItem, "minecraft:" + criterion.substring(4), t);
				if (advId.equals("husbandry/balanced_diet")) earliest(firstItem, withNamespace(criterion), t);
				if (advId.equals("adventure/kill_all_mobs")) earliest(firstKill, withNamespace(criterion), t);
			}
			if (holder.value().display().isPresent() && progress.isDone() && latest != null) {
				advancementDone.put(holder.id().toString(), latest);
			}
		}

		DayEstimator est = new DayEstimator(allStamps, stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)),
				server.overworld().getOverworldClockTime());

		// Items from statistics
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			String source = null;
			if (stats.getValue(Stats.ITEM_PICKED_UP.get(item)) > 0) source = "stat_picked_up";
			else if (stats.getValue(Stats.ITEM_CRAFTED.get(item)) > 0) source = "stat_crafted";
			else if (stats.getValue(Stats.ITEM_USED.get(item)) > 0) source = "stat_used";
			else if (firstItem.containsKey(id) && isItem(id)) source = "advancement_timestamp";
			if (source != null) p.items.put(id, retro(est, firstItem.get(id), source));
		}
		// Items currently carried or in the ender chest
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) Tracker.note(p, inv.getItem(i), -1, "inventory_at_install");
		var ender = player.getEnderChestInventory();
		for (int i = 0; i < ender.getContainerSize(); i++) Tracker.note(p, ender.getItem(i), -1, "enderchest_at_install");

		// Kills (variants are not stored by Minecraft)
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			int killed = stats.getValue(Stats.ENTITY_KILLED.get(type));
			if (killed > 0) {
				String id = EntityType.getKey(type).toString();
				Entry e = retro(est, firstKill.get(id), "stat_killed");
				e.count = killed;
				p.kills.put(id, e);
			}
		}

		// Advancements already reached
		advancementDone.forEach((id, t) -> p.advancements.put(id, retro(est, t, "advancement_at_install")));

		CompletionTracker.LOGGER.info("Imported existing progress for {}: {} items, {} mobs, {} advancements in {} ms",
				player.getName().getString(), p.items.size(), p.kills.size(), p.advancements.size(),
				System.currentTimeMillis() - start);
	}

	private static Entry retro(DayEstimator est, Instant t, String source) {
		return t == null ? new Entry(-1, false, source) : new Entry(est.dayOf(t), true, source);
	}

	private static void earliest(Map<String, Instant> map, String key, Instant t) {
		map.merge(key, t, (a, b) -> a.isBefore(b) ? a : b);
	}

	private static String withNamespace(String s) {
		return s.contains(":") ? s : "minecraft:" + s;
	}

	private static boolean isItem(String id) {
		Identifier key = Identifier.tryParse(id);
		return key != null && BuiltInRegistries.ITEM.containsKey(key);
	}
}
