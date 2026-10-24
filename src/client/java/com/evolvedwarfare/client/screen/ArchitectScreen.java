package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.blueprint.BlueprintLibrary;
import com.evolvedwarfare.blueprint.StoredBlueprint;
import com.evolvedwarfare.client.blueprint.BlueprintPreview;
import com.evolvedwarfare.client.blueprint.ClientBlueprints;
import com.evolvedwarfare.network.BlueprintPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Mesa del arquitecto: a la izquierda la lista de planos (los tuyos y los del mod), a la derecha la vista previa en 3D,
 * el tamaño y los materiales del elegido, y los botones Imprimir (un papel) y Borrar (solo los tuyos).
 */
public class ArchitectScreen extends Screen {
	private static final int W = 380;
	private static final int H = 232;
	private static final int LIST_X = 8;
	private static final int LIST_Y = 24;
	private static final int LIST_W = 130;
	private static final int ROW = 12;
	private static final int ROWS = 16;
	private static final int PREVIEW_X = 146;
	private static final int PREVIEW_Y = 24;
	private static final int PREVIEW_W = 226;
	private static final int PREVIEW_H = 132;
	private static final int TEXT = 0x404040;

	private final BlueprintPreview preview = new BlueprintPreview();
	private int left;
	private int top;
	private int scroll;
	@Nullable
	private String selected;
	private int seenVersion = -1;
	private boolean previewReady;
	private Map<Item, Integer> materials = Map.of();
	private ButtonWidget printButton;
	private ButtonWidget deleteButton;

	public ArchitectScreen() {
		super(Text.translatable("block.evolvedwarfare.architect_table"));
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		printButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.architect.print"),
				b -> action(BlueprintPayloads.Action.PRINT)).dimensions(left + PREVIEW_X, top + H - 26, 110, 18).build());
		printButton.setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.architect.print.tip")));
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.architect.delete"),
				b -> action(BlueprintPayloads.Action.DELETE)).dimensions(left + PREVIEW_X + 116, top + H - 26, 110, 18).build());
		if (selected == null && !ClientBlueprints.entries.isEmpty()) {
			select(ClientBlueprints.entries.get(0).id());
		}
		updateButtons();
	}

	@Nullable
	private BlueprintLibrary.Summary current() {
		for (BlueprintLibrary.Summary s : ClientBlueprints.entries) {
			if (s.id().equals(selected)) {
				return s;
			}
		}
		return null;
	}

	private static Text nameOf(BlueprintLibrary.Summary s) {
		return s.builtin() ? Text.translatable(s.name()) : Text.literal(s.name());
	}

	private void select(String id) {
		selected = id;
		previewReady = false;
		preview.clear();
		materials = Map.of();
		if (ClientBlueprints.get(id) == null) {
			ClientPlayNetworking.send(new BlueprintPayloads.Request(id));
		}
		updateButtons();
	}

	private void action(int action) {
		if (selected != null) {
			ClientPlayNetworking.send(new BlueprintPayloads.Action(action, selected, ClientBlueprints.table));
			if (action == BlueprintPayloads.Action.DELETE) {
				ClientBlueprints.forget(selected);
				selected = null;
			}
		}
	}

	private void updateButtons() {
		BlueprintLibrary.Summary s = current();
		if (printButton == null) {
			return;
		}
		printButton.active = s != null;
		deleteButton.active = s != null && !s.builtin() && (ClientBlueprints.admin || s.author().equals(ClientBlueprints.player));
	}

	@Override
	public void tick() {
		super.tick();
		preview.tick();
		if (seenVersion != ClientBlueprints.version) {
			seenVersion = ClientBlueprints.version;
			if (selected != null && current() == null) {
				selected = null;
			}
			if (selected == null && !ClientBlueprints.entries.isEmpty()) {
				select(ClientBlueprints.entries.get(0).id());
			}
			updateButtons();
		}
		if (!previewReady && selected != null) {
			BlueprintPayloads.Data data = ClientBlueprints.get(selected);
			if (data != null) {
				preview.set(data.sizeX(), data.sizeY(), data.sizeZ(), data.palette(), data.blocks());
				int[] raw = new int[data.blocks().size()];
				for (int i = 0; i < raw.length; i++) {
					raw[i] = data.blocks().get(i);
				}
				materials = new StoredBlueprint(data.id(), "", "", false, data.sizeX(), data.sizeY(), data.sizeZ(), data.palette(), raw)
						.materials();
				previewReady = true;
			}
		}
	}

	// ------------------------------------------------------------------ ratón

	private int maxScroll() {
		return Math.max(0, ClientBlueprints.entries.size() - ROWS);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int lx = left + LIST_X;
		int ly = top + LIST_Y;
		if (button == 0 && mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + ROWS * ROW) {
			int index = scroll + (int) ((mouseY - ly) / ROW);
			if (index >= 0 && index < ClientBlueprints.entries.size()) {
				select(ClientBlueprints.entries.get(index).id());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean inPreview(double mouseX, double mouseY) {
		return mouseX >= left + PREVIEW_X && mouseX < left + PREVIEW_X + PREVIEW_W && mouseY >= top + PREVIEW_Y
				&& mouseY < top + PREVIEW_Y + PREVIEW_H;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && inPreview(mouseX, mouseY)) {
			preview.drag(deltaX, deltaY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inPreview(mouseX, mouseY)) {
			preview.scroll(verticalAmount);
			return true;
		}
		scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
		return true;
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		Panels.panel(context, left, top, W, H);
		context.fill(left + LIST_X - 1, top + LIST_Y - 1, left + LIST_X + LIST_W + 1, top + LIST_Y + ROWS * ROW + 1, 0xFF373737);
		context.fill(left + LIST_X, top + LIST_Y, left + LIST_X + LIST_W, top + LIST_Y + ROWS * ROW, 0xFF8B8B8B);
		context.fill(left + PREVIEW_X - 1, top + PREVIEW_Y - 1, left + PREVIEW_X + PREVIEW_W + 1, top + PREVIEW_Y + PREVIEW_H + 1,
				0xFF373737);
		preview.render(context, left + PREVIEW_X, top + PREVIEW_Y, PREVIEW_W, PREVIEW_H);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, title, left + 8, top + 8, TEXT, false);
		// lista
		List<BlueprintLibrary.Summary> entries = ClientBlueprints.entries;
		for (int row = 0; row < ROWS; row++) {
			int index = scroll + row;
			if (index >= entries.size()) {
				break;
			}
			BlueprintLibrary.Summary s = entries.get(index);
			int ry = top + LIST_Y + row * ROW;
			boolean sel = s.id().equals(selected);
			if (sel) {
				context.fill(left + LIST_X, ry, left + LIST_X + LIST_W, ry + ROW, 0xFF5A6E8C);
			}
			String text = textRenderer.trimToWidth(nameOf(s).getString(), LIST_W - 8);
			context.drawText(textRenderer, text, left + LIST_X + 3, ry + 2, sel ? 0xFFFFFF : (s.builtin() ? 0xE0E0C0 : 0xFFFFFF), true);
		}
		if (entries.isEmpty()) {
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.architect.empty"), left + LIST_X + 3, top + LIST_Y + 4,
					0xFFFFFF, true);
		}
		// datos del elegido
		BlueprintLibrary.Summary s = current();
		int iy = top + PREVIEW_Y + PREVIEW_H + 4;
		if (s != null) {
			context.drawText(textRenderer, nameOf(s).copy().formatted(Formatting.BOLD), left + PREVIEW_X, iy, TEXT, false);
			Text info = Text.translatable("gui.evolvedwarfare.architect.info", s.sizeX(), s.sizeY(), s.sizeZ(), s.blocks(),
					s.builtin() ? Text.translatable("gui.evolvedwarfare.architect.builtin") : Text.literal(s.author()));
			context.drawText(textRenderer, info, left + PREVIEW_X, iy + 11, 0x5A5A5A, false);
			// materiales principales
			int mx = left + PREVIEW_X;
			int my = iy + 22;
			int shown = 0;
			List<Text> tooltip = null;
			for (Map.Entry<Item, Integer> e : materials.entrySet()) {
				if (shown >= 8) {
					break;
				}
				int ix = mx + shown * 28;
				context.drawItem(new ItemStack(e.getKey()), ix, my);
				String count = e.getValue() >= 1000 ? (e.getValue() / 1000) + "k" : Integer.toString(e.getValue());
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 200);
				context.drawText(textRenderer, count, ix + 17 - textRenderer.getWidth(count) + 6, my + 9, 0xFFFFFF, true);
				context.getMatrices().pop();
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= my && mouseY < my + 16) {
					tooltip = new ArrayList<>();
					tooltip.add(e.getKey().getName());
					tooltip.add(Text.literal("× " + e.getValue()).formatted(Formatting.GRAY));
				}
				shown++;
			}
			if (!previewReady) {
				context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.architect.loading"), left + PREVIEW_X + 6,
						top + PREVIEW_Y + 6, 0x404040, false);
			}
			if (tooltip != null) {
				context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
			}
		}
		// barra de la lista
		if (entries.size() > ROWS) {
			int trackX = left + LIST_X + LIST_W - 3;
			int trackY = top + LIST_Y;
			int trackH = ROWS * ROW;
			int thumb = Math.max(10, trackH * ROWS / entries.size());
			int offset = (trackH - thumb) * scroll / Math.max(1, maxScroll());
			context.fill(trackX, trackY + offset, trackX + 2, trackY + offset + thumb, 0xFFDDDDDD);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
