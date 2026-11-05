package com.evolvedwarfare.client.fishing;

import com.evolvedwarfare.fishing.FishMinigame;
import com.evolvedwarfare.fishing.FishSpecies;
import com.evolvedwarfare.fishing.FishingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

/**
 * El minijuego de pesca en pantalla: la barra vertical con el agua, el pez (su icono) que se mueve, la zona verde que sube
 * mientras mantienes pulsado (clic o espacio) y baja al soltar, la barra de captura al lado y, a veces, un cofre.
 */
public class FishingScreen extends Screen {
	private static final int BAR_H = 150;
	private static final int BAR_W = 18;
	private final FishingPayloads.Start start;
	private final FishMinigame game;
	private boolean sent;
	private int endTicks = -1;

	public FishingScreen(FishingPayloads.Start start) {
		super(Text.translatable("screen.evolvedwarfare.fishing"));
		this.start = start;
		FishSpecies.Behavior[] all = FishSpecies.Behavior.values();
		this.game = new FishMinigame(start.difficulty(), all[MathHelper.clamp(start.behavior(), 0, all.length - 1)], start.zoneSize(), start.treasure(),
				Random.create(start.seed()));
	}

	public int session() {
		return start.session();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private boolean holding() {
		long w = MinecraftClient.getInstance().getWindow().getHandle();
		return GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS || InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_SPACE);
	}

	@Override
	public void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (endTicks >= 0) {
			if (++endTicks > 12) {
				close();
			}
			return;
		}
		boolean wasTreasure = game.treasureCaught;
		boolean wasInside = game.inside();
		boolean hold = holding();
		game.tick(hold);
		com.evolvedwarfare.client.anim.ActivityPoses.reeling(hold, Math.min(1.0F, Math.abs(game.fishVel) * 25.0F));
		if (client.player != null) {
			if (game.treasureCaught && !wasTreasure) {
				client.player.playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.6F, 1.4F);
			}
			if (game.ticks % 4 == 0) {
				client.player.playSound(SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 0.15F, game.inside() ? 1.6F : 0.9F);
			}
			if (wasInside != game.inside() && !game.inside()) {
				client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.4F, 0.6F);
			}
		}
		if (game.done()) {
			send();
			endTicks = 0;
			if (client.player != null) {
				client.player.playSound(game.caught() ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.5F,
						game.caught() ? 1.6F : 0.6F);
			}
		}
	}

	private void send() {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new FishingPayloads.Result(start.session(), game.caught(), game.caught() && game.perfect, game.treasureCaught));
		}
	}

	/** El servidor la ha cerrado: no se manda nada. */
	public void abort() {
		sent = true;
		close();
	}

	@Override
	public void close() {
		send();
		super.close();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = width / 2 + 60;
		int y = height / 2 - BAR_H / 2;
		float t = MathHelper.clamp(delta, 0.0F, 1.0F);
		// el marco de madera
		context.fill(x - 6, y - 6, x + BAR_W + 22, y + BAR_H + 6, 0xFF5A3A1E);
		context.fill(x - 4, y - 4, x + BAR_W + 20, y + BAR_H + 4, 0xFF8A5A2E);
		// el agua
		context.fill(x, y, x + BAR_W, y + BAR_H, 0xFF1E4E8C);
		context.fill(x + 1, y + 1, x + BAR_W - 1, y + BAR_H - 1, 0xFF2A64AA);
		// la zona verde
		float zone = MathHelper.lerp(t, game.prevZone, game.zone);
		int zTop = y + BAR_H - (int) ((zone + game.zoneSize) * BAR_H);
		int zBot = y + BAR_H - (int) (zone * BAR_H);
		int green = game.inside() ? 0xC060E060 : 0xA040B040;
		context.fill(x + 1, Math.max(y, zTop), x + BAR_W - 1, Math.min(y + BAR_H, zBot), green);
		context.fill(x + 1, Math.max(y, zTop), x + BAR_W - 1, Math.max(y, zTop) + 1, 0xFFB0FFB0);
		// el cofre
		if (game.treasureVisible) {
			int ty = y + BAR_H - (int) (game.treasure * BAR_H) - 8;
			context.getMatrices().push();
			context.getMatrices().translate(x + 1, ty, 0);
			context.getMatrices().scale(1.0F, 1.0F, 1.0F);
			context.drawItem(new ItemStack(Items.CHEST), 0, 0);
			context.getMatrices().pop();
			int w = (int) (game.treasureProgress * 16);
			context.fill(x + 1, ty + 16, x + 17, ty + 18, 0xFF202020);
			context.fill(x + 1, ty + 16, x + 1 + w, ty + 18, 0xFFFFD040);
		}
		// el pez
		float fish = MathHelper.lerp(t, game.prevFish, game.fish);
		int fy = y + BAR_H - (int) (fish * BAR_H) - 8;
		int shake = game.difficulty > 60 && game.ticks % 3 == 0 ? (int) (Math.random() * 2) : 0;
		context.drawItem(start.fish(), x + 1 + shake, fy);
		// la barra de captura
		int px = x + BAR_W + 6;
		context.fill(px, y, px + 8, y + BAR_H, 0xFF2A1A0E);
		int filled = (int) (game.progress * (BAR_H - 2));
		int color = game.progress < 0.3F ? 0xFFE04030 : game.progress < 0.6F ? 0xFFF0C040 : 0xFF50D050;
		context.fill(px + 1, y + BAR_H - 1 - filled, px + 7, y + BAR_H - 1, color);
		// textos
		var font = textRenderer;
		context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.fishing.level", start.level()), x + BAR_W / 2 + 8,
				y + BAR_H + 10, 0xA0E0FF);
		Text hint = Text.translatable(endTicks >= 0 ? (game.caught() ? "screen.evolvedwarfare.fishing.caught" : "screen.evolvedwarfare.fishing.lost")
				: "screen.evolvedwarfare.fishing.hint");
		context.drawCenteredTextWithShadow(font, hint, x + BAR_W / 2 + 8, y - 18, endTicks >= 0 ? (game.caught() ? 0x60FF60 : 0xFF6060) : 0xFFFFFF);
		if (game.perfect && game.ticks > 20 && endTicks < 0) {
			context.drawCenteredTextWithShadow(font, Text.translatable("screen.evolvedwarfare.fishing.perfect"), x + BAR_W / 2 + 8, y + BAR_H + 22,
					0xFFD040);
		}
	}
}
