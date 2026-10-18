package de.labrwn.completiontracker.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A ready-to-draw snapshot of the player's progress. Built on the server thread, read by the screen. */
public class TrackerModel {
	public enum Kind { MOB, ITEM, ADVANCEMENT }

	public enum State { DONE, OPEN, UNKNOWN }

	public static class Sub {
		public Component name;
		public State state;
		public Component hint;
	}

	public static class Row {
		public Kind kind;
		public String key;
		public Component name;
		public String searchName;
		public ItemStack icon;
		public String cat;
		public int diff;
		/** The entry itself is reached (mob killed, item collected, advancement done). */
		public boolean done;
		/** Done including all variants or conditions. */
		public boolean complete;
		public State state;
		public Component meta;
		public Component status;
		public Component hint;
		public boolean unknownVariants;
		public final List<Sub> subs = new ArrayList<>();
		public final List<Component[]> facts = new ArrayList<>();
		/** Advancement frame: task, goal or challenge. */
		public String frame = "task";
		public int order;
	}

	public static class ChronEntry {
		public Kind kind;
		public Component name;
		public ItemStack icon;
		public boolean goal;
	}

	public static class Day {
		public int day;
		public Component label;
		public Component sub;
		public final List<ChronEntry> entries = new ArrayList<>();
	}

	public static class Totals {
		public int total;
		public int done;
		public final int[] diffTotal = new int[5];
		public final int[] diffDone = new int[5];

		public int percent() {
			return total == 0 ? 0 : done * 100 / total;
		}
	}

	public final List<Row> mobs = new ArrayList<>();
	public final List<Row> items = new ArrayList<>();
	public final List<Row> advancements = new ArrayList<>();
	public final List<Day> chronicle = new ArrayList<>();
	public final Totals mobTotals = new Totals();
	public final Totals itemTotals = new Totals();
	public final Totals advTotals = new Totals();
	public int today;

	public Totals all() {
		Totals t = new Totals();
		t.total = mobTotals.total + itemTotals.total + advTotals.total;
		t.done = mobTotals.done + itemTotals.done + advTotals.done;
		return t;
	}
}
