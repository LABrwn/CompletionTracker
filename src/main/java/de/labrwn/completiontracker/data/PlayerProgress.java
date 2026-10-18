package de.labrwn.completiontracker.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Everything the tracker knows about one player in one world. */
public class PlayerProgress {
	public int formatVersion = 1;
	/** Minecraft day on which the mod first saw this player. */
	public int installedDay = -1;
	/** Item id -> entry, e.g. "minecraft:diamond". */
	public final Map<String, Entry> items = new ConcurrentHashMap<>();
	/** Entity id -> entry, e.g. "minecraft:zombie". */
	public final Map<String, Entry> kills = new ConcurrentHashMap<>();
	/** "entity/variant" -> entry, e.g. "minecraft:sheep/red". Only known from installation on. */
	public final Map<String, Entry> variants = new ConcurrentHashMap<>();
	/** Advancement id -> entry. */
	public final Map<String, Entry> advancements = new ConcurrentHashMap<>();

	public transient boolean dirty;
}
