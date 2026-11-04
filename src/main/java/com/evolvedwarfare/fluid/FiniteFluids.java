package com.evolvedwarfare.fluid;

import com.evolvedwarfare.config.EwConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Agua y lava realistas: cada bloque de líquido tiene una cantidad, de 1 a 25 capas (25 = bloque lleno), y el líquido
 * <b>no se crea ni se destruye</b>: cae, se reparte por los lados hasta quedar casi plano y, si hay un borde, se va por
 * él. Así:
 * <ul>
 * <li>Un cubo de agua o de lava son <b>4 capas</b>: echado en el suelo se extiende en una capa fina por toda la
 * superficie cercana (si está cerrada), y no hace ríos infinitos.</li>
 * <li>Sacar un cubo de un lago no deja un agujero: se lleva <b>4 capas de la superficie</b> de todo el lago (un poco de
 * cada bloque de arriba), y el nivel baja. Un estanque de 6x6 se vacía con unos cuantos cubos; un lago grande, con
 * muchísimos.</li>
 * <li>Ya no hay fuentes infinitas (dos fuentes no crean una tercera).</li>
 * <li><b>El mar, los ríos y los pantanos sí son infinitos</b> (por debajo del nivel del mar): siempre se rellenan. Y la
 * lava del mar del Nether.</li>
 * </ul>
 * Los bloques anegados (escalones, vallas... con agua) no sueltan su agua.
 */
public final class FiniteFluids {
	public static final int FULL = 25;
	/** Lo que lleva un cubo. */
	public static final int BUCKET = 4;
	public static final int BOTTLE = 1;
	public static final int CANTEEN = 2;
	/** Lo más lejos que se busca por un cuerpo de líquido al sacar de él (bloques). */
	private static final int SEARCH = 4096;

	private FiniteFluids() {
	}

	public static boolean enabled() {
		return EwConfig.get().realisticFluids;
	}

	public static boolean handles(Fluid fluid) {
		return fluid.matchesType(Fluids.WATER) || fluid.matchesType(Fluids.LAVA);
	}

	// ------------------------------------------------------------------ cuánto hay

	/** Las capas de ese líquido en ese sitio: 0 si cabe (aire o plantas que se lleva), -1 si no cabe nada (sólido, otro líquido, anegado). */
	public static int amountAt(World world, BlockPos pos, Fluid fluid) {
		BlockState state = world.getBlockState(pos);
		FluidState fs = state.getFluidState();
		if (!fs.isEmpty()) {
			if (!fs.getFluid().matchesType(fluid) || !(state.getBlock() instanceof FluidBlock)) {
				return -1;
			}
			if (fs.isStill()) {
				return FULL;
			}
			int level = Math.max(1, Math.min(7, fs.getLevel()));
			if (world instanceof net.minecraft.server.world.ServerWorld sw) {
				int stored = FluidUnits.get(sw).get(pos);
				if (stored > 0 && levelOf(stored) == level) {
					return stored;
				}
			}
			return Math.max(1, Math.min(FULL - 1, Math.round(level * FULL / 8.0F)));
		}
		if (state.isAir()) {
			return 0;
		}
		if (state.isReplaceable() && !state.blocksMovement() && state.getBlock() != Blocks.CAULDRON) {
			return 0;
		}
		return -1;
	}

	/** La altura que enseña el bloque (1 a 7) con esas capas (1 a 24). */
	public static int levelOf(int units) {
		return Math.max(1, Math.min(7, (units * 8 + FULL - 1) / FULL));
	}

	/** Lo que cabe todavía en ese sitio (0 si nada). */
	private static int room(World world, BlockPos pos, Fluid fluid) {
		int a = amountAt(world, pos, fluid);
		return a < 0 ? 0 : FULL - a;
	}

	/** ¿Es un sitio donde ese líquido nunca se acaba (mar, río y pantano bajo el nivel del mar; el mar de lava del Nether)? */
	public static boolean isInfinite(World world, BlockPos pos, Fluid fluid) {
		if (pos.getY() >= world.getSeaLevel()) {
			return false;
		}
		if (fluid.matchesType(Fluids.LAVA)) {
			return world.getDimension().ultrawarm();
		}
		RegistryEntry<Biome> biome = world.getBiome(pos);
		return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_DEEP_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)
				|| biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP);
	}

	/** Pone esa cantidad de capas (0 = nada). */
	public static void setAmount(World world, BlockPos pos, Fluid fluid, int amount) {
		BlockState old = world.getBlockState(pos);
		FlowableFluid flowable = (FlowableFluid) (fluid.matchesType(Fluids.LAVA) ? Fluids.LAVA : Fluids.WATER);
		BlockState next;
		if (amount <= 0) {
			if (!(old.getBlock() instanceof FluidBlock)) {
				return;
			}
			next = Blocks.AIR.getDefaultState();
		} else if (amount >= FULL) {
			next = flowable.getStill(false).getBlockState();
		} else {
			next = flowable.getFlowing(levelOf(amount), false).getBlockState();
		}
		if (world instanceof net.minecraft.server.world.ServerWorld sw) {
			FluidUnits.get(sw).set(pos, amount);
		}
		if (old == next) {
			return;
		}
		if (!old.isAir() && !(old.getBlock() instanceof FluidBlock)) {
			// una planta o algo que se lleva el agua
			Block.dropStacks(old, world, pos, world.getBlockEntity(pos));
		}
		world.setBlockState(pos, next, Block.NOTIFY_ALL);
	}

	/** Añade capas a un sitio (hasta llenarlo). Devuelve las que han cabido. */
	public static int add(World world, BlockPos pos, Fluid fluid, int units) {
		int a = amountAt(world, pos, fluid);
		if (a < 0) {
			return 0;
		}
		int put = Math.min(units, FULL - a);
		if (put > 0) {
			setAmount(world, pos, fluid, a + put);
		}
		return put;
	}

	// ------------------------------------------------------------------ cómo se mueve

	/**
	 * Un tic de un bloque de líquido: cae todo lo que quepa abajo, y lo demás se reparte por los lados (una capa a cada
	 * vecino que tenga dos menos, o uno menos si ese vecino sigue bajando; y por los bordes, todo). Devuelve si se ha
	 * movido algo.
	 */
	public static boolean tick(World world, BlockPos pos, Fluid fluid) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof FluidBlock)) {
			return false; // anegado: no suelta su agua
		}
		int a = amountAt(world, pos, fluid);
		if (a <= 0) {
			return false;
		}
		boolean infinite = isInfinite(world, pos, fluid);
		int start = a;
		if (infinite) {
			a = FULL;
		}
		boolean moved = false;
		// 1. cae
		BlockPos down = pos.down();
		int below = room(world, down, fluid);
		if (below > 0 && world.isInBuildLimit(down)) {
			int move = infinite ? below : Math.min(a, below);
			add(world, down, fluid, move);
			if (!infinite) {
				a -= move;
			}
			moved = true;
		}
		// 2. se reparte por los lados (solo si abajo no cabe más)
		if (a > 0 && room(world, down, fluid) == 0) {
			List<Direction> dirs = new ArrayList<>(Direction.Type.HORIZONTAL.stream().toList());
			Collections.shuffle(dirs, new java.util.Random(world.random.nextLong()));
			for (Direction d : dirs) {
				if (a <= 0) {
					break;
				}
				BlockPos n = pos.offset(d);
				int na = amountAt(world, n, fluid);
				if (na < 0 || na >= FULL) {
					continue;
				}
				boolean edge = na == 0 && room(world, n.down(), fluid) > 0;
				int give = 0;
				if (edge) {
					give = Math.max(1, a / 2);
				} else if (a - na >= 2) {
					give = Math.max(1, (a - na) / 3);
				} else if (a - na == 1 && lower(world, n, na, pos, fluid)) {
					give = 1;
				}
				if (give > 0) {
					give = add(world, n, fluid, Math.min(give, a));
					if (!infinite) {
						a -= give;
					}
					moved = true;
				}
			}
		}
		if (a != start) {
			setAmount(world, pos, fluid, a);
		}
		if (moved && a > 0) {
			world.scheduleFluidTick(pos, fluid.matchesType(Fluids.LAVA) ? Fluids.LAVA : Fluids.WATER, fluid.getTickRate(world));
		}
		return moved || a != start;
	}

	/** ¿Tiene ese vecino a su vez otro más bajo (o un hueco debajo)? Así una capa sigue bajando y el agua queda plana. */
	private static boolean lower(World world, BlockPos n, int na, BlockPos from, Fluid fluid) {
		if (room(world, n.down(), fluid) > 0) {
			return true;
		}
		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos m = n.offset(d);
			if (m.equals(from)) {
				continue;
			}
			int ma = amountAt(world, m, fluid);
			if (ma >= 0 && ma < na) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ sacar líquido

	/**
	 * Saca capas de un cuerpo de líquido (el de ese bloque): de la superficie, un poco de cada bloque de arriba. Si el
	 * cuerpo toca el mar (o un río), no se acaba. Devuelve false (y no saca nada) si no hay bastante.
	 */
	public static boolean drain(World world, BlockPos start, Fluid fluid, int units) {
		if (!enabled()) {
			return true;
		}
		if (amountAt(world, start, fluid) <= 0) {
			return false;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		TreeMap<Integer, List<BlockPos>> layers = new TreeMap<>(Collections.reverseOrder());
		queue.add(start.toImmutable());
		seen.add(start.toImmutable());
		long total = 0;
		while (!queue.isEmpty() && seen.size() < SEARCH) {
			BlockPos p = queue.poll();
			if (isInfinite(world, p, fluid)) {
				return true;
			}
			int a = amountAt(world, p, fluid);
			total += a;
			layers.computeIfAbsent(p.getY(), k -> new ArrayList<>()).add(p);
			for (Direction d : Direction.values()) {
				BlockPos q = p.offset(d);
				if (!seen.contains(q) && amountAt(world, q, fluid) > 0 && world.getBlockState(q).getBlock() instanceof FluidBlock) {
					seen.add(q.toImmutable());
					queue.add(q.toImmutable());
				}
			}
		}
		if (total < units) {
			return false;
		}
		int need = units;
		for (Map.Entry<Integer, List<BlockPos>> layer : layers.entrySet()) {
			List<BlockPos> blocks = layer.getValue();
			Collections.shuffle(blocks, new java.util.Random(world.random.nextLong()));
			int[] left = new int[blocks.size()];
			for (int i = 0; i < blocks.size(); i++) {
				left[i] = amountAt(world, blocks.get(i), fluid);
			}
			boolean any = true;
			while (need > 0 && any) {
				any = false;
				for (int i = 0; i < blocks.size() && need > 0; i++) {
					if (left[i] > 0) {
						left[i]--;
						need--;
						any = true;
					}
				}
			}
			for (int i = 0; i < blocks.size(); i++) {
				setAmount(world, blocks.get(i), fluid, left[i]);
			}
			if (need <= 0) {
				break;
			}
		}
		return true;
	}

	/** Cuántas capas hay en total en un cuerpo de líquido (para las pruebas; -1 si es infinito). */
	public static long total(World world, BlockPos start, Fluid fluid) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.toImmutable());
		seen.add(start.toImmutable());
		long total = 0;
		while (!queue.isEmpty() && seen.size() < SEARCH) {
			BlockPos p = queue.poll();
			if (isInfinite(world, p, fluid)) {
				return -1;
			}
			total += Math.max(0, amountAt(world, p, fluid));
			for (Direction d : Direction.values()) {
				BlockPos q = p.offset(d);
				if (!seen.contains(q) && amountAt(world, q, fluid) > 0) {
					seen.add(q.toImmutable());
					queue.add(q.toImmutable());
				}
			}
		}
		return total;
	}
}
