package com.evolvedwarfare.client.render;

import com.evolvedwarfare.cart.CartEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;

/** Quien tira de una carreta lleva los brazos un poco atrás y hacia fuera, agarrando las varas (sin balancearlos). */
public final class CartPullPose {
	private CartPullPose() {
	}

	public static void apply(BipedEntityModel<?> model, LivingEntity entity) {
		Long seen = CartEntity.CLIENT_PULLERS.get(entity.getId());
		if (seen == null || entity.getWorld().getTime() - seen > 3L || entity.hasVehicle()) {
			return;
		}
		model.rightArm.pitch = 0.18F;
		model.leftArm.pitch = 0.18F;
		model.rightArm.yaw = 0.0F;
		model.leftArm.yaw = 0.0F;
		model.rightArm.roll = 0.14F;
		model.leftArm.roll = -0.14F;
	}
}
