package de.labrwn.completiontracker.net;

import de.labrwn.completiontracker.CompletionTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The two network messages: the client asks for its data, the server answers with it. */
public final class Payloads {
	public static final int MAX_SIZE = 8 * 1024 * 1024;

	private Payloads() {
	}

	/** Client → server: "send me my progress". */
	public record Request() implements CustomPacketPayload {
		public static final Request INSTANCE = new Request();
		public static final Type<Request> TYPE = new Type<>(CompletionTracker.id("request"));
		public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server → client: the player's progress as JSON ({@link SyncData}). */
	public record Data(String json) implements CustomPacketPayload {
		public static final Type<Data> TYPE = new Type<>(CompletionTracker.id("data"));
		public static final StreamCodec<FriendlyByteBuf, Data> CODEC = CustomPacketPayload.codec(Data::write, Data::new);

		private Data(FriendlyByteBuf buf) {
			this(buf.readUtf(MAX_SIZE));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(json, MAX_SIZE);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
