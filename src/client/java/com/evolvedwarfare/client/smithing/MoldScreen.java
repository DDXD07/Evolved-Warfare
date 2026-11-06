package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.MoldPayloads;
import com.evolvedwarfare.smithing.Smithing;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/**
 * El molde de arcilla, al ponerlo: primero se elige la pieza en una rueda que gira (pasa el ratón: crece, se ve su silueta,
 * los lingotes que lleva y su mango), y luego se talla la arcilla con un palo: mantén clic izquierdo para rascar y clic
 * derecho para volver a tapar. La silueta de la pieza se ve tenue. La arcilla se va secando (la gota de agua): seca, cuesta
 * más rascar. Las estrellas de arriba dicen lo bien que va; al terminar (el botón o Intro) se ven las que salen.
 */
public class MoldScreen extends Screen {
	private static final Identifier STARS = EvolvedWarfare.id("textures/gui/fish_stars.png");
	private static final float DRY_SECONDS = 35.0F;

	private final BlockPos pos;
	private ForgedPart chosen;
	private long chosenAt;
	private final float[] hoverScale = new float[ForgedPart.values().length];
	private ForgedPart hovered;
	// tallar
	private final float[][] carve = new float[16][16];
	private boolean[][] target;
	private int targetCount;
	private long carveStart;
	private long lastFrame;
	private boolean scraping;
	private boolean filling;
	private long lastSound;
	private float toolWobble;
	private long finishedAt = -1;
	private int finalStars;
	private int dinged;
	private boolean sent;
	private final List<float[]> crumbs = new ArrayList<>();

	public MoldScreen(BlockPos pos) {
		super(Text.translatable("block.evolvedwarfare.clay_mold"));
		this.pos = pos;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static long now() {
		return Util.getMeasuringTimeMs();
	}

	private void sound(SoundEvent event, float volume, float pitch) {
		var player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(event, volume, pitch);
		}
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		long t = now();
		float dt = lastFrame == 0 ? 0.0F : Math.min(0.1F, (t - lastFrame) / 1000.0F);
		lastFrame = t;
		if (chosen == null) {
			wheel(ctx, mouseX, mouseY, t, dt);
		} else {
			carving(ctx, mouseX, mouseY, t, dt);
		}
	}

	private void big(DrawContext ctx, ItemStack stack, float cx, float cy, float scale, float z) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy, z);
		ctx.getMatrices().scale(scale, scale, scale);
		ctx.drawItem(stack, -8, -8);
		ctx.getMatrices().pop();
	}

	/** La rueda de piezas. */
	private void wheel(DrawContext ctx, int mouseX, int mouseY, long t, float dt) {
		int cx = width / 2;
		int cy = height / 2;
		float radius = Math.min(width, height) * 0.36F;
		ForgedPart[] all = ForgedPart.values();
		float spin = (t % 120000L) / 1000.0F * 0.12F;
		hovered = null;
		double best = 22.0 * 22.0;
		float[][] at = new float[all.length][2];
		for (int i = 0; i < all.length; i++) {
			double a = spin + i * Math.PI * 2 / all.length - Math.PI / 2;
			at[i][0] = cx + (float) Math.cos(a) * radius;
			at[i][1] = cy + (float) Math.sin(a) * radius * 0.82F;
			double d = (mouseX - at[i][0]) * (mouseX - at[i][0]) + (mouseY - at[i][1]) * (mouseY - at[i][1]);
			if (d < best) {
				best = d;
				hovered = all[i];
			}
		}
		// el círculo de fondo (la mesa de arcilla)
		disc(ctx, cx, cy, (int) (radius * 0.55F), 0xB0302824);
		for (int i = 0; i < all.length; i++) {
			boolean over = all[i] == hovered;
			hoverScale[i] += ((over ? 3.2F : 2.0F) - hoverScale[i]) * Math.min(1.0F, dt * 12.0F);
			if (over) {
				disc(ctx, (int) at[i][0], (int) at[i][1], (int) (14 + 3 * MathHelper.sin(t / 150.0F)), 0x60FFB040);
			}
			float bob = MathHelper.sin(t / 500.0F + i) * 2.0F;
			ItemStack icon = Smithing.part(all[i], ForgeMaterial.IRON, 2);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(at[i][0], at[i][1] + bob, 100.0F);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(over ? MathHelper.sin(t / 200.0F) * 6.0F : 0.0F));
			ctx.getMatrices().scale(hoverScale[i], hoverScale[i], hoverScale[i]);
			ctx.drawItem(icon, -8, -8);
			ctx.getMatrices().pop();
		}
		if (hovered != null) {
			// en el centro: su silueta en la arcilla, su nombre, lo que cuesta y su mango
			boolean[][] mask = PartMasks.of(hovered);
			int cell = Math.max(3, (int) (radius * 0.055F));
			int x0 = cx - cell * 8;
			int y0 = cy - cell * 8 - 8;
			ctx.fill(x0 - 3, y0 - 3, x0 + cell * 16 + 3, y0 + cell * 16 + 3, 0xFF6E5A4C);
			ctx.fill(x0, y0, x0 + cell * 16, y0 + cell * 16, 0xFFA89482);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					if (mask[x][y]) {
						ctx.fill(x0 + x * cell, y0 + y * cell, x0 + (x + 1) * cell, y0 + (y + 1) * cell, 0xFF4A3A30);
					}
				}
			}
			Text name = Text.translatable("part.evolvedwarfare." + hovered.id);
			ctx.drawCenteredTextWithShadow(textRenderer, name, cx, y0 - 14, 0xFFE8C080);
			int iy = y0 + cell * 16 + 8;
			int n = hovered.cost + hovered.sticks + (hovered.extra == ForgedPart.Extra.NONE ? 0 : 1);
			int ix = cx - n * 9;
			for (int k = 0; k < hovered.cost; k++, ix += 18) {
				ctx.drawItem(new ItemStack(ModItems.HOT_IRON_INGOT), ix, iy);
			}
			for (int k = 0; k < hovered.sticks; k++, ix += 18) {
				ctx.drawItem(new ItemStack(Items.STICK), ix, iy);
			}
			if (hovered.extra != ForgedPart.Extra.NONE) {
				ctx.drawItem(new ItemStack(hovered.extra == ForgedPart.Extra.CHAIN ? Items.CHAIN : Items.LEATHER), ix, iy);
			}
		} else {
			// sin nada encima: un martillo y una gota que invitan a elegir
			big(ctx, new ItemStack(ModItems.SMITHING_HAMMER), cx, cy, 3.0F + 0.2F * MathHelper.sin(t / 300.0F), 100.0F);
		}
	}

	private void disc(DrawContext ctx, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int w = (int) Math.sqrt(r * r - dy * dy);
			ctx.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
		}
	}

	private int cell() {
		return Math.max(6, Math.min(16, (Math.min(width, height) - 70) / 16));
	}

	private int slabX() {
		return width / 2 - cell() * 8;
	}

	private int slabY() {
		return height / 2 - cell() * 8 + 6;
	}

	private float moisture(long t) {
		return Math.max(0.0F, 1.0F - (t - carveStart) / 1000.0F / DRY_SECONDS);
	}

	/** Tallando. */
	private void carving(DrawContext ctx, int mouseX, int mouseY, long t, float dt) {
		int c = cell();
		int x0 = slabX();
		int y0 = slabY();
		float wet = moisture(t);
		float intro = Math.min(1.0F, (t - chosenAt) / 350.0F);
		// rascar o tapar bajo el palo
		boolean busy = finishedAt < 0 && intro >= 1.0F && (scraping || filling);
		if (busy) {
			float fx = (mouseX - x0) / (float) c;
			float fy = (mouseY - y0) / (float) c;
			float rate = (wet > 0.0F ? 14.0F : 3.5F) * dt;
			boolean changed = false;
			for (int y = Math.max(0, (int) fy - 1); y <= Math.min(15, (int) fy + 1); y++) {
				for (int x = Math.max(0, (int) fx - 1); x <= Math.min(15, (int) fx + 1); x++) {
					float dx = x + 0.5F - fx;
					float dy = y + 0.5F - fy;
					if (dx * dx + dy * dy > 0.62F) {
						continue;
					}
					float before = carve[x][y];
					carve[x][y] = MathHelper.clamp(carve[x][y] + (scraping ? rate : -rate * 1.5F), 0.0F, 1.0F);
					if (before < 1.0F && carve[x][y] >= 1.0F || before > 0.0F && carve[x][y] <= 0.0F) {
						changed = true;
					}
					if (scraping && carve[x][y] > before && Math.random() < 0.25) {
						crumbs.add(new float[] {x0 + (x + 0.5F) * c, y0 + (y + 0.5F) * c, (float) (Math.random() - 0.5) * 60.0F,
								-(float) Math.random() * 50.0F - 10.0F, 0.0F});
					}
				}
			}
			toolWobble += dt * 30.0F;
			if (changed && t - lastSound > 70) {
				lastSound = t;
				sound(scraping ? SoundEvents.BLOCK_MUD_HIT : SoundEvents.BLOCK_MUD_PLACE, 0.25F, 0.9F + (float) Math.random() * 0.3F);
			}
		}
		// la losa: entra con un pequeño zoom
		ctx.getMatrices().push();
		float zoom = 0.6F + 0.4F * (1.0F - (1.0F - intro) * (1.0F - intro));
		ctx.getMatrices().translate(width / 2.0F, height / 2.0F, 0);
		ctx.getMatrices().scale(zoom, zoom, 1.0F);
		ctx.getMatrices().translate(-width / 2.0F, -height / 2.0F, 0);
		ctx.fill(x0 - 8, y0 - 8, x0 + c * 16 + 8, y0 + c * 16 + 8, 0xFF5A4636);
		ctx.fill(x0 - 6, y0 - 6, x0 + c * 16 + 6, y0 + c * 16 + 6, 0xFF8C7866);
		int dryTint = (int) ((1.0F - wet) * 30);
		float pulse = 0.5F + 0.5F * MathHelper.sin(t / 260.0F);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int px = x0 + x * c;
				int py = y0 + y * c;
				float k = carve[x][y];
				int base = noise(x, y);
				int clay = rgb(168 + base + dryTint, 150 + base + dryTint, 134 + base + dryTint / 2);
				if (k >= 1.0F) {
					// tallada: el hueco, con sombra arriba y a la izquierda
					ctx.fill(px, py, px + c, py + c, rgb(74 + base / 2, 60 + base / 2, 50 + base / 2));
					if (y == 0 || carve[x][y - 1] < 1.0F) {
						ctx.fill(px, py, px + c, py + Math.max(1, c / 5), 0xFF3A2C22);
					}
					if (x == 0 || carve[x - 1][y] < 1.0F) {
						ctx.fill(px, py, px + Math.max(1, c / 5), py + c, 0xFF3A2C22);
					}
				} else {
					ctx.fill(px, py, px + c, py + c, Quads.lerpColor(clay, 0xFF6A5444, k * 0.8F));
					if (k <= 0.0F && wet < 0.25F && noise(y, x) > 12) {
						// grietas de seca
						ctx.fill(px + c / 3, py + c / 4, px + c / 3 + 1, py + c * 3 / 4, 0x40302018);
					}
					if (target[x][y]) {
						// la silueta, tenue
						int a = (int) (40 + 40 * pulse);
						int s = Math.max(1, c / 4);
						ctx.fill(px + c / 2 - s / 2, py + c / 2 - s / 2, px + c / 2 - s / 2 + s, py + c / 2 - s / 2 + s, a << 24 | 0x2A1A10);
					}
				}
			}
		}
		ctx.getMatrices().pop();
		// las migas
		for (int i = crumbs.size() - 1; i >= 0; i--) {
			float[] m = crumbs.get(i);
			m[4] += dt;
			m[3] += 220.0F * dt;
			m[0] += m[2] * dt;
			m[1] += m[3] * dt;
			if (m[4] > 0.8F) {
				crumbs.remove(i);
				continue;
			}
			int a = (int) ((1.0F - m[4] / 0.8F) * 255);
			ctx.fill((int) m[0], (int) m[1], (int) m[0] + 2, (int) m[1] + 2, a << 24 | 0x9A8270);
		}
		// la humedad: el frasco de agua y su barra
		int bx = x0 + c * 16 + 18;
		int by = y0 + 10;
		ctx.drawItem(new ItemStack(Items.POTION), bx - 4, y0 - 12);
		ctx.fill(bx, by, bx + 8, by + c * 12, 0xFF2A2420);
		int h = (int) (c * 12 * wet);
		ctx.fill(bx + 1, by + c * 12 - h, bx + 7, by + c * 12, wet > 0.25F ? 0xFF4A90E0 : 0xFFB08050);
		// las estrellas de lo que va saliendo
		int stars = finishedAt >= 0 ? finalStars : stars(score());
		long since = finishedAt < 0 ? 0 : t - finishedAt;
		for (int i = 0; i < 4; i++) {
			int sx = width / 2 - 34 + i * 18;
			int sy = y0 - 26;
			float pop = finishedAt >= 0 && i < stars ? MathHelper.clamp((since - i * 220L) / 180.0F, 0.0F, 1.0F) : 1.0F;
			float sc = 1.6F + (finishedAt >= 0 && i < stars ? 1.2F * (1.0F - pop) * pop * 4.0F : 0.0F);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(sx + 6, sy + 6, 200);
			ctx.getMatrices().scale(sc, sc, 1);
			if (finishedAt >= 0 && i < stars && pop > 0.0F && i >= dinged) {
				dinged = i + 1; // un "ding" por estrella
				sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 0.8F + i * 0.15F);
			}
			if (i < stars && pop > 0.0F) {
				ctx.drawTexture(STARS, -4, -4, i * 7, 0, 7, 7, 28, 7);
			} else {
				ctx.setShaderColor(0.25F, 0.22F, 0.2F, 0.8F);
				ctx.drawTexture(STARS, -4, -4, 0, 0, 7, 7, 28, 7);
				ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
			ctx.getMatrices().pop();
		}
		// el botón de terminar: un sello redondo con una marca
		int dx = x0 + c * 16 + 22;
		int dy = y0 + c * 16 - 10;
		boolean overDone = Math.abs(mouseX - dx) < 13 && Math.abs(mouseY - dy) < 13;
		disc(ctx, dx, dy, overDone ? 14 : 12, overDone ? 0xFF7AC060 : 0xFF4E8A3C);
		ctx.fill(dx - 6, dy, dx - 3, dy + 3, 0xFFFFFFFF);
		ctx.fill(dx - 3, dy + 2, dx, dy + 5, 0xFFFFFFFF);
		ctx.fill(dx, dy - 1, dx + 3, dy + 3, 0xFFFFFFFF);
		ctx.fill(dx + 3, dy - 4, dx + 6, dy, 0xFFFFFFFF);
		// el palo, en el ratón
		if (finishedAt < 0) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(mouseX, mouseY, 300);
			float wob = busy ? MathHelper.sin(toolWobble) * 8.0F : 0.0F;
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F + wob));
			ctx.getMatrices().scale(1.6F, 1.6F, 1.6F);
			ctx.drawItem(new ItemStack(Items.STICK), -2, -14);
			ctx.getMatrices().pop();
		}
		if (finishedAt >= 0 && t - finishedAt > 1400) {
			send();
			close();
		}
	}

	private static int rgb(int r, int g, int b) {
		return 0xFF000000 | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(b, 0, 255);
	}

	private static int noise(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return (h >>> 24) % 18;
	}

	// ------------------------------------------------------------------ la nota

	/** Lo bien tallado (0-1): lo que se ha sacado de la silueta, menos lo que se ha sacado de fuera (cerca del borde, la mitad). */
	float score() {
		if (target == null || targetCount == 0) {
			return 0.0F;
		}
		float hit = 0.0F;
		float extra = 0.0F;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (carve[x][y] < 1.0F) {
					continue;
				}
				if (target[x][y]) {
					hit++;
				} else {
					extra += nearTarget(x, y) ? 0.5F : 1.5F;
				}
			}
		}
		return MathHelper.clamp((hit - extra) / targetCount, 0.0F, 1.0F);
	}

	private boolean nearTarget(int x, int y) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && target[nx][ny]) {
					return true;
				}
			}
		}
		return false;
	}

	/** Las estrellas (como la calidad): 1 a 4. */
	static int stars(float score) {
		return score < 0.35F ? 1 : score < 0.6F ? 2 : score < 0.85F ? 3 : 4;
	}

	// ------------------------------------------------------------------ entrada

	private void choose(ForgedPart part) {
		chosen = part;
		chosenAt = now();
		carveStart = chosenAt;
		target = PartMasks.of(part);
		targetCount = PartMasks.count(target);
		sound(SoundEvents.BLOCK_MUD_PLACE, 0.8F, 0.8F);
	}

	private void finish() {
		if (chosen == null || finishedAt >= 0) {
			return;
		}
		finishedAt = now();
		finalStars = stars(score());
	}

	private void send() {
		if (sent || chosen == null) {
			return;
		}
		sent = true;
		long[] bits = new long[4];
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (carve[x][y] >= 1.0F) {
					int i = y * 16 + x;
					bits[i >> 6] |= 1L << (i & 63);
				}
			}
		}
		if ((bits[0] | bits[1] | bits[2] | bits[3]) == 0L) {
			return; // sin tallar nada: el molde se queda en blanco
		}
		ClientPlayNetworking.send(new MoldPayloads.Carve(pos, chosen.ordinal(), bits, score()));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (chosen == null) {
			if (hovered != null && button == 0) {
				choose(hovered);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}
		int c = cell();
		int dx = slabX() + c * 16 + 22;
		int dy = slabY() + c * 16 - 10;
		if (button == 0 && Math.abs(mouseX - dx) < 13 && Math.abs(mouseY - dy) < 13) {
			finish();
			return true;
		}
		if (button == 0) {
			scraping = true;
		} else if (button == 1) {
			filling = true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			scraping = false;
		} else if (button == 1) {
			filling = false;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (chosen != null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			finish();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		// a medio tallar y cerrado: cuenta lo que haya (si no se ha empezado, el molde se queda en blanco)
		if (chosen != null && !sent) {
			send();
		}
		super.close();
	}
}
