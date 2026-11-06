package com.evolvedwarfare.client.backpack;

import net.minecraft.client.gui.DrawContext;

/** Dibuja paneles y huecos al estilo de vanilla (gris, con su relieve), sin textura. */
public final class Panels {
	public static final int FACE = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, EDGE = 0xFF000000;

	private Panels() {
	}

	public static void panel(DrawContext c, int x, int y, int w, int h) {
		c.fill(x + 1, y, x + w - 1, y + 1, EDGE);
		c.fill(x + 1, y + h - 1, x + w - 1, y + h, EDGE);
		c.fill(x, y + 1, x + 1, y + h - 1, EDGE);
		c.fill(x + w - 1, y + 1, x + w, y + h - 1, EDGE);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
		c.fill(x + 1, y + 1, x + w - 3, y + 3, LIGHT);
		c.fill(x + 1, y + 1, x + 3, y + h - 3, LIGHT);
		c.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, DARK);
		c.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, DARK);
	}

	/** Un hueco de 18x18 (x, y: su esquina). */
	public static void slot(DrawContext c, int x, int y) {
		c.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
		c.fill(x, y, x + 17, y + 1, 0xFF373737);
		c.fill(x, y, x + 1, y + 17, 0xFF373737);
		c.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
		c.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
	}
}
