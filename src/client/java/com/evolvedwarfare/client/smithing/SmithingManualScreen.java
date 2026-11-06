package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgeQuality;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.Smithing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/**
 * El Manual del Herrero: un libro abierto (el mismo papel que la guía) de seis dobles páginas que explica la forja paso a
 * paso, cada una con su ilustración: el grabado de la forja (con el martillo que golpea y las chispas), calentar el
 * lingote, el martillo y el yunque, el minijuego (con la barra en marcha), las piezas (pasa el ratón por encima), montarlas
 * en la mesa y la calidad. Se pasa de página con las flechas (del libro o del teclado).
 */
public class SmithingManualScreen extends Screen {
	private static final Identifier BOOK = EvolvedWarfare.id("textures/gui/guide.png");
	private static final Identifier FORGE = EvolvedWarfare.id("textures/gui/smithing_manual/forge.png");
	private static final int FORGE_W = 112;
	private static final int FORGE_H = 84;
	/** Dónde está el lingote en el grabado, y el extremo del mango del martillo (para que la cabeza caiga sobre él). */
	private static final int INGOT_X = 57;
	private static final int INGOT_Y = 33;
	private static final int HAMMER_PIVOT_X = INGOT_X - 20;
	private static final int HAMMER_PIVOT_Y = INGOT_Y + 21;
	private static final int BOOK_W = 292;
	private static final int BOOK_H = 190;
	private static final int PAGE_W = 120;
	private static final int LEFT_X = 18;
	private static final int RIGHT_X = 156;
	private static final int TOP = 16;
	private static final int BOTTOM = 162;
	private static final int SPREADS = 8;

	private static final int INK = 0xFF3B2A1A;
	private static final int TITLE = 0xFF7A2E12;
	private static final int FAINT = 0xFF8A7556;
	private static final String K = "manual.evolvedwarfare.smithing.";
	private static final Identifier STARS = EvolvedWarfare.id("textures/gui/fish_stars.png");

	private static int lastSpread;
	private int spread;
	private int left;
	private int top;
	private PageTurnWidget previous;
	private PageTurnWidget next;
	/** Lo que hay que enseñar al pasar el ratón (las piezas). */
	private List<Text> hover;

	public SmithingManualScreen() {
		super(Text.translatable("item.evolvedwarfare.smithing_manual"));
		spread = lastSpread;
	}

	@Override
	protected void init() {
		left = (width - BOOK_W) / 2;
		top = (height - BOOK_H) / 2;
		previous = addDrawableChild(new PageTurnWidget(left + LEFT_X - 4, top + BOOK_H - 26, false, button -> turn(-1), true));
		next = addDrawableChild(new PageTurnWidget(left + BOOK_W - 44, top + BOOK_H - 26, true, button -> turn(1), true));
		updateButtons();
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	private void turn(int delta) {
		int to = MathHelper.clamp(spread + delta, 0, SPREADS - 1);
		if (to != spread) {
			spread = to;
			lastSpread = to;
			updateButtons();
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		}
	}

	private void updateButtons() {
		if (previous != null) {
			previous.visible = spread > 0;
			next.visible = spread < SPREADS - 1;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.drawTexture(BOOK, left, top, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		hover = null;
		int l = left + LEFT_X;
		int r = left + RIGHT_X;
		int y = top + TOP;
		long t = Util.getMeasuringTimeMs();
		switch (spread) {
			case 0 -> {
				cover(context, l, y);
				forgeEngraving(context, r, y, t);
			}
			case 1 -> {
				heating(context, l, y);
				crucible(context, r, y, t);
			}
			case 2 -> {
				moldRecipe(context, l, y);
				carvingPage(context, r, y, t);
			}
			case 3 -> {
				pouring(context, l, y, t);
				cracking(context, r, y, t);
			}
			case 4 -> {
				hammerRecipe(context, l, y);
				anvil(context, r, y, t);
			}
			case 5 -> {
				minigame(context, l, y, t);
				strikes(context, r, y);
			}
			case 6 -> {
				parts(context, l, y, mouseX, mouseY);
				assembly(context, r, y);
			}
			default -> {
				quality(context, l, y);
				level(context, r, y, t);
			}
		}
		Text number = Text.literal((spread + 1) + " / " + SPREADS);
		context.drawText(textRenderer, number, r + PAGE_W / 2 - textRenderer.getWidth(number) / 2, top + BOOK_H - 22, FAINT, false);
		if (hover != null) {
			context.drawTooltip(textRenderer, hover, mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ las páginas

	private void cover(DrawContext ctx, int x, int y) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + PAGE_W / 2.0F, y + 4, 0);
		Text name = Text.translatable(K + "title");
		float big = Math.min(1.4F, (PAGE_W - 4) / (float) textRenderer.getWidth(name));
		ctx.getMatrices().scale(big, big, 1.0F);
		ctx.drawText(textRenderer, name, -textRenderer.getWidth(name) / 2, 0, TITLE, false);
		ctx.getMatrices().pop();
		Text subtitle = Text.translatable(K + "subtitle");
		centered(ctx, subtitle, x, y + 20, FAINT, Math.min(0.75F, (PAGE_W - 4) / (float) textRenderer.getWidth(subtitle)));
		rule(ctx, x, y + 29);
		// los tres pasos, con sus iconos
		ItemStack[] icons = {new ItemStack(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE), new ItemStack(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD),
				new ItemStack(ModItems.SMITHING_HAMMER)};
		int cy = y + 36;
		for (int i = 0; i < 3; i++) {
			ctx.drawItem(icons[i], x + 2, cy);
			cy += paragraph(ctx, K + "intro.step" + (i + 1), x + 22, cy + 1, PAGE_W - 22, 0.8F) + 6;
			cy = Math.max(cy, y + 36 + (i + 1) * 20);
		}
		paragraphs(ctx, new String[] {"intro.a"}, x, cy + 2, PAGE_W, top + BOTTOM - cy - 2);
	}

	private void forgeEngraving(DrawContext ctx, int x, int y, long t) {
		int ix = x + (PAGE_W - FORGE_W) / 2;
		int iy = y + 6;
		frame(ctx, ix, iy, FORGE_W, FORGE_H);
		ctx.drawTexture(FORGE, ix, iy, 0, 0, FORGE_W, FORGE_H, FORGE_W, FORGE_H);
		// el martillo que golpea el lingote (gira desde el extremo del mango) y las chispas al golpear
		float phase = (t % 1400L) / 1400.0F;
		float lift = phase < 0.7F ? MathHelper.sin(phase / 0.7F * MathHelper.PI) : 0.0F;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(ix + HAMMER_PIVOT_X, iy + HAMMER_PIVOT_Y, 120.0F);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0F * lift));
		ctx.getMatrices().scale(2.2F, 2.2F, 2.2F);
		ctx.drawItem(new ItemStack(ModItems.SMITHING_HAMMER), -3, -13);
		ctx.getMatrices().pop();
		if (phase >= 0.7F && phase < 0.95F) {
			float k = (phase - 0.7F) / 0.25F;
			java.util.Random random = new java.util.Random(t / 1400L);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 400.0F);
			for (int i = 0; i < 14; i++) {
				double a = random.nextDouble() * Math.PI;
				double v = 10 + random.nextDouble() * 22;
				int sx = (int) (ix + INGOT_X + Math.cos(a) * v * k);
				int sy = (int) (iy + INGOT_Y - Math.sin(a) * v * k + 30 * k * k);
				int col = k < 0.5F ? 0xFFFFF0A0 : 0xFFFF8A20;
				ctx.fill(sx, sy, sx + 1, sy + 1, col);
			}
			ctx.getMatrices().pop();
		}
		centered(ctx, Text.translatable(K + "engraving"), x, iy + FORGE_H + 4, FAINT, 0.7F);
		paragraphs(ctx, new String[] {"intro.b"}, x, iy + FORGE_H + 14, PAGE_W, top + BOTTOM - iy - FORGE_H - 14);
	}

	private void heating(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "heat.title", x, y);
		row(ctx, x, cy, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.BLAST_FURNACE), new ItemStack(ModItems.HOT_IRON_INGOT), "5 s");
		row(ctx, x, cy + 34, new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.FURNACE), new ItemStack(ModItems.HOT_GOLD_INGOT), "10 s");
		paragraphs(ctx, new String[] {"heat.a", "heat.b"}, x, cy + 70, PAGE_W, top + BOTTOM - cy - 70);
	}

	/** El crisol: sobre la hoguera, con los lingotes al rojo cayendo dentro. */
	private void crucible(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "crucible.title", x, y);
		int cx = x + 34;
		big(ctx, new ItemStack(Items.CAMPFIRE), cx - 20, cy + 30, 2.5F);
		big(ctx, new ItemStack(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE), cx - 20, cy + 6, 2.5F);
		// un lingote que cae dentro, cada poco
		float p = (t % 1800L) / 1800.0F;
		if (p < 0.6F) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 300.0F);
			big(ctx, new ItemStack(ModItems.HOT_IRON_INGOT), cx - 8, (int) (cy - 4 + p / 0.6F * 16), 1.5F);
			ctx.getMatrices().pop();
		} else {
			// salpica
			java.util.Random r = new java.util.Random(t / 1800L);
			for (int i = 0; i < 8; i++) {
				float k = (p - 0.6F) / 0.4F;
				int sx = (int) (cx + 4 + (r.nextFloat() - 0.5F) * 30 * k);
				int sy = (int) (cy + 12 - r.nextFloat() * 14 * k + 20 * k * k);
				ctx.fill(sx, sy, sx + 1, sy + 1, 0xFFFFA030);
			}
		}
		// la receta: 5 ladrillos
		ItemStack b = new ItemStack(Items.BRICK);
		ItemStack e = ItemStack.EMPTY;
		int gx = x + 66;
		smallGrid(ctx, gx, cy + 4, new ItemStack[] {b, e, b, b, b, b});
		paragraphs(ctx, new String[] {"crucible.a", "crucible.b"}, x, cy + 66, PAGE_W, top + BOTTOM - cy - 66);
	}

	/** Una rejilla de 3x2 pequeña (para recetas cortas). */
	private void smallGrid(DrawContext ctx, int x, int y, ItemStack[] items) {
		ctx.fill(x - 1, y - 1, x + 55, y + 37, 0xFF6A4A2A);
		for (int i = 0; i < 6; i++) {
			int sx = x + (i % 3) * 18;
			int sy = y + (i / 3) * 18;
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xFF8B7B66);
			ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9B993);
			if (!items[i].isEmpty()) {
				ctx.drawItem(items[i], sx + 1, sy + 1);
			}
		}
		arrow(ctx, x + 20, y + 41);
		ctx.drawItem(new ItemStack(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE), x + 36, y + 39);
	}

	private void moldRecipe(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "mold.title", x, y);
		ItemStack water = net.minecraft.component.type.PotionContentsComponent.createStack(Items.POTION, net.minecraft.potion.Potions.WATER);
		ItemStack e = ItemStack.EMPTY;
		int gx = x + 8;
		grid(ctx, gx, cy, new ItemStack[] {e, water, e, e, new ItemStack(Items.CLAY_BALL), e, e, new ItemStack(Items.STICK), e});
		arrow(ctx, gx + 58, cy + 22);
		big(ctx, new ItemStack(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD), gx + 76, cy + 11, 2.0F);
		paragraphs(ctx, new String[] {"mold.a"}, x, cy + 60, PAGE_W, top + BOTTOM - cy - 60);
	}

	/** Tallar: la silueta de una hoja de espada que se va rascando sola, con el palo. */
	private void carvingPage(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "carve.title", x, y);
		boolean[][] mask = PartMasks.of(ForgedPart.SWORD_BLADE);
		int c = 3;
		int x0 = x + 8;
		int y0 = cy + 2;
		ctx.fill(x0 - 3, y0 - 3, x0 + c * 16 + 3, y0 + c * 16 + 3, 0xFF6E5A4C);
		ctx.fill(x0, y0, x0 + c * 16, y0 + c * 16, 0xFFA89482);
		// se talla en diagonal, de abajo arriba, en bucle
		float p = (t % 5000L) / 4000.0F;
		int done = (int) (Math.min(1.0F, p) * 32);
		int toolX = x0;
		int toolY = y0 + c * 16;
		for (int yy = 0; yy < 16; yy++) {
			for (int xx = 0; xx < 16; xx++) {
				int d = xx + (15 - yy);
				if (mask[xx][yy] && d < done) {
					ctx.fill(x0 + xx * c, y0 + yy * c, x0 + (xx + 1) * c, y0 + (yy + 1) * c, 0xFF4A3A30);
				} else if (mask[xx][yy]) {
					ctx.fill(x0 + xx * c + 1, y0 + yy * c + 1, x0 + xx * c + 2, y0 + yy * c + 2, 0x60402010);
				}
				if (mask[xx][yy] && d == done) {
					toolX = x0 + xx * c;
					toolY = y0 + yy * c;
				}
			}
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(toolX, toolY, 300.0F);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F + MathHelper.sin(t / 60.0F) * 6.0F));
		ctx.drawItem(new ItemStack(Items.STICK), -2, -14);
		ctx.getMatrices().pop();
		// las estrellas que van saliendo
		int stars = Math.min(4, 1 + (int) (Math.min(1.0F, p) * 3.99F));
		for (int i = 0; i < 4; i++) {
			int sx = x + 70 + (i % 2) * 18;
			int sy = cy + 6 + (i / 2) * 18;
			if (i < stars) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(sx, sy, 0);
				ctx.getMatrices().scale(2.0F, 2.0F, 1.0F);
				ctx.drawTexture(STARS, 0, 0, i * 7, 0, 7, 7, 28, 7);
				ctx.getMatrices().pop();
			} else {
				ctx.fill(sx + 3, sy + 3, sx + 11, sy + 11, 0x40000000);
			}
		}
		paragraphs(ctx, new String[] {"carve.a", "carve.b"}, x, cy + 60, PAGE_W, top + BOTTOM - cy - 60);
	}

	/** Verter: el crisol inclinado, el chorro y el medidor que sube hasta la raya. */
	private void pouring(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "pour.title", x, y);
		float p = (t % 4000L) / 3000.0F;
		float fill = Math.min(1.05F, p * 1.05F);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + 28, cy + 14, 100.0F);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(25.0F + Math.min(1.0F, p) * 25.0F));
		ctx.getMatrices().scale(2.2F, 2.2F, 2.2F);
		ctx.drawItem(new ItemStack(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE), -8, -8);
		ctx.getMatrices().pop();
		if (p < 1.0F) {
			ctx.fill(x + 44, cy + 20, x + 46, cy + 44, 0xFFFF9030);
		}
		big(ctx, new ItemStack(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD), x + 30, cy + 34, 2.0F);
		// el medidor (como el de la mira)
		int gx = x + 92;
		int h = 52;
		int gy = cy;
		ctx.fill(gx - 1, gy - 1, gx + 11, gy + h + 1, 0xFF201810);
		ctx.fill(gx, gy, gx + 10, gy + h, 0xFF3A3632);
		int full = gy + h - (int) (h / 1.15F);
		ctx.fill(gx, gy, gx + 10, full - 2, 0x60D03020);
		ctx.fill(gx, full - 2, gx + 10, full + 3, 0xA040D040);
		int level = (int) (fill / 1.15F * h);
		boolean perfect = fill >= 0.97F && fill <= 1.03F;
		ctx.fill(gx + 1, gy + h - level, gx + 9, gy + h, perfect ? 0xFFFFF080 : 0xFFFF9030);
		ctx.fill(gx - 3, full, gx + 13, full + 1, 0xFFFFFFFF);
		paragraphs(ctx, new String[] {"pour.a", "pour.b"}, x, cy + 62, PAGE_W, top + BOTTOM - cy - 62);
	}

	/** Romper el molde: tres golpes y sale la pieza en bruto. */
	private void cracking(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "crack.title", x, y);
		float p = (t % 3000L) / 3000.0F;
		int hits = Math.min(3, (int) (p * 4.0F));
		float shake = (p * 4.0F) % 1.0F < 0.15F && hits < 3 ? MathHelper.sin(t / 20.0F) * 2.0F : 0.0F;
		if (hits < 3) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(shake, 0, 0);
			big(ctx, new ItemStack(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD), x + 14, cy + 10, 2.6F);
			ctx.getMatrices().pop();
			// las grietas
			for (int i = 0; i < hits; i++) {
				int sx = x + 24 + i * 9;
				ctx.fill(sx, cy + 22, sx + 1, cy + 34, 0xFF2A1E16);
				ctx.fill(sx + 1, cy + 28, sx + 4, cy + 29, 0xFF2A1E16);
			}
		} else {
			float k = (p - 0.75F) / 0.25F;
			ItemStack piece = Smithing.rough(ForgedPart.AXE_HEAD, ForgeMaterial.IRON, 0.8F);
			big(ctx, piece, x + 22, (int) (cy + 18 - k * 6), 2.4F);
			java.util.Random r = new java.util.Random(7);
			for (int i = 0; i < 10; i++) {
				int sx = (int) (x + 40 + (r.nextFloat() - 0.5F) * 60 * k);
				int sy = (int) (cy + 30 - r.nextFloat() * 18 * k + 30 * k * k);
				ctx.fill(sx, sy, sx + 2, sy + 2, 0xFFA89482);
			}
		}
		for (int i = 0; i < 3; i++) {
			ctx.fill(x + 84 + i * 10, cy + 10, x + 91 + i * 10, cy + 17, i < hits ? 0xFFE07020 : 0xFF505050);
		}
		paragraphs(ctx, new String[] {"crack.a", "crack.b"}, x, cy + 56, PAGE_W, top + BOTTOM - cy - 56);
	}

	private void hammerRecipe(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "hammer.title", x, y);
		ItemStack i = new ItemStack(Items.IRON_INGOT);
		ItemStack s = new ItemStack(Items.STICK);
		ItemStack e = ItemStack.EMPTY;
		int gx = x + 8;
		grid(ctx, gx, cy, new ItemStack[] {i, i, e, i, s, e, e, s, e});
		arrow(ctx, gx + 58, cy + 22);
		big(ctx, new ItemStack(ModItems.SMITHING_HAMMER), gx + 76, cy + 11, 2.0F);
		paragraphs(ctx, new String[] {"hammer.a"}, x, cy + 60, PAGE_W, top + BOTTOM - cy - 60);
	}

	private void anvil(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "anvil.title", x, y);
		big(ctx, new ItemStack(Items.ANVIL), x + 8, cy + 6, 2.5F);
		float bob = MathHelper.sin(t / 300.0F) * 1.5F;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, bob, 200.0F);
		big(ctx, Smithing.rough(ForgedPart.PICKAXE_HEAD, ForgeMaterial.IRON, 0.8F), x + 14, cy - 2, 2.0F);
		ctx.getMatrices().pop();
		// "clic derecho" y la elección de la pieza
		Text click = Text.translatable(K + "anvil.click");
		int bx = x + 60;
		ctx.fill(bx, cy + 8, bx + 58, cy + 20, 0xFF6A4A2A);
		ctx.fill(bx + 1, cy + 9, bx + 57, cy + 19, 0xFFE8D6A8);
		small(ctx, click, bx + 29 - (int) (textRenderer.getWidth(click) * 0.7F / 2), cy + 11, INK, 0.7F);
		ItemStack[] choices = {Smithing.part(ForgedPart.PICKAXE_HEAD, ForgeMaterial.IRON, 3), new ItemStack(Items.DIAMOND),
				Smithing.part(ForgedPart.PICKAXE_HEAD, ForgeMaterial.DIAMOND, 3)};
		for (int k = 0; k < choices.length; k++) {
			int px = bx + k * 20;
			ctx.fill(px, cy + 24, px + 18, cy + 42, 0xFF8A7556);
			ctx.fill(px + 1, cy + 25, px + 17, cy + 41, k == 1 ? 0xFFF0C070 : 0xFFD8C8A0);
			ctx.drawItem(choices[k], px + 1, cy + 25);
		}
		paragraphs(ctx, new String[] {"anvil.a", "anvil.b"}, x, cy + 50, PAGE_W, top + BOTTOM - cy - 50);
	}

	private void minigame(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "game.title", x, y);
		int w = PAGE_W - 8;
		int bx = x + 4;
		int by = cy + 10;
		ctx.fill(bx - 3, by - 12, bx + w + 3, by + 34, 0xFF2A2420);
		float target = 0.62F;
		int tc = bx + (int) (target * w);
		int hw = (int) (0.11F * w);
		int pw = (int) (0.04F * w);
		int ow = (int) (0.2F * w);
		ctx.fill(bx, by, bx + w, by + 12, 0xFF3A3632);
		ctx.fill(tc - ow, by + 1, tc + ow, by + 11, 0xFF6A4020);
		ctx.fill(tc - hw, by + 1, tc + hw, by + 11, 0xFFE07020);
		ctx.fill(tc - pw, by + 1, tc + pw, by + 11, 0xFFFFE060);
		// el martillo, de un lado a otro
		float h = 0.5F + 0.5F * MathHelper.sin(t / 520.0F);
		int hx = bx + (int) (h * w);
		boolean perfect = Math.abs(hx - tc) <= pw;
		boolean good = Math.abs(hx - tc) <= hw;
		ctx.fill(hx - 1, by - 5, hx + 2, by + 17, perfect ? 0xFFFFFFFF : 0xFFC8C8D0);
		ctx.fill(hx - 4, by - 8, hx + 5, by - 4, 0xFF8A8A96);
		// el calor (se va gastando) y los golpes
		float heat = 1.0F - ((t % 9000L) / 9000.0F) * 0.8F;
		ctx.fill(bx, by + 19, bx + w, by + 22, 0xFF202020);
		ctx.fill(bx, by + 19, bx + (int) (heat * w), by + 22, heat > 0.5F ? 0xFFFF8020 : 0xFFD04010);
		int[] cols = {0xFFFFE060, 0xFF60D060, 0xFFC08040, 0xFF505050, 0xFF505050, 0xFF505050};
		for (int i = 0; i < cols.length; i++) {
			int sx = bx + w - (cols.length - i) * 9;
			ctx.fill(sx, by + 25, sx + 7, by + 31, cols[i]);
		}
		Text verdict = Text.translatable(K + (perfect ? "game.perfect" : good ? "game.good" : "game.wait"));
		small(ctx, verdict, bx, by + 25, perfect ? 0xFFFFE060 : good ? 0xFFE09040 : 0xFFA09080, 0.7F);
		// leyenda de colores
		int ly = by + 40;
		legend(ctx, x, ly, 0xFFFFE060, "game.legend.perfect");
		legend(ctx, x + 40, ly, 0xFFE07020, "game.legend.good");
		legend(ctx, x + 80, ly, 0xFF6A4020, "game.legend.poor");
		paragraphs(ctx, new String[] {"game.a"}, x, ly + 12, PAGE_W, top + BOTTOM - ly - 12);
	}

	private void strikes(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "strikes.title", x, y);
		ItemStack[] icons = {Smithing.part(ForgedPart.LONGSWORD_BLADE, ForgeMaterial.IRON, 2), new ItemStack(ModItems.HOT_IRON_INGOT),
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND)};
		String[] keys = {"strikes.a", "strikes.b", "strikes.c", "strikes.d"};
		int room = top + BOTTOM - cy;
		float scale = 0.8F;
		for (int attempt = 0; attempt < 6; attempt++) {
			int total = 0;
			for (String key : keys) {
				total += Math.max(18, wrapped(key, PAGE_W - 20, scale).size() * lineH(scale)) + 4;
			}
			if (total <= room) {
				break;
			}
			scale -= 0.05F;
		}
		for (int i = 0; i < keys.length; i++) {
			ctx.drawItem(icons[i], x, cy);
			int h = Math.max(18, paragraph(ctx, K + keys[i], x + 20, cy + 1, PAGE_W - 20, scale));
			cy += h + 4;
		}
	}

	private void parts(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
		int cy = heading(ctx, "parts.title", x, y);
		ForgedPart[] all = ForgedPart.values();
		int cell = 30;
		for (int i = 0; i < all.length; i++) {
			ForgedPart part = all[i];
			int px = x + (i % 4) * cell;
			int py = cy + (i / 4) * 32;
			boolean over = mouseX >= px && mouseX < px + cell - 2 && mouseY >= py && mouseY < py + 30;
			ctx.fill(px, py, px + cell - 2, py + 30, over ? 0x50E07020 : 0x20000000);
			big(ctx, Smithing.part(part, ForgeMaterial.IRON, 2), px + 4, py + 1, 1.25F);
			Text cost = Text.literal("×" + part.cost);
			small(ctx, cost, px + (cell - 2) / 2 - (int) (textRenderer.getWidth(cost) * 0.7F / 2), py + 23, TITLE, 0.7F);
			if (over) {
				List<Text> lines = new ArrayList<>();
				lines.add(Text.translatable("part.evolvedwarfare." + part.id));
				lines.add(Text.translatable(K + "parts.cost", part.cost).formatted(net.minecraft.util.Formatting.GOLD));
				lines.add(Text.translatable(K + "parts.handle." + part.extra.name().toLowerCase(java.util.Locale.ROOT), part.sticks)
						.formatted(net.minecraft.util.Formatting.GRAY));
				lines.add(Text.translatable(K + "parts.strikes", part.strikes).formatted(net.minecraft.util.Formatting.GRAY));
				hover = lines;
			}
		}
		paragraphs(ctx, new String[] {"parts.a"}, x, cy + 98, PAGE_W, top + BOTTOM - cy - 98);
	}

	private void assembly(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "assembly.title", x, y);
		ItemStack e = ItemStack.EMPTY;
		ItemStack blade = Smithing.part(ForgedPart.SWORD_BLADE, ForgeMaterial.IRON, 3);
		int gx = x + 8;
		grid(ctx, gx, cy, new ItemStack[] {e, blade, e, e, new ItemStack(Items.STICK), e, e, new ItemStack(Items.LEATHER), e});
		arrow(ctx, gx + 58, cy + 22);
		big(ctx, new ItemStack(Items.IRON_SWORD), gx + 76, cy + 11, 2.0F);
		paragraphs(ctx, new String[] {"assembly.a", "assembly.b"}, x, cy + 60, PAGE_W, top + BOTTOM - cy - 60);
	}

	private ItemStack[] tools;

	private static final int[] QUALITY_COLORS = {0xFF6B6B6B, INK, 0xFF2E7D32, 0xFF8E24AA};

	private void quality(DrawContext ctx, int x, int y) {
		int cy = heading(ctx, "quality.title", x, y);
		String[] heads = {"quality.durability", "quality.damage", "quality.speed"};
		int[] cols = {x + 54, x + 76, x + 98};
		for (int i = 0; i < heads.length; i++) {
			Text h = Text.translatable(K + heads[i]);
			small(ctx, h, cols[i] + 8 - (int) (textRenderer.getWidth(h) * 0.6F / 2), cy, FAINT, 0.6F);
		}
		cy += 8;
		for (int q = 1; q <= 4; q++) {
			int ry = cy + (q - 1) * 13;
			if (q % 2 == 0) {
				ctx.fill(x, ry - 2, x + PAGE_W, ry + 10, 0x14000000);
			}
			Text name = Text.translatable("forge_quality.evolvedwarfare." + ForgeQuality.key(q));
			small(ctx, name, x + 2, ry, QUALITY_COLORS[q - 1], 0.65F);
			String[] values = {"×" + new java.math.BigDecimal(String.valueOf(ForgeQuality.durability(q))).stripTrailingZeros().toPlainString().replace('.', ','),
					signed(ForgeQuality.damage(q), ""), signed(ForgeQuality.speed(q) * 100, " %")};
			for (int i = 0; i < values.length; i++) {
				Text v = Text.literal(values[i]);
				small(ctx, v, cols[i] + 8 - (int) (textRenderer.getWidth(v) * 0.7F / 2), ry + 1, QUALITY_COLORS[q - 1], 0.7F);
			}
		}
		// una herramienta de cada calidad
		if (tools == null) {
			tools = new ItemStack[4];
			for (int q = 1; q <= 4; q++) {
				tools[q - 1] = ForgeQuality.apply(new ItemStack(Items.IRON_PICKAXE), q);
			}
		}
		int ty = cy + 54;
		for (int q = 0; q < 4; q++) {
			ctx.drawItem(tools[q], x + 8 + q * 28, ty);
			ctx.fill(x + 6 + q * 28, ty + 17, x + 26 + q * 28, ty + 18, QUALITY_COLORS[q]);
		}
		paragraphs(ctx, new String[] {"quality.a"}, x, ty + 22, PAGE_W, top + BOTTOM - ty - 22);
	}

	private static String signed(double v, String unit) {
		if (Math.abs(v) < 1e-6) {
			return "=";
		}
		String n = String.format(java.util.Locale.ROOT, "%.1f", Math.abs(v)).replace(".0", "").replace('.', ',');
		return (v > 0 ? "+" : "-") + n + unit;
	}

	private void level(DrawContext ctx, int x, int y, long t) {
		int cy = heading(ctx, "level.title", x, y);
		// el nivel sube de 0 a 10 y la zona buena se agranda
		int lv = (int) ((t / 700L) % 11L);
		int w = PAGE_W - 8;
		int bx = x + 4;
		ctx.fill(bx - 2, cy - 2, bx + w + 2, cy + 26, 0xFF2A2420);
		ctx.fill(bx, cy, bx + w, cy + 10, 0xFF3A3632);
		int hw = (int) ((0.07F + lv * 0.006F) * w);
		int c = bx + w / 2;
		ctx.fill(c - hw * 2, cy + 1, c + hw * 2, cy + 9, 0xFF6A4020);
		ctx.fill(c - hw, cy + 1, c + hw, cy + 9, 0xFFE07020);
		ctx.fill(c - Math.max(1, hw / 3), cy + 1, c + Math.max(1, hw / 3), cy + 9, 0xFFFFE060);
		for (int i = 0; i <= 10; i++) {
			int sx = bx + i * (w / 11);
			ctx.fill(sx, cy + 14, sx + w / 11 - 2, cy + 18, i <= lv ? 0xFF60D060 : 0xFF505050);
		}
		Text label = Text.translatable(K + "level.label", lv);
		small(ctx, label, bx, cy + 19, 0xFFE8D6A8, 0.6F);
		paragraphs(ctx, new String[] {"level.a", "level.b", "level.c"}, x, cy + 32, PAGE_W, top + BOTTOM - cy - 32);
	}

	// ------------------------------------------------------------------ piezas de dibujo

	/** El título de una página y la raya de debajo; devuelve dónde sigue. */
	private int heading(DrawContext ctx, String key, int x, int y) {
		Text title = Text.translatable(K + key);
		float scale = Math.min(1.0F, (PAGE_W - 4) / (float) textRenderer.getWidth(title));
		small(ctx, title, x + PAGE_W / 2 - (int) (textRenderer.getWidth(title) * scale / 2), y + (int) ((1.0F - scale) * 4), TITLE, scale);
		rule(ctx, x, y + 11);
		return y + 18;
	}

	private void rule(DrawContext ctx, int x, int y) {
		ctx.fill(x + 10, y, x + PAGE_W - 10, y + 1, 0x407A2E12);
	}

	private void frame(DrawContext ctx, int x, int y, int w, int h) {
		ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF6A4A2A);
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFD8C49A);
	}

	/** Lo de antes → con qué → lo de después (iconos grandes), con una nota bajo el del medio. */
	private void row(DrawContext ctx, int x, int y, ItemStack from, ItemStack with, ItemStack to, String note) {
		big(ctx, from, x + 2, y, 1.75F);
		arrow(ctx, x + 32, y + 10);
		big(ctx, with, x + 46, y, 1.75F);
		Text n = Text.literal(note);
		small(ctx, n, x + 60 - (int) (textRenderer.getWidth(n) * 0.7F / 2), y + 28, TITLE, 0.7F);
		arrow(ctx, x + 76, y + 10);
		big(ctx, to, x + 90, y, 1.75F);
	}

	private void arrow(DrawContext ctx, int x, int y) {
		ctx.fill(x, y + 3, x + 9, y + 5, 0xFF8A6A3A);
		ctx.fill(x + 7, y + 1, x + 9, y + 7, 0xFF8A6A3A);
		ctx.fill(x + 9, y + 2, x + 11, y + 6, 0xFF8A6A3A);
		ctx.fill(x + 11, y + 3, x + 12, y + 5, 0xFF8A6A3A);
	}

	/** Una rejilla de mesa de 3x3. */
	private void grid(DrawContext ctx, int x, int y, ItemStack[] items) {
		ctx.fill(x - 2, y - 2, x + 56, y + 56, 0xFF6A4A2A);
		for (int i = 0; i < 9; i++) {
			int sx = x + (i % 3) * 18;
			int sy = y + (i / 3) * 18;
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xFF8B7B66);
			ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9B993);
			if (!items[i].isEmpty()) {
				ctx.drawItem(items[i], sx + 1, sy + 1);
			}
		}
	}

	private void big(DrawContext ctx, ItemStack stack, int x, int y, float scale) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(scale, scale, scale);
		ctx.drawItem(stack, 0, 0);
		ctx.getMatrices().pop();
	}

	private void legend(DrawContext ctx, int x, int y, int color, String key) {
		ctx.fill(x, y + 1, x + 6, y + 6, color);
		small(ctx, Text.translatable(K + key), x + 8, y + 1, INK, 0.6F);
	}

	private void small(DrawContext ctx, Text text, int x, int y, int color, float scale) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 300.0F);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		ctx.drawText(textRenderer, text, 0, 0, color, false);
		ctx.getMatrices().pop();
	}

	private void centered(DrawContext ctx, Text text, int x, int y, int color, float scale) {
		small(ctx, text, x + PAGE_W / 2 - (int) (textRenderer.getWidth(text) * scale / 2), y, color, scale);
	}

	private static int lineH(float scale) {
		return Math.round(9 * scale);
	}

	private List<OrderedText> wrapped(String key, int width, float scale) {
		return textRenderer.wrapLines(Text.translatable(K + key), (int) (width / scale));
	}

	/** Un párrafo con su escala; devuelve su alto. */
	private int paragraph(DrawContext ctx, String fullKey, int x, int y, int width, float scale) {
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(fullKey), (int) (width / scale));
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 300.0F);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		for (int i = 0; i < lines.size(); i++) {
			ctx.drawText(textRenderer, lines.get(i), 0, i * 9, INK, false);
		}
		ctx.getMatrices().pop();
		return Math.round(lines.size() * 9 * scale);
	}

	/** Varios párrafos que tienen que caber en {@code room}: se encogen un poco si no caben. */
	private void paragraphs(DrawContext ctx, String[] keys, int x, int y, int width, int room) {
		float scale = 0.8F;
		for (int attempt = 0; attempt < 7; attempt++) {
			int total = -4;
			for (String key : keys) {
				total += wrapped(key, width, scale).size() * lineH(scale) + 4;
			}
			if (total <= room) {
				break;
			}
			scale -= 0.04F;
		}
		int cy = y;
		for (String key : keys) {
			cy += paragraph(ctx, K + key, x, cy, width, scale) + 4;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			turn(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			turn(1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
