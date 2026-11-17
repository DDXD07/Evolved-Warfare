package com.evolvedwarfare.difficulty.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.CreeperEntity;

/** El creeper que no puede llegar hasta ti (estás encerrado, a menos de 4,5 bloques y sin verle) revienta la pared. */
public class CreeperBreachGoal extends Goal {
	private final CreeperEntity creeper;
	private int check;

	public CreeperBreachGoal(CreeperEntity creeper) {
		this.creeper = creeper;
	}

	@Override
	public boolean canStart() {
		if (check-- > 0) {
			return false;
		}
		check = 20;
		LivingEntity target = creeper.getTarget();
		if (target == null || !target.isAlive() || creeper.isIgnited() || creeper.squaredDistanceTo(target) > 4.5 * 4.5 || creeper.canSee(target)) {
			return false;
		}
		Path path = creeper.getNavigation().findPathTo(target, 0);
		return path == null || !path.reachesTarget();
	}

	@Override
	public void start() {
		creeper.ignite();
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}
}
