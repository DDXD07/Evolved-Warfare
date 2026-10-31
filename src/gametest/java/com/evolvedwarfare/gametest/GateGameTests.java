package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.GateMoverEntity;
import com.evolvedwarfare.block.GateWinchBlock;
import com.evolvedwarfare.block.GateWinchBlockEntity;
import com.evolvedwarfare.block.PortcullisBlock;
import com.evolvedwarfare.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El rastrillo y el puente levadizo se mueven con animación: mientras tanto sus bloques no están (los lleva una entidad
 * que los dibuja moviéndose) y al acabar quedan en su sitio nuevo: el rastrillo arriba o en el suelo, el puente de pie o
 * tumbado.
 */
public class GateGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void portcullisSlides(TestContext ctx) {
		floor(ctx);
		BlockState bars = ModBlocks.PORTCULLIS.getDefaultState().with(PortcullisBlock.AXIS, Direction.Axis.X);
		for (int x = 1; x <= 3; x++) {
			for (int y = 1; y <= 2; y++) {
				ctx.setBlockState(x, y, 2, bars);
			}
		}
		GateWinchBlockEntity winch = winch(ctx, new BlockPos(0, 1, 2), Direction.NORTH);
		winch.onRedstone(true);
		ctx.waitAndRun(6, () -> {
			boolean animating = winch.isAnimating();
			boolean lifted = ctx.getBlockState(new BlockPos(2, 1, 2)).isAir();
			boolean mover = movers(ctx) > 0;
			ctx.waitAndRun(30, () -> {
				boolean up = is(ctx, 1, 3, 3, 4, 2, bars.getBlock()) && is(ctx, 1, 3, 1, 2, 2, Blocks.AIR);
				boolean done = !winch.isAnimating() && movers(ctx) == 0;
				LOG.info("[EW-GAMETEST] rastrillo: en marcha={} (bloques quitados={}, entidad={}), arriba={}, acabado={}", animating, lifted, mover,
						up, done);
				check(animating && lifted && mover, "El rastrillo no se mueve con animación");
				check(up && done, "El rastrillo no queda subido 2 bloques al acabar");
				winch.onRedstone(false);
				ctx.waitAndRun(30, () -> {
					boolean down = is(ctx, 1, 3, 1, 2, 2, bars.getBlock()) && is(ctx, 1, 3, 3, 4, 2, Blocks.AIR);
					LOG.info("[EW-GAMETEST] rastrillo cerrado: abajo={}", down);
					check(down && movers(ctx) == 0, "El rastrillo no vuelve a bajar hasta el suelo");
					ctx.complete();
				});
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
	public void drawbridgeSwings(TestContext ctx) {
		// puente bajado de 3 x 4 (hacia el sur) con el torno pegado a la fila de la bisagra
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 4; z++) {
				ctx.setBlockState(x, 1, z, ModBlocks.DRAWBRIDGE);
			}
		}
		GateWinchBlockEntity winch = winch(ctx, new BlockPos(2, 1, 0), Direction.SOUTH);
		winch.onRedstone(false);
		ctx.waitAndRun(8, () -> {
			boolean animating = winch.isAnimating();
			boolean lifted = ctx.getBlockState(new BlockPos(2, 1, 3)).isAir() && ctx.getBlockState(new BlockPos(2, 1, 1)).isAir();
			boolean mover = movers(ctx) > 0;
			ctx.waitAndRun(45, () -> {
				// de pie sobre la bisagra (z = 1, de y = 1 a 4) y nada donde estaba tumbado
				boolean raised = is(ctx, 1, 3, 1, 4, 1, ModBlocks.DRAWBRIDGE) && is(ctx, 1, 3, 1, 1, 2, Blocks.AIR)
						&& is(ctx, 1, 3, 1, 1, 4, Blocks.AIR);
				LOG.info("[EW-GAMETEST] puente: en marcha={} (bloques quitados={}, entidad={}), subido={}", animating, lifted, mover, raised);
				check(animating && lifted && mover, "El puente no se mueve con animación");
				check(raised && !winch.isAnimating() && movers(ctx) == 0, "El puente no queda de pie sobre la bisagra");
				winch.onRedstone(true);
				ctx.waitAndRun(45, () -> {
					boolean lowered = true;
					for (int z = 1; z <= 4; z++) {
						lowered &= is(ctx, 1, 3, 1, 1, z, ModBlocks.DRAWBRIDGE);
					}
					lowered &= is(ctx, 1, 3, 2, 4, 1, Blocks.AIR);
					LOG.info("[EW-GAMETEST] puente bajado otra vez: {}", lowered);
					check(lowered && movers(ctx) == 0, "El puente no vuelve a bajar");
					ctx.complete();
				});
			});
		});
	}

	// ------------------------------------------------------------------ ayudas

	private static GateWinchBlockEntity winch(TestContext ctx, BlockPos pos, Direction facing) {
		ctx.setBlockState(pos, ModBlocks.GATE_WINCH.getDefaultState().with(GateWinchBlock.FACING, facing));
		if (!(ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(pos)) instanceof GateWinchBlockEntity winch)) {
			throw new GameTestException("No hay torno");
		}
		return winch;
	}

	private static int movers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		return world.getEntitiesByClass(GateMoverEntity.class, new Box(origin).expand(8), e -> true).size();
	}

	/** ¿Son de ese bloque todas las posiciones x0..x1, y0..y1 (con esa z)? */
	private static boolean is(TestContext ctx, int x0, int x1, int y0, int y1, int z, Block block) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				if (!ctx.getBlockState(new BlockPos(x, y, z)).isOf(block)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}
}
