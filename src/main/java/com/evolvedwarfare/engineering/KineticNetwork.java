package com.evolvedwarfare.engineering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * El solucionador de la red de fuerza. Se recalcula al final del tic en las redes que han cambiado (al poner o quitar un
 * bloque, o cuando una fuente cambia lo que da):
 * <ol>
 * <li>recorre la red y apunta a cada bloque su relación de velocidad con el primero (×1 por un eje, ×−1 entre dos
 * engranajes, ×−½ o ×−2 entre uno pequeño y uno grande); si un lazo de engranajes no cuadra, se atasca;</li>
 * <li>las fuentes que giran tienen que pedir la misma velocidad para el primero; si no, se atasca;</li>
 * <li>la fuerza que hay es la suma de la de las fuentes que giran; la que se gasta, la de cada máquina por su velocidad
 * (por eso ir más deprisa con engranajes cuesta más fuerza); si no llega, se para todo (sobrecargada).</li>
 * </ol>
 */
public final class KineticNetwork {
	public static final int MAX_NODES = 4096;
	private static final Map<ServerWorld, Set<BlockPos>> DIRTY = new WeakHashMap<>();

	private KineticNetwork() {
	}

	public static void markDirty(ServerWorld world, BlockPos pos) {
		DIRTY.computeIfAbsent(world, w -> new HashSet<>()).add(pos.toImmutable());
	}

	/** Al final de cada tic: recalcula las redes que han cambiado. */
	public static void flush(ServerWorld world) {
		Set<BlockPos> dirty = DIRTY.remove(world);
		if (dirty == null) {
			return;
		}
		Set<BlockPos> done = new HashSet<>();
		for (BlockPos p : dirty) {
			if (!done.contains(p)) {
				done.addAll(solve(world, p));
			}
			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (!done.contains(n) && world.getBlockState(n).getBlock() instanceof Kinetic) {
					done.addAll(solve(world, n));
				}
			}
		}
	}

	/** Un vecino unido y la relación de velocidades (la del vecino = la mía × ratio). */
	record Link(BlockPos pos, float ratio) {
	}

	/** Con quién está unido un bloque de la red. */
	static List<Link> links(ServerWorld world, BlockPos pos, BlockState state) {
		List<Link> out = new ArrayList<>();
		if (!(state.getBlock() instanceof Kinetic k)) {
			return out;
		}
		for (Direction d : Direction.values()) {
			if (!k.hasAxle(state, d)) {
				continue;
			}
			BlockPos n = pos.offset(d);
			BlockState ns = world.getBlockState(n);
			if (ns.getBlock() instanceof Kinetic nk && nk.hasAxle(ns, d.getOpposite())) {
				out.add(new Link(n, 1.0F));
			}
		}
		int gear = k.gear(state);
		Direction.Axis axis = k.axis(state);
		if (gear > 0 && axis != null) {
			List<Direction> plane = new ArrayList<>();
			for (Direction d : Direction.values()) {
				if (d.getAxis() != axis) {
					plane.add(d);
				}
			}
			for (Direction d : plane) {
				// al lado: dos pequeños
				if (gear == 1) {
					BlockPos n = pos.offset(d);
					BlockState ns = world.getBlockState(n);
					if (ns.getBlock() instanceof Kinetic nk && nk.gear(ns) == 1 && nk.axis(ns) == axis) {
						out.add(new Link(n, -1.0F));
					}
				}
				// en diagonal: uno pequeño y uno grande
				for (Direction e : plane) {
					if (e.getAxis() == d.getAxis() || d.ordinal() > e.ordinal()) {
						continue;
					}
					BlockPos n = pos.offset(d).offset(e);
					BlockState ns = world.getBlockState(n);
					if (ns.getBlock() instanceof Kinetic nk && nk.axis(ns) == axis && nk.gear(ns) + gear == 3) {
						out.add(new Link(n, gear == 1 ? -0.5F : -2.0F));
					}
				}
			}
		}
		return out;
	}

	/** Recalcula la red de ese bloque; devuelve los bloques que la forman. */
	public static Set<BlockPos> solve(ServerWorld world, BlockPos start) {
		Map<BlockPos, Float> factor = new HashMap<>();
		BlockState s0 = world.getBlockState(start);
		if (!(s0.getBlock() instanceof Kinetic)) {
			return Set.of();
		}
		boolean jammed = false;
		factor.put(start.toImmutable(), 1.0F);
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		todo.add(start.toImmutable());
		while (!todo.isEmpty() && factor.size() < MAX_NODES) {
			BlockPos p = todo.poll();
			float f = factor.get(p);
			for (Link l : links(world, p, world.getBlockState(p))) {
				float want = f * l.ratio();
				Float have = factor.get(l.pos());
				if (have == null) {
					factor.put(l.pos(), want);
					todo.add(l.pos());
				} else if (Math.abs(have - want) > 1e-3F * Math.max(1.0F, Math.abs(want))) {
					jammed = true;
				}
			}
		}
		// las fuentes: todas tienen que pedir lo mismo
		Float root = null;
		float capacity = 0.0F;
		for (Map.Entry<BlockPos, Float> e : factor.entrySet()) {
			if (world.getBlockEntity(e.getKey()) instanceof KineticBlockEntity be && Math.abs(be.generated) > 0.01F) {
				float r = be.generated / e.getValue();
				if (root == null) {
					root = r;
				} else if (Math.abs(root - r) > 0.05F) {
					jammed = true;
				}
				capacity += be.generatedCapacity;
			}
		}
		float rootSpeed = root == null || jammed ? 0.0F : root;
		float stress = 0.0F;
		for (Map.Entry<BlockPos, Float> e : factor.entrySet()) {
			BlockState s = world.getBlockState(e.getKey());
			float impact = s.getBlock() instanceof Kinetic k ? k.impact(s) : 0.0F;
			if (world.getBlockEntity(e.getKey()) instanceof KineticBlockEntity be) {
				impact += be.extraImpact;
			}
			stress += impact * Math.abs(rootSpeed * e.getValue());
		}
		int state = jammed ? KineticBlockEntity.JAMMED : stress > capacity + 1e-3F ? KineticBlockEntity.OVERSTRESSED : KineticBlockEntity.OK;
		if (state != KineticBlockEntity.OK) {
			rootSpeed = 0.0F;
		}
		for (Map.Entry<BlockPos, Float> e : factor.entrySet()) {
			if (world.getBlockEntity(e.getKey()) instanceof KineticBlockEntity be) {
				be.apply(rootSpeed * e.getValue(), capacity, stress, state);
			}
		}
		return factor.keySet();
	}
}
