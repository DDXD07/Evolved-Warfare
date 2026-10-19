package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.building.BuildState;
import com.evolvedwarfare.entity.WorkerEntity;
import com.evolvedwarfare.work.WorkAction;
import com.evolvedwarfare.work.WorkContext;
import com.evolvedwarfare.work.WorkStatus;
import java.util.EnumSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * El trabajo de un trabajador: pide a su oficio la siguiente tarea, va hasta el sitio, trabaja el tiempo que toque
 * (moviendo el brazo y, si rompe un bloque, con las grietas) y al final hace el cambio. Si no llega a un sitio en
 * 12 segundos lo deja para más tarde.
 */
public class WorkGoal extends Goal {
	private static final int GIVE_UP_TICKS = 240;
	private static final double SPEED = 0.9;

	private final WorkerEntity worker;
	@Nullable
	private WorkAction action;
	private int ticks;
	private boolean working;
	private int nextPlan;
	@Nullable
	private BlockPos crackPos;

	public WorkGoal(WorkerEntity worker) {
		this.worker = worker;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (worker.age < nextPlan) {
			return false;
		}
		nextPlan = worker.age + 20;
		if (!worker.mayWork()) {
			return false;
		}
		WorkContext ctx = worker.createContext();
		if (ctx == null) {
			return false;
		}
		if (ctx.site.getBuildState() == BuildState.CHOOSING) {
			// primero hay que elegir el edificio (o "Sin edificio") en el menú del puesto
			worker.setWorkStatus(WorkStatus.CHOOSE_BUILDING);
			nextPlan = worker.age + 40;
			return false;
		}
		WorkAction planned = worker.currentJob(ctx).plan(ctx);
		if (planned == null) {
			worker.setWorkStatus(ctx.status());
			// "WORKING" sin tarea = está pasando de capa o mirando más sitios: vuelve a mirar enseguida
			nextPlan = worker.age + (ctx.status() == WorkStatus.WORKING ? 5 : 60);
			return false;
		}
		worker.setWorkStatus(ctx.site.getBuildState() == BuildState.BUILDING ? WorkStatus.BUILDING : WorkStatus.WORKING);
		action = planned;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return action != null && worker.mayWork();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ticks = 0;
		working = false;
		worker.markBusy();
		moveToTarget();
	}

	@Override
	public void stop() {
		clearCrack();
		action = null;
		worker.getNavigation().stop();
	}

	@Override
	public void tick() {
		WorkAction current = action;
		if (current == null) {
			return;
		}
		WorkContext ctx = worker.createContext();
		Entity entity = current.entity();
		if (ctx == null || (entity != null && !entity.isAlive())) {
			finish(5);
			return;
		}
		worker.markBusy();
		ticks++;
		if (!working) {
			double reach = current.reach();
			if (worker.squaredDistanceTo(current.target()) <= reach * reach) {
				working = true;
				ticks = 0;
				worker.getNavigation().stop();
			} else {
				if (ticks % 20 == 0 || (ticks % 5 == 0 && worker.getNavigation().isIdle())) {
					moveToTarget();
				}
				if (ticks > GIVE_UP_TICKS) {
					if (current.remote() || worker.squaredDistanceTo(current.target()) < 64.0) {
						working = true;
						ticks = 0;
						worker.getNavigation().stop();
					} else {
						worker.ignore(current);
						finish(5);
					}
				}
				return;
			}
		}
		Vec3d look = current.look();
		worker.getLookControl().lookAt(look.x, look.y, look.z);
		if (ticks % 8 == 1) {
			worker.swingHand(Hand.MAIN_HAND);
		}
		BlockPos crack = current.breaking();
		if (crack != null && ticks % 2 == 0) {
			crackPos = crack;
			ctx.world.setBlockBreakingInfo(worker.getId(), crack, Math.min(9, ticks * 10 / current.duration()));
		}
		WorkAction.Effect effect = current.effect();
		if (effect != null) {
			effect.tick(ctx, ticks);
		}
		if (ticks >= current.duration()) {
			clearCrack();
			if (current.perform(ctx)) {
				worker.onJobDone();
			}
			finish(4);
		}
	}

	private void finish(int delay) {
		clearCrack();
		action = null;
		nextPlan = worker.age + delay;
	}

	private void moveToTarget() {
		WorkAction current = action;
		if (current == null) {
			return;
		}
		Entity entity = current.entity();
		if (entity != null) {
			worker.getNavigation().startMovingTo(entity, SPEED);
		} else {
			Vec3d target = current.target();
			worker.getNavigation().startMovingTo(target.x, target.y, target.z, SPEED);
		}
	}

	private void clearCrack() {
		if (crackPos != null) {
			worker.getWorld().setBlockBreakingInfo(worker.getId(), crackPos, -1);
			crackPos = null;
		}
	}
}
