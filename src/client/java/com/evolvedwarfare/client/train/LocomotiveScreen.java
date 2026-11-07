package com.evolvedwarfare.client.train;

import com.evolvedwarfare.train.LocomotiveScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** La caldera: combustible, el fuego (lo que queda), la velocidad y el regulador con sus botones. */
public class LocomotiveScreen extends HandledScreen<LocomotiveScreenHandler> {
	public LocomotiveScreen(LocomotiveScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(0)).dimensions(x + 112, y + 50, 16, 16).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("0"), b -> click(2)).dimensions(x + 130, y + 50, 16, 16).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(1)).dimensions(x + 148, y + 50, 16, 16).build());
	}

	private void click(int id) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, id);
		}
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int x0 = x, y0 = y;
		// el marco (como los de vanilla: gris claro con borde)
		ctx.fill(x0, y0, x0 + backgroundWidth, y0 + backgroundHeight, 0xFF555555);
		ctx.fill(x0 + 1, y0 + 1, x0 + backgroundWidth - 1, y0 + backgroundHeight - 1, 0xFFC6C6C6);
		ctx.fill(x0 + 1, y0 + 1, x0 + backgroundWidth - 2, y0 + 2, 0xFFFFFFFF);
		ctx.fill(x0 + 1, y0 + 1, x0 + 2, y0 + backgroundHeight - 2, 0xFFFFFFFF);
		// los huecos
		for (var slot : handler.slots) {
			int sx = x0 + slot.x - 1, sy = y0 + slot.y - 1;
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
			ctx.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
			ctx.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
			ctx.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
			ctx.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
		}
		// el fuego: una barra de la caldera, roja y naranja, de lo que le queda
		int bx = x0 + 44, by = y0 + 20, bw = 52, bh = 10;
		ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF373737);
		ctx.fill(bx, by, bx + bw, by + bh, 0xFF2A1A12);
		int filled = (int) Math.round(bw * Math.min(1.0, handler.fuel() / (double) handler.maxFuel()));
		for (int i = 0; i < filled; i++) {
			int c = (i / 2) % 2 == 0 ? 0xFFE8701A : 0xFFF0A030;
			ctx.fill(bx + i, by, bx + i + 1, by + bh, c);
		}
		// el regulador: siete marcas, de marcha atrás a toda máquina
		int tx = x0 + 112, ty = y0 + 22;
		for (int n = -2; n <= 4; n++) {
			int mx = tx + (n + 2) * 7;
			int c = n == 0 ? 0xFF606060 : n < 0 ? 0xFF4060A0 : 0xFF60A040;
			ctx.fill(mx, ty, mx + 5, ty + 8, n == handler.throttle() ? 0xFFFFE060 : c);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		super.drawForeground(ctx, mouseX, mouseY);
		int minutes = handler.fuel() / 1200, seconds = handler.fuel() / 20 % 60;
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.locomotive.fuel", minutes, String.format("%02d", seconds)), 44, 33, 0x404040, false);
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.locomotive.speed", handler.speedTenths() / 10, handler.speedTenths() % 10), 112, 33,
				0x404040, false);
		int t = handler.throttle();
		ctx.drawText(textRenderer, Text.translatable("message.evolvedwarfare.train.notch." + (t > 0 ? "forward" : t < 0 ? "reverse" : "neutral"), Math.abs(t)),
				112, 12, 0x404040, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
