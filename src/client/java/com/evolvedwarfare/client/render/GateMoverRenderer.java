package com.evolvedwarfare.client.render;

import com.evolvedwarfare.block.GateMoverEntity;
import java.util.List;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dibuja el rastrillo o el puente levadizo que se está moviendo: sus mismos bloques, desplazados (el rastrillo) o girados
 * sobre la bisagra (el puente), con la luz del sitio por donde pasan.
 */
public class GateMoverRenderer extends EntityRenderer<GateMoverEntity> {
	public GateMoverRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public Identifier getTexture(GateMoverEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}

	/** Se ve aunque el centro de la entidad (una esquina de la puerta) quede fuera de la pantalla. */
	@Override
	public boolean shouldRender(GateMoverEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(GateMoverEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light) {
		List<BlockPos> blocks = entity.blocks();
		List<BlockState> states = entity.states();
		if (blocks.isEmpty() || blocks.size() != states.size()) {
			return;
		}
		BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
		Vec3d base = new Vec3d(MathHelper.lerp(tickDelta, entity.prevX, entity.getX()), MathHelper.lerp(tickDelta, entity.prevY, entity.getY()),
				MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()));
		if (entity.isSwing()) {
			Direction out = entity.out();
			// eje de giro: a lo largo de la bisagra, para que al girar la punta pase de "hacia fuera" a "hacia arriba"
			Vector3f axis = new Vector3f(out.getOffsetX(), out.getOffsetY(), out.getOffsetZ()).cross(new Vector3f(0.0F, 1.0F, 0.0F));
			float angle = entity.angle(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
			Quaternionf rotation = new Quaternionf().rotationAxis(angle, axis.x, axis.y, axis.z);
			for (int i = 0; i < blocks.size(); i++) {
				BlockPos rel = blocks.get(i);
				int k = rel.getX() * out.getOffsetX() + rel.getZ() * out.getOffsetZ();
				BlockPos side = rel.offset(out, -k);
				// el centro del bloque: el de su sitio en la bisagra, girado k bloques hacia fuera
				Vector3f arm = new Vector3f(out.getOffsetX() * k, 0.0F, out.getOffsetZ() * k).rotate(rotation);
				Vec3d centre = base.add(side.getX() + 0.5 + arm.x, side.getY() + 0.5 + arm.y, side.getZ() + 0.5 + arm.z);
				matrices.push();
				matrices.translate(side.getX() + 0.5, side.getY() + 0.5, side.getZ() + 0.5);
				matrices.multiply(rotation);
				matrices.translate(out.getOffsetX() * k - 0.5, -0.5, out.getOffsetZ() * k - 0.5);
				manager.renderBlockAsEntity(states.get(i), matrices, vertexConsumers, light(entity, centre), OverlayTexture.DEFAULT_UV);
				matrices.pop();
			}
		} else {
			float offset = entity.offset(tickDelta);
			for (int i = 0; i < blocks.size(); i++) {
				BlockPos rel = blocks.get(i);
				Vec3d centre = base.add(rel.getX() + 0.5, rel.getY() + 0.5 + offset, rel.getZ() + 0.5);
				matrices.push();
				matrices.translate(rel.getX(), rel.getY() + offset, rel.getZ());
				manager.renderBlockAsEntity(states.get(i), matrices, vertexConsumers, light(entity, centre), OverlayTexture.DEFAULT_UV);
				matrices.pop();
			}
		}
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	private static int light(GateMoverEntity entity, Vec3d at) {
		return WorldRenderer.getLightmapCoordinates(entity.getWorld(), BlockPos.ofFloored(at));
	}
}
