package com.evolvedwarfare.client.wood;

import com.evolvedwarfare.wood.FallingTreeEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** El árbol que cae: sus bloques, girando sobre el borde de la base. */
public class FallingTreeRenderer extends EntityRenderer<FallingTreeEntity> {
	public FallingTreeRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.0F;
	}

	@Override
	public boolean shouldRender(FallingTreeEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 160 * 160;
	}

	@Override
	public Identifier getTexture(FallingTreeEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public void render(FallingTreeEntity tree, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		int[] states = tree.states();
		int[] rel = tree.rel();
		if (states.length == 0 || states.length != rel.length) {
			return;
		}
		BlockPos origin = tree.origin();
		Direction dir = tree.direction();
		Vec3d pivot = FallingTreeEntity.pivot(origin, dir);
		float angle = FallingTreeEntity.angle(tree.age + tickDelta, tree.duration());
		BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
		matrices.push();
		// el eje: arriba x dirección (el árbol se vence hacia "dir")
		matrices.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(angle), dir.getOffsetZ(), 0.0F, -dir.getOffsetX()));
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (int i = 0; i < states.length; i++) {
			BlockState state = Block.getStateFromRawId(states[i]);
			int dx = FallingTreeEntity.unpackX(rel[i]);
			int dy = FallingTreeEntity.unpackY(rel[i]);
			int dz = FallingTreeEntity.unpackZ(rel[i]);
			at.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
			int blockLight = WorldRenderer.getLightmapCoordinates(tree.getWorld(), at);
			matrices.push();
			matrices.translate(at.getX() - pivot.x, at.getY() - pivot.y, at.getZ() - pivot.z);
			blocks.renderBlockAsEntity(state, matrices, consumers, blockLight, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		matrices.pop();
		super.render(tree, yaw, tickDelta, matrices, consumers, light);
	}
}
