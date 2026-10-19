package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.CombatNpc;
import com.evolvedwarfare.entity.NpcCombat;
import com.evolvedwarfare.entity.NpcRole;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Hand;

/**
 * Arqueros y ballesteros: se colocan a distancia, se mueven de lado para no ser un blanco fácil y se
 * apartan si el enemigo se acerca demasiado.
 * <ul>
 *   <li>Arco: tensa 1 segundo y dispara; repite cada 1-1,5 s.</li>
 *   <li>Ballesta: carga (~1,25 s), apunta un momento y dispara un virote más fuerte.</li>
 * </ul>
 */
public class NpcRangedAttackGoal extends Goal {
	private static final double RANGE = 15.0;
	private static final double RANGE_SQ = RANGE * RANGE;

	private final PathAwareEntity shooter;
	private final CombatNpc npc;
	private int seeTime;
	private int cooldown;
	private int aimTime;
	private boolean loaded;
	private int strafeTimer = -1;
	private boolean strafeLeft;
	private boolean backwards;

	public <T extends PathAwareEntity & CombatNpc> NpcRangedAttackGoal(T shooter) {
		this.shooter = shooter;
		this.npc = shooter;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	private boolean bow() {
		return npc.getRole().kind() == NpcRole.Kind.BOW && NpcCombat.hasBow(shooter);
	}

	private boolean crossbow() {
		return npc.getRole().kind() == NpcRole.Kind.CROSSBOW && NpcCombat.hasCrossbow(shooter);
	}

	private float damageMultiplier() {
		return 1.0F + 0.1F * (npc.getCombatLevel() - 1);
	}

	@Override
	public boolean canStart() {
		LivingEntity target = shooter.getTarget();
		if (!(bow() || crossbow()) || target == null || !target.isAlive()) {
			return false;
		}
		if (!npc.withinLeash(target.getPos())) {
			shooter.setTarget(null);
			return false;
		}
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		shooter.setAttacking(true);
	}

	@Override
	public void stop() {
		shooter.setAttacking(false);
		seeTime = 0;
		strafeTimer = -1;
		if (shooter.isUsingItem()) {
			shooter.clearActiveItem();
		}
	}

	@Override
	public void tick() {
		LivingEntity target = shooter.getTarget();
		if (target == null) {
			return;
		}
		double distance = shooter.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
		boolean canSee = shooter.getVisibilityCache().canSee(target);
		if (canSee != seeTime > 0) {
			seeTime = 0;
		}
		seeTime += canSee ? 1 : -1;

		if (distance <= RANGE_SQ && seeTime >= 20) {
			shooter.getNavigation().stop();
			strafeTimer++;
		} else {
			shooter.getNavigation().startMovingTo(target, 1.0);
			strafeTimer = -1;
		}
		if (strafeTimer >= 20) {
			if (shooter.getRandom().nextFloat() < 0.3F) {
				strafeLeft = !strafeLeft;
			}
			if (shooter.getRandom().nextFloat() < 0.3F) {
				backwards = !backwards;
			}
			strafeTimer = 0;
		}
		if (strafeTimer > -1) {
			if (distance > RANGE_SQ * 0.75) {
				backwards = false;
			} else if (distance < RANGE_SQ * 0.2) {
				backwards = true;
			}
			shooter.getMoveControl().strafeTo(backwards ? -0.5F : 0.5F, strafeLeft ? 0.5F : -0.5F);
			shooter.lookAtEntity(target, 30.0F, 30.0F);
		} else {
			shooter.getLookControl().lookAt(target, 30.0F, 30.0F);
		}

		if (bow()) {
			tickBow(target, canSee);
		} else if (crossbow()) {
			tickCrossbow(target, canSee);
		}
	}

	private void tickBow(LivingEntity target, boolean canSee) {
		if (shooter.isUsingItem()) {
			if (!canSee && seeTime < -60) {
				shooter.clearActiveItem();
			} else if (canSee) {
				int useTime = shooter.getItemUseTime();
				if (useTime >= 20) {
					shooter.clearActiveItem();
					NpcCombat.shootArrow(shooter, target, false, BowItem.getPullProgress(useTime), damageMultiplier());
					cooldown = 20 + shooter.getRandom().nextInt(10);
				}
			}
		} else if (--cooldown <= 0 && seeTime >= -60) {
			shooter.setCurrentHand(Hand.MAIN_HAND);
		}
	}

	private void tickCrossbow(LivingEntity target, boolean canSee) {
		if (!loaded) {
			if (!shooter.isUsingItem()) {
				if (--cooldown <= 0) {
					shooter.setCurrentHand(Hand.MAIN_HAND);
				}
			} else if (shooter.getItemUseTime() >= CrossbowItem.getPullTime(shooter.getActiveItem(), shooter)) {
				shooter.clearActiveItem();
				loaded = true;
				aimTime = 10 + shooter.getRandom().nextInt(10);
			}
		} else if (canSee && --aimTime <= 0) {
			NpcCombat.shootArrow(shooter, target, true, 1.0F, damageMultiplier());
			loaded = false;
			cooldown = 15 + shooter.getRandom().nextInt(10);
		}
	}
}
