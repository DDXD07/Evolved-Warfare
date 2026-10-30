package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.screen.CropCrateScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** La Caja de cultivos: su hueco, cuánto lleva (de 64) y si ya está llena para venderla. */
public class CropCrateScreen extends HandledScreen<CropCrateScreenHandler> {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/gui/container/crop_crate.png");

	public CropCrateScreen(CropCrateScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 133;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		ItemStack inside = handler.contents();
		int left = 108;
		if (inside.isEmpty()) {
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.crop_crate.empty"), left, 20, 0x6B6B6B, false);
			context.drawText(textRenderer, Text.literal("0/" + CropCrateItem.CAPACITY), left, 31, 0x404040, false);
		} else {
			int capacity = CropCrateItem.capacity(inside.getItem());
			int count = inside.getCount();
			boolean full = count >= capacity;
			context.drawText(textRenderer, Text.literal(count + "/" + capacity), left, 20, 0x404040, false);
			context.drawText(textRenderer, full ? Text.translatable("gui.evolvedwarfare.crop_crate.full")
					: Text.translatable("gui.evolvedwarfare.crop_crate.missing", capacity - count), left, 31, full ? 0x2E7D32 : 0xA0522D, false);
			// barrita de lo lleno que está
			int width = 56;
			context.fill(left, 41, left + width, 44, 0xFF555555);
			context.fill(left, 41, left + Math.round(width * count / (float) capacity), 44, full ? 0xFF4CAF50 : 0xFFD9A441);
		}
	}
}
