package com.evolvedwarfare.client.screen;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.building.Blueprint;
import com.evolvedwarfare.building.BlueprintGenerator;
import com.evolvedwarfare.building.BlueprintStates;
import com.evolvedwarfare.building.BuildMaterials;
import com.evolvedwarfare.building.BuildState;
import com.evolvedwarfare.building.Material;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.screen.BlueprintScreenHandler;
import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkArea;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Menú del edificio del puesto: vista previa en 3D (se gira arrastrando y se acerca con la rueda) del diseño de la
 * semilla actual, los materiales que necesita y los botones para cambiar de diseño, construirlo o seguir sin él.
 *
 * <p>La vista previa se genera aquí mismo con el mismo código que usa el servidor para la obra, así que lo que se ve
 * es exactamente lo que se construye. El borde de color en el suelo es la zona de trabajo del oficio.</p>
 */
public class BlueprintScreen extends HandledScreen<BlueprintScreenHandler> {
	private static final int W = 324;
	private static final int H = 214;
	private static final int PX = 8;
	private static final int PY = 28;
	private static final int PW = 200;
	private static final int PH = 154;
	private static final int SX = 216;
	private static final int TEXT = 0x404040;
	private static final int MUTED = 0x6A6A6A;
	private static final int GOOD = 0x2E7D32;
	private static final int BAD = 0xC62828;
	private static final int DEPTH_BUFFER_BIT = 0x100;

	private record PreviewBlock(BlockPos pos, BlockState state) {
	}

	private ButtonWidget previousButton;
	private ButtonWidget newSeedButton;
	private ButtonWidget buildButton;
	private ButtonWidget cancelButton;
	private ButtonWidget skipButton;

	private float yaw = 205.0F;
	private float pitch = 28.0F;
	private float zoom = 1.0F;
	private boolean autoSpin = true;
	private int previewSeed = Integer.MIN_VALUE;
	private final List<PreviewBlock> preview = new ArrayList<>();
	private Map<Material, Integer> materials = Map.of();
	private float centerX;
	private float centerY;
	private float centerZ;
	private float extent = 1.0F;

	public BlueprintScreen(BlueprintScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = W;
		this.backgroundHeight = H;
	}

	@Override
	protected void init() {
		super.init();
		int by = y + H - 24;
		previousButton = button("previous", BlueprintScreenHandler.BUTTON_PREVIOUS, x + 8, by, 62);
		newSeedButton = button("new_seed", BlueprintScreenHandler.BUTTON_NEW_SEED, x + 72, by, 70);
		buildButton = button("build", BlueprintScreenHandler.BUTTON_BUILD, x + 144, by, 64);
		cancelButton = button("cancel", BlueprintScreenHandler.BUTTON_CANCEL, x + 144, by, 64);
		skipButton = button("skip", BlueprintScreenHandler.BUTTON_SKIP, x + SX, by, W - SX - 8);
		ButtonWidget site = ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.blueprint.button.site"),
						b -> click(BlueprintScreenHandler.BUTTON_SITE))
				.dimensions(x + W - 64, y + 5, 56, 16).build();
		site.setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.blueprint.button.site.tip")));
		addDrawableChild(site);
		updateButtons();
	}

	private ButtonWidget button(String key, int id, int bx, int by, int width) {
		ButtonWidget button = ButtonWidget.builder(Text.translatable("gui.evolvedwarfare.blueprint.button." + key), b -> click(id))
				.dimensions(bx, by, width, 18).build();
		button.setTooltip(Tooltip.of(Text.translatable("gui.evolvedwarfare.blueprint.button." + key + ".tip")));
		return addDrawableChild(button);
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
		if (autoSpin) {
			yaw += 0.5F;
		}
	}

	private void updateButtons() {
		if (previousButton == null) {
			return;
		}
		BuildState state = handler.state();
		boolean choosing = state == BuildState.CHOOSING || state == BuildState.NONE;
		previousButton.visible = choosing;
		previousButton.active = handler.historySize() > 0;
		newSeedButton.visible = choosing;
		buildButton.visible = choosing;
		cancelButton.visible = state == BuildState.BUILDING;
		skipButton.visible = state == BuildState.CHOOSING;
	}

	// ------------------------------------------------------------------ vista previa

	/** Vuelve a generar la vista previa si ha cambiado la semilla. */
	private void ensurePreview() {
		int seed = handler.seed();
		if (seed == previewSeed) {
			return;
		}
		previewSeed = seed;
		preview.clear();
		Profession profession = handler.getProfession();
		Blueprint blueprint = BlueprintGenerator.generate(profession.id(), seed);
		Set<Long> used = new HashSet<>();
		for (Blueprint.Piece piece : blueprint.pieces()) {
			if (piece.isAir() || piece.state() == null) {
				continue;
			}
			BlockPos pos = new BlockPos(piece.x(), piece.y(), piece.z());
			preview.add(new PreviewBlock(pos, BlueprintStates.parse(piece.state())));
			used.add(pos.asLong());
		}
		// el propio puesto, en el centro
		preview.add(new PreviewBlock(BlockPos.ORIGIN, ModBlocks.WORK_SITES.get(profession).getDefaultState()));
		used.add(BlockPos.ORIGIN.asLong());
		materials = blueprint.materials();

		// tamaño del edificio (sin el cercado) para el suelo de hierba
		int minX = 0;
		int maxX = 0;
		int minZ = 0;
		int maxZ = 0;
		for (Blueprint.Piece piece : blueprint.pieces()) {
			if (!piece.has(Blueprint.GROUND)) {
				minX = Math.min(minX, piece.x());
				maxX = Math.max(maxX, piece.x());
				minZ = Math.min(minZ, piece.z());
				maxZ = Math.max(maxZ, piece.z());
			}
		}
		// borde de la zona de trabajo en el suelo
		WorkArea area = profession.area();
		if (area.exists()) {
			BlockBox box = area.box(BlockPos.ORIGIN, Direction.NORTH);
			BlockState carpet = (area.pit() ? Blocks.ORANGE_CARPET : Blocks.LIME_CARPET).getDefaultState();
			for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
				for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
					boolean edge = x == box.getMinX() || x == box.getMaxX() || z == box.getMinZ() || z == box.getMaxZ();
					BlockPos pos = new BlockPos(x, 0, z);
					if (edge && used.add(pos.asLong())) {
						preview.add(new PreviewBlock(pos, carpet));
					}
				}
			}
		}
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		for (int x = minX - 1; x <= maxX + 1; x++) {
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				BlockPos pos = new BlockPos(x, -1, z);
				if (used.add(pos.asLong())) {
					preview.add(new PreviewBlock(pos, grass));
				}
			}
		}

		// encuadre
		int bx0 = Integer.MAX_VALUE;
		int by0 = Integer.MAX_VALUE;
		int bz0 = Integer.MAX_VALUE;
		int bx1 = Integer.MIN_VALUE;
		int by1 = Integer.MIN_VALUE;
		int bz1 = Integer.MIN_VALUE;
		for (PreviewBlock block : preview) {
			BlockPos p = block.pos();
			bx0 = Math.min(bx0, p.getX());
			by0 = Math.min(by0, p.getY());
			bz0 = Math.min(bz0, p.getZ());
			bx1 = Math.max(bx1, p.getX());
			by1 = Math.max(by1, p.getY());
			bz1 = Math.max(bz1, p.getZ());
		}
		centerX = (bx0 + bx1 + 1) / 2.0F;
		centerY = (by0 + by1 + 1) / 2.0F;
		centerZ = (bz0 + bz1 + 1) / 2.0F;
		extent = Math.max(Math.max(bx1 - bx0 + 1, bz1 - bz0 + 1), (by1 - by0 + 1) * 1.3F);
	}

	private void renderPreview(DrawContext context) {
		ensurePreview();
		MinecraftClient mc = client != null ? client : MinecraftClient.getInstance();
		BlockRenderManager renderer = mc.getBlockRenderManager();
		context.enableScissor(x + PX, y + PY, x + PX + PW, y + PY + PH);
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x + PX + PW / 2.0F, y + PY + PH / 2.0F + 6.0F, 300.0F);
		float scale = Math.min(PW, PH) / (extent * 1.35F) * zoom;
		matrices.scale(scale, -scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.translate(-centerX, -centerY, -centerZ);
		VertexConsumerProvider.Immediate consumers = context.getVertexConsumers();
		for (PreviewBlock block : preview) {
			matrices.push();
			matrices.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
			renderer.renderBlockAsEntity(block.state(), matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE,
					OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		context.draw();
		matrices.pop();
		context.disableScissor();
		// que los bloques de la vista previa no tapen las ayudas que se dibujan después
		RenderSystem.clear(DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
	}

	private boolean inPreview(double mouseX, double mouseY) {
		return mouseX >= x + PX && mouseX < x + PX + PW && mouseY >= y + PY && mouseY < y + PY + PH;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && inPreview(mouseX, mouseY)) {
			yaw += (float) deltaX * 1.2F;
			pitch = MathHelper.clamp(pitch + (float) deltaY * 0.8F, -5.0F, 85.0F);
			autoSpin = false;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inPreview(mouseX, mouseY)) {
			zoom = MathHelper.clamp(zoom * (verticalAmount > 0 ? 1.12F : 1.0F / 1.12F), 0.4F, 4.0F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ------------------------------------------------------------------ dibujo

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		panel(context, x, y, W, H);
		context.fill(x + PX - 1, y + PY - 1, x + PX + PW + 1, y + PY + PH + 1, 0xFF373737);
		context.fillGradient(x + PX, y + PY, x + PX + PW, y + PY + PH, 0xFF9CC4E4, 0xFFE4EDF2);
		renderPreview(context);
	}

	/** Panel gris con bordes, como los de vanilla. */
	private static void panel(DrawContext context, int px, int py, int w, int h) {
		context.fill(px + 1, py, px + w - 1, py + h, 0xFF000000);
		context.fill(px, py + 1, px + w, py + h - 1, 0xFF000000);
		context.fill(px + 1, py + 1, px + w - 1, py + h - 1, 0xFFC6C6C6);
		context.fill(px + 1, py + 1, px + w - 2, py + 3, 0xFFFFFFFF);
		context.fill(px + 1, py + 1, px + 3, py + h - 2, 0xFFFFFFFF);
		context.fill(px + 3, py + h - 3, px + w - 1, py + h - 1, 0xFF555555);
		context.fill(px + w - 3, py + 3, px + w - 1, py + h - 1, 0xFF555555);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, 8, 6, TEXT, false);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.seed", handler.seed()), 8, 17, MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.hint"), PX + 4, PY + PH - 11, 0x505050, false);

		// materiales
		context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.materials"), SX, PY, TEXT, false);
		int row = PY + 11;
		for (Material material : Material.values()) {
			Integer count = materials.get(material);
			if (count == null || count <= 0) {
				continue;
			}
			context.drawItem(new ItemStack(BuildMaterials.icon(material)), SX, row);
			Text line = Text.translatable("gui.evolvedwarfare.blueprint.material_line", count,
					Text.translatable(BuildMaterials.translationKey(material)));
			context.drawText(textRenderer, line, SX + 19, row + 4, TEXT, false);
			row += 18;
		}

		// estado de la obra
		BuildState state = handler.state();
		int sy = H - 44;
		switch (state) {
			case CHOOSING -> context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.state.choosing"), SX, sy, MUTED, false);
			case NONE -> context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.state.none"), SX, sy, MUTED, false);
			case DONE -> context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.state.done"), SX, sy, GOOD, false);
			case BUILDING -> {
				int total = Math.max(1, handler.total());
				int placed = Math.min(handler.placed(), total);
				context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.blueprint.state.building", placed, handler.total()),
						SX, sy - 4, GOOD, false);
				int barW = W - SX - 8;
				context.fill(SX, sy + 6, SX + barW, sy + 10, 0xFF555555);
				context.fill(SX, sy + 6, SX + barW * placed / total, sy + 10, 0xFF4CAF50);
				Material missing = WorkSiteBlockEntity.missingFromNote(handler.note());
				if (missing != null) {
					context.drawText(textRenderer, Text.translatable("gui.evolvedwarfare.site.note.missing",
							Text.translatable(BuildMaterials.translationKey(missing))), SX, sy + 13, BAD, false);
				}
			}
		}
	}
}
