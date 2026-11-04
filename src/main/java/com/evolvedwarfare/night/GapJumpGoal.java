package com.evolvedwarfare.night;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Si hay un hueco de 1 o 2 bloques de largo entre el zombi y su presa, lo salta (si al otro lado hay donde caer). */
public class GapJumpGoal extends Goal {
	private final MobEntity mob;
	private int cooldown;
	private Direction dir = Direction.NORTH;
	private int length;

	public GapJumpGoal(MobEntity mob) {
		this.mob = mob;
	}

	private static boolean solid(World world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return !s.getCollisionShape(world, pos).isEmpty() && s.getFluidState().isEmpty();
	}

	private static boolean free(World world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	/** La longitud del hueco (1 o 2) que hay delante en esa dirección con suelo al otro lado, o 0. */
	public static int gap(World world, BlockPos feet, Direction d) {
		BlockPos first = feet.offset(d);
		if (solid(world, first.down()) || !free(world, first) || !free(world, first.up()) || solid(world, first.down(2))) {
			return 0; // no es un hueco (o es solo un escalón)
		}
		for (int k = 2; k <= 3; k++) {
			BlockPos land = feet.offset(d, k);
			if (!free(world, land) || !free(world, land.up())) {
				return 0; // algo en medio del salto
			}
			if (solid(world, land.down())) {
				return k - 1;
			}
		}
		return 0;
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		LivingEntity prey = mob.getTarget();
		if (prey == null || !mob.isOnGround() || mob.isTouchingWater() || mob.squaredDistanceTo(prey) < 4.0) {
			return false;
		}
		dir = Direction.getFacing(prey.getX() - mob.getX(), 0, prey.getZ() - mob.getZ());
		if (dir.getAxis().isVertical()) {
			return false;
		}
		length = gap(mob.getWorld(), mob.getBlockPos(), dir);
		return length > 0 && prey.getY() > mob.getY() - 4;
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	@Override
	public void start() {
		double speed = length == 1 ? 0.36 : 0.5;
		mob.setVelocity(dir.getOffsetX() * speed, 0.45, dir.getOffsetZ() * speed);
		mob.velocityDirty = true;
		mob.setYaw(dir.asRotation());
		mob.setBodyYaw(dir.asRotation());
		cooldown = 30;
	}
}
