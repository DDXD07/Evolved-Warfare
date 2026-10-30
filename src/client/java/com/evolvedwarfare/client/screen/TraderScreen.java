package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.logistics.Money;
import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.trade.Deal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * Tienda de un comerciante de ciudad, en dos pestañas: <b>Compra</b> (todo lo que vende) y <b>Venta</b> (todo lo que te
 * compra), en fichas de 4 columnas con barra de desplazamiento. Cada ficha lleva el lote, el precio y cuántas unidades
 * le quedan hoy (al menos 20 de cada cosa; repone cada mañana), con el marco del color de su rareza.
 * Clic: un lote; Mayús+clic: muchos.
 */
public class TraderScreen extends HandledScreen<TraderScreenHandler> {
	private static final int EARN = 0x2E7D32;
	private static final int BAD = 0xC62828;
	private static final int COLUMNS = TraderScreenHandler.COLUMNS;
	private static final int ROWS = TraderScreenHandler.ROWS;
	private static final int CARD_W = TraderScreenHandler.CARD_W;
	private static final int CARD_H = TraderScreenHandler.CARD_H;
	private static final int STEP_X = CARD_W + TraderScreenHandler.CARD_GAP;
	private static final int STEP_Y = CARD_H + TraderScreenHandler.CARD_GAP;
	private static final int GRID_X = TraderScreenHandler.GRID_X;
	private static final int GRID_Y = TraderScreenHandler.GRID_Y;
	private static final int BAR_X = GRID_X + COLUMNS * STEP_X + 1;
	private static final int BAR_W = 6;
	private static final int BAR_H = ROWS * STEP_Y - 2;
	/** Ancho que tiene el precio en una ficha (lo que queda entre el objeto y la moneda). */
	private static final int PRICE_ROOM = CARD_W - 22 - 12;

	private final List<Integer> sells = new ArrayList<>();
	private final List<Integer> buys = new ArrayList<>();
	private boolean showSells = true;
	private int scroll;
	private boolean draggingBar;
	private ButtonWidget sellTab;
	private ButtonWidget buyTab;
	private ItemStack coin = ItemStack.EMPTY;
	private final TraderTheme theme;

	public TraderScreen(TraderScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = TraderScreenHandler.WIDTH;
		this.backgroundHeight = TraderScreenHandler.HEIGHT;
		this.playerInventoryTitleX = TraderScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = TraderScreenHandler.INVENTORY_Y - 11;
		this.theme = TraderTheme.of(handler.traderType());
		List<Deal> deals = handler.deals();
		for (int i = 0; i < deals.size(); i++) {
			(deals.get(i).sell() ? sells : buys).add(i);
		}
	}

	@Override
	protected void init() {
		super.init();
		coin = new ItemStack(Market.currency());
		int tabW = 62;
		int tabY = y + 17;
		sellTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.trader.tab_sells", sells.size()),
				b -> setTab(true)).dimensions(x + GRID_X, tabY, tabW, 14).build());
		buyTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.trader.tab_buys", buys.size()),
				b -> setTab(false)).dimensions(x + GRID_X + tabW + 2, tabY, tabW, 14).build());
		updateTabs();
	}

	private void setTab(boolean sell) {
		showSells = sell;
		scroll = 0;
		updateTabs();
	}

	private void updateTabs() {
		if (sellTab != null) {
			sellTab.setMessage(tabLabel("gui.evolvedwarfare.trader.tab_sells", sells.size(), showSells));
			buyTab.setMessage(tabLabel("gui.evolvedwarfare.trader.tab_buys", buys.size(), !showSells));
		}
	}

	private static Text tabLabel(String key, int count, boolean selected) {
		return selected ? Text.translatable(key, count).formatted(Formatting.YELLOW, Formatting.UNDERLINE)
				: Text.translatable(key, count);
	}

	private List<Integer> shown() {
		return showSells ? sells : buys;
	}

	private int maxScroll() {
		int rows = (shown().size() + COLUMNS - 1) / COLUMNS;
		return Math.max(0, rows - ROWS);
	}

	/** Índice del catálogo bajo el ratón, o -1. */
	private int dealAt(double mouseX, double mouseY) {
		double gx = mouseX - x - GRID_X;
		double gy = mouseY - y - GRID_Y;
		if (gx < 0 || gy < 0) {
			return -1;
		}
		int col = (int) (gx / STEP_X);
		int row = (int) (gy / STEP_Y);
		if (col >= COLUMNS || row >= ROWS || gx - col * STEP_X >= CARD_W || gy - row * STEP_Y >= CARD_H) {
			return -1;
		}
		int pos = (row + scroll) * COLUMNS + col;
		List<Integer> shown = shown();
		return pos < shown.size() ? shown.get(pos) : -1;
	}

	private int coins() {
		return client != null && client.player != null ? Market.countCoins(client.player.getInventory()) : 0;
	}

	private int carried(Deal deal) {
		if (client == null || client.player == null) {
			return 0;
		}
		PlayerInventory inventory = client.player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && stack.isOf(deal.item()) && stack.getComponentChanges().isEmpty()) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ ratón

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int index = dealAt(mouseX, mouseY);
			if (index >= 0 && client != null && client.interactionManager != null) {
				client.interactionManager.clickButton(handler.syncId, index * 2 + (Screen.hasShiftDown() ? 1 : 0));
				return true;
			}
			if (overBar(mouseX, mouseY) && maxScroll() > 0) {
				draggingBar = true;
				scrollTo(mouseY);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean overBar(double mouseX, double mouseY) {
		return mouseX >= x + BAR_X && mouseX < x + BAR_X + BAR_W && mouseY >= y + GRID_Y && mouseY < y + GRID_Y + BAR_H;
	}

	private void scrollTo(double mouseY) {
		double t = (mouseY - y - GRID_Y) / BAR_H;
		scroll = MathHelper.clamp((int) Math.round(t * maxScroll()), 0, maxScroll());
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingBar) {
			scrollTo(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingBar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseY < y + TraderScreenHandler.INVENTORY_Y - 12) {
			scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
		super.render(context, mouseX, mouseY, delta);
		int index = dealAt(mouseX, mouseY);
		if (index >= 0 && handler.getCursorStack().isEmpty()) {
			context.drawTooltip(textRenderer, tooltip(index), mouseX, mouseY);
		} else {
			drawMouseoverTooltip(context, mouseX, mouseY);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		theme.drawPanel(context, x, y, backgroundWidth, backgroundHeight, GRID_Y + ROWS * STEP_Y, TraderScreenHandler.INVENTORY_Y);
		List<Integer> shown = shown();
		int hovered = dealAt(mouseX, mouseY);
		for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
			int cx = x + GRID_X + (slot % COLUMNS) * STEP_X;
			int cy = y + GRID_Y + (slot / COLUMNS) * STEP_Y;
			int pos = scroll * COLUMNS + slot;
			if (pos >= shown.size()) {
				context.fill(cx, cy, cx + CARD_W, cy + CARD_H, theme.cardEmpty());
				continue;
			}
			int index = shown.get(pos);
			Deal deal = handler.deals().get(index);
			// marco del color de la rareza y ficha (más clara bajo el ratón)
			context.fill(cx, cy, cx + CARD_W, cy + CARD_H, deal.tier().frame);
			context.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, index == hovered ? theme.cardHover() : theme.card());
			Panels.slot(context, cx + 2, cy + 6, theme.slotInner());
		}
		// barra de desplazamiento
		context.fill(x + BAR_X, y + GRID_Y, x + BAR_X + BAR_W, y + GRID_Y + BAR_H, theme.bar());
		int max = maxScroll();
		int knobH = max == 0 ? BAR_H : Math.max(12, BAR_H * ROWS / (ROWS + max));
		int knobY = max == 0 ? 0 : (BAR_H - knobH) * scroll / max;
		context.fill(x + BAR_X, y + GRID_Y + knobY, x + BAR_X + BAR_W, y + GRID_Y + knobY + knobH, max == 0 ? (theme.knob() & 0x00FFFFFF) | 0x80000000 : theme.knob());
		for (net.minecraft.screen.slot.Slot slot : handler.slots) {
			Panels.slot(context, x + slot.x - 1, y + slot.y - 1, theme.slotInner());
		}
		theme.drawEmblems(context, x, y, backgroundWidth, TraderScreenHandler.INVENTORY_Y, TraderScreenHandler.INVENTORY_X);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 12, 8, theme.headerText(), false);
		// monedas que llevas, arriba a la derecha
		String money = Money.full(coins());
		int moneyX = backgroundWidth - 12 - 10 - textRenderer.getWidth(money);
		context.drawText(textRenderer, money, moneyX, 8, theme.money(), false);
		smallCoin(context, backgroundWidth - 12 - 9, 7);
		// nivel de la ciudad
		int level = handler.cityLevel();
		Text city = handler.pricePercent() > 0
				? Text.translatable("gui.evolvedwarfare.trader.city", level, handler.pricePercent())
				: Text.translatable("gui.evolvedwarfare.trader.city_base", level);
		context.drawText(textRenderer, city, backgroundWidth - 9 - textRenderer.getWidth(city), 20, theme.levelColor(level), false);

		int have = coins();
		List<Integer> shown = shown();
		for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
			int pos = scroll * COLUMNS + slot;
			if (pos >= shown.size()) {
				break;
			}
			int index = shown.get(pos);
			Deal deal = handler.deals().get(index);
			int cx = GRID_X + (slot % COLUMNS) * STEP_X;
			int cy = GRID_Y + (slot / COLUMNS) * STEP_Y;
			ItemStack stack = deal.stack();
			context.drawItem(stack, cx + 3, cy + 7);
			context.drawItemInSlot(textRenderer, stack, cx + 3, cy + 7);
			int left = handler.left(index);
			if (left <= 0) {
				// agotado (o ya no compra más): el objeto, apagado
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 300);
				context.fill(cx + 3, cy + 7, cx + 19, cy + 23, 0x80303030);
				context.getMatrices().pop();
			}
			// precio
			String price = Money.compact(deal.price());
			int color;
			if (deal.sell()) {
				color = have >= deal.price() ? theme.price() : theme.lockText();
			} else {
				color = carried(deal) >= deal.count() ? earn() : theme.sub();
			}
			// los precios largos ("12,5k") se encogen un poco para que quepan con la moneda
			int priceW = textRenderer.getWidth(price);
			float priceScale = priceW > PRICE_ROOM ? Math.max(0.7F, PRICE_ROOM / (float) priceW) : 1.0F;
			context.getMatrices().push();
			context.getMatrices().translate(cx + 22, cy + 5 + (1.0F - priceScale) * 4.0F, 0);
			context.getMatrices().scale(priceScale, priceScale, 1.0F);
			context.drawText(textRenderer, price, 0, 0, color, false);
			context.getMatrices().pop();
			smallCoin(context, cx + 23 + Math.round(priceW * priceScale), cy + 4);
			// segunda línea: cuántas unidades le quedan hoy (de las que mueve al día) o agotado
			String second;
			int secondColor;
			if (left <= 0) {
				second = Text.translatable(deal.sell() ? "gui.evolvedwarfare.trader.sold_out" : "gui.evolvedwarfare.trader.full").getString();
				secondColor = theme.lockText();
			} else {
				second = left * deal.count() + "/" + deal.stock() * deal.count();
				secondColor = theme.sub();
			}
			context.getMatrices().push();
			context.getMatrices().translate(cx + 22, cy + 18, 0);
			context.getMatrices().scale(0.75F, 0.75F, 1.0F);
			context.drawText(textRenderer, textRenderer.trimToWidth(second, 44), 0, 0, secondColor, false);
			context.getMatrices().pop();
		}
		if (shown.isEmpty()) {
			Text none = Text.translatable("gui.evolvedwarfare.trader.empty");
			context.drawText(textRenderer, none, GRID_X + 4, GRID_Y + 4, theme.labelText(), false);
		}
		Text hint = Text.translatable(showSells ? "gui.evolvedwarfare.trader.hint_sells" : "gui.evolvedwarfare.trader.hint_buys");
		String line = textRenderer.trimToWidth(hint.getString(), backgroundWidth - 16);
		context.drawText(textRenderer, line, 8, GRID_Y + ROWS * STEP_Y + 1, theme.hintText(), false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, theme.labelText(), false);
	}

	/** Verde de "te lo compra", legible sobre las fichas oscuras. */
	private int earn() {
		int c = theme.card();
		int luminance = ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255);
		return luminance < 1000 ? 0xFF7CE08A : EARN;
	}

	private void smallCoin(DrawContext context, int px, int py) {
		context.getMatrices().push();
		context.getMatrices().translate(px, py, 0);
		context.getMatrices().scale(0.6F, 0.6F, 1.0F);
		context.drawItem(coin, 0, 0);
		context.getMatrices().pop();
	}

	/**
	 * Lo justo de cada ficha: el nombre (y sus encantamientos), la rareza con la calidad, el precio del lote y cuántas
	 * quedan hoy. Los controles ya salen debajo de las fichas.
	 */
	private List<Text> tooltip(int index) {
		Deal deal = handler.deals().get(index);
		ItemStack stack = deal.stack();
		List<Text> lines = new ArrayList<>();
		lines.add(Text.empty().append(stack.getName()).formatted(stack.getRarity().getFormatting()));
		Text enchantments = enchantments(stack);
		if (enchantments != null) {
			lines.add(enchantments);
		}
		MutableText info = Text.translatable(deal.tier().translationKey()).formatted(deal.tier().color);
		com.evolvedwarfare.trade.Quality quality = deal.quality();
		if (quality != null) {
			info.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
					.append(Text.translatable(quality.translationKey()).formatted(quality.color));
			if (stack.isDamaged()) {
				int percent = Math.round(100.0F * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage());
				info.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
						.append(Text.translatable("gui.evolvedwarfare.trader.durability", percent).formatted(Formatting.GRAY));
			}
		}
		lines.add(info);
		lines.add(Text.translatable("gui.evolvedwarfare.trader.lot", deal.count(), Money.full(deal.price()), coin.getName())
				.formatted(deal.sell() ? Formatting.GOLD : Formatting.GREEN));
		int left = handler.left(index) * deal.count();
		int total = deal.stock() * deal.count();
		lines.add((deal.sell() ? Text.translatable("gui.evolvedwarfare.trader.left", left, total)
				: Text.translatable("gui.evolvedwarfare.trader.left_buy", left, total, carried(deal))).formatted(Formatting.GRAY));
		return lines;
	}

	/** Los encantamientos en una sola línea ("Protección IV, Irrompibilidad III"), o null si no tiene. */
	@org.jetbrains.annotations.Nullable
	private static Text enchantments(ItemStack stack) {
		net.minecraft.component.type.ItemEnchantmentsComponent component = stack.getEnchantments();
		if (component.isEmpty()) {
			return null;
		}
		MutableText out = Text.empty();
		boolean first = true;
		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> entry
				: component.getEnchantmentEntries()) {
			if (!first) {
				out.append(Text.literal(", "));
			}
			out.append(net.minecraft.enchantment.Enchantment.getName(entry.getKey(), entry.getIntValue()));
			first = false;
		}
		return out.formatted(Formatting.GRAY);
	}
}
