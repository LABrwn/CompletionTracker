package de.labrwn.completiontracker;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared entry point. Tracking logic that runs on the (integrated) server lives here. */
public class CompletionTracker implements ModInitializer {
	public static final String MOD_ID = "completiontracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Completion Tracker loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
