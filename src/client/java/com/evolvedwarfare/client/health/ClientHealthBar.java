package com.evolvedwarfare.client.health;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * La barra de vida (fase 114): con más de 15 corazones de vida máxima, los corazones se juntan en una barra del mismo
 * ancho que una fila (81 píxeles), roja (verde envenenado, negra con wither, azul helado), con lo absorbido en dorado al
 * final, un rastro rosa de lo que se acaba de perder y la vida en números encima.
 */
public final class ClientHealthBar {
	/** Corazones a partir de los que sale la barra. */
	public static final float HEARTS = 15.0F;
	public static final int W = 81, H = 9;
	private static float trail = -1.0F;
	private static long lastFrame;

	private ClientHealthBar() {
	}

	public static boolean active(PlayerEntity player) {
		return player != null && player.getMaxHealth() > HEARTS * 2.0F + 0.01F;
	}

	public static void render(DrawContext ctx, PlayerEntity player, int x, int y) {
		float max = player.getMaxHealth();
		float hp = MathHelper.clamp(player.getHealth(), 0.0F, max);
		float abs = player.getAbsorptionAmount();
		float total = max + abs;
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0.0F : Math.min(0.2F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		if (trail < hp || trail < 0.0F) {
			trail = hp;
		} else {
			trail = Math.max(hp, trail - Math.max(2.0F, (trail - hp) * 2.5F) * dt);
		}
		int fill, top;
		if (player.hasStatusEffect(StatusEffects.WITHER)) {
			fill = 0xFF2A2A2A;
			top = 0xFF505050;
		} else if (player.hasStatusEffect(StatusEffects.POISON)) {
			fill = 0xFF4E8A1E;
			top = 0xFF7CC040;
		} else if (player.isFrozen()) {
			fill = 0xFF5AA8D8;
			top = 0xFF9AD4F4;
		} else {
			fill = 0xFFB41E1E;
			top = 0xFFE85050;
		}
		// marco y fondo
		ctx.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF0A0A0C);
		ctx.fill(x, y, x + W, y + H, 0xFF3A1416);
		int wHp = Math.round(W * hp / total);
		int wTrail = Math.round(W * trail / total);
		int wAbs = Math.round(W * abs / total);
		if (wTrail > wHp) {
			ctx.fill(x + wHp, y, x + wTrail, y + H, 0xFFF0A0A8);
		}
		if (wHp > 0) {
			boolean low = hp <= max * 0.25F && (player.age / 6) % 2 == 0;
			ctx.fill(x, y, x + wHp, y + H, low ? top : fill);
			ctx.fill(x, y, x + wHp, y + 2, top);
		}
		if (wAbs > 0) {
			int ax = Math.min(x + W - wAbs, x + Math.max(wHp, wTrail));
			ctx.fill(ax, y, ax + wAbs, y + H, 0xFFE0B020);
			ctx.fill(ax, y, ax + wAbs, y + 2, 0xFFFFE070);
		}
		// marcas cada 5 corazones
		for (float m = 10.0F; m < total; m += 10.0F) {
			int mx = x + Math.round(W * m / total);
			ctx.fill(mx, y + H - 3, mx + 1, y + H, 0x80000000);
		}
		String text = Math.round(hp) + "/" + Math.round(max) + (abs > 0.0F ? " +" + Math.round(abs) : "");
		MinecraftClient client = MinecraftClient.getInstance();
		ctx.getMatrices().push();
		float scale = 0.75F;
		int tw = client.textRenderer.getWidth(text);
		ctx.getMatrices().translate(x + W / 2.0F - tw * scale / 2.0F, y + 1.5F, 0.0F);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		ctx.drawTextWithShadow(client.textRenderer, text, 0, 0, 0xFFFFFFFF);
		ctx.getMatrices().pop();
	}
}
