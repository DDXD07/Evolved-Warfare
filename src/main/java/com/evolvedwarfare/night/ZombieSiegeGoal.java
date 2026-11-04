package com.evolvedwarfare.night;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un zombi que no puede llegar hasta su presa se abre camino: rompe lo que tiene delante (y la cabeza), excava hacia abajo
 * en escalera si está más abajo, o despeja hacia arriba si está más alto. Tarda según lo duro del bloque: la tierra un
 * momento, la piedra unos segundos, el hierro bastante y la obsidiana más de un minuto. Nada más duro que la obsidiana
 * (ni la roca madre).
 */
public class ZombieSiegeGoal extends Goal {
	/** Lo más duro que rompen (la obsidiana). */
	public static final float MAX_HARDNESS = 50.0F;
	private final MobEntity mob;
	private Vec3d lastPos = Vec3d.ZERO;
	private int stuck;
	private int checkTimer;
	@Nullable
	private BlockPos target;
	private int progress;
	private int needed;
	private int lastStage = -1;

	public ZombieSiegeGoal(MobEntity mob) {
		this.mob = mob;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
	}

	/** Tics que tarda un zombi en romper ese bloque (o -1 si no puede). */
	public static int breakTicks(World world, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) {
			return -1; // aire, agua, antorchas, botones... no estorban
		}
		float hardness = state.getHardness(world, pos);
		if (hardness < 0.0F || hardness > MAX_HARDNESS) {
			return -1;
		}
		return Math.max(12, Math.round(hardness * 30.0F));
	}

	private boolean allowed() {
		return mob.getWorld() instanceof net.minecraft.server.world.ServerWorld sw && sw.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
				&& NightThreat.breakBlocks();
	}

	@Override
	public boolean canStart() {
		LivingEntity prey = mob.getTarget();
		if (prey == null || !prey.isAlive() || !allowed()) {
			stuck = 0;
			return false;
		}
		if (++checkTimer < 10) {
			return false;
		}
		checkTimer = 0;
		double dist = mob.squaredDistanceTo(prey);
		if (dist < 2.5 * 2.5 || dist > 64 * 64) {
			stuck = 0;
			return false;
		}
		Path path = mob.getNavigation().getCurrentPath();
		boolean noWay = path == null || path.isFinished() || !path.reachesTarget();
		boolean still = mob.getPos().squaredDistanceTo(lastPos) < 0.25;
		lastPos = mob.getPos();
		if (noWay || still) {
			stuck++;
		} else {
			stuck = 0;
		}
		if (stuck < 3) {
			return false;
		}
		target = obstacle(prey);
		return target != null;
	}

	/** El bloque que estorba de camino a la presa (o null). */
	@Nullable
	public BlockPos obstacle(LivingEntity prey) {
		World world = mob.getWorld();
		BlockPos feet = mob.getBlockPos();
		double dx = prey.getX() - mob.getX();
		double dz = prey.getZ() - mob.getZ();
		int dy = prey.getBlockY() - feet.getY();
		Direction main = Direction.getFacing(dx, 0, dz);
		Direction side = Math.abs(dx) > Math.abs(dz) ? Direction.getFacing(0, 0, dz) : Direction.getFacing(dx, 0, 0);
		double flat = Math.sqrt(dx * dx + dz * dz);
		List<BlockPos> order = new ArrayList<>();
		if (dy < -1 && flat < 2.0) {
			order.add(feet.down()); // justo encima: excava hacia abajo
		}
		for (Direction d : new Direction[] {main, side}) {
			BlockPos front = feet.offset(d);
			if (dy < -1) {
				// escalera hacia abajo
				order.add(front);
				order.add(front.up());
				order.add(front.down());
			} else if (dy > 1) {
				// escalera hacia arriba: hueco sobre la cabeza y sobre el escalón
				order.add(feet.up(2));
				order.add(front.up());
				order.add(front.up(2));
			} else {
				order.add(front.up());
				order.add(front);
			}
		}
		for (BlockPos p : order) {
			if (breakTicks(world, p, world.getBlockState(p)) > 0) {
				return p.toImmutable();
			}
		}
		return null;
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
		progress = 0;
		lastStage = -1;
		needed = target == null ? 1 : breakTicks(mob.getWorld(), target, mob.getWorld().getBlockState(target));
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity prey = mob.getTarget();
		if (target == null || prey == null || !prey.isAlive() || !allowed()) {
			return false;
		}
		if (mob.getBlockPos().getSquaredDistance(target) > 3.2 * 3.2) {
			return false;
		}
		return breakTicks(mob.getWorld(), target, mob.getWorld().getBlockState(target)) > 0;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (target == null) {
			return;
		}
		World world = mob.getWorld();
		mob.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		progress++;
		if (progress % 12 == 0) {
			mob.swingHand(Hand.MAIN_HAND);
			BlockState state = world.getBlockState(target);
			world.playSound(null, target, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.8F, 0.7F);
		}
		int stage = Math.min(9, progress * 10 / Math.max(1, needed));
		if (stage != lastStage) {
			lastStage = stage;
			world.setBlockBreakingInfo(mob.getId(), target, stage);
		}
		if (progress >= needed) {
			world.breakBlock(target, true, mob);
			world.setBlockBreakingInfo(mob.getId(), target, -1);
			target = null;
			stuck = 0;
		}
	}

	@Override
	public void stop() {
		if (target != null) {
			mob.getWorld().setBlockBreakingInfo(mob.getId(), target, -1);
		}
		target = null;
		progress = 0;
	}
}
