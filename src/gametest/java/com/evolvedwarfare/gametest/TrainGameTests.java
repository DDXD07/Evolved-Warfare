package com.evolvedwarfare.gametest;

import com.evolvedwarfare.train.Building;
import com.evolvedwarfare.train.LocomotiveEntity;
import com.evolvedwarfare.train.PlatformEntity;
import com.evolvedwarfare.train.TenderEntity;
import com.evolvedwarfare.train.Track;
import com.evolvedwarfare.train.TrackItem;
import com.evolvedwarfare.train.TrainCarEntity;
import com.evolvedwarfare.train.Trains;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El tren grande: las vías de dos de ancho se enganchan solas, la locomotora quema el carbón del ténder y arrastra a sus
 * vagones por rectas y curvas a su distancia, y en la plataforma se construye con apoyos (lo que se queda sin apoyo, cae).
 */
public class TrainGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static List<ChunkPos> force(ServerWorld world, BlockPos o, int dx, int dz) {
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = -16; x <= dx + 16; x += 16) {
			for (int z = -16; z <= dz + 16; z += 16) {
				ChunkPos c = new ChunkPos(o.add(x, 0, z));
				forced.add(c);
				world.setChunkForced(c.x, c.z, true);
			}
		}
		return forced;
	}

	private static void clear(ServerWorld world, BlockPos o, int dx, int dz) {
		for (BlockPos p : BlockPos.iterate(o.add(-6, -1, -6), o.add(dx + 6, 8, dz + 6))) {
			world.setBlockState(p, p.getY() == o.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
	}

	/** Pone un trozo con su origen en (x, z) mirando hacia f; devuelve la punta del final (x, z). */
	private static double[] lay(ServerWorld world, Track.Shape shape, boolean left, Direction f, double x, double z, int y) {
		Track.Piece tmp = new Track.Piece(BlockPos.ORIGIN, shape, f, left, false, x, z, y);
		Track.Piece p = new Track.Piece(tmp.blockOf(0, 0), shape, f, left, false, x, z, y);
		TrackItem.place(world, p);
		Vec3d end = p.point(0, p.length(0));
		return new double[] {Math.round(end.x), Math.round(end.z)};
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void steamTrainPullsItsCars(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 400);
		List<ChunkPos> forced = force(world, o, 48, 32);
		clear(world, o, 32, 24);
		int y = o.getY();
		// 10 rectas hacia el este, una curva a la derecha (al sur) y 6 rectas hacia el sur
		double[] at = {o.getX(), o.getZ()};
		for (int i = 0; i < 10; i++) {
			at = lay(world, Track.Shape.STRAIGHT, false, Direction.EAST, at[0], at[1], y);
		}
		at = lay(world, Track.Shape.CURVE, false, Direction.EAST, at[0], at[1], y);
		for (int i = 0; i < 6; i++) {
			at = lay(world, Track.Shape.STRAIGHT, false, Direction.SOUTH, at[0], at[1], y);
		}
		double endX = at[0], endZ = at[1];
		// la locomotora en el octavo trozo, mirando al este, con un ténder y una plataforma detrás
		BlockPos sixth = BlockPos.ofFloored(o.getX() + 15.5, y, o.getZ() + 0.5);
		LocomotiveEntity loco = Trains.LOCOMOTIVE.create(world);
		loco.setTrack(Track.start(world, sixth, Direction.EAST));
		world.spawnEntity(loco);
		TenderEntity tender = Trains.TENDER.create(world);
		tender.setPosition(loco.getPos());
		world.spawnEntity(tender);
		loco.addWagon(tender);
		PlatformEntity flat = Trains.PLATFORM.create(world);
		flat.setPosition(loco.getPos());
		world.spawnEntity(flat);
		loco.addWagon(flat);
		boolean placed = loco.place();
		tender.inventory.setStack(0, new ItemStack(Items.COAL, 8));
		double gap0 = loco.getPos().distanceTo(tender.getPos()), gap1 = tender.getPos().distanceTo(flat.getPos());
		loco.setThrottle(LocomotiveEntity.MAX_NOTCH);
		double startX = loco.getX();
		ctx.runAtTick(220, () -> {
			double gapA = loco.getPos().distanceTo(tender.getPos());
			int coal = tender.inventory.getStack(0).getCount();
			double toEnd = Math.hypot(loco.getX() - endX, loco.getZ() - endZ);
			double dy = Math.abs(loco.getY() - (y + Track.RAIL_TOP)) + Math.abs(flat.getY() - (y + Track.RAIL_TOP));
			LOG.info("[EW-GAMETEST] tren: colocado {}, huecos al poner {} / {}, al final {}, del fin de la vía {}, carbón {}, velocidad {}, altura {}, "
					+ "giro {}, plataforma en {} {}", placed, gap0, gap1, gapA, toEnd, coal, loco.speed(), dy, loco.getYaw(), flat.getX() - o.getX(),
					flat.getZ() - o.getZ());
			double want0 = LocomotiveEntity.HALF_LENGTH + LocomotiveEntity.GAP + TenderEntity.HALF_LENGTH;
			double want1 = TenderEntity.HALF_LENGTH + LocomotiveEntity.GAP + PlatformEntity.HALF_LENGTH;
			boolean ok = placed && Math.abs(gap0 - want0) < 0.05 && Math.abs(gap1 - want1) < 0.05 && Math.abs(gapA - want0) < 0.1 && toEnd < 2.4
					&& coal < 8 && dy < 0.01 && Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(loco.getYaw())) < 1.0F && loco.getX() > startX;
			for (TrainCarEntity c : new TrainCarEntity[] {loco, tender, flat}) {
				c.discard();
			}
			forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
			if (!ok) {
				throw new GameTestException("El tren no va bien por la vía: colocado " + placed + ", huecos " + gap0 + " / " + gap1 + " / " + gapA
						+ ", del fin " + toEnd + ", carbón " + coal + ", altura " + dy + ", giro " + loco.getYaw());
			}
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void trackPiecesSnapAndBreakWhole(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 460);
		List<ChunkPos> forced = force(world, o, 16, 16);
		clear(world, o, 12, 12);
		int y = o.getY();
		double[] end = lay(world, Track.Shape.STRAIGHT, false, Direction.EAST, o.getX(), o.getZ() + 4, y);
		// un trozo nuevo cerca de la punta (mirando a otro lado) se engancha a ella, en su dirección
		TrackItem.Plan plan = TrackItem.plan(world, BlockPos.ofFloored(end[0] + 1.5, y, end[1] + 1.5), Direction.NORTH, Track.Shape.CURVE, true, false);
		boolean snapped = plan.snapped() && plan.valid() && plan.piece().facing() == Direction.EAST && plan.piece().ox() == end[0]
				&& plan.piece().oz() == end[1];
		TrackItem.place(world, plan.piece());
		Track.Piece first = Track.piece(world, BlockPos.ofFloored(o.getX() + 0.5, y, o.getZ() + 3.5));
		boolean linked = first != null && Track.connected(world, first, 0, true);
		// un desvío: por la punta común va por donde esté echado
		Track.Piece curve = Track.piece(world, plan.piece().main());
		Vec3d curveEnd = curve == null ? Vec3d.ZERO : curve.point(0, curve.length(0));
		// romper una parte cualquiera de la curva quita el trozo entero
		BlockPos some = curve == null ? o : curve.blocks().get(curve.blocks().size() - 1);
		world.breakBlock(some, false);
		boolean gone = curve != null && curve.blocks().stream().noneMatch(b -> world.getBlockState(b).isOf(Trains.TRACK));
		LOG.info("[EW-GAMETEST] vías: se engancha {}, conectadas {}, la curva acaba en {}, rota entera {}", snapped, linked,
				curveEnd.subtract(o.getX(), y, o.getZ()), gone);
		forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
		if (!snapped || !linked || !gone) {
			throw new GameTestException("Las vías no se enganchan o no se rompen enteras: " + snapped + " / " + linked + " / " + gone);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void buildingNeedsSupport(TestContext ctx) {
		List<Building.Placed> pieces = new ArrayList<>();
		java.util.function.Function<Building.Slot, Boolean> put = s -> {
			if (!Building.canPlace(pieces, s)) {
				return false;
			}
			pieces.add(new Building.Placed(s.kind(), Building.Material.WOOD, s.level(), s.a(), s.b(), s.rot()));
			return true;
		};
		boolean wall = put.apply(new Building.Slot(Building.Kind.WALL, 0, 0, 0, 0));
		boolean sameSpot = !put.apply(new Building.Slot(Building.Kind.DOORWAY, 0, 0, 0, 0));
		boolean ceiling = put.apply(new Building.Slot(Building.Kind.CEILING, 0, 0, 0, 0));
		boolean nextCeiling = put.apply(new Building.Slot(Building.Kind.CEILING, 0, 1, 0, 0));
		boolean floating = !put.apply(new Building.Slot(Building.Kind.CEILING, 0, 0, 3, 0));
		boolean upstairsWall = put.apply(new Building.Slot(Building.Kind.WINDOW, 1, 1, 0, 0));
		boolean noUpstairsWithoutFloor = !put.apply(new Building.Slot(Building.Kind.WALL, 1, 0, 3, 0));
		boolean stairs = put.apply(new Building.Slot(Building.Kind.STAIRS, 0, 0, 2, 0));
		boolean ceilingOverStairs = !put.apply(new Building.Slot(Building.Kind.CEILING, 0, 0, 2, 0));
		// se apunta al suelo de la plataforma, cerca de un borde: la pared va en ese borde
		Building.Slot aim = Building.slotFor(Building.Kind.WALL, new Vec3d(-1.4, Building.DECK, 0.4), new Vec3d(0, -1, 0));
		boolean aimed = aim != null && aim.rot() == 1 && aim.a() == 0 && aim.level() == 0;
		// se quita la pared de abajo: los techos y la ventana de arriba se caen
		pieces.remove(0);
		List<Building.Placed> fall = Building.unsupported(pieces);
		boolean falls = fall.size() == 3;
		LOG.info("[EW-GAMETEST] construir: pared {}, sitio ocupado {}, techo {}, techo al lado {}, techo en el aire {}, ventana arriba {}, "
				+ "pared arriba sin suelo {}, escalera {}, techo sobre escalera {}, apuntar {}, se caen {}", wall, sameSpot, ceiling, nextCeiling, floating,
				upstairsWall, noUpstairsWithoutFloor, stairs, ceilingOverStairs, aim, fall.size());
		if (!(wall && sameSpot && ceiling && nextCeiling && floating && upstairsWall && noUpstairsWithoutFloor && stairs && ceilingOverStairs && aimed && falls)) {
			throw new GameTestException("Las reglas de construcción fallan");
		}
		ctx.complete();
	}
}
