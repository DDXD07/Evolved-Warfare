package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.SoldierEntity;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Soldados del jugador: atacan a quien ataque a su dueño ('defend') o a quien su dueño ataque
 * (como hacen los lobos domesticados).
 */
public class OwnerTargetGoal extends TrackTargetGoal {
	private final SoldierEntity soldier;
	private final boolean defend;
	private LivingEntity candidate;
	private int lastTime;

	public OwnerTargetGoal(SoldierEntity soldier, boolean defend) {
		super(soldier, false);
		this.soldier = soldier;
		this.defend = defend;
		setControls(EnumSet.of(Goal.Control.TARGET));
	}

	@Override
	public boolean canStart() {
		PlayerEntity owner = soldier.getOwner();
		if (owner == null || !soldier.fights()) {
			return false;
		}
		LivingEntity target = defend ? owner.getAttacker() : owner.getAttacking();
		int time = defend ? owner.getLastAttackedTime() : owner.getLastAttackTime();
		if (target == null || time == lastTime || !soldier.canFight(target)) {
			return false;
		}
		candidate = target;
		return canTrack(target, TargetPredicate.DEFAULT);
	}

	@Override
	public void start() {
		mob.setTarget(candidate);
		PlayerEntity owner = soldier.getOwner();
		if (owner != null) {
			lastTime = defend ? owner.getLastAttackedTime() : owner.getLastAttackTime();
		}
		super.start();
	}
}
