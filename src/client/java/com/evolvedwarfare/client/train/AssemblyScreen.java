package com.evolvedwarfare.client.train;

import com.evolvedwarfare.train.AssemblyScreenHandler;
import com.evolvedwarfare.train.Building;
import com.evolvedwarfare.train.Trains;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** La mesa de ensamblaje: cada coche del tren con las partes que lleva (en verde las que se tienen) y su botón de montar. */
public class AssemblyScreen extends HandledScreen<AssemblyScreenHandler> {
	private static final int BG = 0xE8101820, PANEL = 0xF0172530, BORDER = 0xFF2E8FB0, ACCENT = 0xFF5FD7FF, TEXT = 0xFFD8F4FF, DIM = 0xFF7FA4B8;
	private final List<ButtonWidget> buttons = new ArrayList<>();

	public AssemblyScreen(AssemblyScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 236;
		this.backgroundHeight = 176;
	}

	@Override
	protected void init() {
		super.init();
		buttons.clear();
		for (int i = 0; i < Trains.assemblies().size(); i++) {
			int id = i;
			buttons.add(addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.assembly.build"), b -> {
				if (client != null && client.interactionManager != null) {
					client.interactionManager.clickButton(handler.syncId, id);
				}
			}).dimensions(x + backgroundWidth - 62, y + 26 + i * 48 + 14, 54, 18).build()));
		}
	}

	@Override
	protected void handledScreenTick() {
		for (int i = 0; i < buttons.size(); i++) {
			boolean creative = client != null && client.player != null && client.player.getAbilities().creativeMode;
			buttons.get(i).active = creative || client != null && client.player != null
					&& Building.affordable(client.player.getInventory(), Trains.assemblies().get(i).needs()) > 0;
		}
	}

	private static void frame(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
		ctx.fill(x, y, x + w, y + h, fill);
		ctx.fill(x, y, x + w, y + 1, border);
		ctx.fill(x, y + h - 1, x + w, y + h, border);
		ctx.fill(x, y, x + 1, y + h, border);
		ctx.fill(x + w - 1, y, x + w, y + h, border);
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		frame(ctx, x, y, backgroundWidth, backgroundHeight, BG, BORDER);
		for (int i = 0; i < Trains.assemblies().size(); i++) {
			frame(ctx, x + 6, y + 22 + i * 48, backgroundWidth - 12, 44, PANEL, BORDER);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		ctx.drawText(textRenderer, title, 8, 8, ACCENT, false);
		var player = client == null ? null : client.player;
		for (int i = 0; i < Trains.assemblies().size(); i++) {
			Trains.Assembly a = Trains.assemblies().get(i);
			int ry = 22 + i * 48;
			MatrixStack ms = ctx.getMatrices();
			ms.push();
			ms.translate(10, ry + 6, 0);
			ms.scale(2.0F, 2.0F, 1.0F);
			ctx.drawItem(new ItemStack(a.result()), 0, 0);
			ms.pop();
			ctx.drawText(textRenderer, new ItemStack(a.result()).getName(), 46, ry + 5, TEXT, false);
			for (int k = 0; k < a.needs().size(); k++) {
				Building.Cost c = a.needs().get(k);
				int cx = 46 + k * 34, cy = ry + 18;
				ctx.drawItem(new ItemStack(c.icon()), cx, cy);
				int have = player == null ? 0 : Building.count(player.getInventory(), c.ingredient());
				String s = have + "/" + c.count();
				ms.push();
				ms.translate(cx + 15, cy + 10, 200);
				ms.scale(0.7F, 0.7F, 1.0F);
				ctx.drawText(textRenderer, s, 0, 0, have >= c.count() ? 0xFF7CFC7C : 0xFFFF7070, true);
				ms.pop();
			}
		}
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.assembly.hint"), 8, backgroundHeight - 12, DIM, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		// las partes: su nombre al pasar el ratón
		for (int i = 0; i < Trains.assemblies().size(); i++) {
			Trains.Assembly a = Trains.assemblies().get(i);
			for (int k = 0; k < a.needs().size(); k++) {
				int cx = x + 46 + k * 34, cy = y + 22 + i * 48 + 18;
				if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
					ctx.drawItemTooltip(textRenderer, new ItemStack(a.needs().get(k).icon()), mouseX, mouseY);
				}
			}
		}
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
