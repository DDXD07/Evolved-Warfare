package com.evolvedwarfare.client.render;

import com.evolvedwarfare.client.SiegeAimPreview;
import com.evolvedwarfare.client.render.siege.SiegeModels;
import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Las máquinas de asedio con sus modelos 3D ({@link SiegeModels}). Lo que se mueve: el brazo de la catapulta; el brazo,
 * el contrapeso (que cuelga siempre derecho) y la honda del trebuchet; la torreta, los brazos del arco, la nuez y las
 * cuerdas de la balista (que apunta adonde va el tiro); el tronco del ariete, que se balancea de sus cuerdas; y las
 * ruedas, que giran al empujarla. Las cuentas son las mismas que las de tools/ew_siege_preview.py.
 *
 * <p>A la máquina en la que vas montado se le dibuja además el recorrido del tiro y dónde cae
 * ({@link SiegeAimPreview}).</p>
 */
public class SiegeEngineRenderer extends EntityRenderer<SiegeEngineEntity> {
	static final float CATAPULT_COCKED = -60.0F;
	static final float CATAPULT_FIRED = 32.0F;
	static final int CATAPULT_SWING = 3;
	static final float TREBUCHET_COCKED = -140.0F;
	static final float TREBUCHET_FIRED = 40.0F;
	static final int TREBUCHET_SWING = 8;

	private final SiegeKind kind;
	private final ModelPart root;
	private final List<ModelPart> wheels = new ArrayList<>();
	/** Radio de las ruedas, en bloques. */
	private final float wheelRadius;

	@Nullable
	private ModelPart arm;
	@Nullable
	private ModelPart stone;
	@Nullable
	private ModelPart weight;
	@Nullable
	private ModelPart sling;
	@Nullable
	private ModelPart turret;
	@Nullable
	private ModelPart armL;
	@Nullable
	private ModelPart armR;
	@Nullable
	private ModelPart nut;
	@Nullable
	private ModelPart bolt;
	@Nullable
	private ModelPart cord;
	@Nullable
	private ModelPart log;
	@Nullable
	private ModelPart ropeA;
	@Nullable
	private ModelPart ropeB;

	public SiegeEngineRenderer(EntityRendererFactory.Context context, SiegeKind kind, EntityModelLayer layer, float shadow, float wheelRadius) {
		super(context);
		this.kind = kind;
		this.root = context.getPart(layer);
		this.shadowRadius = shadow;
		this.wheelRadius = wheelRadius;
		if (root.hasChild("frame")) {
			ModelPart frame = root.getChild("frame");
			for (String name : new String[] {"wheel_fl", "wheel_fr", "wheel_bl", "wheel_br"}) {
				if (frame.hasChild(name)) {
					wheels.add(frame.getChild(name));
				}
			}
		}
		switch (kind) {
			case CATAPULT -> {
				arm = root.getChild("arm");
				stone = arm.getChild("stone");
			}
			case TREBUCHET -> {
				arm = root.getChild("arm");
				weight = arm.getChild("weight");
				sling = arm.getChild("sling");
				stone = sling.getChild("stone");
			}
			case BALLISTA -> {
				turret = root.getChild("turret");
				armL = turret.getChild("arm_l");
				armR = turret.getChild("arm_r");
				nut = turret.getChild("nut");
				bolt = turret.getChild("bolt");
				cord = root.getChild("cord");
			}
			case RAM -> {
				log = root.getChild("log");
				ropeA = root.getChild("rope_a");
				ropeB = root.getChild("rope_b");
			}
		}
	}

	@Override
	public Identifier getTexture(SiegeEngineEntity entity) {
		return SiegeModels.TEXTURE;
	}

	/** La que manejas se dibuja siempre (aunque la cámara aérea esté lejos), para ver también el tiro. */
	@Override
	public boolean shouldRender(SiegeEngineEntity engine, Frustum frustum, double x, double y, double z) {
		MinecraftClient client = MinecraftClient.getInstance();
		return (client.player != null && engine.hasPassenger(client.player)) || super.shouldRender(engine, frustum, x, y, z);
	}

	@Override
	public void render(SiegeEngineEntity engine, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		float hurt = engine.getHurtTicks() - tickDelta;
		if (hurt > 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(hurt) * hurt * 0.4F));
		}
		int overlay = OverlayTexture.getUv(0.0F, hurt > 0.0F);
		VertexConsumer buffer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(SiegeModels.TEXTURE));
		float spin = MathHelper.lerp(tickDelta, engine.prevWheelTravel, engine.wheelTravel) / wheelRadius;
		for (ModelPart wheel : wheels) {
			wheel.pitch = spin;
		}
		switch (kind) {
			case CATAPULT -> poseCatapult(engine, tickDelta);
			case TREBUCHET -> poseTrebuchet(engine, tickDelta);
			case BALLISTA -> poseBallista(engine, tickDelta);
			case RAM -> poseRam(engine, tickDelta);
		}
		root.render(matrices, buffer, light, overlay);
		if (kind == SiegeKind.BALLISTA) {
			cords(engine, tickDelta, matrices, buffer, light, overlay);
		}
		matrices.pop();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && engine.hasPassenger(client.player)) {
			SiegeAimPreview.render(engine, tickDelta, matrices, vertices);
		}
		super.render(engine, yaw, tickDelta, matrices, vertices, light);
	}

	// ------------------------------------------------------------------ tiempos

	/** Tics desde el último disparo; -1 si está cargada. */
	static float sinceShot(SiegeEngineEntity engine, float tickDelta) {
		int reload = engine.getReload();
		if (reload <= 0) {
			return -1.0F;
		}
		return Math.max(0.0F, engine.getReloadTotal() - reload + tickDelta);
	}

	static float armAngle(float ticks, int total, float cocked, float fired, int swing) {
		if (ticks < 0.0F) {
			return cocked;
		}
		if (ticks < swing) {
			return MathHelper.lerp(ticks / swing, cocked, fired);
		}
		return MathHelper.lerp(Math.min(1.0F, (ticks - swing) / Math.max(1, total - swing)), fired, cocked);
	}

	private static float smooth(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Ángulo de la honda en el mundo (0 = hacia arriba, 90 = hacia delante, 180 = colgando). */
	static float slingAlpha(float ticks, int total) {
		if (ticks < 0.0F) {
			return 110.0F;
		}
		if (ticks < TREBUCHET_SWING) {
			return MathHelper.lerp(smooth(ticks / TREBUCHET_SWING), 110.0F, 60.0F);
		}
		float u = (ticks - TREBUCHET_SWING) / Math.max(1, total - TREBUCHET_SWING);
		if (u < 0.15F) {
			return MathHelper.lerp(smooth(u / 0.15F), 60.0F, 180.0F);
		}
		if (u < 0.85F) {
			return 180.0F;
		}
		return MathHelper.lerp(smooth((u - 0.85F) / 0.15F), 180.0F, 110.0F);
	}

	private static float rad(float degrees) {
		return degrees * MathHelper.RADIANS_PER_DEGREE;
	}

	// ------------------------------------------------------------------ poses

	private void poseCatapult(SiegeEngineEntity engine, float tickDelta) {
		float ticks = sinceShot(engine, tickDelta);
		arm.pitch = rad(armAngle(ticks, engine.getReloadTotal(), CATAPULT_COCKED, CATAPULT_FIRED, CATAPULT_SWING));
		stone.visible = ticks < 0.0F;
	}

	private void poseTrebuchet(SiegeEngineEntity engine, float tickDelta) {
		float ticks = sinceShot(engine, tickDelta);
		int total = engine.getReloadTotal();
		float a = armAngle(ticks, total, TREBUCHET_COCKED, TREBUCHET_FIRED, TREBUCHET_SWING);
		arm.pitch = rad(a);
		// el contrapeso gira al revés que el brazo: así cuelga siempre derecho
		weight.pitch = rad(-a);
		sling.pitch = rad(slingAlpha(ticks, total) + 180.0F - a);
		stone.visible = ticks < 0.0F;
	}

	private void poseRam(SiegeEngineEntity engine, float tickDelta) {
		float strike = Math.max(0.0F, engine.getStrikeTicks() - tickDelta);
		float theta = -35.0F * MathHelper.sqrt(strike / SiegeEngineEntity.STRIKE_TICKS);
		ropeA.pitch = rad(theta);
		ropeB.pitch = rad(theta);
		// el tronco cuelga de las cuerdas (19 píxeles): sube y avanza como un péndulo, sin girar
		log.pivotY = 19.0F * (1.0F - MathHelper.cos(rad(theta)));
		log.pivotZ = -19.0F * MathHelper.sin(rad(theta));
	}

	/** Hacia dónde apunta la balista, respecto a la máquina: {yaw, elevación}. */
	private float[] ballistaAim(SiegeEngineEntity engine, float tickDelta) {
		float engineYaw = MathHelper.lerp(tickDelta, engine.prevYaw, engine.getYaw());
		MinecraftClient client = MinecraftClient.getInstance();
		SiegeBallistics.Aim aim = client.player != null && engine.hasPassenger(client.player) ? SiegeAimPreview.aim() : null;
		float yaw;
		float elevation;
		if (aim != null) {
			yaw = aim.yaw();
			elevation = aim.elevation();
		} else {
			Entity rider = engine.getFirstPassenger();
			if (rider == null) {
				return new float[] {0.0F, 0.0F};
			}
			yaw = rider.getYaw(tickDelta);
			elevation = -rider.getPitch(tickDelta);
		}
		float relYaw = MathHelper.clamp(MathHelper.wrapDegrees(yaw - engineYaw), -kind.yawArc, kind.yawArc);
		return new float[] {relYaw, MathHelper.clamp(elevation, kind.minElevation, kind.maxElevation)};
	}

	private float ballistaPull(SiegeEngineEntity engine, float tickDelta) {
		float ticks = sinceShot(engine, tickDelta);
		return ticks < 0.0F ? 1.0F : Math.min(1.0F, ticks / engine.getReloadTotal());
	}

	private void poseBallista(SiegeEngineEntity engine, float tickDelta) {
		float[] aim = ballistaAim(engine, tickDelta);
		turret.yaw = rad(-aim[0]);
		turret.pitch = rad(-aim[1]);
		float r = ballistaPull(engine, tickDelta);
		float psi = MathHelper.lerp(r, 12.0F, -28.0F);
		armL.yaw = rad(psi);
		armR.yaw = rad(-psi);
		float nutZ = MathHelper.lerp(r, 9.6F, -9.0F);
		nut.pivotZ = nutZ;
		bolt.pivotZ = nutZ;
		bolt.visible = engine.getReload() <= 0;
		cord.visible = false;
	}

	/** Las cuerdas del arco: de la punta de cada brazo a la nuez (estiradas a lo largo que haga falta). */
	private void cords(SiegeEngineEntity engine, float tickDelta, MatrixStack matrices, VertexConsumer buffer, int light, int overlay) {
		float r = ballistaPull(engine, tickDelta);
		float psi = rad(MathHelper.lerp(r, 12.0F, -28.0F));
		float nutZ = MathHelper.lerp(r, 9.6F, -9.0F);
		cord.visible = true;
		for (int side = -1; side <= 1; side += 2) {
			float tipX = side * (5.0F + 17.0F * MathHelper.cos(psi));
			float tipY = 2.0F;
			float tipZ = 15.5F + 17.0F * MathHelper.sin(psi);
			float dx = -tipX;
			float dy = 5.0F - tipY;
			float dz = nutZ - tipZ;
			float length = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
			matrices.push();
			turret.rotate(matrices);
			matrices.translate(tipX / 16.0F, tipY / 16.0F, tipZ / 16.0F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(dx, dz)));
			matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -Math.asin(dy / length)));
			matrices.scale(1.0F, 1.0F, length);
			cord.render(matrices, buffer, light, overlay);
			matrices.pop();
		}
		cord.visible = false;
	}

	public static SiegeEngineRenderer forKind(EntityRendererFactory.Context context, SiegeKind kind) {
		return switch (kind) {
			case CATAPULT -> new SiegeEngineRenderer(context, kind, SiegeModels.CATAPULT, 1.5F, 6.8F / 16.0F);
			case BALLISTA -> new SiegeEngineRenderer(context, kind, SiegeModels.BALLISTA, 0.8F, 1.0F);
			case TREBUCHET -> new SiegeEngineRenderer(context, kind, SiegeModels.TREBUCHET, 2.6F, 8.0F / 16.0F);
			case RAM -> new SiegeEngineRenderer(context, kind, SiegeModels.RAM, 1.5F, 6.8F / 16.0F);
		};
	}
}
