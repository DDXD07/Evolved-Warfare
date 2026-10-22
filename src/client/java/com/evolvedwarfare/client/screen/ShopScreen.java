package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.screen.ShopScreenHandler;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Tienda del comerciante: 9 ofertas en fichas de 3x3 (objeto con las existencias, lote y precio). Los compradores ven
 * "Comprar"; el dueño ve [-] [+] para el precio (con Mayús, de 10 en 10) y el botón del lote.
 */
public class ShopScreen extends HandledScreen<ShopScreenHandler> {
	private static final int TEXT = 0x404040;
	private static final int OPEN = 0x2E7D32;
	private static final int CLOSED = 0xC62828;
	private static final int OFFERS = ShopScreenHandler.OFFERS;

	private final ButtonWidget[] buyButtons = new ButtonWidget[OFFERS];
	private final ButtonWidget[] downButtons = new ButtonWidget[OFFERS];
	private final ButtonWidget[] upButtons = new ButtonWidget[OFFERS];
	private final ButtonWidget[] bundleButtons = new ButtonWidget[OFFERS];
	private ItemStack coin = ItemStack.EMPTY;

	public ShopScreen(ShopScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = ShopScreenHandler.WIDTH;
		this.backgroundHeight = ShopScreenHandler.HOTBAR_Y + 24;
		this.playerInventoryTitleX = ShopScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = ShopScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		coin = new ItemStack(Market.currency());
		for (int i = 0; i < OFFERS; i++) {
			int slot = i;
			int bx = x + ShopScreenHandler.cardX(i) + 2;
			int by = y + ShopScreenHandler.cardY(i) + 22;
			buyButtons[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.shop.buy"),
					b -> click(ShopScreenHandler.BUY, slot)).dimensions(bx, by, 52, 14).build());
			downButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal("-"),
					b -> click(Screen.hasShiftDown() ? ShopScreenHandler.PRICE_DOWN_10 : ShopScreenHandler.PRICE_DOWN, slot))
					.dimensions(bx, by, 14, 14).build());
			upButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal("+"),
					b -> click(Screen.hasShiftDown() ? ShopScreenHandler.PRICE_UP_10 : ShopScreenHandler.PRICE_UP, slot))
					.dimensions(bx + 15, by, 14, 14).build());
			bundleButtons[i] = addDrawableChild(ButtonWidget.builder(Text.empty(),
					b -> click(Screen.hasShiftDown() ? ShopScreenHandler.BUNDLE_PREVIOUS : ShopScreenHandler.BUNDLE_NEXT, slot))
					.dimensions(bx + 30, by, 22, 14).build());
			downButtons[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.shop.price_down")));
			upButtons[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.shop.price_up")));
			bundleButtons[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.shop.bundle")));
		}
		updateButtons();
	}

	private void click(int kind, int slot) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, kind * OFFERS + slot);
		}
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		updateButtons();
	}

	private void updateButtons() {
		boolean owner = handler.isOwner();
		for (int i = 0; i < OFFERS; i++) {
			if (buyButtons[i] == null) {
				return;
			}
			ItemStack offer = handler.offer(i);
			buyButtons[i].visible = !owner;
			buyButtons[i].active = handler.merchantPresent() && !offer.isEmpty() && offer.getCount() >= handler.bundle(i);
			downButtons[i].visible = owner;
			upButtons[i].visible = owner;
			bundleButtons[i].visible = owner;
			bundleButtons[i].setMessage(Text.literal("x" + handler.bundle(i)));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		Panels.panel(context, x, y, backgroundWidth, backgroundHeight);
		for (int i = 0; i < OFFERS; i++) {
			int cx = x + ShopScreenHandler.cardX(i);
			int cy = y + ShopScreenHandler.cardY(i);
			// ficha un poco más oscura
			context.fill(cx, cy, cx + ShopScreenHandler.CARD_W, cy + ShopScreenHandler.CARD_H, 0xFFB5B5B5);
			context.fill(cx, cy, cx + ShopScreenHandler.CARD_W, cy + 1, 0xFF8B8B8B);
			context.fill(cx, cy, cx + 1, cy + ShopScreenHandler.CARD_H, 0xFF8B8B8B);
		}
		for (Slot slot : handler.slots) {
			Panels.slot(context, x + slot.x - 1, y + slot.y - 1);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 8, 6, TEXT, false);
		for (int i = 0; i < OFFERS; i++) {
			int cx = ShopScreenHandler.cardX(i);
			int cy = ShopScreenHandler.cardY(i);
			ItemStack offer = handler.offer(i);
			if (offer.isEmpty() && !handler.isOwner()) {
				context.drawText(textRenderer, "—", cx + 30, cy + 8, 0x707070, false);
				continue;
			}
			// lote
			context.drawText(textRenderer, "x" + handler.bundle(i), cx + 22, cy + 3, TEXT, false);
			// precio con la moneda en pequeño
			String price = Integer.toString(handler.price(i));
			int px = cx + 22;
			context.drawText(textRenderer, price, px, cy + 12, 0x7A5A00, false);
			int iconX = px + textRenderer.getWidth(price) + 1;
			context.getMatrices().push();
			context.getMatrices().translate(iconX, cy + 11, 0);
			context.getMatrices().scale(0.6F, 0.6F, 1.0F);
			context.drawItem(coin, 0, 0);
			context.getMatrices().pop();
		}
		// abierta o cerrada, arriba a la derecha
		Text state = handler.merchantPresent() ? Text.translatable("gui.evolvedwarfare.shop.open")
				: Text.translatable("gui.evolvedwarfare.shop.closed");
		context.drawText(textRenderer, state, backgroundWidth - 8 - textRenderer.getWidth(state), 6,
				handler.merchantPresent() ? OPEN : CLOSED, false);
		if (handler.isOwner()) {
			Text hint = Text.translatable("gui.evolvedwarfare.shop.owner_hint");
			String line = textRenderer.trimToWidth(hint.getString(), backgroundWidth - 16);
			context.drawText(textRenderer, line, 8, ShopScreenHandler.cardY(OFFERS - 1) + ShopScreenHandler.CARD_H + 3, 0x606060, false);
		}
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, TEXT, false);
	}

	@Override
	protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
		if (focusedSlot != null && focusedSlot.id < OFFERS && focusedSlot.hasStack() && handler.getCursorStack().isEmpty()) {
			int i = focusedSlot.id;
			List<Text> lines = new java.util.ArrayList<>(getTooltipFromItem(client, focusedSlot.getStack()));
			lines.add(Text.translatable("gui.evolvedwarfare.shop.offer", handler.bundle(i), handler.price(i), coin.getName())
					.formatted(net.minecraft.util.Formatting.GOLD));
			lines.add(Text.translatable("gui.evolvedwarfare.shop.stock", focusedSlot.getStack().getCount())
					.formatted(net.minecraft.util.Formatting.GRAY));
			context.drawTooltip(textRenderer, lines, mouseX, mouseY);
			return;
		}
		super.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
