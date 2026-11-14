package com.evolvedwarfare.voxel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * La luz de lo construido encima de un coche (antorchas, faroles, hogueras, lámparas...): como lo que va encima no son
 * bloques del mundo, pone bloques de luz invisibles donde está cada cosa que alumbra y los mueve con el coche.
 */
public final class VoxelLights {
	private final Set<BlockPos> placed = new HashSet<>();

	/** Pone, mueve o quita las luces (cada pocos tics). */
	public void update(ServerWorld world, VoxelHost host) {
		VoxelGrid g = host.grid();
		Map<BlockPos, Integer> want = new HashMap<>();
		if (!g.isEmpty()) {
			for (int i = 0; i < g.size(); i++) {
				BlockState s = g.get(i);
				if (s == null || s.isAir()) {
					continue;
				}
				int lum = s.getLuminance();
				if (lum > 0) {
					Vec3d c = host.gridToWorld(g.center(i));
					want.merge(BlockPos.ofFloored(c), lum, Math::max);
				}
			}
		}
		if (want.isEmpty() && placed.isEmpty()) {
			return;
		}
		for (BlockPos p : Set.copyOf(placed)) {
			if (!want.containsKey(p)) {
				remove(world, p);
				placed.remove(p);
			}
		}
		for (Map.Entry<BlockPos, Integer> e : want.entrySet()) {
			BlockPos p = e.getKey();
			BlockState cur = world.getBlockState(p);
			boolean water = cur.getFluidState().isOf(Fluids.WATER) && cur.getFluidState().isStill();
			if (cur.isOf(Blocks.LIGHT) ? placed.contains(p) && cur.get(LightBlock.LEVEL_15) != e.getValue() : cur.isAir() || water && cur.isOf(Blocks.WATER)) {
				world.setBlockState(p, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, e.getValue())
						.with(Properties.WATERLOGGED, water || cur.isOf(Blocks.LIGHT) && cur.get(Properties.WATERLOGGED)));
				placed.add(p.toImmutable());
			} else if (cur.isOf(Blocks.LIGHT) && placed.contains(p)) {
				// ya está
				continue;
			}
		}
	}

	private static void remove(ServerWorld world, BlockPos p) {
		BlockState cur = world.getBlockState(p);
		if (cur.isOf(Blocks.LIGHT)) {
			world.setBlockState(p, cur.get(Properties.WATERLOGGED) ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState());
		}
	}

	/** Quita todas (el coche se rompe o desaparece). */
	public void clear(ServerWorld world) {
		for (BlockPos p : placed) {
			remove(world, p);
		}
		placed.clear();
	}

	public int count() {
		return placed.size();
	}

	public void write(NbtCompound nbt) {
		nbt.putLongArray("Lights", placed.stream().mapToLong(BlockPos::asLong).toArray());
	}

	public void read(NbtCompound nbt) {
		placed.clear();
		for (long l : nbt.getLongArray("Lights")) {
			placed.add(BlockPos.fromLong(l));
		}
	}
}
