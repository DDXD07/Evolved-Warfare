package com.evolvedwarfare.client.anim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Las hachas de siempre (fase 84) tienen dos golpes distintos, en primera y en tercera persona:
 * <ul>
 * <li><b>Talar</b> (cuando el golpe va contra madera: troncos, tablones, todo lo que se corta con hacha): el hacha sube
 * por encima del hombro y baja de golpe, de arriba abajo, con las dos manos si la otra va libre.</li>
 * <li><b>Atacar</b> (contra un bicho, o al aire): un tajo de lado, de fuera hacia dentro, girando el cuerpo.</li>
 * </ul>
 * Se decide al empezar cada golpe mirando a qué apunta (y no cambia a medio golpe).
 */
public final class AxeSwing {
	private static final class Swing {
		float last = 2.0F;
		boolean chop;
	}

	private static final Map<Integer, Swing> SWINGS = new HashMap<>();

	// {progreso, dx, dy, dz, pitch, yaw, roll}: primera persona, en bloques y grados
	private static final float[][] CHOP_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.3F, 0.02F, 0.16F, 0.08F, 38F, 4F, -6F},
			{0.55F, 0F, -0.16F, -0.16F, -95F, 0F, 0F},
			{0.75F, 0F, -0.12F, -0.1F, -70F, 0F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] ATTACK_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.25F, 0.18F, 0.06F, 0.04F, -10F, 45F, 20F},
			{0.55F, -0.32F, -0.04F, -0.12F, -35F, -60F, -30F},
			{0.75F, -0.24F, -0.04F, -0.08F, -25F, -45F, -20F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	// {progreso, brazo pitch, brazo yaw, brazo roll, cuerpo pitch, cuerpo yaw}: tercera persona, en grados
	private static final float[][] CHOP_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F},
			{0.3F, -172F, 6F, 0F, -6F, 0F},
			{0.55F, -42F, 0F, 0F, 14F, 0F},
			{0.75F, -38F, 0F, 0F, 10F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] ATTACK_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F},
			{0.25F, -95F, 60F, -10F, 0F, 22F},
			{0.55F, -80F, -55F, 0F, 4F, -28F},
			{0.75F, -60F, -40F, 0F, 2F, -18F},
			{1F, -18F, 0F, 0F, 0F, 0F},
	};

	private AxeSwing() {
	}

	public static boolean isAxe(LivingEntity entity, Hand hand) {
		return isPlainAxe(entity.getStackInHand(hand));
	}

	/** Un hacha de las de siempre (no el hacha de batalla, que tiene sus golpes de arma). */
	public static boolean isPlainAxe(net.minecraft.item.ItemStack stack) {
		return stack.getItem() instanceof AxeItem && com.evolvedwarfare.weapon.MedievalWeapons.typeOf(stack) == null;
	}

	/** ¿Este golpe es para talar? (se decide al empezar el golpe) */
	public static boolean chopping(LivingEntity entity, float progress) {
		if (SWINGS.size() > 256) {
			SWINGS.clear();
		}
		Swing s = SWINGS.computeIfAbsent(entity.getId(), id -> new Swing());
		if (progress < s.last) {
			s.chop = aimsAtWood(entity);
		}
		s.last = progress;
		return s.chop;
	}

	private static boolean aimsAtWood(LivingEntity entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == client.player && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
			return false;
		}
		double reach = entity instanceof PlayerEntity p ? p.getBlockInteractionRange() : 4.5;
		HitResult hit = entity.raycast(reach, client.getRenderTickCounter().getTickDelta(true), false);
		if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockState state = entity.getWorld().getBlockState(bh.getBlockPos());
		return state.isIn(BlockTags.AXE_MINEABLE) || state.isIn(BlockTags.LOGS);
	}

	private static float[] sample(float[][] keys, float t) {
		int n = keys[0].length - 1;
		float[] out = new float[n];
		if (t <= keys[0][0]) {
			System.arraycopy(keys[0], 1, out, 0, n);
			return out;
		}
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i], b = keys[i + 1];
			if (t <= b[0]) {
				float u = (t - a[0]) / (b[0] - a[0]);
				u = u * u * (3 - 2 * u);
				for (int k = 0; k < n; k++) {
					out[k] = MathHelper.lerp(u, a[k + 1], b[k + 1]);
				}
				return out;
			}
		}
		System.arraycopy(keys[keys.length - 1], 1, out, 0, n);
		return out;
	}

	/** Primera persona: en vez del golpe de siempre (lo llama HeldItemRendererAxeMixin). */
	public static void firstPerson(MatrixStack m, LivingEntity entity, Arm arm, float progress) {
		int side = arm == Arm.RIGHT ? 1 : -1;
		float[] k = sample(chopping(entity, progress) ? CHOP_FP : ATTACK_FP, progress);
		m.translate(side * k[0], k[1], k[2]);
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * k[4]));
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(k[3]));
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * k[5]));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
	}

	/** Tercera persona (lo llama BipedEntityModelMixin, después de las armas del mod). */
	public static void pose(BipedEntityModel<?> model, LivingEntity entity) {
		float progress = model.handSwingProgress;
		if (progress <= 0.0F) {
			return;
		}
		Hand hand = entity.preferredHand == null ? Hand.MAIN_HAND : entity.preferredHand;
		if (!isAxe(entity, hand)) {
			return;
		}
		boolean chop = chopping(entity, progress);
		float[] k = sample(chop ? CHOP_TP : ATTACK_TP, progress);
		Arm arm = hand == Hand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
		ModelPart main = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart off = arm == Arm.RIGHT ? model.leftArm : model.rightArm;
		Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
		float r = MathHelper.RADIANS_PER_DEGREE;
		float bodyYaw = k[4] * side * r;
		model.body.yaw = bodyYaw;
		model.body.pitch += k[3] * r;
		model.rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
		model.rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
		model.leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
		model.leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
		float sneak = model.sneaking ? 0.4F : 0.0F;
		main.pitch = k[0] * r + sneak;
		main.yaw = k[1] * side * r + bodyYaw;
		main.roll = k[2] * side * r;
		if (chop && entity.getStackInHand(otherHand).isEmpty() && !entity.isUsingItem()) {
			// talando, con las dos manos en el mango
			off.pitch = main.pitch;
			off.yaw = main.yaw - 0.45F * side;
			off.roll = -main.roll;
		} else {
			off.yaw += bodyYaw;
		}
	}
}
