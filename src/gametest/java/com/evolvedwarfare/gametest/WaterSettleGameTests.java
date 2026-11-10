package com.evolvedwarfare.gametest;

import com.evolvedwarfare.fluid.FiniteFluids;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El agua realista se queda quieta: un estanque que se desborda a la orilla (y un charco de una capa) se reparten y
 * paran; antes, la última capa iba y venía para siempre en las orillas y el mundo se trababa cerca del agua.
 */
public class WaterSettleGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
	public void waterSettles(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// por encima del nivel del mar (el agua de ahí es finita)
		BlockPos o = new BlockPos(ctx.getAbsolutePos(BlockPos.ORIGIN).getX() - 300, Math.max(world.getSeaLevel() + 20, ctx.getAbsolutePos(BlockPos.ORIGIN).getY()),
				ctx.getAbsolutePos(BlockPos.ORIGIN).getZ());
		java.util.List<ChunkPos> forced = new java.util.ArrayList<>();
		for (int cx = -1; cx <= 3; cx++) {
			for (int cz = -1; cz <= 3; cz++) {
				ChunkPos c = new ChunkPos((o.getX() >> 4) + cx, (o.getZ() >> 4) + cz);
				forced.add(c);
				world.setChunkForced(c.x, c.z, true);
			}
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -4; x < 40; x++) {
			for (int z = -4; z < 40; z++) {
				world.setBlockState(m.set(o.getX() + x, o.getY() - 3, o.getZ() + z), Blocks.STONE.getDefaultState(), 2);
				// el suelo: un escalón a la altura de la orilla y, más allá, una cuesta suave
				boolean basin = x >= 4 && x < 14 && z >= 4 && z < 14;
				for (int k = -2; k < 0; k++) {
					world.setBlockState(m.set(o.getX() + x, o.getY() + k, o.getZ() + z), basin ? Blocks.WATER.getDefaultState()
							: (x > 24 && k == -1 && (x + z) % 3 == 0 ? Blocks.AIR.getDefaultState() : Blocks.STONE.getDefaultState()), 2);
				}
				for (int k = 0; k < 4; k++) {
					world.setBlockState(m.set(o.getX() + x, o.getY() + k, o.getZ() + z), Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
		// el estanque se desborda: una capa más de agua por encima de la orilla
		for (int x = 4; x < 14; x++) {
			for (int z = 4; z < 14; z++) {
				world.setBlockState(m.set(o.getX() + x, o.getY(), o.getZ() + z), Blocks.WATER.getDefaultState(), 3);
			}
		}
		long[] at = new long[4];
		ctx.runAtTick(1, () -> at[0] = FiniteFluids.ticksDone);
		ctx.runAtTick(500, () -> at[1] = FiniteFluids.ticksDone);
		ctx.runAtTick(600, () -> {
			at[2] = FiniteFluids.ticksDone;
			long early = at[1] - at[0], late = at[2] - at[1];
			LOG.info("[EW-GAMETEST] agua: {} tics de agua en los primeros 500 tics, {} en los 100 últimos", early, late);
			for (int x = -4; x < 40; x++) {
				for (int z = -4; z < 40; z++) {
					for (int k = -3; k < 4; k++) {
						world.setBlockState(m.set(o.getX() + x, o.getY() + k, o.getZ() + z), Blocks.AIR.getDefaultState(), 2);
					}
				}
			}
			forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
			// otras pruebas también mueven agua a la vez: se deja margen
			if (late > 400) {
				throw new GameTestException("El agua no se queda quieta: " + late + " tics de agua en los últimos 100 tics");
			}
			ctx.complete();
		});
	}
}
