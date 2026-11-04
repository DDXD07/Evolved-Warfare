package com.evolvedwarfare.gametest;

import com.evolvedwarfare.fluid.FaucetBlock;
import com.evolvedwarfare.fluid.FiniteFluids;
import com.evolvedwarfare.fluid.HandPumpBlockEntity;
import com.evolvedwarfare.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Agua realista y tuberías: el agua no se crea ni se destruye, un cubo se extiende en una capa, sacar agua baja el nivel, y el grifo. */
public class FluidGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	/** Un pilón de piedra: suelo en y = 0 y paredes hasta {@code height}, de 1 a {@code size} por dentro. */
	private static void basin(TestContext ctx, int size, int height) {
		for (int x = 0; x <= size + 1; x++) {
			for (int z = 0; z <= size + 1; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				boolean wall = x == 0 || z == 0 || x == size + 1 || z == size + 1;
				for (int y = 1; y <= height; y++) {
					ctx.setBlockState(x, y, z, wall ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
	}

	private static int total(TestContext ctx, int size, int height) {
		int sum = 0;
		for (int x = 1; x <= size; x++) {
			for (int z = 1; z <= size; z++) {
				for (int y = 1; y <= height + 1; y++) {
					sum += Math.max(0, FiniteFluids.amountAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(x, y, z)), Fluids.WATER));
				}
			}
		}
		return sum;
	}

	private static int max(TestContext ctx, int size) {
		int m = 0;
		for (int x = 1; x <= size; x++) {
			for (int z = 1; z <= size; z++) {
				m = Math.max(m, FiniteFluids.amountAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(x, 1, z)), Fluids.WATER));
			}
		}
		return m;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void aBucketSpreadsThin(TestContext ctx) {
		basin(ctx, 6, 2);
		ServerWorld world = ctx.getWorld();
		BlockPos center = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		check(!FiniteFluids.isInfinite(world, center, Fluids.WATER), "La zona de pruebas cuenta como mar");
		FiniteFluids.setAmount(world, center, Fluids.WATER, FiniteFluids.FULL);
		ctx.waitAndRun(200, () -> {
			int total = total(ctx, 6, 2);
			int top = max(ctx, 6);
			LOG.info("[EW-GAMETEST] un cubo en el suelo: {} capas en total, el bloque más alto {}", total, top);
			check(total == 8, "El agua se ha creado o perdido: " + total);
			check(top <= 2, "El agua no se ha extendido: " + top);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void lakesDrainLayerByLayer(TestContext ctx) {
		basin(ctx, 6, 2);
		ServerWorld world = ctx.getWorld();
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				for (int y = 1; y <= 2; y++) {
					ctx.setBlockState(x, y, z, Blocks.WATER);
				}
			}
		}
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		int before = total(ctx, 6, 2);
		boolean first = FiniteFluids.drain(world, at, Fluids.WATER, 8);
		int after = total(ctx, 6, 2);
		boolean clicked = FiniteFluids.amountAt(world, at, Fluids.WATER) >= 7;
		int buckets = 1;
		while (buckets < 200) {
			BlockPos wet = null;
			for (int x = 1; x <= 6 && wet == null; x++) {
				for (int z = 1; z <= 6 && wet == null; z++) {
					for (int y = 2; y >= 1 && wet == null; y--) {
						BlockPos p = ctx.getAbsolutePos(new BlockPos(x, y, z));
						if (FiniteFluids.amountAt(world, p, Fluids.WATER) > 0) {
							wet = p;
						}
					}
				}
			}
			if (wet == null || !FiniteFluids.drain(world, wet, Fluids.WATER, 8)) {
				break;
			}
			buckets++;
		}
		int left = total(ctx, 6, 2);
		LOG.info("[EW-GAMETEST] estanque de 6x6x2: {} capas -> un cubo {} -> {} (el bloque mirado sigue {}); {} cubos en total, quedan {}", before, first,
				after, clicked, buckets, left);
		check(before == 576 && first && after == 568, "Un cubo no se lleva 8 capas: " + before + " -> " + after);
		check(clicked, "El cubo ha hecho un agujero en vez de bajar el nivel");
		check(buckets * 8 + left == 576 && buckets >= 66, "No se vacía en unos 72 cubos: " + buckets + ", quedan " + left);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void noInfiniteSources(TestContext ctx) {
		basin(ctx, 3, 1);
		ServerWorld world = ctx.getWorld();
		FiniteFluids.setAmount(world, ctx.getAbsolutePos(new BlockPos(1, 1, 2)), Fluids.WATER, 8);
		FiniteFluids.setAmount(world, ctx.getAbsolutePos(new BlockPos(3, 1, 2)), Fluids.WATER, 8);
		ctx.waitAndRun(120, () -> {
			int total = total(ctx, 3, 1);
			LOG.info("[EW-GAMETEST] dos fuentes con un hueco: {} capas", total);
			check(total == 16, "Dos fuentes han creado agua (o se ha perdido): " + total);
			ctx.complete();
		});
	}

	/** Un pilón 3x3 lleno a la altura y = 2, una tubería que sale de él hasta un grifo (más bajo, o más alto), y un caldero debajo. */
	private static BlockPos plumbing(TestContext ctx, int faucetY) {
		basin(ctx, 3, 2);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				ctx.setBlockState(x, 1, z, Blocks.WATER);
				ctx.setBlockState(x, 2, z, Blocks.WATER);
			}
		}
		// la toma: una tubería en la pared, tocando el agua
		ctx.setBlockState(4, 2, 2, ModBlocks.PIPE.getDefaultState());
		for (int x = 5; x <= 6; x++) {
			ctx.setBlockState(x, 2, 2, ModBlocks.PIPE.getDefaultState());
		}
		for (int y = 3; y <= faucetY; y++) {
			ctx.setBlockState(6, y, 2, ModBlocks.PIPE.getDefaultState());
		}
		ctx.setBlockState(7, faucetY, 2, ModBlocks.FAUCET.getDefaultState().with(FaucetBlock.FACING, Direction.EAST).with(FaucetBlock.OPEN, true));
		ctx.setBlockState(7, faucetY - 1, 2, Blocks.CAULDRON);
		return new BlockPos(7, faucetY - 1, 2);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void faucetFillsACauldron(TestContext ctx) {
		BlockPos cauldron = plumbing(ctx, 2);
		ctx.waitAndRun(120, () -> {
			var state = ctx.getBlockState(cauldron);
			int level = state.isOf(Blocks.WATER_CAULDRON) ? state.get(LeveledCauldronBlock.LEVEL) : 0;
			int pool = total(ctx, 3, 2);
			LOG.info("[EW-GAMETEST] grifo: caldero a nivel {}, al pilón le quedan {} de 144 capas", level, pool);
			check(level >= 2, "El grifo no llena el caldero: " + state);
			check(pool < 144 && pool > 100, "El agua del grifo no sale del pilón: " + pool);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void uphillNeedsThePump(TestContext ctx) {
		BlockPos cauldron = plumbing(ctx, 4);
		ctx.waitAndRun(60, () -> {
			boolean dry = ctx.getBlockState(cauldron).isOf(Blocks.CAULDRON);
			// una bomba en la tubería, con unos golpes de palanca
			ctx.setBlockState(5, 2, 2, ModBlocks.HAND_PUMP.getDefaultState());
			HandPumpBlockEntity pump = (HandPumpBlockEntity) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(5, 2, 2)));
			for (int i = 0; i < 4; i++) {
				pump.stroke(i * 10L);
			}
			ctx.waitAndRun(120, () -> {
				var state = ctx.getBlockState(cauldron);
				LOG.info("[EW-GAMETEST] grifo cuesta arriba: sin bomba seco {}, con bomba {} (bombeo que queda {})", dry, state, pump.charge());
				check(dry, "El agua sube sola por la tubería");
				check(state.isOf(Blocks.WATER_CAULDRON), "Con la bomba no sube el agua");
				ctx.complete();
			});
		});
	}
}
