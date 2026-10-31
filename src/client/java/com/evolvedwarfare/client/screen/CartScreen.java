package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.cart.CartScreenHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * La carga de la carreta vista desde arriba (las varas arriba): 9 huecos en la tabla. Una cama se ve ocupando sus dos
 * huecos, del color de la cama.
 */
public class CartScreen extends HandledScreen<CartScreenHandler> {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/gui/container/cart.png");

	public CartScreen(CartScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = CartScreenHandler.WIDTH;
		backgroundHeight = CartScreenHandler.HEIGHT;
		playerInventoryTitleY = CartScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
		// las camas: un rectángulo de su color sobre sus dos huecos, y la almohada en el de la cabecera
		for (int foot = 0; foot < 9; foot++) {
			int head = handler.bedHeadOf(foot);
			if (head < 0) {
				continue;
			}
			ItemStack bed = handler.slots.get(head).getStack();
			int color = bed.getItem() instanceof BlockItem item && item.getBlock() instanceof BedBlock block
					? block.getColor().getEntityColor() : 0xB02E26;
			int hx = x + CartScreenHandler.GRID_X + (head % 3) * CartScreenHandler.CELL + 2;
			int hy = y + CartScreenHandler.GRID_Y + (head / 3) * CartScreenHandler.CELL + 2;
			int fx = x + CartScreenHandler.GRID_X + (foot % 3) * CartScreenHandler.CELL + 2;
			int fy = y + CartScreenHandler.GRID_Y + (foot / 3) * CartScreenHandler.CELL + 2;
			int x0 = Math.min(hx, fx);
			int y0 = Math.min(hy, fy);
			int x1 = Math.max(hx, fx) + CartScreenHandler.CELL - 4;
			int y1 = Math.max(hy, fy) + CartScreenHandler.CELL - 4;
			context.fill(x0, y0, x1, y1, 0xFF000000 | darker(color));
			context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000 | color);
			// la almohada, en el lado de la cabecera
			int px = hx + 3;
			int py = hy + 3;
			context.fill(px, py, px + CartScreenHandler.CELL - 10, py + CartScreenHandler.CELL - 10, 0xFFEDEDED);
		}
	}

	private static int darker(int rgb) {
		return ((rgb >> 1) & 0x7F7F7F);
	}
}
