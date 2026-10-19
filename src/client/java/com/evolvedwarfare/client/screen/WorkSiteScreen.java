package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.screen.WorkSiteScreenHandler;
import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkArea;
import com.evolvedwarfare.work.WorkStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Menú del puesto de trabajo. Usa la textura del cofre vanilla, cortada y recompuesta: una fila de suministros, un
 * hueco con el rótulo "Producción", dos filas de producción, una franja con el estado y los botones, y el inventario.
 */
public class WorkSiteScreen extends HandledScreen<WorkSiteScreenHandler> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");
	private static final int TEXT = 0x404040;
	private static final int GOOD = 0x2E7D32;
	private static final int NEUTRAL = 0x8A6A00;
	private static final int BAD = 0xC62828;
	/** Fila de la textura que solo tiene fondo gris y bordes: se repite para hacer huecos. */
	private static final int PLAIN_ROW_V = 5;
	private static final int STATUS_TOP = 83;
	private static final int STATUS_HEIGHT = 40;
	private static final String HELP = "[?]";

	private ButtonWidget workerButton;
	private ButtonWidget areaButton;

	public WorkSiteScreen(WorkSiteScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = STATUS_TOP + STATUS_HEIGHT + 96;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		workerButton = ButtonWidget.builder(Text.empty(), b -> click(handler.hasWorker()
						? WorkSiteScreenHandler.BUTTON_DISMISS : WorkSiteScreenHandler.BUTTON_ASSIGN))
				.dimensions(x + 7, y + STATUS_TOP + 25, 80, 14).build();
		addDrawableChild(workerButton);
		areaButton = ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.button.area"), b -> click(WorkSiteScreenHandler.BUTTON_AREA))
				.dimensions(x + 89, y + STATUS_TOP + 25, 80, 14).build();
		areaButton.setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.button.area.tip")));
		areaButton.visible = handler.getProfession().area().exists();
		addDrawableChild(areaButton);
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
		if (workerButton == null) {
			return;
		}
		String key = handler.hasWorker() ? "gui.evolvedwarfare.button.dismiss" : "gui.evolvedwarfare.button.assign";
		workerButton.setMessage(Text.translatable(key));
		workerButton.setTooltip(Tooltip.of(Text.translatable(key + ".tip")));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
		int helpX = x + backgroundWidth - 8 - textRenderer.getWidth(HELP);
		if (mouseX >= helpX && mouseX < helpX + textRenderer.getWidth(HELP) && mouseY >= y + 5 && mouseY < y + 15) {
			context.drawTooltip(textRenderer, helpLines(), mouseX, mouseY);
		}
	}

	/** Qué hace este oficio y qué hay que darle. */
	private List<Text> helpLines() {
		Profession profession = handler.getProfession();
		String base = "profession.evolvedwarfare." + profession.id();
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable(profession.translationKey()).formatted(Formatting.GOLD));
		lines.add(Text.translatable(base + ".info").formatted(Formatting.GRAY));
		WorkArea area = profession.area();
		if (area.exists()) {
			lines.add(Text.translatable(area.pit() ? "gui.evolvedwarfare.site.area_pit" : "gui.evolvedwarfare.site.area",
					area.size(), area.size()).formatted(Formatting.GRAY));
		}
		lines.add(Text.translatable(base + ".supplies").formatted(Formatting.GRAY));
		if (profession.usesTool()) {
			lines.add(Text.translatable("gui.evolvedwarfare.site.help.tool").formatted(Formatting.GRAY));
		}
		lines.add(Text.translatable("gui.evolvedwarfare.site.help.storage").formatted(Formatting.DARK_GRAY));
		return lines;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		// título + fila de suministros
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, 35);
		// hueco con el rótulo "Producción"
		for (int i = 35; i < 47; i++) {
			context.drawTexture(TEXTURE, x, y + i, 0, PLAIN_ROW_V, backgroundWidth, 1);
		}
		// dos filas de producción
		context.drawTexture(TEXTURE, x, y + 47, 0, 17, backgroundWidth, 36);
		// franja de estado
		for (int i = 0; i < STATUS_HEIGHT; i++) {
			context.drawTexture(TEXTURE, x, y + STATUS_TOP + i, 0, PLAIN_ROW_V, backgroundWidth, 1);
		}
		// inventario del jugador
		context.drawTexture(TEXTURE, x, y + STATUS_TOP + STATUS_HEIGHT, 0, 126, backgroundWidth, 96);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 8, 6, TEXT, false);
		context.drawText(textRenderer, HELP, backgroundWidth - 8 - textRenderer.getWidth(HELP), 6, 0x3060A0, false);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.site.output"), 8, 37, TEXT, false);
		Text extra = extraInfo();
		if (extra != null) {
			context.drawText(textRenderer, extra, backgroundWidth - 8 - textRenderer.getWidth(extra), 37, TEXT, false);
		}

		Text worker;
		if (!handler.hasWorker()) {
			worker = Text.translatable("gui.evolvedwarfare.site.no_worker");
		} else {
			Entity entity = client != null && client.world != null ? client.world.getEntityById(handler.workerEntityId()) : null;
			Text name = entity != null ? entity.getName() : Text.translatable("gui.evolvedwarfare.site.worker_far");
			worker = Text.translatable("gui.evolvedwarfare.site.worker", name, handler.jobsDone());
		}
		context.drawText(textRenderer, trim(worker, backgroundWidth - 16), 8, STATUS_TOP + 3, TEXT, false);

		WorkStatus status = handler.status();
		int color = switch (status.tone()) {
			case GOOD -> GOOD;
			case NEUTRAL -> NEUTRAL;
			case BAD -> BAD;
		};
		context.drawText(textRenderer, trim(Text.translatable(status.translationKey()), backgroundWidth - 16), 8, STATUS_TOP + 14, color, false);
		context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, TEXT, false);
	}

	/** Combustible (talleres con fuego) o capa por la que va la mina. */
	private Text extraInfo() {
		Profession profession = handler.getProfession();
		if (profession == Profession.BLACKSMITH || profession == Profession.COOK) {
			return Text.translatable("gui.evolvedwarfare.site.fuel", handler.fuel());
		}
		if (profession.area().pit() && handler.depth() != WorkSiteScreenHandler.NO_DEPTH) {
			return Text.translatable("gui.evolvedwarfare.site.depth", handler.depth());
		}
		return null;
	}

	private Text trim(Text text, int width) {
		if (textRenderer.getWidth(text) <= width) {
			return text;
		}
		return Text.literal(textRenderer.trimToWidth(text.getString(), width - textRenderer.getWidth("...")) + "...");
	}
}
