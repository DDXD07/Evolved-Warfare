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
import net.minecraft.block.BlockState;
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
							blocks.renderBlockAsEntity(s, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
							matrices.pop();
						}
					}
				}
			}
			matrices.pop();
			super.render(car, yaw, tickDelta, matrices, consumers, light);
		}
	}
}
