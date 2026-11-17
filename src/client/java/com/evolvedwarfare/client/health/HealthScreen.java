package com.evolvedwarfare.client.health;

import com.evolvedwarfare.health.BodyPart;
import com.evolvedwarfare.registry.ModEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * La pantalla de la salud (tecla H): el personaje, un muñeco grande con cada parte de su color, la lista de las partes
 * (vida, hueso roto, entablillado y lo que le queda), cómo estás (corazones, sangre, infección, venenos, desinfectado,
 * regeneración) y qué hacer. No pausa el juego: se ve cambiar en directo. Al pasar el ratón por una parte (en el muñeco o
 * en la lista) se resalta en los dos sitios.
 */
public class HealthScreen extends Screen {
	private static final int W = 384, H = 222;
	private static final int DOLL_SCALE = 4;

	public HealthScreen() {
		super(Text.translatable("screen.evolvedwarfare.health.title"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (ClientHealth.open != null && ClientHealth.open.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return Math.max(2, (height - H) / 2);
	}

	private int dollX() {
		return left() + 92;
	}

	private int dollY() {
		return top() + 26;
	}

	private int listX() {
		return left() + 172;
	}

	private int rowY(BodyPart part) {
		return top() + 22 + part.ordinal() * 20;
	}

	/** La parte que hay bajo el ratón (en el muñeco o en la lista). */
	@Nullable
	private BodyPart hovered(int mx, int my) {
		for (BodyPart part : BodyPart.values()) {
			int[] b = ClientHealth.box(part);
			int px = dollX() + b[0] * DOLL_SCALE, py = dollY() + b[1] * DOLL_SCALE;
			if (mx >= px && mx < px + b[2] * DOLL_SCALE && my >= py && my < py + b[3] * DOLL_SCALE) {
				return part;
			}
			int ry = rowY(part);
			if (mx >= listX() && mx < left() + W - 6 && my >= ry - 1 && my < ry + 19) {
				return part;
			}
		}
		return null;
	}

	private static String time(int ticks) {
		int s = Math.max(0, ticks / 20);
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	private static String num(float v) {
		return v == Math.round(v) ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		PlayerEntity player = client == null ? null : client.player;
		if (player == null) {
			return;
		}
		int x0 = left(), y0 = top();
		// el marco
		ctx.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF6B5433);
		ctx.fill(x0, y0, x0 + W, y0 + H, 0xE0181410);
		ctx.drawCenteredTextWithShadow(textRenderer, title, x0 + W / 2, y0 + 7, 0xFFE8D8B0);
		if (!ClientHealth.enabled()) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.health.disabled"), x0 + W / 2, y0 + H / 2,
					0xFFAAAAAA);
			return;
		}
		BodyPart hover = hovered(mouseX, mouseY);

		// el personaje
		ctx.fill(x0 + 8, y0 + 22, x0 + 84, y0 + 152, 0x40000000);
		InventoryScreen.drawEntity(ctx, x0 + 8, y0 + 22, x0 + 84, y0 + 152, 52, 0.0625F, mouseX, mouseY, player);

		// el muñeco
		ClientHealth.drawDoll(ctx, dollX(), dollY(), DOLL_SCALE, hover);

		// la lista de las partes
		for (BodyPart part : BodyPart.values()) {
			int ry = rowY(part);
			int lx = listX();
			if (part == hover) {
				ctx.fill(lx - 3, ry - 2, x0 + W - 5, ry + 18, 0x30FFFFFF);
			}
			float f = ClientHealth.fraction(part);
			ctx.drawTextWithShadow(textRenderer, Text.translatable("health.evolvedwarfare.part_title." + part.name().toLowerCase(Locale.ROOT)), lx, ry,
					0xFFFFFFFF);
			// la barra de vida
			int bx = lx, by = ry + 11, bw = 70;
			ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xFF000000);
			ctx.fill(bx, by, bx + Math.round(bw * f), by + 4, ClientHealth.color(f));
			ctx.drawTextWithShadow(textRenderer, num(f * part.max) + " / " + num(part.max), bx + bw + 5, by - 2, 0xFFBBBBBB);
			// el estado
			MutableText state;
			if (ClientHealth.splinted(part)) {
				state = Text.translatable("screen.evolvedwarfare.health.state.splinted", time(ClientHealth.splintLeft(part) * 20))
						.formatted(Formatting.GOLD);
			} else if (ClientHealth.broken(part)) {
				state = Text.translatable("screen.evolvedwarfare.health.state.broken").formatted(Formatting.RED, Formatting.BOLD);
			} else if (f <= 0.0F) {
				state = Text.translatable("screen.evolvedwarfare.health.state.destroyed").formatted(Formatting.DARK_RED);
			} else if (f < 0.25F) {
				state = Text.translatable("screen.evolvedwarfare.health.state.critical").formatted(Formatting.RED);
			} else if (f < 0.6F) {
				state = Text.translatable("screen.evolvedwarfare.health.state.wounded").formatted(Formatting.GOLD);
			} else if (f < 0.999F) {
				state = Text.translatable("screen.evolvedwarfare.health.state.bruised").formatted(Formatting.YELLOW);
			} else {
				state = Text.translatable("screen.evolvedwarfare.health.state.healthy").formatted(Formatting.GREEN);
			}
			int sw = textRenderer.getWidth(state);
			ctx.drawTextWithShadow(textRenderer, state, x0 + W - 8 - sw, ry, 0xFFFFFFFF);
		}

		// cómo estás
		int sy = y0 + 160;
		ctx.fill(x0 + 6, sy - 4, x0 + W - 6, sy - 3, 0xFF6B5433);
		List<Text> status = new ArrayList<>();
		status.add(Text.translatable("screen.evolvedwarfare.health.hearts", ClientHealth.hearts(), ClientHealth.maxHearts())
				.formatted(Formatting.LIGHT_PURPLE));
		status.add(Text.translatable("screen.evolvedwarfare.health.danger", ClientHealth.danger())
				.formatted(ClientHealth.danger() >= 10 ? Formatting.RED : ClientHealth.danger() >= 5 ? Formatting.GOLD : Formatting.GRAY));
		if (ClientStamina.enabled()) {
			status.add(Text.translatable(ClientStamina.exhausted() ? "screen.evolvedwarfare.health.stamina_exhausted" : "screen.evolvedwarfare.health.stamina",
					Math.round(ClientStamina.value())).formatted(ClientStamina.exhausted() ? Formatting.RED : Formatting.YELLOW));
		}
		if (ClientHealth.bleedSeconds() > 0) {
			status.add(Text.translatable("screen.evolvedwarfare.health.bleeding", ClientHealth.bleedSeconds()).formatted(Formatting.RED));
		}
		effect(status, player, ModEffects.INFECTION, "infection", Formatting.DARK_GREEN, true);
		effect(status, player, ModEffects.VENOM, "venom", Formatting.GREEN, true);
		effect(status, player, StatusEffects.POISON, "poison", Formatting.GREEN, false);
		effect(status, player, ModEffects.PARASITES, "parasites", Formatting.DARK_GREEN, false);
		effect(status, player, ModEffects.DISINFECTED, "disinfected", Formatting.AQUA, false);
		boolean regen = ClientHealth.bleedSeconds() <= 0 && ClientHealth.fraction(BodyPart.TORSO) > 0.0F;
		status.add(Text.translatable(regen ? "screen.evolvedwarfare.health.regen.slow" : "screen.evolvedwarfare.health.regen.stopped")
				.formatted(regen ? Formatting.GRAY : Formatting.RED));
		ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.health.status").formatted(Formatting.UNDERLINE), x0 + 10, sy,
				0xFFE8D8B0);
		for (int k = 0; k < status.size() && k < 5; k++) {
			ctx.drawTextWithShadow(textRenderer, status.get(k), x0 + 10, sy + 11 + k * 10, 0xFFFFFFFF);
		}
		for (int k = 5; k < status.size(); k++) {
			ctx.drawTextWithShadow(textRenderer, status.get(k), x0 + 100, sy + 11 + (k - 5) * 10, 0xFFFFFFFF);
		}

		// qué hacer
		int ax = x0 + 196;
		ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.evolvedwarfare.health.advice").formatted(Formatting.UNDERLINE), ax, sy,
				0xFFE8D8B0);
		List<Text> advice = advice(player);
		int line = 0;
		for (Text a : advice) {
			for (var wrapped : textRenderer.wrapLines(a, W - (ax - x0) - 10)) {
				if (line < 5) {
					ctx.drawTextWithShadow(textRenderer, wrapped, ax, sy + 11 + line * 10, 0xFFDDDDDD);
				}
				line++;
			}
		}
	}

	private static void effect(List<Text> out, PlayerEntity player, RegistryEntry<StatusEffect> effect, String key, Formatting color,
			boolean graded) {
		StatusEffectInstance have = player.getStatusEffect(effect);
		if (have == null) {
			return;
		}
		String t = have.isInfinite() ? "∞" : time(have.getDuration());
		out.add(graded ? Text.translatable("screen.evolvedwarfare.health.effect_graded." + key, have.getAmplifier() + 1, t).formatted(color)
				: Text.translatable("screen.evolvedwarfare.health.effect." + key, t).formatted(color));
	}

	private static List<Text> advice(PlayerEntity player) {
		List<Text> out = new ArrayList<>();
		if (ClientHealth.bleedSeconds() > 0) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.bandage")));
		}
		for (BodyPart part : BodyPart.values()) {
			if (ClientHealth.broken(part) && !ClientHealth.splinted(part)) {
				out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.splint",
						Text.translatable(part.translationKey()))));
			}
		}
		if (player.hasStatusEffect(ModEffects.INFECTION)) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.infection")));
		}
		if (player.hasStatusEffect(ModEffects.VENOM) || player.hasStatusEffect(StatusEffects.POISON)) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.venom")));
		}
		if (player.hasStatusEffect(ModEffects.PARASITES)) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.parasites")));
		}
		if (ClientStamina.exhausted()) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.rest_stamina")));
		}
		boolean hurt = false;
		for (BodyPart part : BodyPart.values()) {
			hurt |= !ClientHealth.broken(part) && ClientHealth.fraction(part) < 0.999F;
		}
		if (out.isEmpty() && hurt) {
			out.add(Text.literal("• ").append(Text.translatable("screen.evolvedwarfare.health.advice.rest")));
		}
		if (out.isEmpty()) {
			out.add(Text.translatable("screen.evolvedwarfare.health.advice.fine").formatted(Formatting.GREEN));
		}
		return out;
	}
}
