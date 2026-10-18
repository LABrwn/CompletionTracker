package de.labrwn.completiontracker.client;

import com.google.gson.Gson;
import de.labrwn.completiontracker.CompletionTracker;
import de.labrwn.completiontracker.net.Payloads;
import de.labrwn.completiontracker.net.SyncData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Holds the latest progress the server sent to this client. */
public final class TrackerClientData {
	private static final Gson GSON = new Gson();
	private static volatile SyncData latest;
	private static volatile int version;

	private TrackerClientData() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Data.TYPE, (payload, context) -> {
			try {
				latest = GSON.fromJson(payload.json(), SyncData.class);
				version++;
			} catch (Exception e) {
				CompletionTracker.LOGGER.error("Could not read tracker data from the server", e);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latest = null);
	}

	public static SyncData latest() {
		return latest;
	}

	public static int version() {
		return version;
	}
}
