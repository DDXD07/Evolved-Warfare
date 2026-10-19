package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.BanditEntity;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * Patrullas y asaltos: los bandidos caminan hacia su destino por tramos de 16 bloques (así no dependen
 * del alcance máximo de búsqueda de caminos). Al llegar, el bandido sigue su rutina.
 */
public class MarchGoal extends Goal {
	private static final double ARRIVED_SQ = 8.0 * 8.0;
	private static final double STEP = 16.0;

	private final BanditEntity bandit;
	private int repathTimer;

	public MarchGoal(BanditEntity bandit) {
		this.bandit = bandit;
		setControls(EnumSet.of(Goal.Control.MOVE));
	}

	private boolean marching() {
		BlockPos target = bandit.getMarchTarget();
		if (target == null || bandit.getTarget() != null) {
			return false;
		}
		if (bandit.squaredDistanceTo(Vec3d.ofBottomCenter(target)) <= ARRIVED_SQ) {
			bandit.arrived();
			return false;
		}
		return true;
	}

	@Override
	public boolean canStart() {
		return marching();
	}

	@Override
	public boolean shouldContinue() {
		return marching();
	}

	@Override
	public void start() {
		repathTimer = 0;
	}

	@Override
	public void tick() {
		if (--repathTimer > 0 && !bandit.getNavigation().isIdle()) {
			return;
		}
		repathTimer = 40;
		BlockPos target = bandit.getMarchTarget();
		if (target == null) {
			return;
		}
		Vec3d from = bandit.getPos();
		Vec3d to = Vec3d.ofBottomCenter(target);
		Vec3d delta = to.subtract(from);
		double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		Vec3d step = length > STEP ? from.add(delta.x / length * STEP, 0.0, delta.z / length * STEP) : to;
		int x = (int) Math.floor(step.x);
		int z = (int) Math.floor(step.z);
		int y = bandit.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		bandit.getNavigation().startMovingTo(x + 0.5, y, z + 0.5, 1.0);
	}
}
