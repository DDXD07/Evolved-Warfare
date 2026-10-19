package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Orden "Seguir": camina detrás de su dueño y, si se queda muy atrás, aparece a su lado. */
public class FollowOwnerNpcGoal extends Goal {
	private static final double START_DISTANCE_SQ = 4.0 * 4.0;
	private static final double STOP_DISTANCE_SQ = 2.5 * 2.5;
	private static final double RUN_DISTANCE_SQ = 10.0 * 10.0;
	private static final double TELEPORT_DISTANCE_SQ = 24.0 * 24.0;

	private final AbstractNpcEntity npc;
	private final double speed;
	private PlayerEntity owner;
	private int repathTimer;

	public FollowOwnerNpcGoal(AbstractNpcEntity npc, double speed) {
		this.npc = npc;
		this.speed = speed;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (npc.getEffectiveOrder() != NpcOrder.FOLLOW) {
			return false;
		}
		PlayerEntity player = npc.getOwner();
		if (player == null || player.isSpectator() || player.getWorld() != npc.getWorld()) {
			return false;
		}
		if (npc.squaredDistanceTo(player) < START_DISTANCE_SQ) {
			return false;
		}
		owner = player;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return npc.getEffectiveOrder() == NpcOrder.FOLLOW
				&& owner != null && owner.isAlive() && !owner.isSpectator()
				&& owner.getWorld() == npc.getWorld()
				&& npc.squaredDistanceTo(owner) > STOP_DISTANCE_SQ;
	}

	@Override
	public void start() {
		repathTimer = 0;
	}

	@Override
	public void stop() {
		owner = null;
		npc.getNavigation().stop();
	}

	@Override
	public void tick() {
		npc.getLookControl().lookAt(owner);
		if (--repathTimer > 0) {
			return;
		}
		repathTimer = 10;
		double distance = npc.squaredDistanceTo(owner);
		if (distance > TELEPORT_DISTANCE_SQ) {
			tryTeleportNearOwner();
		} else {
			npc.getNavigation().startMovingTo(owner, distance > RUN_DISTANCE_SQ ? speed * 1.3 : speed);
		}
	}

	private void tryTeleportNearOwner() {
		World world = npc.getWorld();
		BlockPos center = owner.getBlockPos();
		for (int attempt = 0; attempt < 12; attempt++) {
			int dx = npc.getRandom().nextInt(7) - 3;
			int dz = npc.getRandom().nextInt(7) - 3;
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue; // no aparecer encima del jugador
			}
			int dy = npc.getRandom().nextInt(3) - 1;
			BlockPos pos = center.add(dx, dy, dz);
			if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
					&& world.isAir(pos) && world.isAir(pos.up())) {
				npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, npc.getYaw(), npc.getPitch());
				npc.getNavigation().stop();
				return;
			}
		}
	}
}
