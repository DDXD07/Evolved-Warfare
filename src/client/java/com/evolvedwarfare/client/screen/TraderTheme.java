package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.trade.TraderType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * El diseño de la tienda de cada oficio: el material del fondo (una textura de bloque del juego, teñida), los colores
 * del marco, de las fichas y de los textos, un adorno propio en el borde y cuatro objetos de su oficio a los lados del
 * inventario.
 *
 * <ul>
 *   <li><b>Tendero</b>: tablones de roble y un toldo de rayas rojas y blancas.</li>
 *   <li><b>Armero</b>: planchas de acero oscuro con remaches.</li>
 *   <li><b>Cantero</b>: ladrillos de piedra con esquinas cinceladas.</li>
 *   <li><b>Joyero</b>: terciopelo morado con ribete de oro y gemas en las esquinas.</li>
 *   <li><b>Sastre</b>: paño azul con una costura blanca alrededor.</li>
 *   <li><b>Mercader de especias</b>: arenisca con cenefas de alfombra.</li>
 *   <li><b>Peletero</b>: abeto oscuro con nieve cayendo del borde y escarcha en las esquinas.</li>
 * </ul>
 */
record TraderTheme(
		Identifier tile, float tintR, float tintG, float tintB,
		int bevelLight, int bevelDark,
		int header, int headerText, boolean darkHeader, int money,
		int inset, int labelText, int hintText,
		int card, int cardHover, int cardLocked, int cardEmpty, int slotInner,
		int price, int sub, int lockText,
		int bar, int knob,
		Trim trim, int accentA, int accentB, int accentC,
		Item[] emblems) {

	enum Trim {
		AWNING, RIVETS, CHISEL, GILDED, STITCH, RUG, SNOW
	}

	private static Identifier block(String name) {
		return Identifier.ofVanilla("textures/block/" + name + ".png");
	}

	static TraderTheme of(TraderType type) {
		return switch (type) {
			case GROCER -> new TraderTheme(block("oak_planks"), 1.0F, 0.95F, 0.85F,
					0xFFE8C88A, 0xFF6B4A22,
					0xC8F6EBD2, 0xFF4A2E10, false, 0xFF7A5A00,
					0x70F6EBD2, 0xFF4A2E10, 0xFF6B5434,
					0xFFEEDFB8, 0xFFF8EED2, 0xFFCDBE98, 0x60F6EBD2, 0xFFB8A27A,
					0xFF7A5A00, 0xFF6B5434, 0xFF8B1A1A,
					0xFF6B4A22, 0xFFE8C88A,
					Trim.AWNING, 0xFFC0392B, 0xFFF6EBD2, 0xFF8E2A20,
					new Item[] {Items.BREAD, Items.APPLE, Items.CARROT, Items.PUMPKIN_PIE});
			case ARMORER -> new TraderTheme(block("iron_block"), 0.42F, 0.44F, 0.48F,
					0xFF9AA3AD, 0xFF1E2023,
					0xC0101215, 0xFFE8E8E8, true, 0xFFF2C14E,
					0x60000000, 0xFFD0D4D8, 0xFFA0A6AD,
					0xFF8E959D, 0xFFA9B0B8, 0xFF6D737A, 0x40000000, 0xFF5A6067,
					0xFF3E2A00, 0xFF262A2F, 0xFF6E0F0F,
					0xFF1E2023, 0xFF9AA3AD,
					Trim.RIVETS, 0xFFC8CDD2, 0xFF141618, 0xFF7C848C,
					new Item[] {Items.SHIELD, Items.IRON_SWORD, Items.CROSSBOW, Items.IRON_HELMET});
			case MASON -> new TraderTheme(block("stone_bricks"), 0.92F, 0.92F, 0.92F,
					0xFFDADADA, 0xFF3E3E3E,
					0xC8E6E6E6, 0xFF2F2F2F, false, 0xFF6A4E00,
					0x70E6E6E6, 0xFF2F2F2F, 0xFF4E4E4E,
					0xFFD0D0CC, 0xFFE2E2DE, 0xFFAAAAA6, 0x60E6E6E6, 0xFF8A8A86,
					0xFF6A4E00, 0xFF4A4A4A, 0xFF8B1A1A,
					0xFF3E3E3E, 0xFFDADADA,
					Trim.CHISEL, 0xFF8A8A8A, 0xFF5A5A5A, 0xFFB5B5B5,
					new Item[] {Items.IRON_PICKAXE, Items.BRICKS, Items.STONE_BRICKS, Items.LANTERN});
			case JEWELER -> new TraderTheme(block("purple_wool"), 0.40F, 0.30F, 0.48F,
					0xFFE8C04A, 0xFF5A3A08,
					0xC0180A24, 0xFFF3D27A, true, 0xFFF3D27A,
					0x60000000, 0xFFE6D8F5, 0xFFB9A8CF,
					0xFF3E2A55, 0xFF54397A, 0xFF2A1D3A, 0x50000000, 0xFF241733,
					0xFFF3D27A, 0xFFB9A8CF, 0xFFFF8A8A,
					0xFF2A1D3A, 0xFFE8C04A,
					Trim.GILDED, 0xFFE8C04A, 0xFF8E6A14, 0xFFFFF1B0,
					new Item[] {Items.DIAMOND, Items.GOLD_INGOT, Items.EMERALD, Items.AMETHYST_SHARD});
			case TAILOR -> new TraderTheme(block("blue_wool"), 0.78F, 0.84F, 1.0F,
					0xFF9FB6E0, 0xFF1F3563,
					0xC8F4EFE3, 0xFF1F3563, false, 0xFF6A4E00,
					0x78F4EFE3, 0xFF1F3563, 0xFF3D4F78,
					0xFFEDE6D6, 0xFFF7F2E6, 0xFFC9C2B2, 0x60F4EFE3, 0xFFB3A994,
					0xFF6A4E00, 0xFF4A4436, 0xFF8B1A1A,
					0xFF1F3563, 0xFFF4EFE3,
					Trim.STITCH, 0xFFF4EFE3, 0xFF1F3563, 0xFFC0392B,
					new Item[] {Items.SHEARS, Items.STRING, Items.RED_WOOL, Items.PAINTING});
			case SPICE_MERCHANT -> new TraderTheme(block("cut_sandstone"), 1.0F, 0.93F, 0.80F,
					0xFFF0D9A0, 0xFF8A5A1E,
					0xC8FFF1D0, 0xFF6B2E0E, false, 0xFF8B3A00,
					0x68FFF1D0, 0xFF6B2E0E, 0xFF7A4A22,
					0xFFF1E0B5, 0xFFFAEDCC, 0xFFD1BE90, 0x60FFF1D0, 0xFFC2A266,
					0xFF8B3A00, 0xFF6B4A22, 0xFF8B1A1A,
					0xFF8A5A1E, 0xFFF0D9A0,
					Trim.RUG, 0xFFB03A2E, 0xFF1F7A7A, 0xFFE0A21B,
					new Item[] {Items.SUGAR, Items.COCOA_BEANS, Items.BLAZE_POWDER, Items.GLISTERING_MELON_SLICE});
			case FURRIER -> new TraderTheme(block("spruce_planks"), 0.58F, 0.52F, 0.48F,
					0xFFE8F1F7, 0xFF2E2016,
					0xD0EAF3F8, 0xFF2B3A48, false, 0xFF6A4E00,
					0x58000000, 0xFFEAF3F8, 0xFFB9C9D6,
					0xFFDDE7EE, 0xFFEEF4F8, 0xFFB7C3CC, 0x40000000, 0xFF9AA9B4,
					0xFF6A4E00, 0xFF3E4C58, 0xFF8B1A1A,
					0xFF2E2016, 0xFFE8F1F7,
					Trim.SNOW, 0xFFFFFFFF, 0xFFDCE8F0, 0xFFB9D2E3,
					new Item[] {Items.RABBIT_HIDE, Items.LEATHER, Items.SNOWBALL, Items.CAMPFIRE});
		};
	}

	/** Color del nivel de la ciudad, legible sobre la cabecera del tema. */
	int levelColor(int level) {
		if (darkHeader) {
			if (level <= 3) {
				return 0xFF7CE08A;
			}
			if (level <= 6) {
				return 0xFFFFC24D;
			}
			return level <= 8 ? 0xFFFF7A6E : 0xFFD99BFF;
		}
		if (level <= 3) {
			return 0xFF2E7D32;
		}
		if (level <= 6) {
			return 0xFFB26A00;
		}
		return level <= 8 ? 0xFFC62828 : 0xFF7B1FA2;
	}

	// ------------------------------------------------------------------ dibujo del fondo

	/** Fondo, marco, cabecera, zonas de las fichas y del inventario, y el adorno del oficio. */
	void drawPanel(DrawContext context, int x, int y, int w, int h, int gridBottom, int inventoryTop) {
		// material de fondo
		context.setShaderColor(tintR, tintG, tintB, 1.0F);
		for (int ty = 0; ty < h; ty += 16) {
			for (int tx = 0; tx < w; tx += 16) {
				context.drawTexture(tile, x + tx, y + ty, 0.0F, 0.0F, Math.min(16, w - tx), Math.min(16, h - ty), 16, 16);
			}
		}
		context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		// marco en relieve
		context.fill(x, y, x + w, y + 1, 0xFF000000);
		context.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
		context.fill(x, y, x + 1, y + h, 0xFF000000);
		context.fill(x + w - 1, y, x + w, y + h, 0xFF000000);
		context.fill(x + 1, y + 1, x + w - 1, y + 3, bevelLight);
		context.fill(x + 1, y + 1, x + 3, y + h - 1, bevelLight);
		context.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, bevelDark);
		context.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, bevelDark);
		// cabecera, zona de las fichas y zona del inventario
		context.fill(x + 4, y + 4, x + w - 4, y + 32, header);
		context.fill(x + 4, y + 32, x + w - 4, y + gridBottom + 10, inset);
		context.fill(x + 4, y + inventoryTop - 12, x + w - 4, y + h - 4, inset);
		drawTrim(context, x, y, w, h);
	}

	private void drawTrim(DrawContext context, int x, int y, int w, int h) {
		switch (trim) {
			case AWNING -> {
				// toldo de rayas con flecos ondulados bajo la cabecera
				for (int sx = 4, i = 0; sx < w - 4; sx += 8, i++) {
					int color = i % 2 == 0 ? accentA : accentB;
					int ex = Math.min(sx + 8, w - 4);
					context.fill(x + sx, y + 4, x + ex, y + 7, color);
					context.fill(x + sx, y + 32, x + ex, y + 33, color);
					context.fill(x + sx + 2, y + 33, x + ex - 2, y + 34, color);
				}
				context.fill(x + 4, y + 7, x + w - 4, y + 8, accentC);
			}
			case RIVETS -> {
				for (int sx = 10; sx < w - 8; sx += 16) {
					rivet(context, x + sx, y + 1);
					rivet(context, x + sx, y + h - 4);
				}
				for (int sy = 18; sy < h - 8; sy += 16) {
					rivet(context, x + 1, y + sy);
					rivet(context, x + w - 4, y + sy);
				}
				context.fill(x + 4, y + 32, x + w - 4, y + 33, accentC);
			}
			case CHISEL -> {
				Identifier chiseled = block("chiseled_stone_bricks");
				int[][] corners = {{0, 0, 10}, {w - 10, 0, 10}, {0, h - 14, 14}, {w - 14, h - 14, 14}};
				for (int[] c : corners) {
					int size = c[2];
					context.fill(x + c[0], y + c[1], x + c[0] + size, y + c[1] + size, 0xFF000000);
					context.drawTexture(chiseled, x + c[0] + 1, y + c[1] + 1, 2.0F, 2.0F, size - 2, size - 2, 16, 16);
				}
				context.fill(x + 4, y + 32, x + w - 4, y + 33, accentB);
			}
			case GILDED -> {
				// ribete de oro y una gema en cada esquina
				context.fill(x + 4, y + 4, x + w - 4, y + 5, accentA);
				context.fill(x + 4, y + h - 5, x + w - 4, y + h - 4, accentA);
				context.fill(x + 4, y + 4, x + 5, y + h - 4, accentA);
				context.fill(x + w - 5, y + 4, x + w - 4, y + h - 4, accentA);
				context.fill(x + 4, y + 32, x + w - 4, y + 33, accentA);
				context.fill(x + 4, y + 33, x + w - 4, y + 34, accentB);
				int[][] corners = {{1, 1}, {w - 9, 1}, {1, h - 9}, {w - 9, h - 9}};
				for (int[] c : corners) {
					gem(context, x + c[0], y + c[1]);
				}
			}
			case STITCH -> {
				// costura: puntadas blancas a 6 píxeles del borde
				for (int sx = 6; sx < w - 6; sx += 4) {
					context.fill(x + sx, y + 6, x + sx + 2, y + 7, accentA);
					context.fill(x + sx, y + h - 7, x + sx + 2, y + h - 6, accentA);
				}
				for (int sy = 6; sy < h - 6; sy += 4) {
					context.fill(x + 6, y + sy, x + 7, y + sy + 2, accentA);
					context.fill(x + w - 7, y + sy, x + w - 6, y + sy + 2, accentA);
				}
				// una cinta roja bajo la cabecera
				context.fill(x + 4, y + 32, x + w - 4, y + 34, accentC);
			}
			case RUG -> {
				// cenefas de alfombra arriba, bajo la cabecera y abajo
				thinRug(context, x + 4, y + 4, w - 8);
				context.fill(x + 4, y + 32, x + w - 4, y + 34, accentA);
				thinRug(context, x + 4, y + h - 7, w - 8);
			}
			case SNOW -> {
				// nieve en el borde de arriba con carámbanos, y escarcha en las esquinas
				context.fill(x + 1, y + 1, x + w - 1, y + 5, accentA);
				for (int sx = 3; sx < w - 3; sx += 7) {
					int drop = 1 + Math.floorMod(sx * 37, 2);
					context.fill(x + sx, y + 5, x + sx + 3, y + 5 + drop, accentA);
					context.fill(x + sx + 1, y + 5 + drop, x + sx + 2, y + 6 + drop, accentB);
				}
				for (int i = 0; i < 12; i++) {
					int fx = (i * 7) % 12;
					int fy = (i * 5) % 12;
					context.fill(x + 4 + fx, y + h - 16 + fy, x + 5 + fx, y + h - 15 + fy, accentC);
					context.fill(x + w - 16 + fx, y + h - 16 + fy, x + w - 15 + fx, y + h - 15 + fy, accentC);
				}
				context.fill(x + 4, y + 32, x + w - 4, y + 33, accentC);
			}
		}
	}

	private void rivet(DrawContext context, int rx, int ry) {
		context.fill(rx, ry, rx + 3, ry + 3, accentB);
		context.fill(rx, ry, rx + 2, ry + 2, accentA);
	}

	private void gem(DrawContext context, int gx, int gy) {
		context.fill(gx + 3, gy, gx + 5, gy + 8, accentA);
		context.fill(gx, gy + 3, gx + 8, gy + 5, accentA);
		context.fill(gx + 2, gy + 2, gx + 6, gy + 6, 0xFF7A2FB0);
		context.fill(gx + 3, gy + 3, gx + 4, gy + 4, accentC);
	}

	/** Cenefa de 3 píxeles: fondo rojo con puntos dorados y turquesa alternos. */
	private void thinRug(DrawContext context, int rx, int ry, int width) {
		context.fill(rx, ry, rx + width, ry + 3, accentA);
		for (int i = 0; i + 3 <= width; i += 4) {
			int color = (i / 4) % 2 == 0 ? accentC : accentB;
			context.fill(rx + i + 1, ry + 1, rx + i + 3, ry + 2, color);
		}
	}

	/** Los cuatro objetos del oficio, grandes, a los lados del inventario. */
	void drawEmblems(DrawContext context, int x, int y, int w, int inventoryTop, int inventoryLeft) {
		int side = inventoryLeft - 4;
		int[][] spots = {
				{4 + (side - 32) / 2, inventoryTop + 2}, {4 + (side - 32) / 2, inventoryTop + 42},
				{w - 4 - side + (side - 32) / 2, inventoryTop + 2}, {w - 4 - side + (side - 32) / 2, inventoryTop + 42}};
		for (int i = 0; i < 4 && i < emblems.length; i++) {
			context.getMatrices().push();
			context.getMatrices().translate(x + spots[i][0], y + spots[i][1], 0);
			context.getMatrices().scale(2.0F, 2.0F, 1.0F);
			context.drawItem(new ItemStack(emblems[i]), 0, 0);
			context.getMatrices().pop();
		}
	}
}
