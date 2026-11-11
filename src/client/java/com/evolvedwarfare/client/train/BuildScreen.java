package com.evolvedwarfare.client.train;

import com.evolvedwarfare.train.Building;
import com.evolvedwarfare.train.Trains;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * El menú de construcción (como el de ARK): arriba, los materiales (paja, madera, piedra, metal); a la izquierda, las
 * piezas (con cuántas se pueden fabricar con lo que se lleva); a la derecha, la pieza elegida en 3D (girando; se puede
 * arrastrar), su resistencia y lo que cuesta (en verde lo que se tiene, en rojo lo que falta), y los botones de fabricar.
 */
public class BuildScreen extends Screen {
	private static final int W = 336, H = 214;
	private static final int BG = 0xE8101820, PANEL = 0xF0172530, BORDER = 0xFF2E8FB0, ACCENT = 0xFF5FD7FF, TEXT = 0xFFD8F4FF, DIM = 0xFF7FA4B8;
	private static final ItemStack[] MATERIAL_ICONS = {new ItemStack(Items.HAY_BLOCK), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.STONE_BRICKS),
			new ItemStack(Items.IRON_BLOCK)};
	private static int material = 1, kind = 0;
	private float yaw = 35.0F, pitch = 22.0F;
	private boolean dragging;
	private ButtonWidget one, five, max;
	private int x0, y0;

	public BuildScreen() {
		super(Text.translatable("gui.evolvedwarfare.build.title"));
	}

	@Override
	protected void init() {
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		int bx = x0 + 196, by = y0 + H - 26;
		one = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.build.craft"), b -> craft(1)).dimensions(bx, by, 58, 18).build());
		five = addDrawableChild(ButtonWidget.builder(Text.literal("×5"), b -> craft(5)).dimensions(bx + 61, by, 30, 18).build());
		max = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.build.max"), b -> craft(64)).dimensions(bx + 94, by, 38, 18).build());
	}

	private Building.Kind kind() {
		return Building.Kind.values()[kind];
	}

	private Building.Material material() {
		return Building.Material.values()[material];
	}

	private int affordable(Building.Kind k) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player == null) {
			return 0;
		}
		return c.player.getAbilities().creativeMode ? 99 : Building.affordable(c.player.getInventory(), k, material());
	}

	private void craft(int n) {
		ClientPlayNetworking.send(new Trains.CraftPiecePayload(kind, material, n));
	}

	@Override
	public void tick() {
		if (!dragging) {
			yaw += 1.2F;
		}
		int can = affordable(kind());
		one.active = can >= 1;
		five.active = can >= 5;
		max.active = can >= 1;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ dónde está cada cosa

	private int[] tab(int i) {
		return new int[] {x0 + 8 + i * 46, y0 + 22, 44, 22};
	}

	private int[] card(int i) {
		return new int[] {x0 + 8 + (i % 4) * 45, y0 + 52 + (i / 4) * 52, 42, 48};
	}

	private static boolean in(int[] r, double mx, double my) {
		return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	private int[] preview() {
		return new int[] {x0 + 196, y0 + 22, 132, 88};
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = 0; i < 4; i++) {
			if (in(tab(i), mx, my)) {
				material = i;
				click();
				return true;
			}
		}
		for (int i = 0; i < Building.Kind.values().length; i++) {
			if (in(card(i), mx, my)) {
				kind = i;
				click();
				return true;
			}
		}
		if (in(preview(), mx, my)) {
			dragging = true;
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging) {
			yaw += (float) dx * 1.5F;
			pitch = MathHelper.clamp(pitch + (float) dy, -10.0F, 80.0F);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		dragging = false;
		return super.mouseReleased(mx, my, button);
	}

	private void click() {
		MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
				net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.2F));
	}

	// ------------------------------------------------------------------ dibujar

	private static void frame(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
		ctx.fill(x, y, x + w, y + h, fill);
		ctx.fill(x, y, x + w, y + 1, border);
		ctx.fill(x, y + h - 1, x + w, y + h, border);
		ctx.fill(x, y, x + 1, y + h, border);
		ctx.fill(x + w - 1, y, x + w, y + h, border);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x90000000);
		frame(ctx, x0, y0, W, H, BG, BORDER);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(textRenderer, title, x0 + 8, y0 + 8, ACCENT, false);
		Text hint = Text.translatable("gui.evolvedwarfare.build.material." + material().id);
		ctx.drawText(textRenderer, hint, x0 + W - 8 - textRenderer.getWidth(hint), y0 + 8, DIM, false);
		// los materiales
		for (int i = 0; i < 4; i++) {
			int[] r = tab(i);
			boolean sel = i == material, hov = in(r, mouseX, mouseY);
			frame(ctx, r[0], r[1], r[2], r[3], sel ? 0xFF22465A : hov ? 0xFF1C3140 : PANEL, sel ? ACCENT : BORDER);
			ctx.drawItem(MATERIAL_ICONS[i], r[0] + 3, r[1] + 3);
			String name = Text.translatable("gui.evolvedwarfare.build.material." + Building.Material.values()[i].id).getString();
			MatrixStack ms = ctx.getMatrices();
			ms.push();
			ms.translate(r[0] + 21, r[1] + 8, 0);
			ms.scale(0.62F, 0.62F, 1.0F);
			ctx.drawText(textRenderer, name, 0, 0, sel ? TEXT : DIM, false);
			ms.pop();
		}
		// las piezas
		Building.Kind[] kinds = Building.Kind.values();
		int hovered = -1;
		for (int i = 0; i < kinds.length; i++) {
			int[] r = card(i);
			boolean sel = i == kind, hov = in(r, mouseX, mouseY);
			if (hov) {
				hovered = i;
			}
			int can = affordable(kinds[i]);
			frame(ctx, r[0], r[1], r[2], r[3], sel ? 0xFF22465A : hov ? 0xFF1C3140 : PANEL, sel ? ACCENT : can > 0 ? BORDER : 0xFF4A3A3A);
			ItemStack icon = new ItemStack(Trains.pieceItem(kinds[i], material()));
			MatrixStack ms = ctx.getMatrices();
			ms.push();
			ms.translate(r[0] + 9, r[1] + 5, 0);
			ms.scale(1.5F, 1.5F, 1.0F);
			ctx.drawItem(icon, 0, 0);
			ms.pop();
			String count = can >= 99 ? "∞" : String.valueOf(can);
			ctx.drawText(textRenderer, count, r[0] + r[2] - 3 - textRenderer.getWidth(count), r[1] + r[3] - 11, can > 0 ? 0xFF7CFC7C : 0xFFFF7070, true);
		}
		// la elegida: en 3D, su nombre, resistencia y coste
		int[] p = preview();
		frame(ctx, p[0], p[1], p[2], p[3], 0xFF0C141A, BORDER);
		drawPreview(ctx, p);
		int tx = x0 + 196, ty = p[1] + p[3] + 5;
		Text name = new ItemStack(Trains.pieceItem(kind(), material())).getName();
		ctx.drawText(textRenderer, name, tx, ty, TEXT, false);
		int hp = Math.round(material().hp * (kind() == Building.Kind.RAILING ? 0.5F : 1.0F));
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.build.hp", hp), tx, ty + 11, DIM, false);
		List<Building.Cost> costs = Building.cost(kind(), material());
		var player = MinecraftClient.getInstance().player;
		for (int i = 0; i < costs.size(); i++) {
			Building.Cost c = costs.get(i);
			int cx = tx + (i % 2) * 66, cy = ty + 23 + (i / 2) * 18;
			ctx.drawItem(new ItemStack(c.icon()), cx, cy);
			int have = player == null ? 0 : Building.count(player.getInventory(), c.ingredient());
			ctx.drawText(textRenderer, have + "/" + c.count(), cx + 18, cy + 4, have >= c.count() ? 0xFF7CFC7C : 0xFFFF7070, false);
		}
		// el consejo de abajo
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.build.hint1"), x0 + 8, y0 + H - 34, DIM, false);
		ctx.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.build.hint2"), x0 + 8, y0 + H - 22, DIM, false);
		if (hovered >= 0) {
			ctx.drawTooltip(textRenderer, new ItemStack(Trains.pieceItem(kinds[hovered], material())).getName(), mouseX, mouseY);
		}
	}

	private void drawPreview(DrawContext ctx, int[] r) {
		List<Building.Part> parts = Building.parts(kind(), 0, 0, 0, 0, false);
		Box all = null;
		for (Building.Part part : parts) {
			if (part.tex() != Building.Tex.NONE) {
				all = all == null ? part.box() : all.union(part.box());
			}
		}
		if (all == null) {
			return;
		}
		ctx.enableScissor(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(r[0] + r[2] / 2.0F, r[1] + r[3] / 2.0F + 4.0F, 300.0F);
		double extent = Math.max(all.getLengthX(), Math.max(all.getLengthY(), all.getLengthZ()));
		float scale = (float) (Math.min(r[2], r[3]) / (extent * 1.45));
		m.scale(scale, -scale, scale);
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		var c = all.getCenter();
		m.translate(-c.x, -c.y, -c.z);
		VertexConsumerProvider.Immediate consumers = ctx.getVertexConsumers();
		TrainRenderers.drawPiece(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)), parts, material(),
				LightmapTextureManager.MAX_LIGHT_COORDINATE, -1);
		ctx.draw();
		m.pop();
		ctx.disableScissor();
		RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
	}
}
