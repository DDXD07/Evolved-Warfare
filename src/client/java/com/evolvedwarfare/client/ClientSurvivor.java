package com.evolvedwarfare.client;

import com.evolvedwarfare.network.SurvivorPayload;
import com.evolvedwarfare.skill.SkillData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * El nivel de superviviente en pantalla (fase 112, como en ARK): una placa con el nivel y una barra fina a la izquierda
 * de la barra de experiencia, y al ganar experiencia un "+N XP" que sube y se desvanece a su lado (con el porqué si es
 * algo especial: un bioma nuevo, lo primero que fabricas, un élite, el amanecer).
 */
public final class ClientSurvivor {
	private record Pop(Text text, int color, long since) {
	}

	private static final List<Pop> POPS = new ArrayList<>();
	private static final long POP_MS = 2600L;
	/** La experiencia total la última vez (para ver cuánto ha subido). */
	private static float lastTotal = -1.0F;
	/** Lo que ya se ha avisado con su porqué (para no avisarlo dos veces cuando llegue en los datos). */
	private static float credited;
	private static float shown = -1.0F;

	private ClientSurvivor() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SurvivorPayload.ID, (payload, context) -> {
			credited += payload.amount();
			pop(reasonText(payload), color(payload.reason()));
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !ClientSkills.received()) {
				return;
			}
			float total = total(ClientSkills.data);
			if (lastTotal < 0.0F) {
				lastTotal = total;
				return;
			}
			float gained = total - lastTotal;
			lastTotal = total;
			if (gained <= 0.0F) {
				return;
			}
			float rest = gained - credited;
			credited = Math.max(0.0F, credited - gained);
			if (rest >= 0.1F) {
				pop(Text.translatable("hud.evolvedwarfare.survivor.xp", format(rest)), 0x9FE6F0);
			}
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context, tickCounter.getLastFrameDuration()));
	}

	private static void reset() {
		POPS.clear();
		lastTotal = -1.0F;
		credited = 0.0F;
		shown = -1.0F;
	}

	/** La experiencia de superviviente ganada en total (para comparar). */
	private static float total(SkillData data) {
		float sum = data.levelXp;
		for (int l = 1; l < data.level; l++) {
			sum += SkillData.levelXpToNext(l);
		}
		return sum;
	}

	private static String format(float xp) {
		return xp >= 10.0F || Math.abs(xp - Math.round(xp)) < 0.05F ? String.valueOf(Math.round(xp)) : String.format("%.1f", xp);
	}

	private static Text reasonText(SurvivorPayload p) {
		Text what = switch (p.reason()) {
			case "biome" -> Text.translatable("hud.evolvedwarfare.survivor.biome", Text.translatable(p.arg()));
			case "craft" -> Text.translatable("hud.evolvedwarfare.survivor.craft", Text.translatable(p.arg()));
			case "kill" -> Text.translatable("hud.evolvedwarfare.survivor.kill", Text.translatable(p.arg()));
			case "dawn" -> Text.translatable("hud.evolvedwarfare.survivor.dawn", p.arg());
			default -> Text.empty();
		};
		return Text.translatable("hud.evolvedwarfare.survivor.xp_why", format(p.amount()), what);
	}

	private static int color(String reason) {
		return switch (reason) {
			case "biome" -> 0x8CE68C;
			case "kill" -> 0xF0A050;
			case "dawn" -> 0xFFD75E;
			default -> 0xE6E6E6;
		};
	}

	private static void pop(Text text, int color) {
		POPS.add(new Pop(text, color, System.currentTimeMillis()));
		while (POPS.size() > 5) {
			POPS.remove(0);
		}
	}

	private static void render(DrawContext ctx, float frames) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || client.interactionManager == null || !client.interactionManager.hasStatusBars()) {
			return;
		}
		if (!ClientSkills.received()) {
			return;
		}
		SkillData data = ClientSkills.data;
		float target = data.maxLevel() ? 1.0F : MathHelper.clamp(data.levelXp / SkillData.levelXpToNext(data.level), 0.0F, 1.0F);
		if (shown < 0.0F || target < shown - 0.5F) {
			shown = target;
		}
		shown += (target - shown) * Math.min(1.0F, 0.2F * frames);
		// la placa: a la izquierda de la barra de experiencia, encima de la mano izquierda
		int w = 24, h = 13;
		int x = ctx.getScaledWindowWidth() / 2 - 91 - 4 - w;
		int y = ctx.getScaledWindowHeight() - 38;
		boolean max = data.maxLevel();
		int edge = max ? 0xFFE0B040 : 0xFF2A6470;
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0F12);
		ctx.fill(x, y, x + w, y + h, 0xD0141C22);
		ctx.fill(x, y, x + w, y + 1, edge);
		Text level = Text.literal(String.valueOf(data.level));
		ctx.drawCenteredTextWithShadow(client.textRenderer, level, x + w / 2, y + 2, max ? 0xFFFFD75E : 0xFFFFFFFF);
		// la barra fina de abajo
		int by = y + h - 2;
		ctx.fill(x + 1, by, x + w - 1, by + 1, 0xFF1E3A44);
		int fill = Math.round((w - 2) * shown);
		if (fill > 0) {
			ctx.fill(x + 1, by, x + 1 + fill, by + 1, max ? 0xFFFFD75E : 0xFF3CC8E6);
		}
		// los "+N XP": suben desde la placa y se desvanecen
		long now = System.currentTimeMillis();
		POPS.removeIf(p -> now - p.since() > POP_MS);
		int line = 0;
		for (int i = POPS.size() - 1; i >= 0; i--) {
			Pop p = POPS.get(i);
			float age = (now - p.since()) / (float) POP_MS;
			float alpha = age < 0.08F ? age / 0.08F : age > 0.7F ? (1.0F - age) / 0.3F : 1.0F;
			int a = MathHelper.clamp((int) (alpha * 255), 8, 255);
			int ty = y + 2 - line * 10 - Math.round(age * 8);
			int tw = client.textRenderer.getWidth(p.text());
			ctx.drawTextWithShadow(client.textRenderer, p.text(), x - 4 - tw, ty, (a << 24) | (p.color() & 0xFFFFFF));
			line++;
		}
	}
}
