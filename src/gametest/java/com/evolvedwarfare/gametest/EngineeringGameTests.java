package com.evolvedwarfare.gametest;

import com.evolvedwarfare.engineering.Engineering;
import com.evolvedwarfare.engineering.KineticBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La ingeniería medieval (fase 72): la red de fuerza, las relaciones de los engranajes, la sobrecarga, el agua y el viento. */
public class EngineeringGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static KineticBlockEntity be(TestContext ctx, int x, int y, int z) {
		if (ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(new BlockPos(x, y, z))) instanceof KineticBlockEntity k) {
			return k;
		}
		throw new GameTestException("No hay bloque de fuerza en " + x + "," + y + "," + z);
	}

	/** Manivela → ejes → velocímetro → engranaje pequeño → otro pequeño (al revés) y uno grande en diagonal (a la mitad). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_gears", tickLimit = 80)
	public void gearsShareSpeedAndForce(TestContext ctx) {
		ctx.setBlockState(1, 2, 1, Engineering.HAND_CRANK.getDefaultState().with(Properties.FACING, Direction.WEST));
		for (int x = 2; x <= 4; x++) {
			ctx.setBlockState(x, 2, 1, (x == 3 ? Engineering.SPEED_GAUGE.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X)
					: Engineering.SHAFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X)));
		}
		ctx.setBlockState(5, 2, 1, Engineering.SMALL_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(5, 3, 1, Engineering.SMALL_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(5, 3, 2, Engineering.LARGE_GEAR.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.runAtTick(2, () -> {
			KineticBlockEntity crank = be(ctx, 1, 2, 1);
			crank.turnCrank(200);
			crank.refreshSource();
		});
		ctx.runAtTick(8, () -> {
			float s = be(ctx, 2, 2, 1).speed(), g = be(ctx, 3, 2, 1).speed(), a = be(ctx, 5, 2, 1).speed(), b = be(ctx, 5, 3, 1).speed(),
					l = be(ctx, 5, 3, 2).speed();
			LOG.info("[EW-GAMETEST] engranajes: eje {}, velocímetro {}, pequeño {}, pequeño al lado {}, grande {}, fuerza {}/{}", s, g, a, b, l,
					be(ctx, 4, 2, 1).stress(), be(ctx, 4, 2, 1).capacity());
			if (!(Math.abs(Math.abs(s) - 16) < 0.01 && g == s && a == s && Math.abs(b + a) < 0.01 && Math.abs(l - (-0.5F * a)) < 0.01
					&& be(ctx, 4, 2, 1).capacity() > 40)) {
				throw new GameTestException("Las velocidades de la red no cuadran");
			}
			// sobrecarga: 4 de fuerza por rpm a 16 rpm = 64 > 48
			be(ctx, 3, 2, 1).setExtraImpact(4.0F);
		});
		ctx.runAtTick(14, () -> {
			KineticBlockEntity g = be(ctx, 3, 2, 1);
			LOG.info("[EW-GAMETEST] sobrecarga: estado {}, velocidad {}, fuerza {}/{}", g.networkState(), g.speed(), g.stress(), g.capacity());
			if (g.networkState() != KineticBlockEntity.OVERSTRESSED || g.speed() != 0) {
				throw new GameTestException("La red no se sobrecarga");
			}
			g.setExtraImpact(1.0F);
		});
		ctx.runAtTick(20, () -> {
			KineticBlockEntity g = be(ctx, 3, 2, 1);
			LOG.info("[EW-GAMETEST] sin sobrecarga: estado {}, velocidad {}, fuerza {}/{}", g.networkState(), g.speed(), g.stress(), g.capacity());
			if (g.networkState() != KineticBlockEntity.OK || Math.abs(g.speed()) < 1 || Math.abs(g.stress() - 16) > 0.5) {
				throw new GameTestException("La red no vuelve a girar");
			}
			ctx.complete();
		});
	}

	/** El agua que cae por un lado mueve la rueda hidráulica. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_water", tickLimit = 120)
	public void waterTurnsTheWheel(TestContext ctx) {
		ctx.setBlockState(3, 3, 3, Engineering.WATER_WHEEL_SMALL.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X));
		ctx.setBlockState(3, 3, 1, Engineering.SHAFT.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		ctx.setBlockState(3, 6, 4, Blocks.WATER.getDefaultState());
		ctx.runAtTick(70, () -> {
			KineticBlockEntity w = be(ctx, 3, 3, 3);
			for (int y = 1; y <= 6; y++) {
				net.minecraft.util.math.BlockPos q = ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, y, 4));
				LOG.info("[EW-GAMETEST] columna y{}: {} vel {}", y, ctx.getWorld().getBlockState(q), ctx.getWorld().getFluidState(q).getVelocity(ctx.getWorld(), q));
			}
			LOG.info("[EW-GAMETEST] rueda en {}: {}", ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 3, 3)), ctx.getWorld().getBlockState(ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 3, 3))));
			LOG.info("[EW-GAMETEST] rueda hidráulica: da {}, gira {}, fuerza {}, problema {}", w.generated(), w.speed(), w.capacity(), w.problem());
			if (Math.abs(w.speed()) < 1 || w.capacity() <= 0) {
				throw new GameTestException("El agua no mueve la rueda");
			}
			ctx.complete();
		});
	}

	/** El molino de viento gira con el aire libre y se para si algo estorba a las aspas. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "kinetic_wind", tickLimit = 160)
	public void windTurnsTheSails(TestContext ctx) {
		ctx.setBlockState(3, 4, 3, Engineering.WINDMILL_SMALL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
		ctx.runAtTick(50, () -> {
			KineticBlockEntity w = be(ctx, 3, 4, 3);
			LOG.info("[EW-GAMETEST] molino libre: gira {}, fuerza {}", w.speed(), w.capacity());
			if (Math.abs(w.speed()) < 1 || w.capacity() <= 0) {
				throw new GameTestException("El molino no gira con aire libre");
			}
			ctx.setBlockState(3, 5, 3, Blocks.STONE);
		});
		ctx.runAtTick(100, () -> {
			KineticBlockEntity w = be(ctx, 3, 4, 3);
			LOG.info("[EW-GAMETEST] molino tapado: gira {}, problema {}", w.speed(), w.problem());
			if (w.speed() != 0 || w.problem() != 1) {
				throw new GameTestException("El molino gira aunque algo estorba a las aspas");
			}
			ctx.complete();
		});
	}
}
