package de.labrwn.completiontracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.labrwn.completiontracker.CompletionTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLScancode;

/** Client entry point: registers the key (default J) that opens the tracker book. */
public class CompletionTrackerClient implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(CompletionTracker.id("main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.completiontracker.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_J, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				// Key mappings only fire while no other screen is open
				if (client.player != null) {
					client.setScreenAndShow(new TrackerScreen());
				}
			}
		});
	}
}
