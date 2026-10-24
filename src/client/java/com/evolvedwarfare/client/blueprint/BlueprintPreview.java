package com.evolvedwarfare.client.blueprint;

import com.evolvedwarfare.blueprint.StoredBlueprint;
import com.evolvedwarfare.building.BlueprintStates;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.EmptyBlockView;

/**
 * Vista previa en 3D de un plano dentro de una pantalla: arrastrar para girar, rueda para acercar. Solo dibuja los
 * bloques que se ven (los que tienen algún lado al aire), hasta {@value #MAX_BLOCKS}.
 */
public final class BlueprintPreview {
	private static final int MAX_BLOCKS = 9000;
	private static final int DEPTH_BUFFER_BIT = 0x100;

	private record PreviewBlock(BlockPos pos, BlockState state) {
	}

	private final List<PreviewBlock> blocks = new ArrayList<>();
	private float centerX;
	private float centerY;
	private float centerZ;
	private float extent = 1.0F;
	private float yaw = 205.0F;
	private float pitch = 28.0F;
	private float zoom = 1.0F;
	private boolean autoSpin = true;

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public void clear() {
		blocks.clear();
	}

	public void set(int sizeX, int sizeY, int sizeZ, List<String> palette, List<Integer> data) {
		blocks.clear();
		BlockState[] states = new BlockState[palette.size()];
		boolean[] opaque = new boolean[palette.size()];
		for (int i = 0; i < states.length; i++) {
			String text = palette.get(i);
			if (StoredBlueprint.isSolid(text)) {
				try {
					states[i] = BlueprintStates.parse(text);
					opaque[i] = states[i].isOpaque() && states[i].isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
				} catch (RuntimeException e) {
					states[i] = null;
				}
			}
		}
		for (int y = 0; y < sizeY && blocks.size() < MAX_BLOCKS; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					int idx = data.get((y * sizeZ + z) * sizeX + x);
					if (idx < 0 || idx >= states.length || states[idx] == null) {
						continue;
					}
					if (opaque[idx] && hidden(x, y, z, sizeX, sizeY, sizeZ, data, opaque)) {
						continue;
					}
					blocks.add(new PreviewBlock(new BlockPos(x, y, z), states[idx]));
				}
			}
		}
		centerX = sizeX / 2.0F;
		centerY = sizeY / 2.0F;
		centerZ = sizeZ / 2.0F;
		extent = Math.max(Math.max(sizeX, sizeZ), sizeY * 1.3F);
	}

	private static boolean hidden(int x, int y, int z, int sx, int sy, int sz, List<Integer> data, boolean[] opaque) {
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] d : dirs) {
			int nx = x + d[0];
			int ny = y + d[1];
			int nz = z + d[2];
			if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
				return false;
			}
			int idx = data.get((ny * sz + nz) * sx + nx);
			if (idx < 0 || idx >= opaque.length || !opaque[idx]) {
				return false;
			}
		}
		return true;
	}

	public void tick() {
		if (autoSpin) {
			yaw += 0.6F;
		}
	}

	public void render(DrawContext context, int px, int py, int pw, int ph) {
		context.fillGradient(px, py, px + pw, py + ph, 0xFF9CC4E4, 0xFFE4EDF2);
		if (blocks.isEmpty()) {
			return;
		}
		BlockRenderManager renderer = MinecraftClient.getInstance().getBlockRenderManager();
		context.enableScissor(px, py, px + pw, py + ph);
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(px + pw / 2.0F, py + ph / 2.0F + 4.0F, 300.0F);
		float scale = Math.min(pw, ph) / (extent * 1.35F) * zoom;
		matrices.scale(scale, -scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.translate(-centerX, -centerY, -centerZ);
		VertexConsumerProvider.Immediate consumers = context.getVertexConsumers();
		for (PreviewBlock block : blocks) {
			matrices.push();
			matrices.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
			renderer.renderBlockAsEntity(block.state(), matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE,
					OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		context.draw();
		matrices.pop();
		context.disableScissor();
		RenderSystem.clear(DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
	}

	public void drag(double dx, double dy) {
		yaw += (float) dx * 1.2F;
		pitch = MathHelper.clamp(pitch + (float) dy * 0.8F, -5.0F, 85.0F);
		autoSpin = false;
	}

	public void scroll(double amount) {
		zoom = MathHelper.clamp(zoom * (amount > 0 ? 1.12F : 1.0F / 1.12F), 0.4F, 5.0F);
	}
}
