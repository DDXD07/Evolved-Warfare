package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.FactionNpcEntity;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Escolta de una facción aliada: va detrás del jugador al que acompaña (y aparece a su lado si se queda lejos). */
public class EscortFollowGoal extends Goal {
	private static final double START_DISTANCE_SQ = 6.0 * 6.0;
	private static final double STOP_DISTANCE_SQ = 3.0 * 3.0;
	private static final double TELEPORT_DISTANCE_SQ = 32.0 * 32.0;

	private final FactionNpcEntity npc;
	private PlayerEntity player;
	private int repathTimer;

	public EscortFollowGoal(FactionNpcEntity npc) {
		this.npc = npc;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		PlayerEntity escorted = npc.getEscortedPlayer();
		if (escorted == null || npc.getTarget() != null || escorted.getWorld() != npc.getWorld()
				|| npc.squaredDistanceTo(escorted) < START_DISTANCE_SQ) {
			return false;
		}
		player = escorted;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return player != null && player.isAlive() && npc.getTarget() == null && npc.getEscortedPlayer() == player
				&& npc.squaredDistanceTo(player) > STOP_DISTANCE_SQ;
	}

	@Override
	public void start() {
		repathTimer = 0;
	}

	@Override
	public void stop() {
		player = null;
		npc.getNavigation().stop();
	}

	@Override
	public void tick() {
		npc.getLookControl().lookAt(player);
		if (--repathTimer > 0) {
			return;
		}
		repathTimer = 10;
		double distance = npc.squaredDistanceTo(player);
		if (distance > TELEPORT_DISTANCE_SQ) {
			teleportNear();
		} else {
			npc.getNavigation().startMovingTo(player, distance > 12 * 12 ? 1.3 : 1.0);
		}
	}

	private void teleportNear() {
		World world = npc.getWorld();
		BlockPos center = player.getBlockPos();
		for (int attempt = 0; attempt < 12; attempt++) {
			int dx = npc.getRandom().nextInt(9) - 4;
			int dz = npc.getRandom().nextInt(9) - 4;
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			BlockPos pos = center.add(dx, npc.getRandom().nextInt(3) - 1, dz);
			if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
					&& world.isAir(pos) && world.isAir(pos.up())) {
				npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, npc.getYaw(), npc.getPitch());
				npc.getNavigation().stop();
				return;
			}
		}
	}
}
