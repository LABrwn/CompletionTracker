package de.labrwn.completiontracker.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Stage 1: an empty leather book. The real menu follows in stage 3. */
public class TrackerScreen extends Screen {
	private static final int EDGE = 0xFF1E0C06;
	private static final int LEATHER = 0xFF7A2E1C;
	private static final int LEATHER_HI = 0xFFA8492F;
	private static final int PAGE = 0xFFFBF5E3;
	private static final int PAGE_LINE = 0xFFDCCB9F;
	private static final int SPINE = 0xFFC7B182;
	private static final int INK = 0xFF3B2F1F;

	private int bookX, bookY, bookW, bookH;

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
		g.text(font, title, x + (w / 2 - font.width(title)) / 2, y + 12, INK, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
