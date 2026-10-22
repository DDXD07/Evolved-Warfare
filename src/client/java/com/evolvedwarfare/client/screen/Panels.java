package com.evolvedwarfare.client.screen;

import net.minecraft.client.gui.DrawContext;

/** Fondos dibujados a mano con los colores de los menús vanilla (para menús de tamaño propio). */
final class Panels {
	private Panels() {
	}

	/** Panel gris con bordes en relieve. */
	static void panel(DrawContext context, int px, int py, int w, int h) {
		context.fill(px + 1, py, px + w - 1, py + h, 0xFF000000);
		context.fill(px, py + 1, px + w, py + h - 1, 0xFF000000);
		context.fill(px + 1, py + 1, px + w - 1, py + h - 1, 0xFFC6C6C6);
		context.fill(px + 1, py + 1, px + w - 2, py + 3, 0xFFFFFFFF);
		context.fill(px + 1, py + 1, px + 3, py + h - 2, 0xFFFFFFFF);
		context.fill(px + 2, py + h - 3, px + w - 1, py + h - 1, 0xFF555555);
		context.fill(px + w - 3, py + 2, px + w - 1, py + h - 1, 0xFF555555);
	}

	/** Marco hundido de una ranura de 18x18. */
	static void slot(DrawContext context, int sx, int sy) {
		slot(context, sx, sy, 0xFF8B8B8B);
	}

	static void slot(DrawContext context, int sx, int sy, int inner) {
		context.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
		context.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
		context.fill(sx + 1, sy + 1, sx + 17, sy + 17, inner);
	}
}
