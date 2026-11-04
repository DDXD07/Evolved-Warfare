package com.evolvedwarfare.fluid;

import com.evolvedwarfare.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Las tuberías: qué está conectado con un grifo (tuberías y bombas), de dónde toma agua (cualquier agua que toque una
 * tubería: la toma) y si llega por su peso (la toma está a la altura del grifo o más arriba) o hace falta bombearla.
 */
public final class Plumbing {
	private static final int MAX_PIPES = 512;

	/** Lo conectado: las tuberías, las bombas y el agua que tocan. */
	public record Network(List<BlockPos> pipes, List<BlockPos> pumps, List<BlockPos> intakes) {
		/** La toma más alta (el agua llega mejor desde arriba), o null. */
		@Nullable
		public BlockPos bestIntake() {
			BlockPos best = null;
			for (BlockPos p : intakes) {
				if (best == null || p.getY() > best.getY()) {
					best = p;
				}
			}
			return best;
		}
	}

	private Plumbing() {
	}

	public static boolean conducts(BlockState state) {
		return state.isOf(ModBlocks.PIPE) || state.isOf(ModBlocks.HAND_PUMP);
	}

	public static boolean isWater(World world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.getBlock() instanceof FluidBlock && s.getFluidState().getFluid().matchesType(Fluids.WATER);
	}

	/** Todo lo conectado con lo que hay alrededor de ese sitio (sin contar el sitio). */
	public static Network network(World world, BlockPos from) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> pipes = new ArrayList<>();
		List<BlockPos> pumps = new ArrayList<>();
		Set<BlockPos> intakes = new HashSet<>();
		seen.add(from);
		for (Direction d : Direction.values()) {
			BlockPos n = from.offset(d);
			if (conducts(world.getBlockState(n)) && seen.add(n)) {
				queue.add(n);
			}
		}
		while (!queue.isEmpty() && pipes.size() + pumps.size() < MAX_PIPES) {
			BlockPos p = queue.poll();
			BlockState s = world.getBlockState(p);
			(s.isOf(ModBlocks.HAND_PUMP) ? pumps : pipes).add(p);
			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (seen.contains(n)) {
					continue;
				}
				BlockState ns = world.getBlockState(n);
				if (conducts(ns)) {
					seen.add(n);
					queue.add(n);
				} else if (s.isOf(ModBlocks.PIPE) && isWater(world, n)) {
					intakes.add(n.toImmutable());
				}
			}
		}
		return new Network(pipes, pumps, new ArrayList<>(intakes));
	}

	/**
	 * Saca agua por las tuberías hacia un grifo que está en {@code to}: de la toma más alta; si está más abajo que el
	 * grifo, gasta bombeo. Devuelve si ha salido.
	 */
	public static boolean take(World world, BlockPos to, int units) {
		Network net = network(world, to);
		BlockPos intake = net.bestIntake();
		if (intake == null) {
			return false;
		}
		HandPumpBlockEntity pump = null;
		if (intake.getY() < to.getY()) {
			for (BlockPos p : net.pumps()) {
				if (world.getBlockEntity(p) instanceof HandPumpBlockEntity be && be.charge() >= units) {
					pump = be;
					break;
				}
			}
			if (pump == null) {
				return false;
			}
		}
		if (!FiniteFluids.drain(world, intake, Fluids.WATER, units)) {
			return false;
		}
		if (pump != null) {
			pump.use(units);
		}
		return true;
	}

	/** ¿Cabe agua debajo del grifo? (aire, agua sin llenar o un caldero sin llenar) */
	public static boolean canReceive(World world, BlockPos out) {
		BlockState s = world.getBlockState(out);
		if (s.isOf(Blocks.CAULDRON)) {
			return true;
		}
		if (s.isOf(Blocks.WATER_CAULDRON)) {
			return s.get(LeveledCauldronBlock.LEVEL) < 3;
		}
		int a = FiniteFluids.amountAt(world, out, Fluids.WATER);
		return a >= 0 && a < FiniteFluids.FULL;
	}
}
