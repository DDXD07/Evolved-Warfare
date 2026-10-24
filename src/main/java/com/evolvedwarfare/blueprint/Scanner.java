package com.evolvedwarfare.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Escanea una caja del mundo y la convierte en plano, girada para que el frente elegido mire al norte. Solo copia los
 * bloques y sus estados (nunca lo que hay dentro de cofres o similares); el agua y la lava cuentan como aire, y lo
 * irrompible (lecho de roca, bloques de comandos...) se deja sin tocar.
 */
public final class Scanner {
	private Scanner() {
	}

	/** El giro que lleva 'front' al norte. */
	public static BlockRotation rotationToNorth(Direction front) {
		for (BlockRotation rotation : BlockRotation.values()) {
			if (rotation.rotate(front) == Direction.NORTH) {
				return rotation;
			}
		}
		return BlockRotation.NONE;
	}

	public static boolean fits(BlockBox box) {
		int horizontalMax = Math.max(StoredBlueprint.MAX_X, StoredBlueprint.MAX_Z);
		return box.getBlockCountX() <= horizontalMax && box.getBlockCountZ() <= horizontalMax
				&& box.getBlockCountY() <= StoredBlueprint.MAX_Y;
	}

	public static StoredBlueprint scan(ServerWorld world, BlockBox box, Direction front, String id, String name, String author) {
		BlockRotation rotation = rotationToNorth(front);
		int w = box.getBlockCountX();
		int h = box.getBlockCountY();
		int d = box.getBlockCountZ();
		// dónde quedan las esquinas al girar, para empezar en 0
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (BlockPos corner : new BlockPos[] {BlockPos.ORIGIN, new BlockPos(w - 1, 0, 0), new BlockPos(0, 0, d - 1),
				new BlockPos(w - 1, 0, d - 1)}) {
			BlockPos r = corner.rotate(rotation);
			minX = Math.min(minX, r.getX());
			minZ = Math.min(minZ, r.getZ());
		}
		boolean swap = rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90;
		int nx = swap ? d : w;
		int nz = swap ? w : d;
		List<String> palette = new ArrayList<>();
		Map<String, Integer> indexOf = new HashMap<>();
		int[] blocks = new int[nx * h * nz];
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < d; z++) {
				for (int x = 0; x < w; x++) {
					cursor.set(box.getMinX() + x, box.getMinY() + y, box.getMinZ() + z);
					String state = scanState(world, cursor, rotation);
					int idx = indexOf.computeIfAbsent(state, s -> {
						palette.add(s);
						return palette.size() - 1;
					});
					BlockPos r = new BlockPos(x, 0, z).rotate(rotation);
					int lx = r.getX() - minX;
					int lz = r.getZ() - minZ;
					blocks[(y * nz + lz) * nx + lx] = idx;
				}
			}
		}
		return new StoredBlueprint(id, name, author, false, nx, h, nz, palette, blocks);
	}

	private static String scanState(ServerWorld world, BlockPos pos, BlockRotation rotation) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir() || state.getBlock() instanceof FluidBlock) {
			return StoredBlueprint.AIR;
		}
		if (state.getHardness(world, pos) < 0.0F || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.STRUCTURE_BLOCK)
				|| state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.SPAWNER)) {
			return StoredBlueprint.VOID;
		}
		if (state.contains(Properties.WATERLOGGED)) {
			state = state.with(Properties.WATERLOGGED, false);
		}
		return BlockArgumentParser.stringifyBlockState(state.rotate(rotation));
	}
}
