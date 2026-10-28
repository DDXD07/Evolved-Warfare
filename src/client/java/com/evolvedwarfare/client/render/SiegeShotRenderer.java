package com.evolvedwarfare.client.render;

import com.evolvedwarfare.siege.SiegeShotEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Piedras que giran en el aire, la vasija incendiaria y el virote (orientado hacia donde vuela). */
public class SiegeShotRenderer extends EntityRenderer<SiegeShotEntity> {
	private static final BlockState SHAFT = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);

	private final BlockRenderManager blocks;

	public SiegeShotRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.blocks = context.getBlockRenderManager();
		this.shadowRadius = 0.25F;
	}

	@Override
	public Identifier getTexture(SiegeShotEntity entity) {
		return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	}

	private void box(MatrixStack m, VertexConsumerProvider v, int light, BlockState state, double x0, double y0, double z0,
			double x1, double y1, double z1) {
		m.push();
		m.translate(x0, y0, z0);
		m.scale((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0));
		blocks.renderBlockAsEntity(state, m, v, light, OverlayTexture.DEFAULT_UV);
		m.pop();
	}

	@Override
	public void render(SiegeShotEntity shot, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		float spin = (shot.age + tickDelta) * 18.0F;
		switch (shot.getKind()) {
			case STONE, BOULDER -> {
				double half = shot.getKind() == SiegeShotEntity.Kind.BOULDER ? 0.42 : 0.26;
				matrices.translate(0.0, half, 0.0);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin * 0.7F));
				box(matrices, vertices, light, Blocks.COBBLESTONE.getDefaultState(), -half, -half, -half, half, half, half);
			}
			case FIRE_POT -> {
				matrices.translate(0.0, 0.2, 0.0);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
				box(matrices, vertices, light, Blocks.ORANGE_TERRACOTTA.getDefaultState(), -0.2, -0.2, -0.2, 0.2, 0.2, 0.2);
				box(matrices, vertices, 0xF000F0, Blocks.MAGMA_BLOCK.getDefaultState(), -0.12, 0.2, -0.12, 0.12, 0.26, 0.12);
			}
			case BOLT -> {
				float shotYaw = MathHelper.lerp(tickDelta, shot.prevYaw, shot.getYaw());
				float pitch = MathHelper.lerp(tickDelta, shot.prevPitch, shot.getPitch());
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(shotYaw));
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
				box(matrices, vertices, light, SHAFT, -0.05, -0.05, -0.75, 0.05, 0.05, 0.6);
				box(matrices, vertices, light, Blocks.IRON_BLOCK.getDefaultState(), -0.07, -0.07, 0.6, 0.07, 0.07, 0.82);
				box(matrices, vertices, light, Blocks.WHITE_WOOL.getDefaultState(), -0.11, -0.01, -0.75, 0.11, 0.01, -0.5);
				box(matrices, vertices, light, Blocks.WHITE_WOOL.getDefaultState(), -0.01, -0.11, -0.75, 0.01, 0.11, -0.5);
			}
		}
		matrices.pop();
		super.render(shot, yaw, tickDelta, matrices, vertices, light);
	}
}
