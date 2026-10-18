package de.labrwn.completiontracker.net;

import com.google.gson.Gson;
import de.labrwn.completiontracker.data.ProgressStore;
import de.labrwn.completiontracker.track.Days;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Collects a player's progress on the server and sends it to that player's client. */
public final class SyncBuilder {
	private static final Gson GSON = new Gson();

	private SyncBuilder() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Payloads.Request.TYPE, Payloads.Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Payloads.Data.TYPE, Payloads.Data.CODEC, Payloads.MAX_SIZE + 64);
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Request.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = player.level().getServer();
			server.execute(() -> ServerPlayNetworking.send(player, new Payloads.Data(GSON.toJson(build(server, player)))));
		});
	}

	public static SyncData build(MinecraftServer server, ServerPlayer player) {
		SyncData d = new SyncData();
		d.today = Days.today(server);
		d.progress = ProgressStore.get(server, player.getUUID());
		for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
			if (h.value().display().isEmpty() || h.id().getPath().startsWith("recipes/")) continue;
			DisplayInfo info = h.value().display().get();
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(h);
			SyncData.Adv a = new SyncData.Adv();
			a.id = h.id().toString();
			a.titleKey = key(info.title());
			a.titleText = a.titleKey == null ? info.title().getString() : null;
			a.descKey = key(info.description());
			a.descText = a.descKey == null ? info.description().getString() : null;
			a.icon = BuiltInRegistries.ITEM.getKey(info.icon().create().getItem()).toString();
			a.type = info.type().getSerializedName();
			a.done = progress.isDone();
			List<List<String>> reqs = h.value().requirements().requirements();
			if (reqs.size() > 1) {
				for (List<String> group : reqs) {
					SyncData.Req r = new SyncData.Req();
					r.name = group.get(0);
					r.done = group.stream().anyMatch(c -> progress.getCriterion(c) != null && progress.getCriterion(c).isDone());
					a.reqs.add(r);
				}
			}
			d.advancements.add(a);
		}
		return d;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
	}
}
