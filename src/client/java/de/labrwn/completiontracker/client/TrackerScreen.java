package de.labrwn.completiontracker.client;

import de.labrwn.completiontracker.model.ModelBuilder;
import de.labrwn.completiontracker.model.TrackerModel;
import de.labrwn.completiontracker.model.TrackerModel.ChronEntry;
import de.labrwn.completiontracker.model.TrackerModel.Day;
import de.labrwn.completiontracker.model.TrackerModel.Row;
import de.labrwn.completiontracker.model.TrackerModel.State;
import de.labrwn.completiontracker.model.TrackerModel.Sub;
import de.labrwn.completiontracker.model.TrackerModel.Totals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** The tracker book: overview, mobs, items & blocks, advancements and chronicle. */
public class TrackerScreen extends Screen {
	private static final String[] TAB_KEYS = {"overview", "mobs", "items", "advancements", "chronicle"};
	private static final String[][] CATS = {
			{"overall"},
			{"all", "passive", "neutral", "hostile", "boss"},
			{"all", "blocks", "colored", "tools", "food", "other"},
			{"all", "story", "nether", "end", "adventure", "husbandry"},
			{"all_days"}};
	private static final int ROW_H = 24;
	private static final int COL_W = 150;
	private static final int CHRON_ROW = 22;

	// remembered between openings
	private static int tab = 0;
	private static final int[] catIndex = new int[5];
	private static int diff = 0;
	private static int show = 0;
	private static int sort = 0;
	private static String selKey;
	private static String query = "";

	private TrackerModel model;
	private CompletableFuture<TrackerModel> pending;
	private int refreshTimer;

	private List<Row> view = List.of();
	private boolean viewDirty = true;
	private double listScroll;
	private double detailScroll;
	private double chronX;
	private double chronY;
	private boolean chronDragging;
	private static String openDropdown;

	private EditBox search;
	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> popupHits = new ArrayList<>();

	// layout
	private int bx, by, bw, bh, mid;
	private int listX, listY, listW, listH;
	private int detX, detY, detW, detH;
	private int chronAreaX, chronAreaY, chronAreaW, chronAreaH;
	private int ctrlY;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	public TrackerScreen() {
		super(Component.translatable("screen.completiontracker.title"));
	}

	/** Used by the automated in-game test to show a given page. */
	public static void debugShow(int tabIndex, String select, String dropdown) {
		tab = tabIndex;
		selKey = select;
		openDropdown = dropdown;
		if (dropdown != null) {
			dropX = 300;
			dropW = 100;
		}
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	protected void init() {
		layout();
		search = new EditBox(font, listX + 14, listY - 15, listW - 80, 12, Component.translatable("completiontracker.search"));
		search.setBordered(false);
		search.setTextColor(Ui.INK);
		search.setTextShadow(false);
		search.setMaxLength(50);
		search.setHint(Component.translatable("completiontracker.search"));
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			viewDirty = true;
			listScroll = 0;
		});
		addRenderableWidget(search);
		if (model == null) loadModel(true);
	}

	private void loadModel(boolean blocking) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) return;
		var uuid = minecraft.player.getUUID();
		pending = server.submit(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
			return sp == null ? null : ModelBuilder.build(server, sp);
		});
		if (blocking) {
			try {
				model = pending.get(3, TimeUnit.SECONDS);
				pending = null;
				viewDirty = true;
			} catch (Exception ignored) {
				// shown as soon as it is ready
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (pending != null && pending.isDone()) {
			TrackerModel m = pending.getNow(null);
			if (m != null) {
				model = m;
				viewDirty = true;
			}
			pending = null;
		}
		if (++refreshTimer >= 40 && pending == null) {
			refreshTimer = 0;
			loadModel(false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ layout

	private void layout() {
		bx = 8;
		by = 40;
		bw = width - 16;
		bh = height - by - 30;
		mid = bx + bw / 2;
		ctrlY = by + bh + 6;
		int pageTop = by + 6;
		int pageBottom = by + bh - 6;
		listX = bx + 16;
		listY = pageTop + 34;
		listW = mid - 10 - listX;
		listH = pageBottom - 8 - listY;
		detX = mid + 12;
		detY = pageTop + 24;
		detW = bx + bw - 16 - detX;
		detH = pageBottom - 8 - detY;
		chronAreaX = bx + 14;
		chronAreaY = pageTop + 30;
		chronAreaW = bw - 28;
		chronAreaH = pageBottom - 6 - chronAreaY;
	}

	private boolean isListTab() {
		return tab >= 1 && tab <= 3;
	}

	private List<Row> rowsOfTab() {
		if (model == null) return List.of();
		return switch (tab) {
			case 1 -> model.mobs;
			case 2 -> model.items;
			case 3 -> model.advancements;
			default -> List.of();
		};
	}

	private Totals totalsOfTab(int t) {
		return switch (t) {
			case 1 -> model.mobTotals;
			case 2 -> model.itemTotals;
			default -> model.advTotals;
		};
	}

	private List<Row> view() {
		if (!viewDirty) return view;
		String cat = CATS[tab][catIndex[tab]];
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Row> out = new ArrayList<>();
		for (Row r : rowsOfTab()) {
			if (!cat.equals("all") && !cat.equals(r.cat)) continue;
			if (diff != 0 && r.diff != diff) continue;
			if (show == 1 && r.complete) continue;
			if (show == 2 && !r.complete) continue;
			if (!q.isEmpty()) {
				if (r.searchName == null) r.searchName = r.name.getString().toLowerCase(Locale.ROOT);
				boolean match = r.searchName.contains(q);
				for (Sub s : r.subs) if (!match && s.name.getString().toLowerCase(Locale.ROOT).contains(q)) match = true;
				if (!match) continue;
			}
			out.add(r);
		}
		Comparator<Row> byName = Comparator.comparing(r -> r.name.getString());
		switch (sort) {
			case 1 -> out.sort(byName);
			case 2 -> out.sort(Comparator.comparingInt((Row r) -> r.diff).thenComparing(byName));
			case 3 -> out.sort(Comparator.comparingInt((Row r) -> -r.diff).thenComparing(byName));
			default -> out.sort(Comparator.comparingInt(r -> r.order));
		}
		view = out;
		viewDirty = false;
		if (out.stream().noneMatch(r -> r.key.equals(selKey))) selKey = out.isEmpty() ? null : out.get(0).key;
		return view;
	}

	private Row selected() {
		for (Row r : view()) if (r.key.equals(selKey)) return r;
		return null;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractBackground(g, mx, my, pt);
		layout();
		hits.clear();
		search.visible = isListTab();
		search.setX(listX + 14);
		search.setY(listY - 15);
		search.setWidth(listW - 90);

		drawTabs(g, mx, my);
		drawBook(g);
		if (model == null) {
			Ui.centered(g, font, Component.translatable("completiontracker.no_data"), width / 2, by + bh / 2, Ui.INK);
			return;
		}
		switch (tab) {
			case 0 -> drawOverview(g, mx, my);
			case 4 -> drawChronicle(g, mx, my);
			default -> {
				drawList(g, mx, my);
				drawDetail(g, mx, my);
			}
		}
		drawControls(g, mx, my);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		popupHits.clear();
		if (openDropdown != null) drawDropdown(g, mx, my);
	}

	private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
		int x = bx + 12;
		for (int i = 0; i < TAB_KEYS.length; i++) {
			Component label = Component.translatable("completiontracker.tab." + TAB_KEYS[i]);
			Component pct = null;
			if (model != null && i >= 1 && i <= 3) pct = Component.literal(totalsOfTab(i).percent() + " %");
			int w = font.width(label) + 12 + (pct == null ? 0 : font.width(pct) + 8);
			boolean on = tab == i;
			boolean hover = Ui.in(mx, my, x, 6, w, 16);
			Ui.outline(g, x, 6, w, 16, on ? Ui.TAB_TEXT : Ui.EDGE);
			Ui.bevel(g, x, 6, w, 16, on ? Ui.LEATHER_HI : hover ? Ui.LEATHER : Ui.TAB_OFF, on ? 0xFFC96A4C : 0xFF8C3A24, Ui.LEATHER_LO);
			g.text(font, label, x + 6, 10, Ui.TAB_TEXT, true);
			if (pct != null) {
				int px = x + 6 + font.width(label) + 6;
				g.fill(px - 2, 9, px + font.width(pct) + 2, 19, Ui.PAGE);
				g.text(font, pct, px, 10, Ui.INK, false);
			}
			final int t = i;
			hits.add(new Hit(x, 6, w, 16, () -> switchTab(t)));
			x += w + 5;
		}
		// category bookmarks, attached to the book
		x = bx + 12;
		for (int i = 0; i < CATS[tab].length; i++) {
			Component label = Component.translatable("completiontracker.cat." + CATS[tab][i]);
			int w = font.width(label) + 12;
			boolean on = catIndex[tab] == i;
			int top = on ? 26 : 28;
			Ui.outline(g, x, top, w, by - top, Ui.EDGE);
			Ui.bevel(g, x, top, w, by - top + 2, on ? Ui.PAGE : Ui.PAGE2, 0xFFFFFDF4, Ui.PAGE_LINE);
			g.text(font, label, x + 6, top + 3, on ? Ui.INK : Ui.INK_SOFT, false);
			final int c = i;
			hits.add(new Hit(x, top, w, by - top, () -> {
				catIndex[tab] = c;
				viewDirty = true;
				listScroll = 0;
			}));
			x += w + 4;
		}
	}

	private void switchTab(int t) {
		tab = t;
		viewDirty = true;
		listScroll = 0;
		detailScroll = 0;
		openDropdown = null;
	}

	private void drawBook(GuiGraphicsExtractor g) {
		Ui.outline(g, bx, by, bw, bh, Ui.EDGE);
		Ui.bevel(g, bx, by, bw, bh, Ui.LEATHER, Ui.LEATHER_HI, Ui.LEATHER_LO);
		int px = bx + 6, py = by + 6, pw = bw - 12, ph = bh - 12;
		Ui.outline(g, px, py, pw, ph, Ui.EDGE);
		g.fill(px, py, px + pw, py + ph, Ui.PAGE);
		Ui.outline(g, px + 2, py + 2, pw - 4, ph - 4, Ui.PAGE_LINE);
		if (tab != 4) {
			g.fill(mid - 4, py, mid + 4, py + ph, Ui.SPINE);
			g.fill(mid - 4, py, mid - 3, py + ph, Ui.PAGE_LINE);
			g.fill(mid + 3, py, mid + 4, py + ph, Ui.PAGE_LINE);
		}
	}

	// ---------------------------------------------------------------- list + detail

	private void drawList(GuiGraphicsExtractor g, int mx, int my) {
		Totals t = totalsOfTab(tab);
		Component head = Component.empty().append(Component.translatable("completiontracker.tab." + TAB_KEYS[tab]))
				.append(" · " + t.done + " / " + t.total);
		Ui.centered(g, font, head, (bx + mid) / 2, by + 14, Ui.INK_SOFT);
		// search line
		g.text(font, "⌕", listX + 2, listY - 15, Ui.INK_SOFT, false);
		Component count = Component.translatable("completiontracker.entries", view().size());
		g.text(font, count, listX + listW - font.width(count), listY - 15, Ui.INK_SOFT, false);
		g.fill(listX, listY - 4, listX + listW, listY - 3, search.isFocused() ? Ui.LEATHER : Ui.PAGE_LINE);

		List<Row> rows = view();
		double max = Math.max(0, rows.size() * ROW_H - listH);
		listScroll = Math.max(0, Math.min(max, listScroll));
		if (rows.isEmpty()) {
			Ui.centered(g, font, Component.translatable("completiontracker.no_results"), listX + listW / 2, listY + 30, Ui.INK_SOFT);
			return;
		}
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		int first = (int) (listScroll / ROW_H);
		int last = Math.min(rows.size(), first + listH / ROW_H + 2);
		for (int i = first; i < last; i++) {
			Row r = rows.get(i);
			int y = listY + i * ROW_H - (int) listScroll;
			boolean sel = r.key.equals(selKey);
			boolean hover = Ui.in(mx, my, listX, y, listW, ROW_H) && Ui.in(mx, my, listX, listY, listW, listH);
			if (sel) g.fill(listX, y, listX + listW, y + ROW_H - 1, Ui.SEL);
			else if (hover) g.fill(listX, y, listX + listW, y + ROW_H - 1, Ui.HOVER);
			for (int dx = 0; dx < listW; dx += 4) g.fill(listX + dx, y + ROW_H - 1, listX + dx + 2, y + ROW_H, Ui.PAGE_LINE);
			Ui.frame(g, listX + 3, y + 2, 19, r.complete, r.icon);
			Component badge = Component.translatable("completiontracker.diff." + r.diff);
			int bw2 = Ui.plaqueWidth(font, badge);
			int right = listX + listW - 14;
			Ui.state(g, font, right, y + 7, r.state);
			Ui.plaque(g, font, badge, right - 4 - bw2, y + 6, Ui.DIFF[r.diff]);
			int textW = right - 8 - bw2 - (listX + 27);
			Ui.textClip(g, font, r.name, listX + 27, y + 3, textW, Ui.INK);
			Ui.textClip(g, font, r.meta, listX + 27, y + 13, textW, Ui.INK_SOFT);
			if (hover) rowTooltip(g, r, mx, my);
			hits.add(new Hit(listX, Math.max(y, listY), listW, Math.min(ROW_H, listY + listH - Math.max(y, listY)), () -> {
				selKey = r.key;
				detailScroll = 0;
			}));
		}
		g.disableScissor();
		if (max > 0) {
			int barH = Math.max(16, listH * listH / (rows.size() * ROW_H));
			int barY = listY + (int) ((listH - barH) * (listScroll / max));
			g.fill(listX + listW + 2, listY, listX + listW + 5, listY + listH, Ui.PAGE2);
			g.fill(listX + listW + 2, barY, listX + listW + 5, barY + barH, Ui.SPINE);
		}
	}

	private void rowTooltip(GuiGraphicsExtractor g, Row r, int mx, int my) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(r.name.getVisualOrderText());
		lines.add(Component.translatable("completiontracker.diff." + r.diff).withStyle(Ui.DIFF_FMT[r.diff]).getVisualOrderText());
		if (r.hint != null) lines.addAll(font.split(r.hint.copy().withStyle(net.minecraft.ChatFormatting.GRAY), 220));
		g.setTooltipForNextFrame(font, lines, mx, my);
	}

	private void drawDetail(GuiGraphicsExtractor g, int mx, int my) {
		Ui.centered(g, font, Component.translatable("completiontracker.details"), (mid + bx + bw) / 2, by + 14, Ui.INK_SOFT);
		Row r = selected();
		if (r == null) {
			Ui.centered(g, font, Component.translatable("completiontracker.nothing_selected"), detX + detW / 2, detY + 30, Ui.INK_SOFT);
			return;
		}
		g.enableScissor(detX, detY, detX + detW, detY + detH);
		int y = detY - (int) detailScroll;
		int x = detX;
		// header
		if (r.kind == TrackerModel.Kind.ADVANCEMENT) Ui.advFrame(g, x, y, r.frame, r.complete, r.icon);
		else Ui.frame(g, x + 1, y + 1, 24, r.complete, r.icon);
		g.text(font, r.name, x + 32, y + 4, Ui.INK, false);
		Component kind = Component.translatable("completiontracker.kind." + switch (r.kind) {
			case MOB -> "mob";
			case ITEM -> "item";
			case ADVANCEMENT -> "advancement";
		}).append(" · ").append(Component.translatable("completiontracker.cat." + r.cat));
		g.text(font, kind, x + 32, y + 15, Ui.INK_SOFT, false);
		y += 32;
		// chips
		int cx = x;
		int statusColor = r.complete ? 0xFF55FF55 : r.state == State.UNKNOWN ? 0xFFB9ADDB : Ui.WHITE;
		Ui.plaque(g, font, r.status, cx, y, statusColor);
		cx += Ui.plaqueWidth(font, r.status) + 4;
		Ui.plaque(g, font, Component.translatable("completiontracker.diff." + r.diff), cx, y, Ui.DIFF[r.diff]);
		y += 16;
		// facts
		for (Component[] f : r.facts) {
			g.text(font, f[0], x, y, Ui.INK_SOFT, false);
			g.text(font, f[1], x + 70, y, Ui.INK, false);
			y += 11;
		}
		if (!r.facts.isEmpty()) y += 3;
		// hint
		if (r.hint != null && !r.hint.getString().isEmpty()) {
			y += Ui.tipBox(g, font, Component.translatable("completiontracker.hint"), r.hint, x, y, detW - 6) + 6;
		}
		if (r.unknownVariants) {
			List<FormattedCharSequence> lines = font.split(Component.translatable("completiontracker.unknown_variants"), detW - 18);
			int h = lines.size() * 10 + 8;
			g.fill(x, y, x + detW - 6, y + h, Ui.UNK_BG);
			Ui.outline(g, x + 1, y + 1, detW - 8, h - 2, Ui.UNK);
			int ty = y + 5;
			for (FormattedCharSequence l : lines) {
				g.text(font, l, x + 6, ty, 0xFF3D2F57, false);
				ty += 10;
			}
			y += h + 6;
		}
		// sub list
		if (!r.subs.isEmpty()) {
			int done = (int) r.subs.stream().filter(s -> s.state == State.DONE).count();
			g.text(font, Component.translatable(r.kind == TrackerModel.Kind.MOB ? "completiontracker.variants" : "completiontracker.conditions"), x, y, Ui.INK, false);
			Component c = Component.literal(done + " / " + r.subs.size());
			g.text(font, c, x + detW - 6 - font.width(c), y, Ui.INK_SOFT, false);
			y += 12;
			Ui.bar(g, x + 1, y, detW - 8, 5, done * 100 / r.subs.size(), Ui.XP);
			y += 10;
			List<Sub> subs = new ArrayList<>(r.subs);
			subs.sort(Comparator.comparingInt(s -> s.state == State.OPEN ? 0 : s.state == State.UNKNOWN ? 1 : 2));
			for (Sub s : subs) {
				Ui.state(g, font, x + 2, y + 1, s.state);
				g.text(font, s.name, x + 18, y + 2, Ui.INK, false);
				y += 12;
				if (s.state != State.DONE && s.hint != null) {
					for (FormattedCharSequence l : font.split(s.hint, detW - 26)) {
						g.text(font, l, x + 18, y, Ui.INK_SOFT, false);
						y += 10;
					}
				}
				for (int dx = 0; dx < detW - 6; dx += 4) g.fill(x + dx, y + 1, x + dx + 2, y + 2, Ui.PAGE_LINE);
				y += 4;
			}
		}
		g.disableScissor();
		int contentH = y + (int) detailScroll - detY;
		detailScroll = Math.max(0, Math.min(Math.max(0, contentH - detH), detailScroll));
	}

	// ---------------------------------------------------------------- overview

	private void drawOverview(GuiGraphicsExtractor g, int mx, int my) {
		int lx = bx + 16, lw = mid - 10 - lx;
		int top = by + 12;
		Ui.centered(g, font, Component.translatable("completiontracker.cat.overall"), lx + lw / 2, top, Ui.INK_SOFT);
		Totals all = model.all();
		// big percentage
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(lx, top + 16);
		pose.scale(2f, 2f);
		g.text(font, all.percent() + " %", 0, 0, Ui.INK, false);
		pose.popMatrix();
		int bigW = font.width(all.percent() + " %") * 2 + 10;
		Ui.bar(g, lx + bigW, top + 18, lw - bigW, 6, all.percent(), Ui.XP);
		g.text(font, Component.translatable("completiontracker.overview.entries_day", all.done, all.total, model.today), lx + bigW, top + 28, Ui.INK_SOFT, false);

		int cardsTop = top + 44;
		int cardH = Math.max(46, (by + bh - 14 - cardsTop - 12) / 3);
		ItemStack[] icons = {new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.CHEST), new ItemStack(Items.WRITABLE_BOOK)};
		for (int i = 1; i <= 3; i++) {
			int cy = cardsTop + (i - 1) * (cardH + 6);
			Totals t = totalsOfTab(i);
			boolean hover = Ui.in(mx, my, lx, cy, lw, cardH);
			g.fill(lx, cy, lx + lw, cy + cardH, hover ? Ui.HOVER : Ui.PAGE2);
			Ui.outline(g, lx + 1, cy + 1, lw - 2, cardH - 2, hover ? Ui.LEATHER_HI : Ui.PAGE_LINE);
			Ui.frame(g, lx + 6, cy + 5, 20, t.percent() >= 100, icons[i - 1]);
			g.text(font, Component.translatable("completiontracker.tab." + TAB_KEYS[i]), lx + 32, cy + 7, Ui.INK, false);
			Component pc = Component.literal(t.percent() + " %");
			g.text(font, pc, lx + lw - 8 - font.width(pc), cy + 7, Ui.INK, false);
			Component cnt = Component.literal(t.done + " / " + t.total);
			Ui.bar(g, lx + 32, cy + 19, lw - 48 - font.width(cnt), 5, t.percent(), Ui.XP);
			g.text(font, cnt, lx + lw - 8 - font.width(cnt), cy + 18, Ui.INK_SOFT, false);
			if (cardH >= 46) {
				int dx = lx + 32;
				int dy = cy + 31;
				for (int d = 1; d <= 4; d++) {
					String s = Component.translatable("completiontracker.diff." + d).getString() + " " + t.diffDone[d] + "/" + t.diffTotal[d];
					int w = font.width(s) + 12;
					if (dx + w > lx + lw - 6) {
						dx = lx + 32;
						dy += 10;
						if (dy > cy + cardH - 9) break;
					}
					g.fill(dx, dy + 1, dx + 7, dy + 8, 0xFF000000);
					g.fill(dx + 1, dy + 2, dx + 6, dy + 7, Ui.DIFF[d]);
					g.text(font, s, dx + 10, dy, Ui.INK_SOFT, false);
					dx += w + 6;
				}
			}
			final int t2 = i;
			hits.add(new Hit(lx, cy, lw, cardH, () -> switchTab(t2)));
		}

		// right page: up next + recent
		int rx = mid + 12, rw = bx + bw - 16 - rx;
		Ui.centered(g, font, Component.translatable("completiontracker.overview.next"), rx + rw / 2, top, Ui.INK_SOFT);
		g.text(font, Component.translatable("completiontracker.overview.next_note"), rx, top + 14, Ui.INK_SOFT, false);
		int y = top + 28;
		int bottom = by + bh - 14;
		List<Day> recent = new ArrayList<>();
		for (int i = model.chronicle.size() - 1; i >= 0 && recent.size() < 3; i--) {
			if (model.chronicle.get(i).day >= 0) recent.add(model.chronicle.get(i));
		}
		int recentH = 14 + recent.size() * 22;
		int perArea = (bottom - y - recentH) / (ROW_H * 3) >= 2 ? 2 : 1;
		for (int i = 1; i <= 3; i++) {
			List<Row> open = new ArrayList<>();
			for (Row r : i == 1 ? model.mobs : i == 2 ? model.items : model.advancements) if (!r.complete) open.add(r);
			open.sort(Comparator.comparingInt((Row r) -> r.diff).thenComparing(r -> r.name.getString()));
			for (Row r : open.subList(0, Math.min(perArea, open.size()))) {
				if (y + ROW_H > bottom - recentH) break;
				boolean hover = Ui.in(mx, my, rx, y, rw, ROW_H);
				if (hover) g.fill(rx, y, rx + rw, y + ROW_H - 1, Ui.HOVER);
				Ui.frame(g, rx + 3, y + 2, 19, false, r.icon);
				Component badge = Component.translatable("completiontracker.diff." + r.diff);
				int bw2 = Ui.plaqueWidth(font, badge);
				Ui.plaque(g, font, badge, rx + rw - 4 - bw2, y + 6, Ui.DIFF[r.diff]);
				Ui.textClip(g, font, r.name, rx + 27, y + 3, rw - 40 - bw2, Ui.INK);
				g.text(font, Component.translatable("completiontracker.tab." + TAB_KEYS[i]), rx + 27, y + 13, Ui.INK_SOFT, false);
				for (int dx = 0; dx < rw; dx += 4) g.fill(rx + dx, y + ROW_H - 1, rx + dx + 2, y + ROW_H, Ui.PAGE_LINE);
				if (hover) rowTooltip(g, r, mx, my);
				final int t2 = i;
				hits.add(new Hit(rx, y, rw, ROW_H, () -> {
					switchTab(t2);
					catIndex[t2] = 0;
					diff = 0;
					show = 0;
					query = "";
					search.setValue("");
					selKey = r.key;
					viewDirty = true;
				}));
				y += ROW_H;
			}
		}
		y = Math.max(y + 8, bottom - recentH);
		Ui.centered(g, font, Component.translatable("completiontracker.overview.recent"), rx + rw / 2, y, Ui.INK_SOFT);
		y += 14;
		for (Day d : recent) {
			Component label = Component.empty().append(d.label).append(d.sub.getString().isEmpty() ? Component.empty() : Component.literal(" · ").append(d.sub));
			g.text(font, label, rx, y, Ui.INK, false);
			StringBuilder names = new StringBuilder();
			for (ChronEntry e : d.entries) {
				if (names.length() > 0) names.append(", ");
				names.append(e.name.getString());
				if (names.length() > 120) break;
			}
			g.text(font, Ui.clip(font, names.toString(), rw), rx, y + 10, Ui.INK_SOFT, false);
			y += 22;
		}
	}

	// ---------------------------------------------------------------- chronicle

	private void drawChronicle(GuiGraphicsExtractor g, int mx, int my) {
		int x = bx + 16;
		int top = by + 12;
		String[] kinds = {"advancement", "mob", "item"};
		for (String k : kinds) {
			Component c = Component.translatable("completiontracker.kind." + k);
			Ui.frame(g, x, top - 1, 10, true, null);
			g.text(font, c, x + 14, top, Ui.INK_SOFT, false);
			x += font.width(c) + 26;
		}
		Component note = Component.translatable("completiontracker.chronicle.note", model.today);
		g.text(font, note, bx + bw - 16 - font.width(note), top, Ui.INK_SOFT, false);

		List<Day> days = model.chronicle;
		int maxRows = 0;
		for (Day d : days) maxRows = Math.max(maxRows, Math.min(d.entries.size(), 11));
		double maxX = Math.max(0, days.size() * COL_W - chronAreaW);
		double maxY = Math.max(0, 34 + maxRows * CHRON_ROW - chronAreaH);
		chronX = Math.max(0, Math.min(maxX, chronX));
		chronY = Math.max(0, Math.min(maxY, chronY));

		g.enableScissor(chronAreaX, chronAreaY, chronAreaX + chronAreaW, chronAreaY + chronAreaH);
		int ox = chronAreaX - (int) chronX;
		int oy = chronAreaY - (int) chronY;
		// main time line
		int lineY = oy + 11;
		int lineStart = ox + 20, lineEnd = ox + Math.max(1, days.size()) * COL_W - COL_W + 30;
		g.fill(lineStart, lineY - 1, lineEnd, lineY + 2, 0xFF000000);
		g.fill(lineStart, lineY, lineEnd, lineY + 1, 0xFFF6F6F6);
		for (int i = 0; i < days.size(); i++) {
			int cx = ox + i * COL_W;
			if (cx > chronAreaX + chronAreaW || cx + COL_W < chronAreaX) continue;
			Day d = days.get(i);
			// day node
			int nw = Math.max(font.width(d.label), font.width(d.sub)) + 12;
			Ui.outline(g, cx + 6, oy, nw, 22, Ui.EDGE);
			Ui.bevel(g, cx + 6, oy, nw, 22, Ui.LEATHER, Ui.LEATHER_HI, Ui.LEATHER_LO);
			g.text(font, d.label, cx + 12, oy + 2, Ui.TAB_TEXT, true);
			g.text(font, d.sub, cx + 12, oy + 12, 0xFFE6D3A8, false);
			// branch
			int shown = Math.min(10, d.entries.size());
			boolean more = d.entries.size() > 10;
			int n = shown + (more ? 1 : 0);
			int bxl = cx + 16;
			int ey0 = oy + 30;
			if (n > 0) {
				g.fill(bxl - 1, oy + 22, bxl + 2, ey0 + (n - 1) * CHRON_ROW + 10, 0xFF000000);
				g.fill(bxl, oy + 22, bxl + 1, ey0 + (n - 1) * CHRON_ROW + 10, 0xFFF6F6F6);
			}
			for (int k = 0; k < n; k++) {
				int ey = ey0 + k * CHRON_ROW;
				g.fill(bxl, ey + 8, bxl + 10, ey + 11, 0xFF000000);
				g.fill(bxl, ey + 9, bxl + 10, ey + 10, 0xFFF6F6F6);
				int fx = bxl + 10;
				if (k < shown) {
					ChronEntry e = d.entries.get(k);
					Ui.frame(g, fx, ey, 18, true, e.icon);
					Ui.textClip(g, font, e.name, fx + 22, ey + 1, COL_W - 52, Ui.INK);
					g.text(font, Component.translatable("completiontracker.kind." + switch (e.kind) {
						case MOB -> "mob";
						case ITEM -> "item";
						case ADVANCEMENT -> "advancement";
					}), fx + 22, ey + 10, Ui.INK_SOFT, false);
					if (Ui.in(mx, my, fx, ey, COL_W - 30, 18) && Ui.in(mx, my, chronAreaX, chronAreaY, chronAreaW, chronAreaH)) {
						List<FormattedCharSequence> lines = new ArrayList<>();
						lines.add(e.name.getVisualOrderText());
						lines.add(Component.empty().append(d.label).append(" ").append(d.sub).withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
						g.setTooltipForNextFrame(font, lines, mx, my);
					}
				} else {
					Ui.frame(g, fx, ey, 18, false, null);
					g.text(font, "…", fx + 6, ey + 4, Ui.WHITE, true);
					int rest = d.entries.size() - 10;
					g.text(font, Component.translatable("completiontracker.chronicle.more", rest), fx + 22, ey + 1, Ui.INK, false);
					g.text(font, Component.translatable("completiontracker.chronicle.hover"), fx + 22, ey + 10, Ui.INK_SOFT, false);
					if (Ui.in(mx, my, fx, ey, COL_W - 30, 18) && Ui.in(mx, my, chronAreaX, chronAreaY, chronAreaW, chronAreaH)) {
						List<FormattedCharSequence> lines = new ArrayList<>();
						lines.add(Component.translatable("completiontracker.chronicle.more_title", rest).getVisualOrderText());
						int maxLines = Math.max(5, (height - 40) / 10 - 2);
						for (int j = 10; j < d.entries.size(); j++) {
							if (j - 10 >= maxLines) {
								lines.add(Component.literal("… +" + (d.entries.size() - j)).withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
								break;
							}
							lines.add(Component.literal("• ").append(d.entries.get(j).name).withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
						}
						g.setTooltipForNextFrame(font, lines, mx, my);
					}
				}
			}
		}
		g.disableScissor();
	}

	// ---------------------------------------------------------------- controls + dropdown

	private void drawControls(GuiGraphicsExtractor g, int mx, int my) {
		int h = 18;
		int y = ctrlY;
		int total = 0;
		record Btn(Component text, int dot, boolean on, Runnable action, String dropdown) {
		}
		List<Btn> btns = new ArrayList<>();
		if (isListTab()) {
			for (int d = 0; d <= 4; d++) {
				final int dd = d;
				btns.add(new Btn(Component.translatable("completiontracker.diff." + d), Ui.DIFF[d], diff == d, () -> {
					diff = dd;
					viewDirty = true;
					listScroll = 0;
				}, null));
			}
			btns.add(new Btn(Component.translatable("completiontracker.show", Component.translatable("completiontracker.show." + show)).append(" ▼"), 0, show != 0, null, "show"));
			btns.add(new Btn(Component.translatable("completiontracker.sort", Component.translatable("completiontracker.sort." + sort)).append(" ▼"), 0, false, null, "sort"));
		} else if (tab == 4) {
			btns.add(new Btn(Component.translatable("completiontracker.chronicle.start"), 0, false, () -> chronX = 0, null));
			btns.add(new Btn(Component.literal("<"), 0, false, () -> chronX -= COL_W * 3, null));
			btns.add(new Btn(Component.literal(">"), 0, false, () -> chronX += COL_W * 3, null));
			btns.add(new Btn(Component.translatable("completiontracker.chronicle.latest"), 0, false, () -> chronX = Double.MAX_VALUE, null));
		}
		int[] widths = new int[btns.size()];
		for (int i = 0; i < btns.size(); i++) {
			widths[i] = Ui.buttonWidth(font, btns.get(i).text, btns.get(i).dot != 0);
			total += widths[i] + 4;
		}
		Totals all = model.all();
		Component overall = Component.translatable("completiontracker.overall");
		Component pct = Component.literal(all.percent() + " %  " + all.done + " / " + all.total);
		int overallW = font.width(overall) + 6 + 80 + 6 + font.width(pct);
		boolean showOverall = total + overallW + 12 <= width - 16;
		int x = (width - total - (showOverall ? overallW + 12 : 0)) / 2;
		for (int i = 0; i < btns.size(); i++) {
			Btn b = btns.get(i);
			boolean hover = Ui.in(mx, my, x, y, widths[i], h) && openDropdown == null;
			Ui.button(g, font, b.text, x, y, widths[i], h, hover, b.on, b.dot);
			final int bxp = x;
			final int bw2 = widths[i];
			if (b.dropdown != null) {
				hits.add(new Hit(x, y, widths[i], h, () -> {
					openDropdown = b.dropdown.equals(openDropdown) ? null : b.dropdown;
					dropX = bxp;
					dropW = bw2;
				}));
			} else {
				hits.add(new Hit(x, y, widths[i], h, b.action));
			}
			x += widths[i] + 4;
		}
		if (showOverall) {
			x += 8;
			g.text(font, overall, x, y + 5, Ui.WHITE, true);
			x += font.width(overall) + 6;
			Ui.bar(g, x, y + 6, 80, 5, all.percent(), Ui.XP);
			x += 86;
			g.text(font, pct, x, y + 5, 0xFF80FF20, true);
		}
	}

	private static int dropX, dropW;

	private void drawDropdown(GuiGraphicsExtractor g, int mx, int my) {
		String[] keys = openDropdown.equals("show")
				? new String[]{"completiontracker.show.0", "completiontracker.show.1", "completiontracker.show.2"}
				: new String[]{"completiontracker.sort.0", "completiontracker.sort.1", "completiontracker.sort.2", "completiontracker.sort.3"};
		int current = openDropdown.equals("show") ? show : sort;
		int w = dropW;
		for (String k : keys) w = Math.max(w, font.width(Component.translatable(k)) + 24);
		int h = keys.length * 12 + 4;
		int x = dropX;
		int y = ctrlY - h - 3;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFA0A0A0);
		g.fill(x, y, x + w, y + h, 0xFF000000);
		for (int i = 0; i < keys.length; i++) {
			int iy = y + 2 + i * 12;
			boolean hover = Ui.in(mx, my, x, iy, w, 12);
			if (hover) g.fill(x + 1, iy, x + w - 1, iy + 12, 0xFF3A3A3A);
			Component label = Component.literal(i == current ? "> " : "  ").append(Component.translatable(keys[i]));
			g.text(font, label, x + 5, iy + 2, i == current ? 0xFFFFFF55 : hover ? Ui.WHITE : 0xFFE0E0E0, false);
			final int idx = i;
			popupHits.add(new Hit(x, iy, w, 12, () -> {
				if (openDropdown.equals("show")) show = idx;
				else sort = idx;
				viewDirty = true;
				listScroll = 0;
				openDropdown = null;
			}));
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (openDropdown != null) {
			for (Hit h : popupHits) {
				if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
					h.action.run();
					return true;
				}
			}
			openDropdown = null;
			// a click on the dropdown's own button just closes it
			for (Hit h : hits) if (Ui.in(mx, my, h.x, h.y, h.w, h.h) && h.y >= ctrlY) return true;
		}
		if (super.mouseClicked(event, doubleClick)) return true;
		if (search.isFocused() && !Ui.in(mx, my, search.getX(), search.getY(), search.getWidth(), search.getHeight())) search.setFocused(false);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
				h.action.run();
				return true;
			}
		}
		if (tab == 4 && Ui.in(mx, my, chronAreaX, chronAreaY, chronAreaW, chronAreaH)) {
			chronDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		chronDragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (chronDragging) {
			chronX -= dx;
			chronY -= dy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (openDropdown != null) return true;
		if (isListTab()) {
			if (Ui.in(mx, my, listX, listY, listW + 6, listH)) {
				listScroll -= scrollY * ROW_H * 2;
				return true;
			}
			if (Ui.in(mx, my, detX, detY, detW, detH)) {
				detailScroll -= scrollY * 20;
				return true;
			}
		}
		if (tab == 4) {
			if (hasShiftDown()) chronY -= scrollY * 30;
			else chronX -= (scrollY != 0 ? scrollY : scrollX) * 60;
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	private static boolean hasShiftDown() {
		return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
	}
}
