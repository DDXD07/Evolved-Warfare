package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgeMinigame;
import com.evolvedwarfare.smithing.ForgeQuality;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.Smithing;
import com.evolvedwarfare.smithing.SmithingPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

/**
 * La forja en el yunque: primero eliges la pieza (lo que cuesta cada una en lingotes al rojo; con hierro y diamantes, con
 * filo de diamante), y luego el minijuego: golpea cuando el martillo pase por lo naranja (en el centro, perfecto), antes de
 * que se enfríe el lingote.
 */
public class ForgingScreen extends Screen {
	private final SmithingPayloads.Open open;
	private final ForgeMaterial material;
	private boolean diamond;
	private ForgedPart chosen;
	private ForgeMinigame game;
	private boolean sent;
	private int endTicks = -1;
	private final List<Text> feedback = new ArrayList<>();
	private int flash;

	public ForgingScreen(SmithingPayloads.Open open) {
		super(Text.translatable("screen.evolvedwarfare.forging"));
		this.open = open;
		this.material = ForgeMaterial.values()[MathHelper.clamp(open.material(), 0, ForgeMaterial.values().length - 1)];
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		if (chosen != null) {
			return;
		}
		clearChildren();
		List<ForgedPart> parts = new ArrayList<>();
		for (ForgedPart p : ForgedPart.values()) {
			if (p.result(diamond ? ForgeMaterial.DIAMOND : material) != null) {
				parts.add(p);
			}
		}
		int cols = 2;
		int w = 150;
		int x0 = width / 2 - w - 4;
		int y0 = height / 2 - 70;
		for (int i = 0; i < parts.size(); i++) {
			ForgedPart p = parts.get(i);
			boolean afford = open.ingots() >= p.cost && (!diamond || open.diamonds() >= p.cost);
			ButtonWidget b = ButtonWidget.builder(Text.translatable("screen.evolvedwarfare.forging.cost", Text.translatable("part.evolvedwarfare." + p.id), p.cost),
					btn -> start(p)).dimensions(x0 + (i % cols) * (w + 8), y0 + (i / cols) * 22, w, 20).build();
			b.active = afford || (client != null && client.player != null && client.player.isCreative());
			addDrawableChild(b);
		}
		if (material == ForgeMaterial.IRON) {
			int y = y0 + ((parts.size() + 1) / cols) * 22 + 6;
			addDrawableChild(ButtonWidget.builder(Text.literal(diamond ? "☑ " : "☐ ").append(Text.translatable("screen.evolvedwarfare.forging.diamond",
					open.diamonds())), btn -> {
						diamond = !diamond;
						init();
					}).dimensions(width / 2 - 110, y, 220, 20).build());
		}
	}

	private void start(ForgedPart p) {
		chosen = p;
		clearChildren();
		ForgeMaterial m = diamond ? ForgeMaterial.DIAMOND : material;
		game = new ForgeMinigame(p.strikes, open.level(), m.speed, Random.create(open.seed()));
	}

	@Override
	public void tick() {
		if (game == null) {
			return;
		}
		if (endTicks >= 0) {
			if (++endTicks > 30) {
				close();
			}
			return;
		}
		game.tick();
		if (flash > 0) {
			flash--;
		}
		if (game.done()) {
			finish();
		}
	}

	private void hit() {
		if (game == null || game.done() || endTicks >= 0) {
			return;
		}
		float s = game.strike();
		feedback.add(Text.translatable("screen.evolvedwarfare.forging." + (s >= ForgeMinigame.PERFECT ? "perfect" : s >= ForgeMinigame.GOOD ? "good"
				: s > 0 ? "poor" : "miss")));
		flash = 6;
		var player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(s > 0 ? SoundEvents.BLOCK_ANVIL_LAND : SoundEvents.BLOCK_ANVIL_PLACE, 0.35F, 0.8F + s * 0.6F + (float) Math.random() * 0.1F);
		}
		if (game.done()) {
			finish();
		}
	}

	private void finish() {
		if (endTicks < 0) {
			endTicks = 0;
			send();
		}
	}

	private void send() {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new SmithingPayloads.Result(open.session(), chosen == null ? -1 : chosen.ordinal(), diamond,
					game == null ? 0.0F : game.score()));
		}
	}

	@Override
	public void close() {
		send();
		super.close();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (game != null) {
			hit();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (game != null && keyCode == GLFW.GLFW_KEY_SPACE) {
			hit();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		var font = textRenderer;
		int cx = width / 2;
		if (game == null) {
			context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.forging.choose", open.ingots()), cx, height / 2 - 92, 0xFFD080);
			context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.forging.level", open.level()), cx, height / 2 - 80, 0xA0A0A0);
			return;
		}
		int w = 220;
		int x = cx - w / 2;
		int y = height / 2 - 10;
		// el yunque y el lingote
		context.fill(x - 8, y - 30, x + w + 8, y + 46, 0xC0201A18);
		context.drawCenteredTextWithShadow(font, Text.translatable("part.evolvedwarfare." + chosen.id), cx, y - 24, 0xFFFFFF);
		// la barra con la zona buena (naranja) y la perfecta (amarilla)
		context.fill(x, y, x + w, y + 14, 0xFF3A3632);
		int tc = x + (int) (game.target * w);
		int hw = (int) (game.halfWidth * w);
		int pw = (int) (game.halfWidth * 0.35F * w);
		int ow = (int) (game.halfWidth * 1.8F * w);
		context.fill(Math.max(x, tc - ow), y + 1, Math.min(x + w, tc + ow), y + 13, 0xFF6A4020);
		context.fill(Math.max(x, tc - hw), y + 1, Math.min(x + w, tc + hw), y + 13, 0xFFE07020);
		context.fill(Math.max(x, tc - pw), y + 1, Math.min(x + w, tc + pw), y + 13, 0xFFFFE060);
		// el martillo
		float h = MathHelper.lerp(MathHelper.clamp(delta, 0, 1), game.prevHammer, game.hammer);
		int hx = x + (int) (h * w);
		context.fill(hx - 1, y - 6, hx + 2, y + 20, flash > 0 ? 0xFFFFFFFF : 0xFFC8C8D0);
		context.fill(hx - 4, y - 9, hx + 5, y - 5, 0xFF8A8A96);
		// el calor
		int heat = (int) (game.heat * w);
		int hc = game.heat > 0.5F ? 0xFFFF8020 : game.heat > 0.25F ? 0xFFD04010 : 0xFF802010;
		context.fill(x, y + 20, x + w, y + 24, 0xFF202020);
		context.fill(x, y + 20, x + heat, y + 24, hc);
		context.drawTextWithShadow(font, Text.translatable("screen.evolvedwarfare.forging.heat"), x, y + 26, 0xC0A080);
		// los golpes
		for (int i = 0; i < game.strikes; i++) {
			int bx = x + w - (game.strikes - i) * 10;
			int col = i < game.struck ? (game.scores[i] >= 1 ? 0xFFFFE060 : game.scores[i] >= 0.6F ? 0xFF60D060 : game.scores[i] > 0 ? 0xFFC08040 : 0xFFD03030)
					: 0xFF505050;
			context.fill(bx, y + 27, bx + 7, y + 34, col);
		}
		if (!feedback.isEmpty() && flash > 0) {
			context.drawCenteredTextWithShadow(font, feedback.get(feedback.size() - 1), cx, y - 12, 0xFFE080);
		}
		if (endTicks >= 0) {
			int q = ForgeMinigame.quality(game.score());
			context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.forging.result",
					Text.translatable("forge_quality.evolvedwarfare." + ForgeQuality.key(q)).formatted(ForgeQuality.color(q))), cx, y + 38, 0xFFFFFF);
		} else {
			context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.forging.hint"), cx, y + 38, 0xD0D0D0);
		}
	}
}
