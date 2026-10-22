package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.logistics.StorageNetwork;
import com.evolvedwarfare.network.WarehouseActionPayload;
import com.evolvedwarfare.screen.WarehouseScreenHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal del almacén: toda la red de cofres en una rejilla de 9x6 con buscador y rueda para bajar.
 * Clic: sacar un montón · clic derecho: medio · Mayús + clic: directo al inventario · clic con algo en el cursor: meterlo
 * (derecho: de uno en uno) · Mayús + clic en tu inventario: meter el montón.
 */
public class WarehouseScreen extends HandledScreen<WarehouseScreenHandler> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");
	private static final int COLUMNS = 9;
	private static final int ROWS = 6;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 18;
	private static final int TEXT = 0x404040;

	private TextFieldWidget search;
	private int scroll;
	private int seenVersion = -1;
	private String seenFilter = "";
	private final List<StorageNetwork.Entry> shown = new ArrayList<>();

	public WarehouseScreen(WarehouseScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 222;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		search = new TextFieldWidget(textRenderer, x + 89, y + 4, 80, 11, Text.translatable("gui.evolvedwarfare.warehouse.search"));
		search.setPlaceholder(Text.translatable("gui.evolvedwarfare.warehouse.search").formatted(Formatting.DARK_GRAY));
		search.setMaxLength(40);
		search.setChangedListener(text -> scroll = 0);
		addDrawableChild(search);
	}

	// ------------------------------------------------------------------ lista

	private void refresh() {
		String filter = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		if (handler.clientVersion() == seenVersion && filter.equals(seenFilter)) {
			return;
		}
		seenVersion = handler.clientVersion();
		seenFilter = filter;
		shown.clear();
		for (StorageNetwork.Entry entry : handler.clientEntries()) {
			if (filter.isEmpty() || entry.prototype().getName().getString().toLowerCase(Locale.ROOT).contains(filter)) {
				shown.add(entry);
			}
		}
		shown.sort(Comparator.comparingInt(StorageNetwork.Entry::count).reversed()
				.thenComparing(entry -> entry.prototype().getName().getString()));
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
	}

	private int maxScroll() {
		return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
	}

	@Nullable
	private StorageNetwork.Entry entryAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - x - GRID_X + 1) / 18.0);
		int row = (int) Math.floor((mouseY - y - GRID_Y + 1) / 18.0);
		if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) {
			return null;
		}
		int index = (scroll + row) * COLUMNS + col;
		return index < shown.size() ? shown.get(index) : null;
	}

	private boolean inGrid(double mouseX, double mouseY) {
		return mouseX >= x + GRID_X - 1 && mouseX < x + GRID_X - 1 + COLUMNS * 18
				&& mouseY >= y + GRID_Y - 1 && mouseY < y + GRID_Y - 1 + ROWS * 18;
	}

	// ------------------------------------------------------------------ ratón y teclado

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (inGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
			StorageNetwork.Entry entry = entryAt(mouseX, mouseY);
			ItemStack cursor = handler.getCursorStack();
			int action;
			if (!cursor.isEmpty()) {
				action = button == 1 ? WarehouseActionPayload.INSERT_ONE : WarehouseActionPayload.INSERT_CURSOR;
			} else if (entry == null) {
				return true;
			} else if (Screen.hasShiftDown()) {
				action = WarehouseActionPayload.TAKE_TO_INVENTORY;
			} else {
				action = button == 1 ? WarehouseActionPayload.TAKE_HALF : WarehouseActionPayload.TAKE_STACK;
			}
			ItemStack prototype = entry != null ? entry.prototype() : ItemStack.EMPTY;
			ClientPlayNetworking.send(new WarehouseActionPayload(handler.syncId, action, prototype));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inGrid(mouseX, mouseY)) {
			scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (search != null && search.isFocused() && keyCode != 256) {
			// mientras se escribe, la tecla del inventario no cierra el menú
			search.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		refresh();
		super.render(context, mouseX, mouseY, delta);
		if (inGrid(mouseX, mouseY) && handler.getCursorStack().isEmpty()) {
			StorageNetwork.Entry entry = entryAt(mouseX, mouseY);
			if (entry != null) {
				List<Text> lines = new ArrayList<>(getTooltipFromItem(client, entry.prototype()));
				lines.add(Text.translatable("gui.evolvedwarfare.warehouse.total", entry.count()).formatted(Formatting.GRAY));
				context.drawTooltip(textRenderer, lines, mouseX, mouseY);
				return;
			}
		}
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, ROWS * 18 + 17);
		context.drawTexture(TEXTURE, x, y + ROWS * 18 + 17, 0, 126, backgroundWidth, 96);
		// barra de desplazamiento a la derecha de la rejilla
		int rows = Math.max(1, (shown.size() + COLUMNS - 1) / COLUMNS);
		if (rows > ROWS) {
			int trackTop = y + GRID_Y;
			int trackHeight = ROWS * 18 - 2;
			int thumb = Math.max(8, trackHeight * ROWS / rows);
			int offset = (trackHeight - thumb) * scroll / Math.max(1, maxScroll());
			context.fill(x + 170, trackTop, x + 172, trackTop + trackHeight, 0xFF8B8B8B);
			context.fill(x + 170, trackTop + offset, x + 172, trackTop + offset + thumb, 0xFF373737);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		Text heading = Text.translatable("gui.evolvedwarfare.warehouse.title", handler.clientContainers());
		context.drawText(textRenderer, heading, 8, 6, TEXT, false);
		context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, TEXT, false);
		if (handler.clientContainers() == 0) {
			List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(Text.translatable("gui.evolvedwarfare.warehouse.empty"), 150);
			int ly = GRID_Y + 30;
			for (net.minecraft.text.OrderedText line : lines) {
				context.drawText(textRenderer, line, 12, ly, 0x606060, false);
				ly += 10;
			}
			return;
		}
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				int index = (scroll + row) * COLUMNS + col;
				if (index >= shown.size()) {
					return;
				}
				StorageNetwork.Entry entry = shown.get(index);
				int sx = GRID_X + col * 18;
				int sy = GRID_Y + row * 18;
				context.drawItem(entry.prototype(), sx, sy);
				drawCount(context, entry.count(), sx, sy);
			}
		}
	}

	/** Cantidad pequeña en la esquina (1, 64, 1.2k, 35k...). */
	private void drawCount(DrawContext context, int count, int sx, int sy) {
		String text = count < 1000 ? Integer.toString(count)
				: count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0)
				: count < 1000000 ? (count / 1000) + "k" : String.format(Locale.ROOT, "%.1fM", count / 1000000.0);
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		float scale = 0.66F;
		context.getMatrices().scale(scale, scale, 1);
		int tx = (int) ((sx + 17) / scale) - textRenderer.getWidth(text);
		int ty = (int) ((sy + 17) / scale) - 7;
		context.drawText(textRenderer, text, tx, ty, 0xFFFFFF, true);
		context.getMatrices().pop();
	}
}
