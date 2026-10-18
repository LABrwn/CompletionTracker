package de.labrwn.completiontracker.track;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estimates the Minecraft day for a real-world timestamp from before the mod was installed.
 *
 * Timestamps are grouped into play sessions (gaps over 20 minutes start a new session). The total
 * session time is stretched to the real play time from the statistics, and play time is converted
 * to world days with the ratio of world clock to play time (sleeping skips nights).
 */
public final class DayEstimator {
	private static final long GAP = 20 * 60;
	private static final long PAD = 5 * 60;

	private final List<long[]> sessions = new ArrayList<>();
	private final double scale;
	private final double ratio;

	public DayEstimator(List<Instant> stamps, int playTicks, long worldTicks) {
		List<Long> secs = new ArrayList<>();
		for (Instant i : stamps) secs.add(i.getEpochSecond());
		secs.add(Instant.now().getEpochSecond());
		Collections.sort(secs);
		for (long s : secs) {
			if (sessions.isEmpty() || s - sessions.get(sessions.size() - 1)[1] > GAP) sessions.add(new long[]{s, s});
			else sessions.get(sessions.size() - 1)[1] = s;
		}
		double total = 0;
		for (long[] se : sessions) total += (se[1] - se[0]) + 2 * PAD;
		scale = total > 0 ? (playTicks / 20.0) / total : 1;
		ratio = playTicks > 0 ? (double) worldTicks / playTicks : 1;
	}

	public int dayOf(Instant t) {
		long s = t.getEpochSecond();
		double acc = 0;
		for (long[] se : sessions) {
			double dur = (se[1] - se[0]) + 2 * PAD;
			if (s <= se[1] + PAD) {
				acc += Math.max(0, s - (se[0] - PAD));
				break;
			}
			acc += dur;
		}
		return (int) (acc * scale * 20 * ratio / Days.TICKS_PER_DAY);
	}
}
