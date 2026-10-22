package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.logistics.RouteData;
import com.evolvedwarfare.logistics.RouteStop;
import com.evolvedwarfare.screen.RouteSlipScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Editor de la hoja de ruta: una fila por parada con su nombre, el botón Recoger/Entregar, tres filtros y quitar.
 * El fondo se dibuja a mano con los colores de los menús vanilla.
 */
public class RouteSlipScreen extends HandledScreen<RouteSlipScreenHandler> {
	private static final int TEXT = 0x404040;
	private static final int PICKUP = 0x2E7D32;
	private static final int DROPOFF = 0x1F4E8C;
	private static final int ACTION_X = 90;
	private static final int ACTION_W = 52;
	private static final int DELETE_X = 204;

	private final ButtonWidget[] actionButtons = new ButtonWidget[RouteData.MAX_STOPS];
	private final ButtonWidget[] deleteButtons = new ButtonWidget[RouteData.MAX_STOPS];

	public RouteSlipScreen(RouteSlipScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = RouteSlipScreenHandler.WIDTH;
		this.backgroundHeight = RouteSlipScreenHandler.HOTBAR_Y + 24;
		this.playerInventoryTitleX = RouteSlipScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = RouteSlipScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		for (int i = 0; i < RouteData.MAX_STOPS; i++) {
			int stop = i;
			int rowY = y + RouteSlipScreenHandler.ROW_Y + i * RouteSlipScreenHandler.ROW_HEIGHT + 1;
			actionButtons[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> click(stop * 2))
					.dimensions(x + ACTION_X, rowY, ACTION_W, 16).build());
			deleteButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> click(stop * 2 + 1))
					.dimensions(x + DELETE_X, rowY, 16, 16).build());
			deleteButtons[i].setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.route.remove")));
		}
		updateButtons();
	}

	private void click(int id) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, id);
		}
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		updateButtons();
	}

	private void updateButtons() {
		List<RouteStop> stops = handler.data().stops();
		for (int i = 0; i < RouteData.MAX_STOPS; i++) {
			if (actionButtons[i] == null) {
				return;
			}
			boolean visible = i < stops.size();
			actionButtons[i].visible = visible;
			deleteButtons[i].visible = visible;
			if (visible) {
				String key = stops.get(i).pickup() ? "gui.evolvedwarfare.route.pickup" : "gui.evolvedwarfare.route.dropoff";
				actionButtons[i].setMessage(Text.translatable(key));
				actionButtons[i].setTooltip(Tooltip.of(Text.translatable(key + ".tip")));
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		List<RouteStop> stops = handler.data().stops();
		for (int i = 0; i < stops.size(); i++) {
			int rowY = y + RouteSlipScreenHandler.ROW_Y + i * RouteSlipScreenHandler.ROW_HEIGHT;
			if (mouseX >= x + 6 && mouseX < x + ACTION_X - 2 && mouseY >= rowY && mouseY < rowY + 18) {
				BlockPos p = stops.get(i).pos();
				List<Text> lines = new ArrayList<>();
				lines.add(stopName(stops.get(i)));
				lines.add(Text.translatable("gui.evolvedwarfare.route.coords", p.getX(), p.getY(), p.getZ()).formatted(Formatting.GRAY));
				if (client != null && client.player != null) {
					int distance = (int) Math.sqrt(client.player.getBlockPos().getSquaredDistance(p));
					lines.add(Text.translatable("gui.evolvedwarfare.route.distance", distance).formatted(Formatting.DARK_GRAY));
				}
				context.drawTooltip(textRenderer, lines, mouseX, mouseY);
				return;
			}
		}
		if (focusedSlot != null && focusedSlot.id < RouteData.MAX_STOPS * RouteData.MAX_FILTERS && !focusedSlot.hasStack()
				&& focusedSlot.id / RouteData.MAX_FILTERS < stops.size()) {
			context.drawTooltip(textRenderer, List.of(Text.translatable("gui.evolvedwarfare.route.filter.tip")), mouseX, mouseY);
			return;
		}
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	private Text stopName(RouteStop stop) {
		if (client != null && client.world != null && client.world.isChunkLoaded(stop.pos())) {
			return client.world.getBlockState(stop.pos()).getBlock().getName();
		}
		BlockPos p = stop.pos();
		return Text.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		Panels.panel(context, x, y, backgroundWidth, backgroundHeight);
		List<RouteStop> stops = handler.data().stops();
		for (Slot slot : handler.slots) {
			boolean ghost = slot.id < RouteData.MAX_STOPS * RouteData.MAX_FILTERS;
			if (ghost && slot.id / RouteData.MAX_FILTERS >= stops.size()) {
				continue;
			}
			Panels.slot(context, x + slot.x - 1, y + slot.y - 1, ghost ? 0xFF9C9C9C : 0xFF8B8B8B);
		}
		// separadores entre paradas
		for (int i = 1; i < stops.size(); i++) {
			int ly = y + RouteSlipScreenHandler.ROW_Y + i * RouteSlipScreenHandler.ROW_HEIGHT - 1;
			context.fill(x + 7, ly, x + backgroundWidth - 7, ly + 1, 0xFFAAAAAA);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 8, 6, TEXT, false);
		List<RouteStop> stops = handler.data().stops();
		if (stops.isEmpty()) {
			int ly = RouteSlipScreenHandler.ROW_Y + 8;
			for (var line : textRenderer.wrapLines(Text.translatable("gui.evolvedwarfare.route.empty"), backgroundWidth - 24)) {
				context.drawText(textRenderer, line, 12, ly, 0x606060, false);
				ly += 10;
			}
		} else {
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.route.header.stop"), 8, 16, 0x707070, false);
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.route.header.filter"), RouteSlipScreenHandler.FILTER_X, 16, 0x707070, false);
		}
		for (int i = 0; i < stops.size(); i++) {
			RouteStop stop = stops.get(i);
			int rowY = RouteSlipScreenHandler.ROW_Y + i * RouteSlipScreenHandler.ROW_HEIGHT;
			context.drawText(textRenderer, (i + 1) + ".", 8, rowY + 5, stop.pickup() ? PICKUP : DROPOFF, false);
			Text name = stopName(stop);
			String trimmed = textRenderer.getWidth(name) > ACTION_X - 24
					? textRenderer.trimToWidth(name.getString(), ACTION_X - 24 - textRenderer.getWidth("…")) + "…"
					: name.getString();
			context.drawText(textRenderer, trimmed, 20, rowY + 5, TEXT, false);
			if (stop.filter().isEmpty()) {
				Text all = Text.translatable("gui.evolvedwarfare.route.filter.all");
				// "todo" en gris dentro del primer hueco de filtro
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 200);
				context.drawText(textRenderer, all, RouteSlipScreenHandler.FILTER_X + 27 - textRenderer.getWidth(all) / 2, rowY + 6, 0x5A5A5A, false);
				context.getMatrices().pop();
			}
		}
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, TEXT, false);
	}
}
