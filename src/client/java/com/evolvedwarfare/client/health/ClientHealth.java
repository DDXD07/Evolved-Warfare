package com.evolvedwarfare.client.health;

import com.evolvedwarfare.health.BodyPart;
import com.evolvedwarfare.network.WoundsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Las heridas en el cliente (fase 92): un muñeco a la derecha de la barra de objetos con cada parte del cuerpo de su color (verde sana,
 * amarilla tocada, roja muy herida, negra sin vida), los huesos rotos con una raya y los entablillados con un marco; y la
 * gota que avisa de que se sangra. Solo se ve si hay algo herido.
 */
public final class ClientHealth {
	private static float[] parts = new float[BodyPart.values().length];
	private static int broken, splinted;
	private static boolean bleeding, enabled;

	static {
		java.util.Arrays.fill(parts, 1.0F);
	}

	private ClientHealth() {
	}

	public static boolean legBroken(boolean right) {
		return enabled && (broken & (right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG).bit()) != 0;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WoundsPayload.ID, (payload, context) -> {
			float[] p = new float[BodyPart.values().length];
			for (int k = 0; k < p.length; k++) {
				p[k] = k < payload.parts().size() ? payload.parts().get(k) : 1.0F;
			}
			parts = p;
			broken = payload.broken();
			splinted = payload.splinted();
			bleeding = payload.bleeding();
			enabled = payload.enabled();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			java.util.Arrays.fill(parts, 1.0F);
			broken = splinted = 0;
			bleeding = enabled = false;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	private static boolean hurt() {
		if (broken != 0 || bleeding) {
			return true;
		}
		for (float f : parts) {
			if (f < 0.999F) {
				return true;
			}
		}
		return false;
	}

	private static int color(float f) {
		if (f <= 0.0F) {
			return 0xFF1A1A1A;
		}
		int r, g;
		if (f > 0.5F) {
			r = (int) (255 * (1.0F - f) * 2.0F);
			g = 200;
		} else {
			r = 230;
			g = (int) (200 * f * 2.0F);
		}
		return 0xFF000000 | r << 16 | g << 8 | 40;
	}

	private static void part(DrawContext ctx, BodyPart part, int x, int y, int w, int h) {
		int k = part.ordinal();
		boolean isBroken = (broken & part.bit()) != 0;
		boolean isSplinted = (splinted & part.bit()) != 0;
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, isSplinted ? 0xFFC8A060 : 0xFF000000);
		ctx.fill(x, y, x + w, y + h, color(parts[k]));
		if (isBroken) {
			// una raya en zigzag: el hueso partido
			int mid = y + h / 2;
			ctx.fill(x, mid - 1, x + w, mid, 0xFFFFFFFF);
			ctx.fill(x, mid, x + w / 2, mid + 1, 0xFFFFFFFF);
		}
	}

	private static void render(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (!enabled || player == null || client.options.hudHidden || client.interactionManager == null
				|| !client.interactionManager.hasStatusBars() || !hurt()) {
			return;
		}
		// a la derecha de la barra de objetos (donde no tapa el chat)
		int x = ctx.getScaledWindowWidth() / 2 + 91 + 10;
		int y = ctx.getScaledWindowHeight() - 34;
		// cabeza 6x6, torso 6x10, brazos 3x10, piernas 3x10
		part(ctx, BodyPart.HEAD, x + 5, y, 6, 6);
		part(ctx, BodyPart.TORSO, x + 5, y + 8, 6, 10);
		part(ctx, BodyPart.RIGHT_ARM, x + 0, y + 8, 3, 10);
		part(ctx, BodyPart.LEFT_ARM, x + 13, y + 8, 3, 10);
		part(ctx, BodyPart.RIGHT_LEG, x + 5, y + 20, 2, 12);
		part(ctx, BodyPart.LEFT_LEG, x + 9, y + 20, 2, 12);
		if (bleeding && player.age / 10 % 2 == 0) {
			ctx.drawTextWithShadow(client.textRenderer, Text.translatable("hud.evolvedwarfare.health.bleeding"), x + 20, y + 20, 0xFFE03030);
		}
	}
}
