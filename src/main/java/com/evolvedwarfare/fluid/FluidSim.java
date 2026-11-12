package com.evolvedwarfare.fluid;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.perf.EwWorkers;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.WorldChunk;

/**
 * El agua y la lava finitas, calculadas en los hilos del mod (fase 55.1). Cada bloque de líquido que tiene que moverse se
 * apunta; cada tic, el hilo del servidor reparte un lote (con los chunks que hacen falta) a un hilo de trabajo, que lee los
 * bloques y decide cuánto cae y cuánto se reparte (igual que {@link FiniteFluids#tick}, pero sin tocar el mundo), y en el tic
 * siguiente el hilo del servidor aplica esos movimientos. Al aplicarlos se vuelve a mirar lo que hay: si algo ha cambiado
 * mientras tanto, se mueve lo que se pueda, y nunca se crea ni se pierde líquido (salvo el del mar, que es infinito).
 */
public final class FluidSim {
	/** Cuántos bloques de líquido se calculan por lote, y cuántos movimientos se aplican por tic como mucho. */
	public static final int BATCH = 4000, APPLY = 6000;

	private FluidSim() {
	}

	/** Lo que cambia un bloque en el lote: las capas que tenía al calcular y las que le quedan (se aplica la diferencia). */
	record Move(long pos, int before, int after, boolean water) {
	}

	private static final class Sim {
		final LongLinkedOpenHashSet waterQueue = new LongLinkedOpenHashSet();
		final LongLinkedOpenHashSet lavaQueue = new LongLinkedOpenHashSet();
		CompletableFuture<List<Move>> running;
		final List<Move> pending = new ArrayList<>();
		int pendingIndex;
	}

	private static final Map<ServerWorld, Sim> SIMS = new WeakHashMap<>();
	/** Para las pruebas: cuántos bloques de líquido se han calculado y cuántos movimientos se han aplicado. */
	public static long computed, applied;

	public static boolean async() {
		return com.evolvedwarfare.config.EwConfig.get().asyncFluids;
	}

	private static Sim sim(ServerWorld world) {
		return SIMS.computeIfAbsent(world, w -> new Sim());
	}

	/** Un bloque de líquido tiene que moverse (lo pide su tic programado). */
	public static void queue(ServerWorld world, BlockPos pos, Fluid fluid) {
		Sim s = sim(world);
		(fluid.matchesType(Fluids.LAVA) ? s.lavaQueue : s.waterQueue).add(pos.asLong());
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(FluidSim::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> SIMS.clear());
	}

	// ------------------------------------------------------------------ cada tic del servidor

	private static void tick(ServerWorld world) {
		Sim s = SIMS.get(world);
		if (s == null) {
			return;
		}
		// 1. lo calculado: se aplica (como mucho APPLY movimientos por tic; lo demás, en el siguiente)
		if (s.running != null && s.running.isDone()) {
			try {
				s.pending.addAll(s.running.join());
			} catch (RuntimeException e) {
				EvolvedWarfare.LOGGER.debug("Agua: falló un lote", e);
			}
			s.running = null;
		}
		if (!s.pending.isEmpty()) {
			apply(world, s);
		}
		// 2. el lote siguiente, al hilo de trabajo (si no hay uno en marcha ni movimientos por aplicar)
		if (s.running == null && s.pending.isEmpty() && (!s.waterQueue.isEmpty() || !s.lavaQueue.isEmpty())) {
			long[] water = take(s.waterQueue), lava = take(s.lavaQueue);
			Long2ObjectOpenHashMap<WorldChunk> chunks = new Long2ObjectOpenHashMap<>();
			for (long[] list : new long[][] {water, lava}) {
				for (long k : list) {
					int cx = BlockPos.unpackLongX(k) >> 4, cz = BlockPos.unpackLongZ(k) >> 4;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							long ck = ChunkPos.toLong(cx + dx, cz + dz);
							if (!chunks.containsKey(ck)) {
								WorldChunk c = world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
								if (c != null) {
									chunks.put(ck, c);
								}
							}
						}
					}
				}
			}
			View view = new View(chunks, FluidUnits.get(world), world.getSeaLevel(), world.getBottomY(), world.getTopY(), world.getDimension().ultrawarm(),
					world.getTime());
			s.running = CompletableFuture.supplyAsync(() -> {
				List<Move> out = new ArrayList<>();
				view.compute(water, Fluids.WATER, out);
				view.compute(lava, Fluids.LAVA, out);
				return out;
			}, EwWorkers.POOL);
		}
	}

	private static long[] take(LongLinkedOpenHashSet queue) {
		int n = Math.min(BATCH, queue.size());
		long[] out = new long[n];
		for (int i = 0; i < n; i++) {
			out[i] = queue.removeFirstLong();
		}
		return out;
	}

	/**
	 * Aplica lo calculado: a cada bloque, la diferencia entre lo que tenía al calcular y lo que le queda, sobre lo que tiene
	 * ahora (si nada ha cambiado entretanto, queda exacto; si algo sí, se ajusta sin pasarse de lleno ni bajar de vacío).
	 * Cada bloque se escribe una sola vez.
	 */
	private static void apply(ServerWorld world, Sim s) {
		int end = Math.min(s.pending.size(), s.pendingIndex + APPLY);
		BlockPos.Mutable at = new BlockPos.Mutable();
		List<BlockPos> again = new ArrayList<>();
		for (int i = s.pendingIndex; i < end; i++) {
			Move m = s.pending.get(i);
			Fluid fluid = m.water() ? Fluids.WATER : Fluids.LAVA;
			at.set(m.pos());
			if (!world.isChunkLoaded(at)) {
				continue;
			}
			int cur = FiniteFluids.amountAt(world, at, fluid);
			if (cur < 0) {
				continue;
			}
			int next = Math.max(0, Math.min(FiniteFluids.FULL, cur + m.after() - m.before()));
			if (next != cur) {
				BlockPos p = at.toImmutable();
				FiniteFluids.setAmount(world, p, fluid, next);
				applied++;
				if (next > 0) {
					again.add(p);
				}
			}
		}
		s.pendingIndex = end;
		if (s.pendingIndex >= s.pending.size()) {
			s.pending.clear();
			s.pendingIndex = 0;
		}
		// lo que ha cambiado y aún tiene líquido, vuelve a mirarse
		for (BlockPos p : again) {
			FluidState fs = world.getFluidState(p);
			if (!fs.isEmpty() && FiniteFluids.handles(fs.getFluid())) {
				FlowableFluid kind = (FlowableFluid) (fs.getFluid().matchesType(Fluids.LAVA) ? Fluids.LAVA : Fluids.WATER);
				world.scheduleFluidTick(p, kind, kind.getTickRate(world));
			}
		}
	}

	// ------------------------------------------------------------------ el cálculo (en el hilo de trabajo)

	/** Lo que ve el hilo de trabajo: los chunks del lote y las capas guardadas; y lo que va moviendo en este lote. */
	private static final class View {
		final Long2ObjectOpenHashMap<WorldChunk> chunks;
		final FluidUnits units;
		final int seaLevel, bottom, top;
		final boolean ultrawarm;
		final java.util.Random random;
		/** Las capas que van quedando en este lote (lo ya movido), y las que había antes. */
		final Long2IntOpenHashMap overlay = new Long2IntOpenHashMap();
		final Long2IntOpenHashMap before = new Long2IntOpenHashMap();
		final BlockPos.Mutable pos = new BlockPos.Mutable();

		View(Long2ObjectOpenHashMap<WorldChunk> chunks, FluidUnits units, int seaLevel, int bottom, int top, boolean ultrawarm, long seed) {
			this.chunks = chunks;
			this.units = units;
			this.seaLevel = seaLevel;
			this.bottom = bottom;
			this.top = top;
			this.ultrawarm = ultrawarm;
			this.random = new java.util.Random(seed);
			overlay.defaultReturnValue(Integer.MIN_VALUE);
		}

		BlockState state(long k) {
			int x = BlockPos.unpackLongX(k), y = BlockPos.unpackLongY(k), z = BlockPos.unpackLongZ(k);
			if (y < bottom || y >= top) {
				return null;
			}
			WorldChunk c = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
			return c == null ? null : c.getBlockState(pos.set(x, y, z));
		}

		/** Como {@link FiniteFluids#amountAt}: capas de ese líquido; 0 si cabe; -1 si no. */
		int amount(long k, Fluid fluid) {
			int o = overlay.get(k);
			if (o != Integer.MIN_VALUE) {
				return o;
			}
			BlockState state = state(k);
			if (state == null) {
				return -1;
			}
			FluidState fs = state.getFluidState();
			if (!fs.isEmpty()) {
				if (!fs.getFluid().matchesType(fluid) || !(state.getBlock() instanceof FluidBlock)) {
					return -1;
				}
				if (fs.isStill()) {
					return FiniteFluids.FULL;
				}
				int level = Math.max(1, Math.min(7, fs.getLevel()));
				int stored = units.getSafe(k);
				if (stored > 0 && FiniteFluids.levelOf(stored) == level) {
					return stored;
				}
				return Math.max(1, Math.min(FiniteFluids.FULL - 1, Math.round(level * FiniteFluids.FULL / 8.0F)));
			}
			if (state.isAir()) {
				return 0;
			}
			if (state.isReplaceable() && !state.blocksMovement() && state.getBlock() != Blocks.CAULDRON) {
				return 0;
			}
			return -1;
		}

		int room(long k, Fluid fluid) {
			int a = amount(k, fluid);
			return a < 0 ? 0 : FiniteFluids.FULL - a;
		}

		boolean infinite(long k, Fluid fluid) {
			int x = BlockPos.unpackLongX(k), y = BlockPos.unpackLongY(k), z = BlockPos.unpackLongZ(k);
			if (y >= seaLevel) {
				return false;
			}
			if (fluid.matchesType(Fluids.LAVA)) {
				return ultrawarm;
			}
			WorldChunk c = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
			if (c == null) {
				return false;
			}
			RegistryEntry<Biome> biome = c.getBiomeForNoiseGen(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z));
			return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_DEEP_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)
					|| biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP);
		}

		/** Cambia lo que queda en un bloque (apuntando lo que tenía al empezar). */
		private void set(long k, int value, Fluid fluid) {
			if (!before.containsKey(k)) {
				before.put(k, Math.max(0, amount(k, fluid)));
			}
			overlay.put(k, value);
		}

		private void move(List<Move> out, long from, long to, int n, Fluid fluid, boolean inf) {
			int t = Math.max(0, amount(to, fluid));
			set(to, Math.min(FiniteFluids.FULL, t + n), fluid);
		}

		/** Los cambios netos del lote. */
		void finish(List<Move> out, Fluid fluid) {
			for (var e : before.long2IntEntrySet()) {
				int after = overlay.get(e.getLongKey());
				if (after != e.getIntValue()) {
					out.add(new Move(e.getLongKey(), e.getIntValue(), after, fluid == Fluids.WATER));
				}
			}
			before.clear();
			overlay.clear();
		}

		/** Lo mismo que {@link FiniteFluids#tick}, para cada bloque del lote, apuntando los movimientos. */
		void compute(long[] cells, Fluid fluid, List<Move> out) {
			for (long k : cells) {
				computed++;
				FiniteFluids.ticksDone++;
				BlockState state = state(k);
				if (state == null || !(state.getBlock() instanceof FluidBlock)) {
					continue;
				}
				int a = amount(k, fluid);
				if (a <= 0) {
					continue;
				}
				boolean inf = infinite(k, fluid);
				if (inf) {
					a = FiniteFluids.FULL;
				}
				long down = BlockPos.offset(k, Direction.DOWN);
				int below = room(down, fluid);
				if (below > 0 && inf && !infinite(down, fluid)) {
					below = 0;
				}
				if (below > 0) {
					int n = inf ? below : Math.min(a, below);
					move(out, k, down, n, fluid, inf);
					if (!inf) {
						a -= n;
					}
				}
				if (a > 0 && room(down, fluid) == 0) {
					int first = random.nextInt(4);
					for (int j = 0; j < 4 && a > 0; j++) {
						Direction d = Direction.fromHorizontal((first + j) & 3);
						long n = BlockPos.offset(k, d);
						int na = amount(n, fluid);
						if (na < 0 || na >= FiniteFluids.FULL || inf && !infinite(n, fluid)) {
							continue;
						}
						long nd = BlockPos.offset(n, Direction.DOWN);
						boolean edge = na == 0 && room(nd, fluid) > 0;
						int give = 0;
						if (edge) {
							give = Math.max(1, a / 2);
						} else if (a - na >= 2) {
							give = Math.max(1, (a - na) / 3);
						} else if (a - na == 1 && room(nd, fluid) > 0) {
							give = 1;
						}
						give = Math.min(give, Math.min(a, FiniteFluids.FULL - na));
						if (give > 0) {
							move(out, k, n, give, fluid, inf);
							if (!inf) {
								a -= give;
							}
						}
					}
				}
				if (!inf) {
					set(k, a, fluid);
				}
			}
			finish(out, fluid);
		}
	}
}
