package com.evolvedwarfare.client.hunting;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hunting.BloodStainEntity;
import com.evolvedwarfare.hunting.CarcassEntity;
import com.evolvedwarfare.hunting.Hunting;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** El animal abatido (el animal de verdad, tumbado de lado) y la mancha de sangre en el suelo. */
public final class HuntingRenderers {
	private static final Identifier BLOOD = EvolvedWarfare.id("textures/entity/blood_stain.png");

	private HuntingRenderers() {
	}

	public static void register() {
		EntityRendererRegistry.register(Hunting.CARCASS, CarcassRenderer::new);
		EntityRendererRegistry.register(Hunting.BLOOD_STAIN, StainRenderer::new);
	}

	static class CarcassRenderer extends EntityRenderer<CarcassEntity> {
		private record Dummy(LivingEntity entity, int look) {
		}

		private final Map<Integer, Dummy> dummies = new HashMap<>();

		CarcassRenderer(EntityRendererFactory.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.5F;
		}

		private LivingEntity dummy(CarcassEntity carcass) {
			int look = carcass.look().hashCode() * 31 + carcass.animalType().hashCode();
			Dummy d = dummies.get(carcass.getId());
			if (d != null && d.look() == look && d.entity().getWorld() == carcass.getWorld()) {
				return d.entity();
			}
			if (dummies.size() > 64) {
				dummies.clear();
			}
			LivingEntity made = null;
			try {
				var type = Registries.ENTITY_TYPE.get(carcass.animalType());
				if (type.create(carcass.getWorld()) instanceof LivingEntity living) {
					living.readNbt(carcass.look());
					made = living;
				}
			} catch (Throwable t) {
				made = null;
			}
			dummies.put(carcass.getId(), new Dummy(made, look));
			return made;
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public void render(CarcassEntity carcass, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			// los primeros tics aún se ve al animal muriendo (y cayendo de lado)
			if (carcass.age < 20) {
				return;
			}
			LivingEntity animal = dummy(carcass);
			if (animal == null) {
				return;
			}
			animal.setPosition(carcass.getPos());
			animal.bodyYaw = animal.prevBodyYaw = 180.0F;
			animal.headYaw = animal.prevHeadYaw = 180.0F;
			animal.setPitch(0.0F);
			animal.prevPitch = 0.0F;
			animal.hurtTime = 0;
			animal.deathTime = 0;
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - carcass.getYaw()));
			matrices.translate(0.0F, carcass.animalWidth() * 0.5F, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
			try {
				EntityRenderer r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(animal);
				r.render(animal, 0.0F, tickDelta, matrices, consumers, light);
			} catch (Throwable t) {
				dummies.put(carcass.getId(), new Dummy(null, 0));
			}
			matrices.pop();
		}

		@Override
		public Identifier getTexture(CarcassEntity entity) {
			return BLOOD;
		}
	}

	static class StainRenderer extends EntityRenderer<BloodStainEntity> {
		StainRenderer(EntityRendererFactory.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(BloodStainEntity stain, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
			float a = stain.alpha(tickDelta);
			if (a <= 0.0F) {
				return;
			}
			matrices.push();
			matrices.translate(0.0F, 0.015F, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(stain.getYaw()));
			VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(BLOOD));
			MatrixStack.Entry e = matrices.peek();
			float s = 1.1F;
			int color = ((int) (a * 255.0F) << 24) | 0xFFFFFF;
			vertex(vc, e, -s, -s, 0.0F, 0.0F, color, light);
			vertex(vc, e, -s, s, 0.0F, 1.0F, color, light);
			vertex(vc, e, s, s, 1.0F, 1.0F, color, light);
			vertex(vc, e, s, -s, 1.0F, 0.0F, color, light);
			matrices.pop();
		}

		private static void vertex(VertexConsumer vc, MatrixStack.Entry e, float x, float z, float u, float v, int color, int light) {
			vc.vertex(e, x, 0.0F, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0.0F, 1.0F, 0.0F);
		}

		@Override
		public Identifier getTexture(BloodStainEntity entity) {
			return BLOOD;
		}

		@Override
		public boolean shouldRender(BloodStainEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
			return entity.squaredDistanceTo(x, y, z) < 64 * 64;
		}
	}

	@SuppressWarnings("unused")
	private static boolean unused(Entity e) {
		return false;
	}
}
