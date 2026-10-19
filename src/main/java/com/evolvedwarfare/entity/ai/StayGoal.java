package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;

/** Orden "Quieto": no se mueve de donde está (puede seguir mirando alrededor). */
public class StayGoal extends Goal {
	private final AbstractNpcEntity npc;

	public StayGoal(AbstractNpcEntity npc) {
		this.npc = npc;
		setControls(EnumSet.of(Goal.Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return npc.getEffectiveOrder() == NpcOrder.STAY;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public void start() {
		npc.getNavigation().stop();
	}
}
