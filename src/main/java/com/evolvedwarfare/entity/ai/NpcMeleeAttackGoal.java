package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.CombatNpc;
import com.evolvedwarfare.entity.NpcCombat;
import com.evolvedwarfare.entity.NpcRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;

/**
 * Cuerpo a cuerpo para guerreros, bárbaros y capitanes. Arqueros y ballesteros también lo usan si se
 * quedan sin su arma. No persiguen más allá de su zona (ver {@link CombatNpc#withinLeash}).
 */
public class NpcMeleeAttackGoal extends MeleeAttackGoal {
	private final PathAwareEntity fighter;
	private final CombatNpc npc;

	public <T extends PathAwareEntity & CombatNpc> NpcMeleeAttackGoal(T fighter, double speed) {
		super(fighter, speed, true);
		this.fighter = fighter;
		this.npc = fighter;
	}

	private boolean usable() {
		NpcRole.Kind kind = npc.getRole().kind();
		return switch (kind) {
			case MELEE -> true;
			case BOW -> !NpcCombat.hasBow(fighter);
			case CROSSBOW -> !NpcCombat.hasCrossbow(fighter);
			case HEALER -> false;
		};
	}

	private boolean targetInZone() {
		LivingEntity target = fighter.getTarget();
		if (target != null && !npc.withinLeash(target.getPos())) {
			fighter.setTarget(null);
			return false;
		}
		return true;
	}

	@Override
	public boolean canStart() {
		return usable() && targetInZone() && super.canStart();
	}

	@Override
	public boolean shouldContinue() {
		return usable() && targetInZone() && super.shouldContinue();
	}

	@Override
	protected int getMaxCooldown() {
		return npc.getRole().meleeCooldown();
	}
}
