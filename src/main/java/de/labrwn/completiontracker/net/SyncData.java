package de.labrwn.completiontracker.net;

import de.labrwn.completiontracker.data.PlayerProgress;

import java.util.ArrayList;
import java.util.List;

/** Everything the client needs to draw the tracker, sent from the server as JSON. */
public class SyncData {
	public int today;
	public PlayerProgress progress;
	public List<Adv> advancements = new ArrayList<>();

	public static class Adv {
		public String id;
		/** Translation key, or null when {@link #titleText} is used. */
		public String titleKey;
		public String titleText;
		public String descKey;
		public String descText;
		public String icon;
		public String type;
		public boolean done;
		public List<Req> reqs = new ArrayList<>();
	}

	public static class Req {
		public String name;
		public boolean done;
	}
}
