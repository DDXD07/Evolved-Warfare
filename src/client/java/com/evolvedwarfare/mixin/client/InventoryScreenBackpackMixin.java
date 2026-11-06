package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.backpack.Backpacks;
import com.evolvedwarfare.client.backpack.Panels;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La pantalla del inventario en supervivencia (fase 38): arriba lo de siempre (armadura, personaje, mesa 2x2, mano izquierda)
 * más el hueco de la espalda; debajo la barra de 5 y, debajo, la mochila (tantas filas como tenga). Se estira o encoge
 * según la mochila que lleves.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenBackpackMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/inventory.png");

	@Shadow
	private float mouseX;

	@Shadow
	private float mouseY;

	protected InventoryScreenBackpackMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	private int evolvedwarfare$height() {
		int slots = client == null || client.player == null ? 0 : Backpacks.size(client.player);
		int rows = (slots + 8) / 9;
		return Backpacks.PACK_Y + Math.max(rows, 1) * 18 + 7;
	}

	private boolean evolvedwarfare$limited() {
		return client != null && client.player != null && Backpacks.limited(client.player);
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void evolvedwarfare$size(CallbackInfo ci) {
		if (evolvedwarfare$limited()) {
			backgroundHeight = evolvedwarfare$height();
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void evolvedwarfare$resize(DrawContext context, int mx, int my, float delta, CallbackInfo ci) {
		if (evolvedwarfare$limited()) {
			int h = evolvedwarfare$height();
			if (h != backgroundHeight) {
				backgroundHeight = h;
				y = (height - backgroundHeight) / 2;
			}
		}
	}

	@Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$background(DrawContext context, float delta, int mx, int my, CallbackInfo ci) {
		if (!evolvedwarfare$limited()) {
			return;
		}
		Panels.panel(context, x, y, backgroundWidth, backgroundHeight);
		// lo de arriba, de la textura de siempre (hasta antes de las filas del inventario)
		context.drawTexture(TEXTURE, x + 3, y + 3, 3, 3, backgroundWidth - 6, 78);
		for (Slot slot : handler.slots) {
			if (slot.isEnabled() && slot.y >= Backpacks.HOTBAR_Y - 2 || slot.id == 46) {
				if (slot.isEnabled()) {
					Panels.slot(context, x + slot.x - 1, y + slot.y - 1);
				}
			}
		}
		if (Backpacks.size(client.player) == 0) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("container.evolvedwarfare.no_backpack"), x + backgroundWidth / 2,
					y + Backpacks.PACK_Y + 5, 0xA0A0A0);
		}
		InventoryScreen.drawEntity(context, x + 26, y + 8, x + 75, y + 78, 30, 0.0625F, mouseX, mouseY, client.player);
		ci.cancel();
	}

	@Inject(method = "drawForeground", at = @At("TAIL"))
	private void evolvedwarfare$labels(DrawContext context, int mx, int my, CallbackInfo ci) {
		if (!evolvedwarfare$limited()) {
			return;
		}
		var worn = Backpacks.worn(client.player);
		Text label = worn.isEmpty() ? Text.translatable("container.evolvedwarfare.backpack_slot")
				: Text.translatable("container.evolvedwarfare.backpack", worn.getName(), com.evolvedwarfare.backpack.BackpackItem.used(worn),
						Backpacks.size(client.player));
		context.drawText(textRenderer, label, 8, Backpacks.PACK_Y - 11, 0x404040, false);
	}
}
