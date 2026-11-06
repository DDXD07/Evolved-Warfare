package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.IdolBlockEntity;
import com.evolvedwarfare.hell.StructureBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** La cabeza del ídolo: gira (despacio) para mirar al jugador que tiene cerca; con los ojos rojos encendidos. */
public class IdolRenderer implements BlockEntityRenderer<IdolBlockEntity> {
	private static final float H0 = 4.0F / 16.0F;
	private static final float S = 10.0F / 16.0F;

	public IdolRenderer(BlockEntityRendererFactory.Context context) {
	}

	private static Sprite sprite(String name) {
		return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(EvolvedWarfare.id("block/" + name));
	}

	@Override
	public void render(IdolBlockEntity idol, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		float base = 180.0F - idol.getCachedState().get(StructureBlocks.Idol.FACING).asRotation();
		float targetYaw = base, targetPitch = 0.0F;
		boolean watching = false;
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			Vec3d c = idol.getPos().toCenterPos().add(0, 0.4, 0);
			Vec3d eye = player.getCameraPosVec(tickDelta);
			Vec3d d = eye.subtract(c);
			if (d.lengthSquared() < 20 * 20) {
				watching = true;
				targetYaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
				targetPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
				targetPitch = MathHelper.clamp(targetPitch, -35.0F, 35.0F);
			}
		}
		if (Float.isNaN(idol.yaw)) {
			idol.yaw = base;
		}
		idol.yaw += MathHelper.wrapDegrees(targetYaw - idol.yaw) * 0.08F;
		idol.pitch += (targetPitch - idol.pitch) * 0.08F;
		matrices.push();
		matrices.translate(0.5F, H0 + S / 2, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(idol.yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(idol.pitch));
		VertexConsumer b = consumers.getBuffer(RenderLayer.getEntitySolid(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
		MatrixStack.Entry e = matrices.peek();
		Sprite stone = sprite("idol_stone"), face = sprite("idol_face");
		float h = S / 2;
		int faceLight = watching ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		// la cara mira a +z (hacia el jugador tras girar)
		quad(b, e, face, -h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1, faceLight, overlay);
		quad(b, e, stone, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1, light, overlay);
		quad(b, e, stone, h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0, light, overlay);
		quad(b, e, stone, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0, light, overlay);
		quad(b, e, stone, -h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0, light, overlay);
		quad(b, e, stone, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0, light, overlay);
		matrices.pop();
	}

	/** Un cuadrado: abajo-izquierda, abajo-derecha, arriba-derecha, arriba-izquierda. */
	private static void quad(VertexConsumer b, MatrixStack.Entry e, Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
			float z2, float x3, float y3, float z3, int nx, int ny, int nz, int light, int overlay) {
		v(b, e, x0, y0, z0, s.getFrameU(0), s.getFrameV(1), nx, ny, nz, light, overlay);
		v(b, e, x1, y1, z1, s.getFrameU(1), s.getFrameV(1), nx, ny, nz, light, overlay);
		v(b, e, x2, y2, z2, s.getFrameU(1), s.getFrameV(0), nx, ny, nz, light, overlay);
		v(b, e, x3, y3, z3, s.getFrameU(0), s.getFrameV(0), nx, ny, nz, light, overlay);
	}

	private static void v(VertexConsumer b, MatrixStack.Entry e, float x, float y, float z, float u, float v, int nx, int ny, int nz, int light, int overlay) {
		b.vertex(e, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(overlay).light(light).normal(e, nx, ny, nz);
	}
}
