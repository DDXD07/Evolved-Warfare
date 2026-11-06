package com.evolvedwarfare.gametest;

import com.evolvedwarfare.train.FlatcarEntity;
import com.evolvedwarfare.train.LocomotiveEntity;
import com.evolvedwarfare.train.Track;
import com.evolvedwarfare.train.Trains;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El tren de vapor (fase 41): las vías, la locomotora con carbón, las plataformas, construir en ellas y llevar lo de encima. */
public class TrainGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void rail(ServerWorld world, BlockPos p, RailShape shape) {
		world.setBlockState(p.down(), Blocks.STONE.getDefaultState(), 2);
		world.setBlockState(p, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, shape), 2);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void railsAreWalked(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// una recta de 3 hacia el este, una curva hacia el sur y 2 al sur
		BlockPos o = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		rail(world, o, RailShape.EAST_WEST);
		rail(world, o.east(), RailShape.EAST_WEST);
		rail(world, o.east(2), RailShape.EAST_WEST);
		rail(world, o.east(3), RailShape.SOUTH_WEST);
		rail(world, o.east(3).south(), RailShape.NORTH_SOUTH);
		rail(world, o.east(3).south(2), RailShape.NORTH_SOUTH);
		Track.Pos start = Track.start(world, o, Direction.EAST);
		Track.Walk ahead = Track.walk(world, start, 3.0);
		Vec3d p = Track.point(world, ahead.pos())[0];
		Track.Walk back = Track.walk(world, ahead.pos(), -3.0);
		Vec3d q = Track.point(world, back.pos())[0];
		Track.Walk end = Track.walk(world, start, 10.0);
		Vec3d s = Track.point(world, start)[0];
		LOG.info("[EW-GAMETEST] vías: de {} a {} (curva), y de vuelta {}; al final {} (parada {})", s, p, q, Track.point(world, end.pos())[0], end.blocked());
		check(!ahead.blocked() && ahead.pos().rail().equals(o.east(3)) && q.distanceTo(s) < 0.01 && end.blocked()
				&& end.pos().rail().equals(o.east(3).south(2)), "El tren no sabe andar por las vías");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void steamTrainCarriesItsLoad(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 40, 3));
		for (int i = 0; i < 64; i++) {
			rail(world, o.east(i), RailShape.EAST_WEST);
		}
		LocomotiveEntity loco = Trains.spawnLocomotive(world, o.east(30), Direction.EAST);
		check(loco != null, "No se pone la locomotora");
		FlatcarEntity a = Trains.addWagon(loco), b = Trains.addWagon(loco);
		check(a != null && b != null, "No se enganchan las plataformas");
		a.set(2, 0, 4, Blocks.OAK_PLANKS.getDefaultState());
		a.set(2, 1, 4, Blocks.OAK_STAIRS.getDefaultState());
		double gap = loco.getX() - a.getX();
		// un cerdo quieto en la segunda plataforma, y un soporte de armadura que cae sobre la primera
		var pig = EntityType.PIG.create(world);
		pig.setAiDisabled(true);
		pig.refreshPositionAndAngles(b.getX() + 1.0, b.getY() + FlatcarEntity.DECK, b.getZ(), 0, 0);
		world.spawnEntity(pig);
		ArmorStandEntity stand = new ArmorStandEntity(world, a.getX() - 1.5, a.getY() + FlatcarEntity.DECK + 3.0, a.getZ() - 1.0);
		world.spawnEntity(stand);
		double startX = loco.getX();
		boolean noFuel = loco.fuel() == 0;
		ctx.runAtTick(30, () -> {
			// sin carbón no anda
			loco.setTarget(LocomotiveEntity.MAX_SPEED);
		});
		ctx.runAtTick(60, () -> LOG.info("[EW-GAMETEST] el tren a los 60: en el mundo {}, vivo {}, edad {}, objetivo {}, vel {}, vía {}", world.getEntity(loco.getUuid()) == loco,
				loco.isAlive(), loco.age, loco.target(), loco.speed(), loco.track()));
		ctx.runAtTick(45, () -> {
			check(Math.abs(loco.getX() - startX) < 0.01, "Anda sin carbón");
			loco.addFuel(10 * LocomotiveEntity.COAL_BLOCK);
			loco.setTarget(LocomotiveEntity.MAX_SPEED);
		});
		ctx.runAtTick(150, () -> {
			double moved = loco.getX() - startX;
			double gapNow = loco.getX() - a.getX();
			boolean pigOn = Math.abs(pig.getX() - b.getX()) < 2.5 && pig.getY() > b.getY() + 1.0;
			boolean standOn = stand.getY() > a.getY() + 1.0 && Math.abs(stand.getX() - a.getX()) < 2.5;
			LOG.info("[EW-GAMETEST] el tren: sin carbón {}, ha andado {} (vel {}), separación {} -> {}, cerdo encima {} ({} / {}), soporte encima {} ({}), "
					+ "carbón {}, bloques {}", noFuel, moved, loco.speed(), gap, gapNow, pigOn, pig.getPos(), b.getPos(), standOn, stand.getPos(), loco.fuel(),
					a.blockCount());
			check(noFuel && moved > 5 && Math.abs(gapNow - LocomotiveEntity.wagonOffset(0)) < 0.3 && pigOn && standOn
					&& loco.fuel() < 10 * LocomotiveEntity.COAL_BLOCK && a.blockCount() == 2, "El tren no lleva bien su carga");
			pig.discard();
			stand.discard();
			a.discard();
			b.discard();
			loco.discard();
			ctx.complete();
		});
	}
}
