package com.evolvedwarfare.gametest;

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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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
		// 14 rectas hacia el este (desde 8 bloques antes, para que quepa detrás la plataforma de 10), una curva a la derecha
		// (al sur) y 6 rectas hacia el sur
		for (BlockPos p : BlockPos.iterate(o.add(-12, -1, -6), o.add(-6, 8, 6))) {
			world.setBlockState(p, p.getY() == o.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		double[] at = {o.getX() - 8, o.getZ()};
		for (int i = 0; i < 14; i++) {
			at = lay(world, Track.Shape.STRAIGHT, false, Direction.EAST, at[0], at[1], y);
		}
		at = lay(world, Track.Shape.CURVE, false, Direction.EAST, at[0], at[1], y);
		for (int i = 0; i < 6; i++) {
			at = lay(world, Track.Shape.STRAIGHT, false, Direction.SOUTH, at[0], at[1], y);
		}
		double endX = at[0], endZ = at[1];
		// la locomotora en el duodécimo trozo, mirando al este, con un ténder y una plataforma (de 10 de largo) detrás
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

	/** La plataforma de 5 x 10 se construye bloque a bloque, hasta 4 de alto; las de antes pasan a bloques (y devuelven las piezas). */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void platformBuildsBlockByBlock(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 600);
		List<ChunkPos> forced = force(world, o, 10, 10);
		clear(world, o, 8, 8);
		PlatformEntity flat = Trains.PLATFORM.create(world);
		flat.refreshPositionAndAngles(o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.5, 0, 0);
		flat.setNoGravity(true);
		world.spawnEntity(flat);
		var g = flat.grid();
		java.util.function.BiFunction<Double, Double, Boolean> onDeck = (x, z) -> {
			var plan = com.evolvedwarfare.voxel.VoxelBuild.plan(g, new com.evolvedwarfare.voxel.VoxelGrid.Hit(-1, net.minecraft.util.math.Direction.UP,
					new Vec3d(x, PlatformEntity.DECK, z)), new ItemStack(Items.OAK_PLANKS), new Vec3d(0, -1, 0));
			if (plan == null) {
				return false;
			}
			com.evolvedwarfare.voxel.VoxelBuild.apply(flat, plan);
			return true;
		};
		// las cuatro esquinas de 5 x 10
		boolean corners = onDeck.apply(-2.4, -4.9) && onDeck.apply(2.4, -4.9) && onDeck.apply(-2.4, 4.9) && onDeck.apply(2.4, 4.9);
		boolean outside = !onDeck.apply(2.6, 0.0);
		// hasta 4 de alto
		int x = 2, z = 5;
		for (int y = 0; y < PlatformEntity.HEIGHT; y++) {
			flat.putBlock(x, y, z, net.minecraft.block.Blocks.STONE.getDefaultState());
		}
		boolean tooHigh = com.evolvedwarfare.voxel.VoxelBuild.plan(g, new com.evolvedwarfare.voxel.VoxelGrid.Hit(g.index(x, PlatformEntity.HEIGHT - 1, z),
				net.minecraft.util.math.Direction.UP, new Vec3d(0, PlatformEntity.DECK + PlatformEntity.HEIGHT, 0.5)), new ItemStack(Items.STONE),
				new Vec3d(0, -1, 0)) == null;
		double top = flat.worldBoxes().stream().mapToDouble(b -> b.maxY).max().orElse(0) - flat.getY();
		// una plataforma de antes: un cofre con hierro y una pared de madera
		NbtCompound old = new NbtCompound();
		NbtCompound chest = new NbtCompound();
		chest.putByte("K", (byte) 0);
		chest.putByte("X", (byte) 1);
		chest.putByte("Z", (byte) 2);
		chest.putByte("L", (byte) 0);
		chest.putByte("F", (byte) 2);
		net.minecraft.util.collection.DefaultedList<ItemStack> inv = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
		inv.set(0, new ItemStack(Items.IRON_INGOT, 9));
		NbtCompound invNbt = new NbtCompound();
		net.minecraft.inventory.Inventories.writeNbt(invNbt, inv, world.getRegistryManager());
		chest.put("Inv", invNbt);
		NbtList blocks = new NbtList();
		blocks.add(chest);
		NbtCompound wall = new NbtCompound();
		wall.putByte("K", (byte) 0);
		wall.putByte("M", (byte) 1);
		NbtList pieces = new NbtList();
		pieces.add(wall);
		old.put("Blocks", blocks);
		old.put("List", pieces);
		PlatformEntity older = Trains.PLATFORM.create(world);
		NbtCompound nbt = older.writeNbt(new NbtCompound());
		nbt.remove("Grid");
		nbt.put("Pieces", old);
		older.readNbt(nbt);
		older.refreshPositionAndAngles(o.getX() + 0.5, o.getY() + 2, o.getZ() + 8.5, 0, 0);
		older.setNoGravity(true);
		world.spawnEntity(older);
		var og = older.grid();
		var m = og.machine(og.index(2, 0, 4));
		boolean migrated = og.get(og.index(2, 0, 4)).isOf(net.minecraft.block.Blocks.CHEST) && m != null && m.inventory.getStack(0).getCount() == 9;
		ctx.waitAndRun(10, () -> {
			int planks = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, older.getBoundingBox().expand(4),
					e -> e.getStack().isOf(Items.OAK_PLANKS)).stream().mapToInt(e -> e.getStack().getCount()).sum();
			LOG.info("[EW-GAMETEST] plataforma: esquinas {}, fuera {}, más de 4 {}, lo macizo llega a {}, la vieja pasa a bloques {} y devuelve {} tablones",
					corners, !outside, !tooHigh, top, migrated, planks);
			flat.discard();
			older.discard();
			world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(o).expand(14), e -> true)
					.forEach(net.minecraft.entity.Entity::discard);
			forced.forEach(cp -> world.setChunkForced(cp.x, cp.z, false));
			if (!(corners && outside && tooHigh && Math.abs(top - (PlatformEntity.DECK + PlatformEntity.HEIGHT)) < 0.01 && migrated && planks == 6)) {
				throw new GameTestException("La plataforma no se construye bien bloque a bloque");
			}
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 900)
	public void platformBlocksWork(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, 520);
		List<ChunkPos> forced = force(world, o, 10, 10);
		clear(world, o, 8, 8);
		PlatformEntity flat = Trains.PLATFORM.create(world);
		flat.refreshPositionAndAngles(o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.5, 0, 0);
		flat.setNoGravity(true);
		world.spawnEntity(flat);
		flat.putBlock(0, 0, 0, net.minecraft.block.Blocks.FURNACE.getDefaultState());
		flat.putBlock(4, 0, 9, net.minecraft.block.Blocks.CAMPFIRE.getDefaultState());
		flat.putBlock(2, 0, 4, net.minecraft.block.Blocks.CHEST.getDefaultState());
		var g = flat.grid();
		var f = g.machine(g.index(0, 0, 0));
		f.inventory.setStack(0, new ItemStack(Items.RAW_IRON, 2));
		f.inventory.setStack(1, new ItemStack(Items.COAL, 1));
		boolean cooking = g.machine(g.index(4, 0, 9)).addToCampfire(world, new ItemStack(Items.BEEF));
		boolean chest = g.machine(g.index(2, 0, 4)) != null;
		ctx.runAtTick(700, () -> {
			int ingots = f.inventory.getStack(2).isOf(Items.IRON_INGOT) ? f.inventory.getStack(2).getCount() : 0;
			boolean cooked = !world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, flat.getBoundingBox().expand(4),
					e -> e.getStack().isOf(Items.COOKED_BEEF)).isEmpty();
			float warm = Trains.trainWarmth(world, BlockPos.ofFloored(flat.gridToWorld(g.center(g.index(4, 0, 9)))));
			LOG.info("[EW-GAMETEST] bloques del tren: cofre {}, lingotes {}, cocinado {} ({}), calor {}", chest, ingots, cooked, cooking, warm);
			flat.discard();
			world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, flat.getBoundingBox().expand(6), e -> true).forEach(net.minecraft.entity.Entity::discard);
			forced.forEach(cp -> world.setChunkForced(cp.x, cp.z, false));
			if (!(chest && ingots == 2 && cooked && cooking && warm > 0)) {
				throw new GameTestException("Los bloques de la plataforma no funcionan: lingotes " + ingots + ", cocinado " + cooked + ", calor " + warm);
			}
			ctx.complete();
		});
	}
}
