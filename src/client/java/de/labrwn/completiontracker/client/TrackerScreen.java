package de.labrwn.completiontracker.client;

import de.labrwn.completiontracker.data.Entry;
import de.labrwn.completiontracker.data.PlayerProgress;
import de.labrwn.completiontracker.data.ProgressStore;
import de.labrwn.completiontracker.track.Days;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Stage 2: a plain summary of what the tracker recorded, so the import can be checked in game.
 * The real menu (tabs, lists, details, chronicle) follows in stage 3.
 */
public class TrackerScreen extends Screen {
	private static final int EDGE = 0xFF1E0C06;
	private static final int LEATHER = 0xFF7A2E1C;
	private static final int LEATHER_HI = 0xFFA8492F;
	private static final int PAGE = 0xFFFBF5E3;
	private static final int PAGE_LINE = 0xFFDCCB9F;
	private static final int SPINE = 0xFFC7B182;
	private static final int INK = 0xFF3B2F1F;
	private static final int INK_SOFT = 0xFF6F5D40;

	private int bookX, bookY, bookW, bookH;
	private final List<Component> summary = new ArrayList<>();
	private final List<Component[]> recent = new ArrayList<>();

	public TrackerScreen() {
		super(Component.translatable("screen.completiontracker.title"));
	}

	@Override
	protected void init() {
		bookW = Math.min(width - 40, 440);
		bookH = Math.min(height - 70, 260);
		bookX = (width - bookW) / 2;
		bookY = (height - bookH) / 2 - 12;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, bookY + bookH + 14, 200, 20)
				.build());
		collect();
	}

	private void collect() {
		summary.clear();
		recent.clear();
		IntegratedServer server = minecraft.getSingleplayerServer();
		PlayerProgress p = server == null || minecraft.player == null ? null : ProgressStore.peek(minecraft.player.getUUID());
		if (p == null) {
			summary.add(Component.translatable("completiontracker.debug.no_data"));
			return;
		}
		int variantCount = p.variants.size();
		summary.add(Component.translatable("completiontracker.debug.today", Days.today(server)));
		summary.add(Component.translatable("completiontracker.debug.installed", p.installedDay));
		summary.add(Component.translatable("completiontracker.debug.mobs", p.kills.size()));
		summary.add(Component.translatable("completiontracker.debug.variants", variantCount));
		summary.add(Component.translatable("completiontracker.debug.items", p.items.size()));
		summary.add(Component.translatable("completiontracker.debug.advancements", p.advancements.size()));

		record Row(int day, boolean est, Component name, Component kind) {}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, Entry> e : p.items.entrySet()) {
			var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
			rows.add(new Row(e.getValue().day, e.getValue().estimated, item.getDefaultInstance().getHoverName(),
					Component.translatable("completiontracker.kind.item")));
		}
		for (Map.Entry<String, Entry> e : p.kills.entrySet()) {
			var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(e.getKey()));
			rows.add(new Row(e.getValue().day, e.getValue().estimated, type.getDescription(),
					Component.translatable("completiontracker.kind.mob")));
		}
		for (Map.Entry<String, Entry> e : p.variants.entrySet()) {
			String[] parts = e.getKey().split("/", 2);
			var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(parts[0]));
			rows.add(new Row(e.getValue().day, false, Component.empty().append(type.getDescription()).append(": " + parts[1]),
					Component.translatable("completiontracker.kind.variant")));
		}
		for (Map.Entry<String, Entry> e : p.advancements.entrySet()) {
			AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(e.getKey()));
			Component title = holder == null ? Component.literal(e.getKey())
					: holder.value().display().map(d -> d.title()).orElse(Component.literal(e.getKey()));
			rows.add(new Row(e.getValue().day, e.getValue().estimated, title,
					Component.translatable("completiontracker.kind.advancement")));
		}
		rows.sort(Comparator.comparingInt(Row::day).reversed());
		for (Row r : rows.subList(0, Math.min(14, rows.size()))) {
			Component day = r.day < 0 ? Component.translatable("completiontracker.day.unknown")
					: Component.translatable(r.est ? "completiontracker.day.estimated" : "completiontracker.day", r.day);
			recent.add(new Component[]{r.name, Component.empty().append(r.kind).append(" · ").append(day)});
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = bookX, y = bookY, w = bookW, h = bookH, mid = x + w / 2;
		g.fill(x - 8, y - 8, x + w + 8, y + h + 8, EDGE);
		g.fill(x - 7, y - 7, x + w + 7, y + h + 7, LEATHER);
		g.fill(x - 7, y - 7, x + w + 7, y - 5, LEATHER_HI);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
		g.fill(x, y, x + w, y + h, PAGE);
		g.fill(x + 2, y + 2, x + w - 2, y + 3, PAGE_LINE);
		g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, PAGE_LINE);
		g.fill(mid - 3, y, mid + 3, y + h, SPINE);

		int left = x + 12, right = mid + 10;
		g.text(font, title, left, y + 10, INK, false);
		int ty = y + 28;
		for (Component line : summary) {
			g.text(font, line, left, ty, INK, false);
			ty += 14;
		}

		g.text(font, Component.translatable("completiontracker.debug.recent"), right, y + 10, INK, false);
		int ry = y + 26;
		int maxW = w / 2 - 22;
		for (Component[] row : recent) {
			if (ry > y + h - 20) break;
			g.text(font, font.plainSubstrByWidth(row[0].getString(), maxW), right, ry, INK, false);
			g.text(font, font.plainSubstrByWidth(row[1].getString(), maxW), right, ry + 9, INK_SOFT, false);
			ry += 21;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
