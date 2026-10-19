package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.screen.NpcScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Menú del NPC: retrato, equipo, provisiones, estado y botones de órdenes. */
public class NpcScreen extends HandledScreen<NpcScreenHandler> {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/gui/npc.png");
	private static final int TEXT = 0x404040;
	private static final int GOOD = 0x2E7D32;
	private static final int WARN = 0xC62828;

	private ButtonWidget followButton;
	private ButtonWidget stayButton;
	private ButtonWidget homeButton;
	private ButtonWidget setHomeButton;

	public NpcScreen(NpcScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 224;
		this.playerInventoryTitleY = NpcScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		followButton = addOrderButton("follow", NpcScreenHandler.BUTTON_FOLLOW, 8, 94);
		stayButton = addOrderButton("stay", NpcScreenHandler.BUTTON_STAY, 89, 94);
		homeButton = addOrderButton("home", NpcScreenHandler.BUTTON_HOME, 8, 112);
		setHomeButton = addOrderButton("set_home", NpcScreenHandler.BUTTON_SET_HOME, 89, 112);
		updateButtons();
	}

	private ButtonWidget addOrderButton(String key, int id, int bx, int by) {
		ButtonWidget button = ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.button." + key), b -> {
			if (client != null && client.interactionManager != null) {
				client.interactionManager.clickButton(handler.syncId, id);
			}
		}).dimensions(x + bx, y + by, 79, 16).build();
		button.setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.button." + key + ".tip")));
		return addDrawableChild(button);
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		updateButtons();
	}

	/** El botón de la orden actual aparece "pulsado" (desactivado). */
	private void updateButtons() {
		if (followButton == null) {
			return;
		}
		NpcOrder order = handler.order();
		followButton.active = order != NpcOrder.FOLLOW;
		stayButton.active = order != NpcOrder.STAY;
		homeButton.active = order != NpcOrder.HOME && handler.hasHome();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
		AbstractNpcEntity npc = handler.getNpc();
		if (npc != null) {
			InventoryScreen.drawEntity(context, x + 26, y + 17, x + 76, y + 89, 30, 0.0625F, mouseX, mouseY, npc);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 8, 6, TEXT, false);
		context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, TEXT, false);

		int infoX = NpcScreenHandler.PROVISIONS_X - 1;
		Text health = Text.translatable("gui.evolvedwarfare.health",
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(handler.health())),
				String.format(java.util.Locale.ROOT, "%.0f", handler.maxHealth()));
		context.drawText(textRenderer, health, infoX, 18, TEXT, false);

		NpcOrder order = handler.order();
		Text orderText = order == NpcOrder.HOME && !handler.hasHome()
				? Text.translatable("order.evolvedwarfare.stay")
				: Text.translatable(order.translationKey());
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.order", orderText), infoX, 28, TEXT, false);

		if (handler.unpaidDays() > 0) {
			int left = AbstractNpcEntity.MAX_UNPAID_DAYS - handler.unpaidDays();
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.unpaid", left), infoX, 38, WARN, false);
		} else {
			int days = handler.provisionDays();
			context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.paid_days", days), infoX, 38, days > 0 ? GOOD : WARN, false);
		}
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.provisions"), infoX, 52, TEXT, false);
	}
}
