package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

/** En casa no se queda como una estatua: de vez en cuando pasea por los alrededores (radio 5). */
public class WanderNearHomeGoal extends Goal {
	private static final int RADIUS = 5;

	private final AbstractNpcEntity npc;
	private final double speed;

	public WanderNearHomeGoal(AbstractNpcEntity npc, double speed) {
		this.npc = npc;
		this.speed = speed;
		setControls(EnumSet.of(Goal.Control.MOVE));
	}

	@Override
	public boolean canStart() {
		BlockPos home = npc.getHome();
		return npc.getEffectiveOrder() == NpcOrder.HOME && home != null && !npc.isBusy()
				&& npc.getRandom().nextInt(120) == 0
				&& npc.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) <= (RADIUS + 1) * (RADIUS + 1);
	}

	@Override
	public boolean shouldContinue() {
		return npc.getEffectiveOrder() == NpcOrder.HOME && !npc.getNavigation().isIdle();
	}

	@Override
	public void start() {
		BlockPos home = npc.getHome();
		if (home == null) {
			return;
		}
		int dx = npc.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
		int dz = npc.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
		npc.getNavigation().startMovingTo(home.getX() + dx + 0.5, home.getY(), home.getZ() + dz + 0.5, speed);
	}
}
