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
import com.evolvedwarfare.engineering.BellowsBlock;
import com.evolvedwarfare.engineering.ArchimedesScrewBlock;
import com.evolvedwarfare.engineering.CraneBlock;
import com.evolvedwarfare.engineering.PulleyBlock;
import com.evolvedwarfare.engineering.MachineBlockEntity;
import com.evolvedwarfare.engineering.MillstoneBlock;
import com.evolvedwarfare.engineering.SawmillBlock;
import com.evolvedwarfare.engineering.SideShaftMachineBlock;
import com.evolvedwarfare.engineering.TripHammerBlock;
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
			"water_wheel_small", "water_wheel_medium", "water_wheel_large", "windmill_small", "windmill_medium", "windmill_large", "machine_millstone", "machine_sawmill", "machine_trip_hammer", "machine_bellows",
			"machine_screw", "machine_pulley", "machine_crane", "hoist_platform", "hoist_hook", "hoist_rope",
			"kinetic_cam", "machine_winch", "clock_hand_hour", "clock_hand_minute"};
	private static final Map<String, EntityModelLayer> LAYERS = new HashMap<>();
	private final Map<String, ModelPart> parts = new HashMap<>();

	public static void register() {
		for (String id : MODELS) {
			EntityModelLayer layer = new EntityModelLayer(EvolvedWarfare.id(id), "main");
			LAYERS.put(id, layer);
			EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.texturedModelData(id));
		}
		BlockEntityRendererFactories.register(Engineering.KINETIC_ENTITY, KineticRenderer::new);
		BlockEntityRendererFactories.register(Engineering.MACHINE_ENTITY, KineticRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Engineering.LIFT_PLATFORM_ENTITY, HoistRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Engineering.CRANE_HOOK, HoistRenderer::new);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(Engineering.PORTCULLIS, RenderLayer.getCutout());
		MetalShieldRenderer.register();
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getOrDefault(com.evolvedwarfare.engineering.Metalwork.STEEL, false)) {
				lines.add(Math.min(1, lines.size()), net.minecraft.text.Text.translatable("tooltip.evolvedwarfare.steel")
						.formatted(net.minecraft.util.Formatting.AQUA));
			}
		});
	}

	static EntityModelLayer layer(String id) {
		return LAYERS.get(id);
	}

	private final net.minecraft.client.render.item.ItemRenderer items;

	public KineticRenderer(BlockEntityRendererFactory.Context ctx) {
		items = ctx.getItemRenderer();
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
		final float spin = angle;
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
		} else if (state.getBlock() instanceof MillstoneBlock) {
			machine(m, consumers, light, "machine_millstone", Direction.EAST, part -> {
				part.getChild("rotor").yaw = spin * MathHelper.RADIANS_PER_DEGREE;
			});
		} else if (state.getBlock() instanceof SawmillBlock) {
			Direction f = SideShaftMachineBlock.facing(state);
			machine(m, consumers, light, "machine_sawmill", f, part -> part.getChild("rotor").roll = -spin * 2.0F * MathHelper.RADIANS_PER_DEGREE);
			if (be instanceof MachineBlockEntity mb && !mb.input().isEmpty()) {
				// lo que se sierra, tumbado en la mesa
				m.push();
				m.translate(0.5, 0.8, 0.5);
				m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.asRotation()));
				m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
				m.scale(0.6F, 0.6F, 0.6F);
				items.renderItem(mb.input(), net.minecraft.client.render.model.json.ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, m,
						consumers, be.getWorld(), 0);
				m.pop();
			}
		} else if (state.getBlock() instanceof TripHammerBlock) {
			Direction f = SideShaftMachineBlock.facing(state);
			// cuatro levas por vuelta: sube despacio y cae de golpe
			float t = ((spin % TripHammerBlock.CAM) + TripHammerBlock.CAM) % TripHammerBlock.CAM / TripHammerBlock.CAM;
			if (be.speed() < 0) {
				t = 1.0F - t;
			}
			float lift = t < 0.85F ? t / 0.85F : 1.0F - (t - 0.85F) / 0.15F;
			machine(m, consumers, light, "machine_trip_hammer", f, part -> {
				part.getChild("rotor").roll = -spin * MathHelper.RADIANS_PER_DEGREE;
				part.getChild("arm").roll = -lift * 22.0F * MathHelper.RADIANS_PER_DEGREE;
			});
		} else if (state.getBlock() instanceof BellowsBlock) {
			Direction f = SideShaftMachineBlock.facing(state);
			float open = (1.0F - MathHelper.cos(spin * 2.0F * MathHelper.RADIANS_PER_DEGREE)) * 0.5F;
			machine(m, consumers, light, "machine_bellows", f, part -> {
				part.getChild("rotor").roll = -spin * 2.0F * MathHelper.RADIANS_PER_DEGREE;
				part.getChild("lid").roll = -open * 16.0F * MathHelper.RADIANS_PER_DEGREE;
			});
		} else if (state.getBlock() instanceof ArchimedesScrewBlock) {
			machine(m, consumers, light, "machine_screw", Direction.EAST, part -> part.getChild("rotor").yaw = spin * MathHelper.RADIANS_PER_DEGREE);
		} else if (state.getBlock() instanceof PulleyBlock) {
			machine(m, consumers, light, "machine_pulley", SideShaftMachineBlock.facing(state),
					part -> part.getChild("rotor").roll = -spin * MathHelper.RADIANS_PER_DEGREE);
		} else if (state.getBlock() instanceof CraneBlock) {
			machine(m, consumers, light, "machine_crane", state.get(Properties.HORIZONTAL_FACING),
					part -> part.getChild("rotor").yaw = spin * MathHelper.RADIANS_PER_DEGREE);
		} else if (state.getBlock() instanceof com.evolvedwarfare.engineering.RedstoneCamBlock) {
			draw(m, consumers, light, "kinetic_cam", axisDir(state.get(Properties.AXIS)), spin);
		} else if (state.getBlock() instanceof com.evolvedwarfare.engineering.PortcullisWinchBlock) {
			machine(m, consumers, light, "machine_winch", SideShaftMachineBlock.facing(state),
					part -> part.getChild("rotor").roll = -spin * MathHelper.RADIANS_PER_DEGREE);
		} else if (state.getBlock() instanceof com.evolvedwarfare.engineering.TowerClockBlock && be instanceof MachineBlockEntity clock) {
			// las agujas: con el reloj en marcha, la hora del mundo; parado, la hora en que se paró
			float time = Math.abs(be.speed()) > 0.01F && be.getWorld() != null ? (be.getWorld().getTimeOfDay() % 24000L) + tickDelta : clock.phase();
			float hours = (time / 1000.0F + 6.0F) % 12.0F;
			float minutes = (time % 1000.0F) / 1000.0F;
			Direction face = state.get(Properties.HORIZONTAL_FACING);
			draw(m, consumers, light, "clock_hand_hour", face, -hours / 12.0F * 360.0F);
			draw(m, consumers, light, "clock_hand_minute", face, -minutes * 360.0F);
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

	/** Una máquina: su frente (+x del modelo) hacia 'front', con las piezas movidas por 'pose' (y luego vueltas a su sitio). */
	private void machine(MatrixStack m, VertexConsumerProvider consumers, int light, String id, Direction front,
			java.util.function.Consumer<ModelPart> pose) {
		ModelPart root = parts.get(id);
		m.push();
		m.translate(0.5, 0.5, 0.5);
		switch (front) {
			case WEST -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
			case SOUTH -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
			case NORTH -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
			default -> {
			}
		}
		m.scale(1.0F, -1.0F, 1.0F);
		pose.accept(root);
		render(m, consumers, light, id);
		root.traverse().forEach(ModelPart::resetTransform);
		m.pop();
	}

	private void render(MatrixStack m, VertexConsumerProvider consumers, int light, String id) {
		ModelPart root = parts.get(id);
		Identifier tex = EvolvedWarfare.id("textures/entity/engineering/" + id + ".png");
		root.render(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)), light, OverlayTexture.DEFAULT_UV);
	}
}
