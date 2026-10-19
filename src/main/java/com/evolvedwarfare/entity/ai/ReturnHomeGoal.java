package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

/** Orden "Ir a casa": camina hasta su casa. */
public class ReturnHomeGoal extends Goal {
	private static final double ARRIVED_SQ = 2.0 * 2.0;

	private final AbstractNpcEntity npc;
	private final double speed;
	private int repathTimer;

	public ReturnHomeGoal(AbstractNpcEntity npc, double speed) {
		this.npc = npc;
		this.speed = speed;
		setControls(EnumSet.of(Goal.Control.MOVE));
	}

	private boolean awayFromHome() {
		BlockPos home = npc.getHome();
		return npc.getEffectiveOrder() == NpcOrder.HOME && home != null
				&& npc.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > ARRIVED_SQ;
	}

	@Override
	public boolean canStart() {
		return awayFromHome();
	}

	@Override
	public boolean shouldContinue() {
		return awayFromHome();
	}

	@Override
	public void start() {
		repathTimer = 0;
	}

	@Override
	public void stop() {
		npc.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (--repathTimer > 0 && !npc.getNavigation().isIdle()) {
			return;
		}
		repathTimer = 40;
		BlockPos home = npc.getHome();
		if (home != null) {
			npc.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, speed);
		}
	}
}
