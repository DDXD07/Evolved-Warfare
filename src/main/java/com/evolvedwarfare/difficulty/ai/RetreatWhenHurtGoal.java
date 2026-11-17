package com.evolvedwarfare.difficulty.ai;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;

/** Muy herido (menos de un 30 % de vida), huye un rato de su presa; luego vuelve a la carga. */
public class RetreatWhenHurtGoal extends Goal {
	private final PathAwareEntity mob;
	private int cooldown, ticks;

	public RetreatWhenHurtGoal(PathAwareEntity mob) {
		this.mob = mob;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		LivingEntity target = mob.getTarget();
		return target != null && mob.getHealth() < mob.getMaxHealth() * 0.3F && mob.squaredDistanceTo(target) < 144.0;
	}

	@Override
	public void start() {
		ticks = 0;
		flee();
	}

	private void flee() {
		LivingEntity target = mob.getTarget();
		Vec3d away = target == null ? null : NoPenaltyTargeting.findFrom(mob, 16, 6, target.getPos());
		if (away != null) {
			mob.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
		}
	}

	@Override
	public boolean shouldContinue() {
		return ++ticks < 80 && mob.getTarget() != null;
	}

	@Override
	public void tick() {
		if (mob.getNavigation().isIdle() && ticks % 10 == 0) {
			flee();
		}
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		cooldown = 300 + mob.getRandom().nextInt(200);
	}
}
