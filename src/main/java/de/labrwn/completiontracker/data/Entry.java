package de.labrwn.completiontracker.data;

/**
 * One tracked fact: an item collected, a mob or variant killed, an advancement reached.
 * {@code day} is the Minecraft day, or -1 when it is unknown.
 */
public class Entry {
	public int day = -1;
	/** True when the day was estimated from timestamps (before installation). */
	public boolean estimated;
	/** Where the entry came from, e.g. "inventory", "stat_picked_up", "kill". */
	public String source = "";
	/** Kill count for mobs and variants. */
	public int count;

	public Entry() {
	}

	public Entry(int day, boolean estimated, String source) {
		this.day = day;
		this.estimated = estimated;
		this.source = source;
	}
}
