package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.backpack.BackpackItem;
import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La barra rápida de 5 (en supervivencia), con la mochila que llevas a la derecha y lo llena que va. */
@Mixin(InGameHud.class)
public abstract class InGameHudHotbarMixin {
	private static final Identifier HOTBAR = Identifier.ofVanilla("hud/hotbar");
	private static final Identifier SELECTION = Identifier.ofVanilla("hud/hotbar_selection");
	private static final Identifier OFFHAND_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");
	private static final Identifier OFFHAND_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");

	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$fiveSlots(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || !Backpacks.limited(player) || client.interactionManager == null || client.interactionManager.getCurrentGameMode() == null
				|| client.interactionManager.getCurrentGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
			return;
		}
		ci.cancel();
		int n = Backpacks.HOTBAR;
		int w = n * 20 + 2;
		int x = context.getScaledWindowWidth() / 2 - w / 2;
		int y = context.getScaledWindowHeight() - 22;
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, -90.0F);
		context.drawGuiTexture(HOTBAR, 182, 22, 0, 0, x, y, w - 1, 22);
		context.drawGuiTexture(HOTBAR, 182, 22, 181, 0, x + w - 1, y, 1, 22);
		int sel = Math.min(player.getInventory().selectedSlot, n - 1);
		context.drawGuiTexture(SELECTION, x - 1 + sel * 20, y - 1, 24, 23);
		ItemStack off = player.getOffHandStack();
		boolean leftArm = player.getMainArm().getOpposite() == Arm.LEFT;
		if (!off.isEmpty()) {
			if (leftArm) {
				context.drawGuiTexture(OFFHAND_LEFT, x - 29, y - 1, 29, 24);
			} else {
				context.drawGuiTexture(OFFHAND_RIGHT, x + w, y - 1, 29, 24);
			}
		}
		// la mochila, al otro lado
		ItemStack pack = Backpacks.worn(player);
		int px = leftArm || off.isEmpty() ? x + w + 4 : x - 29 + 3;
		if (!pack.isEmpty()) {
			context.drawGuiTexture(leftArm || off.isEmpty() ? OFFHAND_RIGHT : OFFHAND_LEFT, leftArm || off.isEmpty() ? x + w : x - 29, y - 1, 29, 24);
		}
		context.getMatrices().pop();
		int seed = 1;
		for (int i = 0; i < n; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (!s.isEmpty()) {
				context.drawItem(player, s, x + 3 + i * 20, y + 3, seed++);
				context.drawItemInSlot(client.textRenderer, s, x + 3 + i * 20, y + 3);
			}
		}
		// los huecos de la carga vacíos: su icono (espada, pico, hacha), tenue
		var atlas = client.getSpriteAtlas(net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		for (int i = 0; i < 3; i++) {
			if (player.getInventory().getStack(i).isEmpty()) {
				context.drawSprite(x + 3 + i * 20, y + 3, 0, 16, 16, atlas.apply(com.evolvedwarfare.backpack.Loadout.emptyIcon(i)), 1.0F, 1.0F, 1.0F, 0.45F);
			}
		}
		if (!off.isEmpty()) {
			int ox = leftArm ? x - 26 : x + w + 10;
			context.drawItem(player, off, ox, y + 3, seed++);
			context.drawItemInSlot(client.textRenderer, off, ox, y + 3);
		}
		if (!pack.isEmpty()) {
			int ix = leftArm || off.isEmpty() ? x + w + 10 : x - 26;
			context.drawItem(player, pack, ix, y + 3, seed);
			// lo llena que va: una barrita (verde, amarilla, roja)
			float fill = Backpacks.inventory(player).fill();
			int used = BackpackItem.used(pack);
			int bw = Math.round(13 * Math.min(1.0F, used / (float) Math.max(1, Backpacks.size(player))));
			int color = fill > 0.75F ? 0xFFE04030 : fill > 0.45F ? 0xFFE0C040 : 0xFF50C050;
			context.fill(ix + 2, y + 16, ix + 15, y + 18, 0xFF000000);
			context.fill(ix + 2, y + 16, ix + 2 + bw, y + 17, color);
			// la carga pesada: cuánto te frena
			int heavy = Backpacks.heavyUsed(player);
			if (heavy > 0) {
				String t = "-" + Math.round(heavy * Backpacks.SLOW_PER_HEAVY * 100) + "%";
				context.drawTextWithShadow(client.textRenderer, t, ix + 19, y + 8, 0xFFFF5040);
			}
		}
		if (px < 0) {
			return;
		}
	}
}
