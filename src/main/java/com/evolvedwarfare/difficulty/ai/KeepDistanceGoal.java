package com.evolvedwarfare.difficulty.ai;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;

/** El arquero no se deja alcanzar: si te acercas a menos de 5 bloques, se aparta unos pasos y vuelve a disparar. */
public class KeepDistanceGoal extends Goal {
	private final PathAwareEntity mob;
	private int cooldown, ticks;

	public KeepDistanceGoal(PathAwareEntity mob) {
		this.mob = mob;
		setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.squaredDistanceTo(target) < 25.0 && mob.canSee(target) && mob.isOnGround();
	}

	@Override
	public void start() {
		LivingEntity target = mob.getTarget();
		Vec3d away = target == null ? null : NoPenaltyTargeting.findFrom(mob, 8, 4, target.getPos());
		if (away != null) {
			mob.getNavigation().startMovingTo(away.x, away.y, away.z, 1.3);
		}
		ticks = 0;
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity target = mob.getTarget();
		return ++ticks < 40 && target != null && !mob.getNavigation().isIdle() && mob.squaredDistanceTo(target) < 81.0;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getLookControl().lookAt(target, 30.0F, 30.0F);
		}
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		cooldown = 30 + mob.getRandom().nextInt(30);
	}
}
