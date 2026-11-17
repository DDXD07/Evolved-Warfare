package com.evolvedwarfare.client.health;

import com.evolvedwarfare.health.BodyPart;
import com.evolvedwarfare.network.WoundsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Las heridas en el cliente (fase 92): un muñeco a la derecha de la barra de objetos con cada parte del cuerpo de su color
 * (verde sana, amarilla tocada, roja muy herida, negra sin vida), los huesos rotos con una raya y los entablillados con un
 * marco; y la gota que avisa de que se sangra. Solo se ve si hay algo herido. Con la tecla de la salud (H) se abre la
 * pantalla con todo el cuerpo ({@link HealthScreen}).
 */
public final class ClientHealth {
	private static float[] parts = new float[BodyPart.values().length];
	private static int[] splintLeft = new int[BodyPart.values().length];
	private static int broken, bleedSeconds, hearts = 10, maxHearts = 10, danger;
	private static boolean enabled;
	public static KeyBinding open;

	static {
		java.util.Arrays.fill(parts, 1.0F);
	}

	private ClientHealth() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static float fraction(BodyPart part) {
		return parts[part.ordinal()];
	}

	public static boolean broken(BodyPart part) {
		return (broken & part.bit()) != 0;
	}

	public static int splintLeft(BodyPart part) {
		return splintLeft[part.ordinal()];
	}

	public static boolean splinted(BodyPart part) {
		return broken(part) && splintLeft[part.ordinal()] > 0;
	}

	public static int bleedSeconds() {
		return bleedSeconds;
	}

	public static int hearts() {
		return hearts;
	}

	public static int maxHearts() {
		return maxHearts;
	}

	/** El nivel de peligro de donde está (fase 95). */
	public static int danger() {
		return danger;
	}

	public static boolean legBroken(boolean right) {
		return enabled && broken(right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG);
	}

	public static void register() {
		open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.evolvedwarfare.health", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
				"key.categories.evolvedwarfare"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new HealthScreen());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WoundsPayload.ID, (payload, context) -> {
			int n = BodyPart.values().length;
			float[] p = new float[n];
			int[] s = new int[n];
			for (int k = 0; k < n; k++) {
				p[k] = k < payload.parts().size() ? payload.parts().get(k) : 1.0F;
				s[k] = k < payload.splintLeft().size() ? payload.splintLeft().get(k) : 0;
			}
			parts = p;
			splintLeft = s;
			broken = payload.broken();
			bleedSeconds = payload.bleedSeconds();
			hearts = payload.hearts() & 0xFF;
			maxHearts = payload.hearts() >> 8 & 0xFF;
			danger = payload.hearts() >> 16;
			enabled = payload.enabled();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			parts = new float[BodyPart.values().length];
			java.util.Arrays.fill(parts, 1.0F);
			splintLeft = new int[BodyPart.values().length];
			broken = bleedSeconds = 0;
			hearts = maxHearts = 10;
			enabled = false;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	private static boolean hurt() {
		if (broken != 0 || bleedSeconds > 0) {
			return true;
		}
		for (float f : parts) {
			if (f < 0.999F) {
				return true;
			}
		}
		return false;
	}

	/** El color de una parte según la vida que le queda (verde → amarillo → rojo; negro sin vida). */
	public static int color(float f) {
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

	/** Dónde va cada parte en el muñeco (en unidades de 1 = el muñeco pequeño): x, y, ancho, alto. */
	public static int[] box(BodyPart part) {
		return switch (part) {
			case HEAD -> new int[] {5, 0, 6, 6};
			case TORSO -> new int[] {5, 8, 6, 10};
			case RIGHT_ARM -> new int[] {0, 8, 3, 10};
			case LEFT_ARM -> new int[] {13, 8, 3, 10};
			case RIGHT_LEG -> new int[] {5, 20, 2, 12};
			case LEFT_LEG -> new int[] {9, 20, 2, 12};
		};
	}

	/** El muñeco entero, a {@code scale} píxeles por unidad (16 × 32 unidades), con la esquina en x, y. */
	public static void drawDoll(DrawContext ctx, int x, int y, int scale, BodyPart highlight) {
		for (BodyPart part : BodyPart.values()) {
			int[] b = box(part);
			int px = x + b[0] * scale, py = y + b[1] * scale, w = b[2] * scale, h = b[3] * scale;
			int border = Math.max(1, scale / 2);
			int edge = part == highlight ? 0xFFFFFFFF : splinted(part) ? 0xFFC8A060 : 0xFF000000;
			ctx.fill(px - border, py - border, px + w + border, py + h + border, edge);
			ctx.fill(px, py, px + w, py + h, color(fraction(part)));
			if (scale > 1) {
				// la vida que le falta, oscurecida desde arriba
				int missing = Math.round(h * (1.0F - fraction(part)));
				ctx.fill(px, py, px + w, py + missing, 0x55000000);
			}
			if (broken(part)) {
				// una raya en zigzag: el hueso partido
				int mid = py + h / 2;
				int t = Math.max(1, scale / 2);
				ctx.fill(px, mid - t, px + w, mid, 0xFFFFFFFF);
				ctx.fill(px, mid, px + w / 2, mid + t, 0xFFFFFFFF);
			}
		}
	}

	private static void render(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (!enabled || player == null || client.options.hudHidden || client.interactionManager == null
				|| !client.interactionManager.hasStatusBars() || !hurt() || client.currentScreen instanceof HealthScreen) {
			return;
		}
		// a la derecha de la barra de objetos (donde no tapa el chat)
		int x = ctx.getScaledWindowWidth() / 2 + 91 + 10;
		int y = ctx.getScaledWindowHeight() - 34;
		drawDoll(ctx, x, y, 1, null);
		if (bleedSeconds > 0 && player.age / 10 % 2 == 0) {
			ctx.drawTextWithShadow(client.textRenderer, Text.translatable("hud.evolvedwarfare.health.bleeding"), x + 20, y + 20, 0xFFE03030);
		}
	}
}
