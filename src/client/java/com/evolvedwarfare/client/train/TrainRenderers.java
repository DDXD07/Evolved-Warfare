package com.evolvedwarfare.client.train;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.client.hell.BossModel;
import com.evolvedwarfare.train.FlatcarEntity;
import com.evolvedwarfare.train.TrainCarEntity;
import com.evolvedwarfare.train.Trains;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Cómo se ven la locomotora (con sus ruedas y bielas girando) y las plataformas (con lo construido encima). */
public final class TrainRenderers {
	private TrainRenderers() {
	}

	public static void register() {
		TrainCarEntity.ClientAccess.set(() -> MinecraftClient.getInstance().player);
		EntityModelLayer loco = new EntityModelLayer(EvolvedWarfare.id("locomotive"), "main");
		EntityModelLayer flat = new EntityModelLayer(EvolvedWarfare.id("flatcar"), "main");
		EntityModelLayerRegistry.registerModelLayer(loco, () -> BossModel.texturedModelData("locomotive"));
		EntityModelLayerRegistry.registerModelLayer(flat, () -> BossModel.texturedModelData("flatcar"));
		EntityRendererRegistry.register(Trains.LOCOMOTIVE, ctx -> new CarRenderer<>(ctx, loco, "locomotive"));
		EntityRendererRegistry.register(Trains.FLATCAR, ctx -> new CarRenderer<>(ctx, flat, "flatcar"));
	}

	static class CarRenderer<T extends TrainCarEntity> extends EntityRenderer<T> {
		private final ModelPart root;
		private final Identifier texture, glow;
		private final List<ModelPart> wheels = new ArrayList<>();
		private final List<ModelPart> rods = new ArrayList<>();
		private final List<float[]> rodBase = new ArrayList<>();

		CarRenderer(EntityRendererFactory.Context ctx, EntityModelLayer layer, String id) {
			super(ctx);
			this.root = ctx.getPart(layer);
			this.texture = EvolvedWarfare.id("textures/entity/train/" + id + ".png");
			this.glow = EvolvedWarfare.id("textures/entity/train/" + id + "_glow.png");
			ModelPart frame = root.getChild("frame");
			for (int k = 0; k < 4; k++) {
				for (int s : new int[] {0, 2}) {
					if (frame.hasChild("wheel_" + k + "_" + s)) {
						wheels.add(frame.getChild("wheel_" + k + "_" + s));
					}
				}
			}
			for (int s : new int[] {0, 2}) {
				if (frame.hasChild("rod_" + s)) {
					ModelPart rod = frame.getChild("rod_" + s);
					rods.add(rod);
					rodBase.add(new float[] {rod.pivotY, rod.pivotZ});
				}
			}
			this.shadowRadius = 1.5F;
		}

		@Override
		public Identifier getTexture(T entity) {
			return texture;
		}

		@Override
		public void render(T car, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			matrices.push();
			float ry = MathHelper.lerpAngleDegrees(tickDelta, car.prevYaw, car.getYaw());
			float rp = MathHelper.lerp(tickDelta, car.prevPitch, car.getPitch());
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ry));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rp));
			float angle = MathHelper.lerp(tickDelta, car.lastWheelAngle, car.wheelAngle);
			for (ModelPart w : wheels) {
				w.pitch = -angle;
			}
			for (int i = 0; i < rods.size(); i++) {
				float phase = angle + i * MathHelper.HALF_PI;
				rods.get(i).pivotY = rodBase.get(i)[0] + MathHelper.cos(phase) * 2.0F;
				rods.get(i).pivotZ = rodBase.get(i)[1] + MathHelper.sin(phase) * 2.0F;
			}
			matrices.push();
			matrices.translate(0.0F, 3.0F, 0.0F);
			matrices.scale(2.0F, -2.0F, -2.0F);
			root.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light, OverlayTexture.DEFAULT_UV);
			root.render(matrices, consumers.getBuffer(RenderLayer.getEyes(glow)), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			matrices.pop();
			if (car instanceof FlatcarEntity flat) {
				var blocks = MinecraftClient.getInstance().getBlockRenderManager();
				for (int y = 0; y < FlatcarEntity.H; y++) {
					for (int z = 0; z < FlatcarEntity.L; z++) {
						for (int x = 0; x < FlatcarEntity.W; x++) {
							BlockState s = flat.get(x, y, z);
							if (s == null) {
								continue;
							}
							matrices.push();
							matrices.translate(x - FlatcarEntity.W / 2.0, FlatcarEntity.DECK + y, z - FlatcarEntity.L / 2.0);
							if (!s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.block.FluidBlock) {
								fluid(flat, x, y, z, s, matrices, consumers, light);
							} else {
								if (s.getRenderType() == BlockRenderType.MODEL) {
									blocks.renderBlockAsEntity(s, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
								}
								if (s.getBlock() instanceof BlockEntityProvider) {
									blockEntity(s, tickDelta, matrices, consumers, light);
								}
							}
							matrices.pop();
						}
					}
				}
			}
			matrices.pop();
			super.render(car, yaw, tickDelta, matrices, consumers, light);
		}

		/** Los bloques que se dibujan con su "entidad de bloque" (cofres, camas, cajas de shulker, carteles...): una de mentira por tipo de bloque. */
		private final java.util.Map<BlockState, BlockEntity> dummies = new java.util.HashMap<>();
		private final java.util.Set<BlockState> broken = new java.util.HashSet<>();

		@SuppressWarnings({"unchecked", "rawtypes"})
		private void blockEntity(BlockState s, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			if (broken.contains(s)) {
				return;
			}
			try {
				var client = MinecraftClient.getInstance();
				BlockEntity be = dummies.get(s);
				if (be == null) {
					be = ((BlockEntityProvider) s.getBlock()).createBlockEntity(BlockPos.ORIGIN, s);
					if (be == null) {
						broken.add(s);
						return;
					}
					dummies.put(s, be);
				}
				if (be.getWorld() != client.world) {
					be.setWorld(client.world);
				}
				BlockEntityRenderer renderer = client.getBlockEntityRenderDispatcher().get(be);
				if (renderer == null) {
					broken.add(s);
					return;
				}
				renderer.render(be, tickDelta, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
			} catch (Throwable t) {
				broken.add(s);
			}
		}

		/** El agua y la lava: un bloque de su textura (con la superficie un poco más baja si no hay más encima). */
		private void fluid(FlatcarEntity flat, int x, int y, int z, BlockState s, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			var client = MinecraftClient.getInstance();
			Sprite sprite = client.getBlockRenderManager().getModels().getModelParticleSprite(s);
			boolean lava = s.isOf(net.minecraft.block.Blocks.LAVA);
			int color = lava ? 0xFFFFFFFF : 0xC03F76E4;
			int lit = lava ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
			VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
			MatrixStack.Entry e = matrices.peek();
			float top = same(flat, x, y + 1, z, s) ? 1.0F : 0.875F;
			float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();
			if (!same(flat, x, y + 1, z, s)) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 0, top, 0, 0, top, 1, 1, top, 1, 1, top, 0, 0, 1, 0);
			}
			if (!same(flat, x, y - 1, z, s) && y > 0) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
			}
			if (!same(flat, x, y, z - 1, s)) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 0, 0, 0, 0, top, 0, 1, top, 0, 1, 0, 0, 0, 0, -1);
			}
			if (!same(flat, x, y, z + 1, s)) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 1, 0, 1, 1, top, 1, 0, top, 1, 0, 0, 1, 0, 0, 1);
			}
			if (!same(flat, x - 1, y, z, s)) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 0, 0, 1, 0, top, 1, 0, top, 0, 0, 0, 0, -1, 0, 0);
			}
			if (!same(flat, x + 1, y, z, s)) {
				quad(vc, e, color, lit, u0, v0, u1, v1, 1, 0, 0, 1, top, 0, 1, top, 1, 1, 0, 1, 1, 0, 0);
			}
		}

		private static boolean same(FlatcarEntity flat, int x, int y, int z, BlockState s) {
			BlockState o = flat.get(x, y, z);
			return o != null && o.getFluidState().getFluid().matchesType(s.getFluidState().getFluid());
		}

		private static void quad(VertexConsumer vc, MatrixStack.Entry e, int color, int light, float u0, float v0, float u1, float v1, float x0, float y0, float z0,
				float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz) {
			vertex(vc, e, x0, y0, z0, u0, v1, color, light, nx, ny, nz);
			vertex(vc, e, x1, y1, z1, u0, v0, color, light, nx, ny, nz);
			vertex(vc, e, x2, y2, z2, u1, v0, color, light, nx, ny, nz);
			vertex(vc, e, x3, y3, z3, u1, v1, color, light, nx, ny, nz);
			// y por detrás (se ve también desde dentro)
			vertex(vc, e, x3, y3, z3, u1, v1, color, light, -nx, -ny, -nz);
			vertex(vc, e, x2, y2, z2, u1, v0, color, light, -nx, -ny, -nz);
			vertex(vc, e, x1, y1, z1, u0, v0, color, light, -nx, -ny, -nz);
			vertex(vc, e, x0, y0, z0, u0, v1, color, light, -nx, -ny, -nz);
		}

		private static void vertex(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float v, int color, int light, float nx, float ny,
				float nz) {
			vc.vertex(e, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
		}
	}
}
