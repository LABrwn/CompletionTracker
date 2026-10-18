package de.labrwn.completiontracker.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.labrwn.completiontracker.CompletionTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and saves {@link PlayerProgress} as JSON in "<world>/completiontracker/<uuid>.json".
 * The game's own save files are never written to.
 */
public final class ProgressStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, PlayerProgress> CACHE = new ConcurrentHashMap<>();

	private ProgressStore() {
	}

	private static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(CompletionTracker.MOD_ID);
	}

	public static boolean exists(MinecraftServer server, UUID id) {
		return CACHE.containsKey(id) || Files.exists(dir(server).resolve(id + ".json"));
	}

	public static PlayerProgress get(MinecraftServer server, UUID id) {
		return CACHE.computeIfAbsent(id, uuid -> load(server, uuid));
	}

	/** The cached progress, or null if the player is not loaded. Safe to call from the client thread. */
	public static PlayerProgress peek(UUID id) {
		return CACHE.get(id);
	}

	private static PlayerProgress load(MinecraftServer server, UUID id) {
		Path file = dir(server).resolve(id + ".json");
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				PlayerProgress p = GSON.fromJson(r, PlayerProgress.class);
				if (p != null) return p;
			} catch (Exception e) {
				CompletionTracker.LOGGER.error("Could not read {}, starting fresh", file, e);
			}
		}
		return new PlayerProgress();
	}

	public static void save(MinecraftServer server, UUID id) {
		PlayerProgress p = CACHE.get(id);
		if (p == null || !p.dirty) return;
		Path folder = dir(server);
		Path file = folder.resolve(id + ".json");
		Path tmp = folder.resolve(id + ".json.tmp");
		try {
			Files.createDirectories(folder);
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(p, w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			p.dirty = false;
		} catch (IOException e) {
			CompletionTracker.LOGGER.error("Could not save {}", file, e);
		}
	}

	public static void saveAll(MinecraftServer server) {
		for (UUID id : CACHE.keySet()) save(server, id);
	}

	public static void unload(MinecraftServer server, UUID id) {
		save(server, id);
		CACHE.remove(id);
	}

	public static void clear() {
		CACHE.clear();
	}
}
