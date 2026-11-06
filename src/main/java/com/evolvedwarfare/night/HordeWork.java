package com.evolvedwarfare.night;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * El trabajo en equipo de la horda, por mundo:
 * <ul>
 * <li><b>Lo que construyen</b> (la tierra de columnas, escalones y puentes): ningún zombi (ni enderman) lo rompe mientras
 * sirva, unos 3 minutos; así no se deshacen el trabajo unos a otros.</li>
 * <li><b>Lo que rompen</b>: cada bloque que se está rompiendo es una obra común. Los que llegan ayudan en la misma (el avance
 * se suma y cae antes) en vez de ponerse con otro de al lado.</li>
 * <li>No se rompe un bloque en el que está de pie otro zombi.</li>
 * </ul>
 */
public final class HordeWork {
	/** Lo que dura protegida la tierra que ponen (3 minutos). */
	public static final long BUILT_TICKS = 3 * 60 * 20;

	public static final class Job {
		public float progress;
		public final int needed;
		public long lastTick;
		/** El primero que empezó: el que enseña las grietas. */
		public final int owner;

		Job(int needed, int owner) {
			this.needed = needed;
			this.owner = owner;
		}
	}

	private static final class State {
		final Long2LongOpenHashMap built = new Long2LongOpenHashMap();
		final Long2ObjectOpenHashMap<Job> jobs = new Long2ObjectOpenHashMap<>();
	}

	private static final Map<World, State> STATES = new WeakHashMap<>();

	private HordeWork() {
	}

	private static State of(World world) {
		return STATES.computeIfAbsent(world, w -> new State());
	}

	/** Lo ha puesto un zombi. */
	public static void built(World world, BlockPos pos) {
		of(world).built.put(pos.asLong(), world.getTime() + BUILT_TICKS);
	}

	/** ¿Es tierra que ha puesto la horda y aún sirve? */
	public static boolean isBuilt(World world, BlockPos pos) {
		State s = of(world);
		long until = s.built.getOrDefault(pos.asLong(), 0L);
		if (until == 0L) {
			return false;
		}
		if (until < world.getTime() || !world.getBlockState(pos).isOf(Blocks.DIRT)) {
			s.built.remove(pos.asLong());
			return false;
		}
		return true;
	}

	/** ¿Hay otro bicho de pie encima (o dentro del hueco de encima)? */
	public static boolean supportsOther(World world, BlockPos pos, MobEntity self) {
		return !world.getOtherEntities(self, new Box(pos.up()).expand(-0.05, 0.0, -0.05),
				e -> e instanceof MobEntity && e.isAlive()).isEmpty();
	}

	/** ¿Se puede (o se debe) romper? Ni lo que ha puesto la horda ni lo que sostiene a otro. */
	public static boolean mayBreak(World world, BlockPos pos, MobEntity self) {
		return !isBuilt(world, pos) && !supportsOther(world, pos, self);
	}

	/** La obra de ese bloque, la empieza si no la había (o si se había abandonado). */
	public static Job join(World world, BlockPos pos, int needed, MobEntity self) {
		State s = of(world);
		Job job = s.jobs.get(pos.asLong());
		if (job == null || world.getTime() - job.lastTick > 40) {
			job = new Job(needed, self.getId());
			s.jobs.put(pos.asLong(), job);
		}
		job.lastTick = world.getTime();
		return job;
	}

	/** Una obra en marcha cerca (para ayudar en ella), o null. */
	public static BlockPos nearbyJob(World world, BlockPos feet, double range) {
		State s = of(world);
		long now = world.getTime();
		BlockPos best = null;
		double bestD = range * range;
		for (var e : s.jobs.long2ObjectEntrySet()) {
			if (now - e.getValue().lastTick > 10) {
				continue;
			}
			BlockPos p = BlockPos.fromLong(e.getLongKey());
			double d = p.getSquaredDistance(feet);
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public static void finish(World world, BlockPos pos) {
		of(world).jobs.remove(pos.asLong());
	}

	/** Cada tanto, se olvida lo viejo. */
	public static void cleanup(World world) {
		State s = of(world);
		long now = world.getTime();
		s.jobs.long2ObjectEntrySet().removeIf(e -> now - e.getValue().lastTick > 200);
		s.built.long2LongEntrySet().removeIf(e -> e.getLongValue() < now);
	}
}
