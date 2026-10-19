package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

/** Orden "Quieto": se queda en su puesto; si lo ha dejado (por ejemplo, para luchar), vuelve a él. */
public class StayGoal extends Goal {
	private static final double ON_POST_SQ = 1.5 * 1.5;

	private final AbstractNpcEntity npc;
	private int repathTimer;

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
		repathTimer = 0;
	}

	@Override
	public void tick() {
		BlockPos post = npc.getStayPos();
		if (post == null || npc.squaredDistanceTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) <= ON_POST_SQ) {
			if (!npc.getNavigation().isIdle()) {
				npc.getNavigation().stop();
			}
			return;
		}
		if (--repathTimer <= 0) {
			repathTimer = 20;
			npc.getNavigation().startMovingTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
		}
	}
}
