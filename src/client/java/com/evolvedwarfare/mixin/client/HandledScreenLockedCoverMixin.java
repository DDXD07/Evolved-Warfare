package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En los cofres, hornos y demás, tapa los huecos cerrados del inventario (los 27 de arriba y los 4 últimos de la barra). */
@Mixin(HandledScreen.class)
public abstract class HandledScreenLockedCoverMixin<T extends ScreenHandler> {
	@Shadow
	@Final
	protected T handler;

	@Shadow
	protected int x;

	@Shadow
	protected int y;

	@Inject(method = "renderBackground", at = @At("TAIL"), require = 0)
	private void evolvedwarfare$cover(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		var player = MinecraftClient.getInstance().player;
		if (player == null || !Backpacks.limited(player) || (Object) this instanceof InventoryScreen) {
			return;
		}
		for (Slot slot : handler.slots) {
			if (slot.inventory instanceof PlayerInventory && slot.getIndex() >= Backpacks.HOTBAR && slot.getIndex() < 36) {
				context.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFFC6C6C6);
			}
		}
	}
}
