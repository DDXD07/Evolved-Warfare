package com.evolvedwarfare.client.health;

import com.evolvedwarfare.health.Stamina;
import com.evolvedwarfare.network.StaminaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * El aguante en el cliente (fase 97): un rayo al estilo de ARK a la izquierda del muñeco de las heridas, que se llena de
 * abajo arriba; aparece cuando no está lleno (y se desvanece al llenarse): azul, naranja con poco y rojo parpadeando
 * agotado. Y agotado no se puede correr.
 */
public final class ClientStamina {
	private static float value = Stamina.MAX, shown = Stamina.MAX;
	private static boolean exhausted, enabled;
	private static float alpha;

	private ClientStamina() {
	}

	public static boolean exhausted() {
		return enabled && exhausted;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static float value() {
		return value;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.ID, (payload, context) -> {
			value = payload.value();
			exhausted = payload.exhausted();
			enabled = payload.enabled();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			value = shown = Stamina.MAX;
			exhausted = enabled = false;
			alpha = 0.0F;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context, tickCounter.getLastFrameDuration()));
	}

	/** El rayo (como el aguante de ARK): se llena de abajo arriba. */
	private static final String[] BOLT = {
			"......#####",
			".....#####.",
			"....#####..",
			"...#####...",
			"..#####....",
			".#########.",
			"#########..",
			"....####...",
			"...####....",
			"..####.....",
			".####......",
			"###........",
			"##.........",
			"#.........."};

	private static boolean in(int col, int row) {
		return row >= 0 && row < BOLT.length && col >= 0 && col < BOLT[row].length() && BOLT[row].charAt(col) == '#';
	}

	private static void render(DrawContext ctx, float frames) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || client.interactionManager == null || !client.interactionManager.hasStatusBars()) {
			return;
		}
		shown += (value - shown) * Math.min(1.0F, 0.35F * frames);
		boolean visible = enabled && (value < Stamina.MAX - 0.01F || exhausted);
		alpha = MathHelper.clamp(alpha + (visible ? 0.15F : -0.04F) * frames, 0.0F, 1.0F);
		if (alpha <= 0.01F) {
			return;
		}
		// a la izquierda del muñeco de las heridas, justo debajo de la barra de experiencia
		int x = ctx.getScaledWindowWidth() / 2 + 91 + 6;
		int y = ctx.getScaledWindowHeight() - 21;
		float f = shown / Stamina.MAX;
		int rows = BOLT.length;
		int filledFrom = rows - Math.round(rows * f);
		int full;
		if (exhausted) {
			full = client.player.age / 5 % 2 == 0 ? 0xE02020 : 0x901010;
		} else if (f < 0.3F) {
			full = 0xF08A20;
		} else {
			full = 0x3CC8E6;
		}
		int a = (int) (alpha * 255) << 24;
		for (int row = -1; row <= rows; row++) {
			for (int col = -1; col <= 11; col++) {
				int color;
				if (in(col, row)) {
					color = row >= filledFrom ? full : 0x1E3A44;
				} else if (in(col - 1, row) || in(col + 1, row) || in(col, row - 1) || in(col, row + 1)) {
					color = 0x0A0F12; // el borde
				} else {
					continue;
				}
				ctx.fill(x + col, y + row, x + col + 1, y + row + 1, a | color);
			}
		}
	}
}
