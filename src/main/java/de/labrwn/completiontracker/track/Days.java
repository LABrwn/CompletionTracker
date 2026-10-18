package de.labrwn.completiontracker.track;

import net.minecraft.server.MinecraftServer;

/** The Minecraft day counter, as shown by the game (overworld clock / 24000). */
public final class Days {
	public static final int TICKS_PER_DAY = 24000;

	private Days() {
	}

	public static int today(MinecraftServer server) {
		return (int) (server.overworld().getOverworldClockTime() / TICKS_PER_DAY);
	}
}
