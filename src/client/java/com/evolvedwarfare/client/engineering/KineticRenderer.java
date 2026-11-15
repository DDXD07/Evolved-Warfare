package com.evolvedwarfare.client.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.client.hell.BossModel;
import com.evolvedwarfare.engineering.CrankBlock;
import com.evolvedwarfare.engineering.Engineering;
import com.evolvedwarfare.engineering.GaugeBlock;
import com.evolvedwarfare.engineering.KineticBlockEntity;
import com.evolvedwarfare.engineering.ShaftBlock;
import com.evolvedwarfare.engineering.WaterWheelBlock;
import com.evolvedwarfare.engineering.WindmillBlock;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Dibuja la red de fuerza girando: ejes, engranajes, manivelas, ruedas hidráulicas, aspas de molino y la aguja de los
 * medidores. Los modelos giran sobre su eje x; aquí se orientan según el bloque.
 */
public class KineticRenderer implements BlockEntityRenderer<KineticBlockEntity> {
	private static final String[] MODELS = {"kinetic_shaft", "kinetic_small_gear", "kinetic_large_gear", "kinetic_crank", "kinetic_gauge_needle",
			"water_wheel_small", "water_wheel_medium", "water_wheel_large", "windmill_small", "windmill_medium", "windmill_large"};
	private static final Map<String, EntityModelLayer> LAYERS = new HashMap<>();
	private final Map<String, ModelPart> parts = new HashMap<>();

	public static void register() {
		for (String id : MODELS) {
			EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(id), "main");
			LAYERS.put(id, layer);
			EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.texturedModelData(id));
		}
		BlockEntityRendererFactories.register(Engineering.KINETIC_ENTITY, KineticRenderer::new);
	}

	public KineticRenderer(BlockEntityRendererFactory.Context ctx) {
		for (String id : MODELS) {
			parts.put(id, ctx.getLayerModelPart(LAYERS.get(id)));
		}
	}

	@Override
	public boolean rendersOutsideBoundingBox(KineticBlockEntity be) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 128;
	}

	@Override
	public void render(KineticBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider consumers, int light, int overlay) {
		BlockState state = be.getCachedState();
		float angle = MathHelper.lerp(tickDelta, be.prevAngle, be.angle);
		if (Math.abs(be.angle - be.prevAngle) > 180) {
			angle = be.angle;
		}
		BlockPos pos = be.getPos();
		if (state.getBlock() instanceof ShaftBlock shaft) {
			int gear = shaft.gear(state);
			String id = gear == 0 ? "kinetic_shaft" : gear == 1 ? "kinetic_small_gear" : "kinetic_large_gear";
			float offset = gear == 1 && ((pos.getX() + pos.getY() + pos.getZ()) & 1) == 1 ? 22.5F : 0.0F;
			draw(m, consumers, light, id, axisDir(state.get(Properties.AXIS)), angle + offset);
		} else if (state.getBlock() instanceof CrankBlock) {
			Direction f = state.get(Properties.FACING);
			draw(m, consumers, light, "kinetic_crank", f, angle * f.getDirection().offset());
		} else if (state.getBlock() instanceof WaterWheelBlock wheel) {
			String id = "water_wheel_" + (wheel.diameter() <= 3 ? "small" : wheel.diameter() <= 5 ? "medium" : "large");
			draw(m, consumers, light, id, axisDir(state.get(Properties.HORIZONTAL_AXIS)), angle);
		} else if (state.getBlock() instanceof WindmillBlock mill) {
			String id = "windmill_" + (mill.diameter() <= 5 ? "small" : mill.diameter() <= 9 ? "medium" : "large");
			Direction back = state.get(Properties.HORIZONTAL_FACING).getOpposite();
			draw(m, consumers, light, id, back, angle * back.getDirection().offset());
		} else if (state.getBlock() instanceof GaugeBlock gauge) {
			// la aguja, arriba: de −90° (nada) a +90° (al máximo)
			float value = gauge.measuresStress() ? (be.capacity() > 0 ? be.stress() / be.capacity() : 0.0F) : Math.abs(be.speed()) / 32.0F;
			float needle = -90.0F + 180.0F * MathHelper.clamp(value, 0.0F, 1.0F);
			m.push();
			m.translate(0.5, 0.5, 0.5);
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-needle - 90.0F));
			m.scale(1.0F, -1.0F, 1.0F);
			render(m, consumers, light, "kinetic_gauge_needle");
			m.pop();
		}
	}

	private static Direction axisDir(Direction.Axis axis) {
		return Direction.from(axis, Direction.AxisDirection.POSITIVE);
	}

	/** El modelo con su x hacia 'dir', girado 'angle' grados sobre ella. */
	private void draw(MatrixStack m, VertexConsumerProvider consumers, int light, String id, Direction dir, float angle) {
		m.push();
		m.translate(0.5, 0.5, 0.5);
		switch (dir) {
			case EAST -> {
			}
			case WEST -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
			case SOUTH -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
			case NORTH -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
			case UP -> m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
			case DOWN -> m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90));
		}
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
		render(m, consumers, light, id);
		m.pop();
	}

	private void render(MatrixStack m, VertexConsumerProvider consumers, int light, String id) {
		ModelPart root = parts.get(id);
		Identifier tex = EvolvedWarfare.id("textures/entity/engineering/" + id + ".png");
		root.render(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)), light, OverlayTexture.DEFAULT_UV);
	}
}
