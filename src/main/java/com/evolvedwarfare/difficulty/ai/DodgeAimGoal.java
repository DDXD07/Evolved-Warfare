package com.evolvedwarfare.difficulty.ai;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/** Si le apuntas con el arco o la ballesta (mirándole de lleno), a veces se echa a un lado de un salto. */
public class DodgeAimGoal extends Goal {
	private final MobEntity mob;
	private final float chance;
	private int cooldown;

	public DodgeAimGoal(MobEntity mob, float chance) {
		this.mob = mob;
		this.chance = chance;
		setControls(EnumSet.of(Control.JUMP));
	}

	/** ¿Le está apuntando? */
	public static boolean aiming(PlayerEntity player, MobEntity mob) {
		ItemStack active = player.getActiveItem();
		boolean ranged = player.isUsingItem() && active.getItem() instanceof BowItem
				|| player.getMainHandStack().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getMainHandStack());
		if (!ranged) {
			return false;
		}
		Vec3d to = mob.getPos().add(0, mob.getHeight() * 0.5, 0).subtract(player.getEyePos());
		double d = to.length();
		return d > 4.0 && d < 32.0 && player.getRotationVec(1.0F).dotProduct(to.normalize()) > 0.985;
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		LivingEntity target = mob.getTarget();
		return target instanceof PlayerEntity player && mob.isOnGround() && aiming(player, mob) && mob.getRandom().nextFloat() < chance;
	}

	@Override
	public void start() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}
		Vec3d to = target.getPos().subtract(mob.getPos());
		Vec3d side = new Vec3d(-to.z, 0, to.x).normalize().multiply(mob.getRandom().nextBoolean() ? 0.55 : -0.55);
		mob.addVelocity(side.x, 0.28, side.z);
		mob.velocityModified = true;
		cooldown = 40 + mob.getRandom().nextInt(40);
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}
}
