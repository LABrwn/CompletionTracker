package de.labrwn.completiontracker.client;

import de.labrwn.completiontracker.model.TrackerModel.State;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Small drawing helpers in the look of the prototype: leather book, cream pages, Minecraft buttons. */
final class Ui {
	static final int EDGE = 0xFF1E0C06;
	static final int LEATHER = 0xFF7A2E1C;
	static final int LEATHER_HI = 0xFFA8492F;
	static final int LEATHER_LO = 0xFF4C1A0E;
	static final int TAB_OFF = 0xFF632414;
	static final int TAB_TEXT = 0xFFF6ECD2;
	static final int PAGE = 0xFFFBF5E3;
	static final int PAGE2 = 0xFFF2E7C9;
	static final int PAGE_LINE = 0xFFDCCB9F;
	static final int SPINE = 0xFFC7B182;
	static final int INK = 0xFF3B2F1F;
	static final int INK_SOFT = 0xFF6F5D40;
	static final int SEL = 0xFFECDCB1;
	static final int HOVER = 0xFFF4EAD0;
	static final int GOLD = 0xFFD9A334;
	static final int GOLD_HI = 0xFFF3D06C;
	static final int GOLD_LO = 0xFF9C6C18;
	static final int GRAY = 0xFFA3A19B;
	static final int GRAY_HI = 0xFFD2D0CA;
	static final int GRAY_LO = 0xFF6C6A64;
	static final int FRAME_EDGE = 0xFF2B1D07;
	static final int PLAQUE = 0xFF2A2219;
	static final int XP = 0xFF80FF20;
	static final int XP_BG = 0xFF1D1D1D;
	static final int TIP_BG = 0xF0100010;
	static final int TIP_B1 = 0xFF5000FF;
	static final int TIP_B2 = 0xFF28007F;
	static final int UNK = 0xFF8C7FB0;
	static final int UNK_BG = 0xFFEFE1F9;
	static final int GOOD = 0xFF3FBF3F;
	static final int WHITE = 0xFFFFFFFF;
	static final int[] DIFF = {0xFFAAAAAA, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555};
	static final ChatFormatting[] DIFF_FMT = {ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED};

	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HL = Identifier.withDefaultNamespace("widget/button_highlighted");

	private Ui() {
	}

	static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int hi, int lo) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, hi);
		g.fill(x, y, x + 1, y + h, hi);
		g.fill(x, y + h - 1, x + w, y + h, lo);
		g.fill(x + w - 1, y, x + w, y + h, lo);
	}

	static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y, color);
		g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
		g.fill(x - 1, y, x, y + h, color);
		g.fill(x + w, y, x + w + 1, y + h, color);
	}

	/** Gold (done) or gray (open) frame with an item icon, like on the advancement screen. */
	static void frame(GuiGraphicsExtractor g, int x, int y, int size, boolean done, ItemStack icon) {
		outline(g, x, y, size, size, FRAME_EDGE);
		if (done) bevel(g, x, y, size, size, GOLD, GOLD_HI, GOLD_LO);
		else bevel(g, x, y, size, size, GRAY, GRAY_HI, GRAY_LO);
		if (icon != null) g.item(icon, x + (size - 16) / 2, y + (size - 16) / 2);
	}

	/** The real advancement frame sprite (task, goal, challenge) in 26x26. */
	static void advFrame(GuiGraphicsExtractor g, int x, int y, String type, boolean done, ItemStack icon) {
		Identifier sprite = Identifier.withDefaultNamespace("advancements/" + type + "_frame_" + (done ? "obtained" : "unobtained"));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 26, 26);
		if (icon != null) g.item(icon, x + 5, y + 5);
	}

	static void state(GuiGraphicsExtractor g, Font font, int x, int y, State s) {
		switch (s) {
			case DONE -> {
				g.fill(x, y, x + 10, y + 10, 0xFF1F5F1F);
				g.fill(x + 1, y + 1, x + 9, y + 9, GOOD);
				g.text(font, "✔", x + 2, y + 1, WHITE, false);
			}
			case UNKNOWN -> {
				g.fill(x, y, x + 10, y + 10, 0xFF5A4F7C);
				g.fill(x + 1, y + 1, x + 9, y + 9, 0xFFB9ADDB);
				g.text(font, "?", x + 3, y + 1, 0xFF3D2F57, false);
			}
			default -> {
				g.fill(x, y, x + 10, y + 10, INK_SOFT);
				g.fill(x + 1, y + 1, x + 9, y + 9, PAGE2);
			}
		}
	}

	static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, int percent, int color) {
		outline(g, x, y, w, h, 0xFF000000);
		g.fill(x, y, x + w, y + h, XP_BG);
		g.fill(x, y, x + Math.max(0, Math.min(w, w * percent / 100)), y + h, color);
	}

	static void plaque(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		int w = font.width(text) + 6;
		g.fill(x, y, x + w, y + 11, PLAQUE);
		g.text(font, text, x + 3, y + 2, color, false);
	}

	static int plaqueWidth(Font font, Component text) {
		return font.width(text) + 6;
	}

	/** Minecraft-style button (stone texture), highlighted on hover. */
	static void button(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w, int h, boolean hover, boolean on, int dotColor) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HL : BUTTON, x, y, w, h);
		if (on) {
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x804F7D3A);
			outline(g, x + 1, y + 1, w - 2, h - 2, 0xFF8FC070);
		}
		int tx = x + (w - font.width(text)) / 2;
		if (dotColor != 0) {
			tx += 4;
			g.fill(tx - 9, y + h / 2 - 3, tx - 3, y + h / 2 + 3, 0xFF000000);
			g.fill(tx - 8, y + h / 2 - 2, tx - 4, y + h / 2 + 2, dotColor);
		}
		g.text(font, text, tx, y + (h - 8) / 2, WHITE, true);
	}

	static int buttonWidth(Font font, Component text, boolean dot) {
		return font.width(text) + (dot ? 20 : 12);
	}

	/** Purple box in the look of a Minecraft tooltip, with wrapped text. Returns the height used. */
	static int tipBox(GuiGraphicsExtractor g, Font font, Component title, Component body, int x, int y, int w) {
		List<FormattedCharSequence> lines = font.split(body, w - 12);
		int h = 8 + 10 + lines.size() * 10 + 2;
		g.fill(x, y, x + w, y + h, TIP_BG);
		outline(g, x + 1, y + 1, w - 2, h - 2, TIP_B1);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, TIP_B2);
		g.text(font, title, x + 6, y + 5, 0xFFFFFF55, false);
		int ty = y + 16;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, x + 6, ty, 0xFFECECEC, false);
			ty += 10;
		}
		return h;
	}

	static String clip(Font font, String s, int w) {
		if (font.width(s) <= w) return s;
		return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
	}

	static void textClip(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int w, int color) {
		g.text(font, clip(font, c.getString(), w), x, y, color, false);
	}

	static void centered(GuiGraphicsExtractor g, Font font, Component c, int cx, int y, int color) {
		g.text(font, c, cx - font.width(c) / 2, y, color, false);
	}

	static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
