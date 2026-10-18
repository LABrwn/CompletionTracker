package de.labrwn.completiontracker.model;

import de.labrwn.completiontracker.catalog.Catalog;
import de.labrwn.completiontracker.data.Entry;
import de.labrwn.completiontracker.data.PlayerProgress;
import de.labrwn.completiontracker.model.TrackerModel.ChronEntry;
import de.labrwn.completiontracker.model.TrackerModel.Day;
import de.labrwn.completiontracker.model.TrackerModel.Kind;
import de.labrwn.completiontracker.model.TrackerModel.Row;
import de.labrwn.completiontracker.model.TrackerModel.State;
import de.labrwn.completiontracker.model.TrackerModel.Sub;
import de.labrwn.completiontracker.model.TrackerModel.Totals;
import de.labrwn.completiontracker.net.SyncData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Turns the catalog and the data sent by the server into a {@link TrackerModel}. Runs on the client. */
public final class ModelBuilder {
	private ModelBuilder() {
	}

	public static TrackerModel build(SyncData data) {
		TrackerModel m = new TrackerModel();
		m.today = data.today;
		PlayerProgress p = data.progress;
		Catalog cat = Catalog.get();
		Map<Integer, Day> days = new TreeMap<>();

		// ---- mobs
		int order = 0;
		for (Catalog.MobDef def : cat.mobs) {
			Identifier id = Identifier.tryParse(def.id);
			if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) continue;
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Row r = new Row();
			r.kind = Kind.MOB;
			r.key = "m:" + def.id;
			r.name = type.getDescription();
			r.icon = spawnEgg(id);
			r.cat = def.cat;
			r.diff = def.diff;
			r.order = order++;
			Entry kill = p.kills.get(def.id);
			r.done = kill != null;
			boolean killedBeforeInstall = kill != null && kill.source.startsWith("stat");
			int variantsDone = 0;
			String path = id.getPath();
			for (String v : def.variants) {
				Sub s = new Sub();
				s.name = Component.translatable("completiontracker.variant." + path + "." + v);
				Entry ve = p.variants.get(def.id + "/" + v);
				s.state = ve != null ? State.DONE : killedBeforeInstall ? State.UNKNOWN : State.OPEN;
				String hk = "completiontracker.variant_hint." + path + "." + v;
				s.hint = Language.getInstance().has(hk) ? Component.translatable(hk) : null;
				if (ve != null) {
					variantsDone++;
					addChron(days, ve, Kind.MOB, Component.empty().append(type.getDescription()).append(": ").append(s.name), r.icon, false);
				}
				r.subs.add(s);
			}
			r.complete = r.done && variantsDone == def.variants.size();
			r.unknownVariants = killedBeforeInstall && !r.complete && !def.variants.isEmpty();
			r.state = r.complete ? State.DONE : r.unknownVariants ? State.UNKNOWN : State.OPEN;
			if (!r.done) r.meta = Component.translatable("completiontracker.status.never_killed");
			else if (!def.variants.isEmpty()) r.meta = Component.translatable("completiontracker.meta.kills_variants", kill.count, variantsDone, def.variants.size());
			else r.meta = Component.translatable("completiontracker.meta.kills", kill.count);
			r.status = Component.translatable(!r.done ? "completiontracker.status.never_killed"
					: r.complete ? "completiontracker.status.complete" : "completiontracker.status.variants_missing");
			r.hint = def.hint == null ? null : Component.translatable("completiontracker.hint." + def.hint);
			if (kill != null) {
				r.facts.add(new Component[]{Component.translatable("completiontracker.fact.kills"), Component.literal(String.valueOf(kill.count))});
				r.facts.add(new Component[]{Component.translatable("completiontracker.fact.reached"), dayText(kill)});
				addChron(days, kill, Kind.MOB, type.getDescription(), r.icon, false);
			}
			count(m.mobTotals, r, 1 + def.variants.size(), (r.done ? 1 : 0) + variantsDone);
			m.mobs.add(r);
		}

		// ---- items
		order = 0;
		for (Catalog.ItemDef def : cat.items) {
			Identifier id = Identifier.tryParse(def.id);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
			Item item = BuiltInRegistries.ITEM.getValue(id);
			ItemStack stack = item.getDefaultInstance();
			Row r = new Row();
			r.kind = Kind.ITEM;
			r.key = "i:" + def.id;
			r.name = stack.getHoverName();
			r.icon = stack;
			r.cat = def.cat;
			r.diff = def.diff;
			r.order = order++;
			Entry e = p.items.get(def.id);
			r.done = r.complete = e != null;
			r.state = r.done ? State.DONE : State.OPEN;
			r.meta = Component.translatable("completiontracker.cat." + def.cat);
			r.status = Component.translatable(r.done ? "completiontracker.status.collected" : "completiontracker.status.missing");
			r.hint = Component.translatable("completiontracker.hint." + def.hint);
			if (e != null) {
				r.facts.add(new Component[]{Component.translatable("completiontracker.fact.found_via"), Component.translatable("completiontracker.source." + e.source)});
				r.facts.add(new Component[]{Component.translatable("completiontracker.fact.reached"), dayText(e)});
				addChron(days, e, Kind.ITEM, r.name, stack, false);
			}
			count(m.itemTotals, r, 1, r.done ? 1 : 0);
			m.items.add(r);
		}

		// ---- advancements
		List<SyncData.Adv> advs = new ArrayList<>(data.advancements);
		advs.sort((x, y) -> x.id.compareTo(y.id));
		order = 0;
		for (SyncData.Adv a : advs) {
			Identifier id = Identifier.tryParse(a.id);
			if (id == null) continue;
			String tab = id.getPath().split("/")[0];
			Row r = new Row();
			r.kind = Kind.ADVANCEMENT;
			r.key = "a:" + a.id;
			r.name = a.titleKey != null ? Component.translatable(a.titleKey) : Component.literal(String.valueOf(a.titleText));
			Identifier icon = Identifier.tryParse(a.icon);
			r.icon = icon != null && BuiltInRegistries.ITEM.containsKey(icon) ? BuiltInRegistries.ITEM.getValue(icon).getDefaultInstance() : new ItemStack(Items.BOOK);
			r.cat = tab;
			r.frame = a.type == null ? "task" : a.type;
			r.diff = cat.advancementDifficulty.getOrDefault(a.id, r.frame.equals("task") ? 1 : r.frame.equals("goal") ? 2 : 3);
			r.order = order++;
			r.done = r.complete = a.done;
			r.state = r.done ? State.DONE : State.OPEN;
			r.hint = a.descKey != null ? Component.translatable(a.descKey) : Component.literal(String.valueOf(a.descText));
			if (a.reqs.size() > 1) {
				int doneCount = 0;
				for (SyncData.Req req : a.reqs) {
					Sub s = new Sub();
					s.name = criterionName(id.getPath(), req.name);
					s.state = req.done ? State.DONE : State.OPEN;
					if (req.done) doneCount++;
					r.subs.add(s);
				}
				r.meta = Component.translatable("completiontracker.meta.done_of", doneCount, a.reqs.size());
			} else {
				r.meta = Component.translatable("completiontracker.cat." + tab);
			}
			r.status = Component.translatable(r.done ? "completiontracker.status.done" : "completiontracker.status.open");
			Entry e = p.advancements.get(a.id);
			if (e != null) {
				r.facts.add(new Component[]{Component.translatable("completiontracker.fact.reached"), dayText(e)});
				addChron(days, e, Kind.ADVANCEMENT, r.name, r.icon, true);
			}
			count(m.advTotals, r, 1, r.done ? 1 : 0);
			m.advancements.add(r);
		}

		// ---- chronicle
		for (Day d : days.values()) {
			d.entries.sort((a, b) -> Integer.compare(rank(a.kind), rank(b.kind)));
			m.chronicle.add(d);
		}
		return m;
	}

	private static int rank(Kind k) {
		return k == Kind.ADVANCEMENT ? 0 : k == Kind.MOB ? 1 : 2;
	}

	private static void count(Totals t, Row r, int units, int doneUnits) {
		t.total += units;
		t.done += doneUnits;
		t.diffTotal[r.diff]++;
		if (r.complete) t.diffDone[r.diff]++;
	}

	private static void addChron(Map<Integer, Day> days, Entry e, Kind kind, Component name, ItemStack icon, boolean goal) {
		Day d = days.computeIfAbsent(e.day, k -> {
			Day nd = new Day();
			nd.day = k;
			nd.label = k < 0 ? Component.translatable("completiontracker.day.unknown") : Component.translatable("completiontracker.day", k);
			nd.sub = Component.empty();
			return nd;
		});
		if (e.day < 0) d.sub = Component.translatable("completiontracker.day.before_install");
		else if (e.estimated) d.sub = Component.translatable("completiontracker.day.estimated_short");
		ChronEntry c = new ChronEntry();
		c.kind = kind;
		c.name = name;
		c.icon = icon;
		c.goal = goal;
		d.entries.add(c);
	}

	private static Component dayText(Entry e) {
		if (e.day < 0) return Component.translatable("completiontracker.day.before_install");
		return Component.translatable(e.estimated ? "completiontracker.day.estimated" : "completiontracker.day", e.day);
	}

	private static ItemStack spawnEgg(Identifier mob) {
		Identifier egg = Identifier.fromNamespaceAndPath(mob.getNamespace(), mob.getPath() + "_spawn_egg");
		return BuiltInRegistries.ITEM.containsKey(egg) ? BuiltInRegistries.ITEM.getValue(egg).getDefaultInstance() : new ItemStack(Items.BARRIER);
	}

	/** A readable name for one condition of a multi-part advancement (biome, mob, food, cat ...). */
	private static Component criterionName(String advancement, String criterion) {
		String c = criterion.contains(":") ? criterion.substring(criterion.indexOf(':') + 1) : criterion;
		Language lang = Language.getInstance();
		for (String prefix : new String[]{"biome.minecraft.", "entity.minecraft.", "item.minecraft.", "block.minecraft."}) {
			if (lang.has(prefix + c)) return Component.translatable(prefix + c);
		}
		String mob = switch (advancement) {
			case "husbandry/complete_catalogue" -> "cat";
			case "husbandry/whole_pack" -> "wolf";
			case "husbandry/leash_all_frog_variants" -> "frog";
			default -> null;
		};
		if (mob != null && lang.has("completiontracker.variant." + mob + "." + c)) {
			return Component.translatable("completiontracker.variant." + mob + "." + c);
		}
		String pretty = c.replace('_', ' ');
		return Component.literal(pretty.isEmpty() ? c : pretty.substring(0, 1).toUpperCase(Locale.ROOT) + pretty.substring(1));
	}
}
