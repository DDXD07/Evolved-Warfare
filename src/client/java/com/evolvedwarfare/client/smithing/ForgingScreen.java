package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgeMinigame;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.Smithing;
import com.evolvedwarfare.smithing.SmithingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

/**
 * El afilado en el yunque de una pieza en bruto: si es de hierro y llevas diamantes, primero eliges (dos fichas: tal cual o
 * con filo de diamante); luego el minijuego del martillo: golpea (clic o espacio) cuando pase por lo naranja (en el centro,
 * perfecto), antes de que se enfríe. Al final, las estrellas de la calidad (el molde, el vertido y el afilado).
 */
public class ForgingScreen extends Screen {
	private static final Identifier STARS = EvolvedWarfare.id("textures/gui/fish_stars.png");
	private final SmithingPayloads.Open open;
	private final ForgeMaterial material;
	private final ForgedPart part;
	private boolean diamond;
	private ForgeMinigame game;
	private boolean sent;
	private int endTicks = -1;
	private int flash;
	private float lastScore = -1.0F;
	private int dinged;

	public ForgingScreen(SmithingPayloads.Open open) {
		super(Text.translatable("screen.evolvedwarfare.forging"));
		this.open = open;
		this.material = ForgeMaterial.values()[MathHelper.clamp(open.material(), 0, ForgeMaterial.values().length - 1)];
		this.part = ForgedPart.values()[MathHelper.clamp(open.part(), 0, ForgedPart.values().length - 1)];
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private boolean canDiamond() {
		return material == ForgeMaterial.IRON && open.diamonds() >= part.cost && part.result(ForgeMaterial.DIAMOND) != null;
	}

	@Override
	protected void init() {
		if (game == null && !canDiamond()) {
			start(false);
		}
	}

	private void start(boolean withDiamond) {
		diamond = withDiamond;
		ForgeMaterial m = diamond ? ForgeMaterial.DIAMOND : material;
		game = new ForgeMinigame(part.strikes, open.level(), m.speed, Random.create(open.seed()));
	}

	@Override
	public void tick() {
		if (game == null) {
			return;
		}
		if (endTicks >= 0) {
			if (++endTicks > 40) {
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
		lastScore = s;
		com.evolvedwarfare.client.anim.ActivityPoses.strike();
		flash = 8;
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
			ClientPlayNetworking.send(new SmithingPayloads.Result(open.session(), game == null ? -1 : part.ordinal(), diamond,
					game == null ? 0.0F : game.score()));
		}
	}

	@Override
	public void close() {
		send();
		super.close();
	}

	private int tileX(int i) {
		return width / 2 + (i == 0 ? -70 : 10);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (game != null) {
			hit();
			return true;
		}
		int y = height / 2 - 30;
		for (int i = 0; i < 2; i++) {
			if (mouseX >= tileX(i) && mouseX < tileX(i) + 60 && mouseY >= y && mouseY < y + 70) {
				start(i == 1);
				MinecraftClient.getInstance().player.playSound(SoundEvents.BLOCK_ANVIL_PLACE, 0.4F, 1.4F);
				return true;
			}
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
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// afilando, sin oscurecer ni difuminar: se ve el yunque y las manos con el martillo
		if (game == null) {
			super.renderBackground(context, mouseX, mouseY, delta);
		}
	}

	private void big(DrawContext ctx, ItemStack stack, float cx, float cy, float scale, float angle) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy, 100);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
		ctx.getMatrices().scale(scale, scale, scale);
		ctx.drawItem(stack, -8, -8);
		ctx.getMatrices().pop();
	}

	private void stars(DrawContext ctx, int cx, int y, int n, long since) {
		for (int i = 0; i < 4; i++) {
			float pop = MathHelper.clamp((since - i * 200L) / 160.0F, 0.0F, 1.0F);
			boolean lit = i < n && pop > 0.0F;
			if (lit && i >= dinged) {
				dinged = i + 1;
				MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 0.8F + i * 0.15F);
			}
			float sc = 1.8F + (lit ? 4.0F * pop * (1.0F - pop) : 0.0F);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(cx - 30 + i * 20, y, 200);
			ctx.getMatrices().scale(sc, sc, 1);
			if (lit) {
				ctx.drawTexture(STARS, -4, -4, i * 7, 0, 7, 7, 28, 7);
			} else {
				ctx.setShaderColor(0.25F, 0.22F, 0.2F, 0.8F);
				ctx.drawTexture(STARS, -4, -4, 0, 0, 7, 7, 28, 7);
				ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
			ctx.getMatrices().pop();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		long t = Util.getMeasuringTimeMs();
		int cx = width / 2;
		if (game == null) {
			// elegir: tal cual o con filo de diamante (dos fichas con la pieza girando)
			int y = height / 2 - 30;
			for (int i = 0; i < 2; i++) {
				int x = tileX(i);
				boolean over = mouseX >= x && mouseX < x + 60 && mouseY >= y && mouseY < y + 70;
				context.fill(x - 2, y - 2, x + 62, y + 72, over ? 0xFFFFC060 : 0xFF5A4636);
				context.fill(x, y, x + 60, y + 70, i == 1 ? 0xFF1E3A40 : 0xFF2A2420);
				ForgeMaterial m = i == 1 ? ForgeMaterial.DIAMOND : material;
				big(context, Smithing.part(part, m, 3), x + 30, y + 30, over ? 3.0F : 2.5F, MathHelper.sin(t / 400.0F + i) * 8.0F);
				if (i == 1) {
					for (int k = 0; k < part.cost; k++) {
						context.drawItem(new ItemStack(net.minecraft.item.Items.DIAMOND), x + 30 - part.cost * 6 + k * 12 - 4, y + 52);
					}
				}
			}
			return;
		}
		int w = 220;
		int x = cx - w / 2;
		int y = height / 2 + 20;
		context.fill(x - 8, y - 34, x + w + 8, y + 40, 0xC0201A18);
		// la pieza en bruto, que brilla al golpear
		float shake = flash > 0 ? MathHelper.sin(flash * 2.0F) * 3.0F : 0.0F;
		big(context, Smithing.part(part, diamond ? ForgeMaterial.DIAMOND : material, 0), cx, y - 18, 1.8F, shake);
		// la barra con la zona buena (naranja) y la perfecta (amarilla)
		context.fill(x, y, x + w, y + 14, 0xFF3A3632);
		int tc = x + (int) (game.target * w);
		int hw = (int) (game.halfWidth * w);
		int pw = (int) (game.halfWidth * 0.35F * w);
		int ow = (int) (game.halfWidth * 1.8F * w);
		context.fill(Math.max(x, tc - ow), y + 1, Math.min(x + w, tc + ow), y + 13, 0xFF6A4020);
		context.fill(Math.max(x, tc - hw), y + 1, Math.min(x + w, tc + hw), y + 13, 0xFFE07020);
		context.fill(Math.max(x, tc - pw), y + 1, Math.min(x + w, tc + pw), y + 13, 0xFFFFE060);
		float h = MathHelper.lerp(MathHelper.clamp(delta, 0, 1), game.prevHammer, game.hammer);
		int hx = x + (int) (h * w);
		context.fill(hx - 1, y - 6, hx + 2, y + 20, flash > 0 ? 0xFFFFFFFF : 0xFFC8C8D0);
		context.fill(hx - 4, y - 9, hx + 5, y - 5, 0xFF8A8A96);
		// chispas del último golpe
		if (flash > 0 && lastScore > 0) {
			java.util.Random r = new java.util.Random(game.struck * 31L);
			for (int i = 0; i < (int) (lastScore * 14); i++) {
				double a = r.nextDouble() * Math.PI;
				double v = (8 - flash) * (2 + r.nextDouble() * 3);
				int sx = (int) (hx + Math.cos(a) * v);
				int sy = (int) (y - Math.sin(a) * v);
				context.fill(sx, sy, sx + 1, sy + 1, lastScore >= 1 ? 0xFFFFF0A0 : 0xFFFF9030);
			}
		}
		// el calor
		int heat = (int) (game.heat * w);
		int hc = game.heat > 0.5F ? 0xFFFF8020 : game.heat > 0.25F ? 0xFFD04010 : 0xFF802010;
		context.fill(x, y + 20, x + w, y + 24, 0xFF202020);
		context.fill(x, y + 20, x + heat, y + 24, hc);
		// los golpes
		for (int i = 0; i < game.strikes; i++) {
			int bx = x + w - (game.strikes - i) * 10;
			int col = i < game.struck ? (game.scores[i] >= 1 ? 0xFFFFE060 : game.scores[i] >= 0.6F ? 0xFF60D060 : game.scores[i] > 0 ? 0xFFC08040 : 0xFFD03030)
					: 0xFF505050;
			context.fill(bx, y + 28, bx + 7, y + 35, col);
		}
		if (endTicks >= 0) {
			stars(context, cx, y - 46, Smithing.finalQuality(open.cast(), game.score()), endTicks * 50L);
		}
	}
}
