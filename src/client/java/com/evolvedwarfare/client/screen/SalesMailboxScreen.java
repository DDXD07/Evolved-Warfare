package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.block.SalesMailboxBlockEntity;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.screen.SalesMailboxScreenHandler;
import com.evolvedwarfare.world.Hordes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * El Buzón de Ventas: los 4x4 huecos (lo que no se puede vender, en rojo), el hueco de las monedas, cuánto falta para
 * la próxima venta, cuánto sacarías ahora y lo que dio la última.
 */
public class SalesMailboxScreen extends HandledScreen<SalesMailboxScreenHandler> {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/gui/container/sales_mailbox.png");
	private static final int TEXT_X = 96;

	public SalesMailboxScreen(SalesMailboxScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 186;
		playerInventoryTitleY = SalesMailboxScreenHandler.INVENTORY_Y - 11;
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
		// lo que no se vende: fondo rojo en su hueco
		for (Slot slot : handler.slots) {
			if (handler.isSaleSlot(slot) && slot.hasStack() && !SalesMailboxBlockEntity.sellable(slot.getStack())) {
				context.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0x90C03030);
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		// cuánto falta para el amanecer (cuando empieza el día siguiente)
		long timeOfDay = client != null && client.world != null ? client.world.getTimeOfDay() : 0L;
		long ticks = Hordes.DAY - Math.floorMod(timeOfDay, Hordes.DAY);
		long seconds = ticks / 20L;
		String left = seconds / 60 + ":" + String.format("%02d", seconds % 60);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.sales_mailbox.next"), TEXT_X, 18, 0x404040, false);
		context.drawText(textRenderer, Text.literal(left), TEXT_X, 28, 0x2E5FA8, false);

		// lo que darían ahora (con la flecha hacia el hueco de las monedas)
		long pending = (long) Math.floor(handler.pendingValue() + 1.0E-9);
		context.drawItem(new ItemStack(Market.currency()), TEXT_X, 70);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.sales_mailbox.pending", pending), TEXT_X + 18, 74,
				pending > 0 ? 0x2E7D32 : 0x6B6B6B, false);
		if (handler.lastSold() > 0) {
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.sales_mailbox.last", handler.lastSold(), handler.lastCoins()),
					TEXT_X, 88, 0x6B6B6B, false);
		}
	}

	/** En los huecos de venta, cuánto te darán por ese montón (o que no se vende). */
	@Override
	protected List<Text> getTooltipFromItem(ItemStack stack) {
		List<Text> tooltip = new ArrayList<>(super.getTooltipFromItem(stack));
		if (focusedSlot != null && handler.isSaleSlot(focusedSlot) && focusedSlot.getStack() == stack) {
			double value = SalesMailboxBlockEntity.saleValue(stack);
			if (value > 0.0) {
				tooltip.add(Text.translatable("gui.evolvedwarfare.sales_mailbox.worth", format(value)).formatted(Formatting.GOLD));
			} else {
				tooltip.add(Text.translatable("gui.evolvedwarfare.sales_mailbox.unsellable").formatted(Formatting.RED));
			}
		}
		return tooltip;
	}

	private static String format(double value) {
		return value >= 10.0 ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
