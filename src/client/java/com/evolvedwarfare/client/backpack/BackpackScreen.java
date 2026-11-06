package com.evolvedwarfare.client.backpack;

import com.evolvedwarfare.backpack.BackpackScreenHandler;
import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** La pantalla de una mochila: un panel como el de un cofre, con tantos huecos como tenga, y debajo tu barra de 5. */
public class BackpackScreen extends HandledScreen<BackpackScreenHandler> {
	public BackpackScreen(BackpackScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		int rows = BackpackScreenHandler.rows(handler.count());
		backgroundHeight = 18 + rows * 18 + 14 + 18 + 8;
		playerInventoryTitleY = 18 + rows * 18 + 3;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		Panels.panel(context, x, y, backgroundWidth, backgroundHeight);
		for (Slot slot : handler.slots) {
			if (slot.isEnabled()) {
				Panels.slot(context, x + slot.x - 1, y + slot.y - 1);
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
		context.drawText(textRenderer, Text.translatable("container.evolvedwarfare.hotbar", Backpacks.HOTBAR), playerInventoryTitleX, playerInventoryTitleY,
				0x404040, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
