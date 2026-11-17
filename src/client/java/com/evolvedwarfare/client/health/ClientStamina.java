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
 * El aguante en el cliente (fase 97): una barrita bajo la mira que aparece cuando no está lleno (y se desvanece al
 * llenarse): amarilla, naranja con poco y roja parpadeando agotado. Y agotado no se puede correr.
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
		int w = 40, h = 3;
		int x = ctx.getScaledWindowWidth() / 2 - w / 2;
		int y = ctx.getScaledWindowHeight() / 2 + 9;
		float f = shown / Stamina.MAX;
		int color;
		if (exhausted) {
			color = client.player.age / 5 % 2 == 0 ? 0xE02020 : 0x801010;
		} else if (f < 0.3F) {
			color = 0xF08020;
		} else {
			color = 0xF0D040;
		}
		int a = (int) (alpha * 200) << 24;
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, a | 0x101010);
		ctx.fill(x, y, x + Math.round(w * f), y + h, (int) (alpha * 255) << 24 | color);
	}
}
