package com.evolvedwarfare.gametest;

import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.trade.Deal;
import com.evolvedwarfare.trade.Tier;
import com.evolvedwarfare.trade.TraderType;
import com.evolvedwarfare.trade.VillageShops;
import com.evolvedwarfare.world.VillageBuildings;
import com.evolvedwarfare.world.VillageExtrasPiece;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las aldeas vanilla: sus edificios nuevos (mercado, taberna, granero, atalaya) y los aldeanos tenderos (y los que solo trabajan). */
public class VillageGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void villagesGetNewBuildings(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		Structure village = registry.get(Identifier.ofVanilla("village_plains"));
		ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
		int planned = 0, tries = 0;
		boolean market = false;
		int decor = 0;
		StringBuilder found = new StringBuilder();
		for (int i = 0; i < 6 && planned < 3; i++) {
			tries++;
			ChunkPos at = new ChunkPos(new ChunkPos(ctx.getAbsolutePos(BlockPos.ORIGIN)).x + 40 + i * 20, 40);
			StructureStart start = village.createStructureStart(world.getRegistryManager(), gen, gen.getBiomeSource(),
					world.getChunkManager().getNoiseConfig(), world.getStructureTemplateManager(), world.getSeed() + i, at, 0, world, biome -> true);
			if (!start.hasChildren()) {
				continue;
			}
			for (StructurePiece piece : start.getChildren()) {
				if (piece instanceof VillageExtrasPiece extras) {
					int[] b = extras.buildings();
					int n = 0, dec = 0;
					boolean m = false;
					StringBuilder f = new StringBuilder();
					for (int k = 0; k + 4 < b.length; k += 5) {
						if (VillageBuildings.decor(b[k])) {
							dec++;
						} else {
							n++;
							f.append(b[k]).append(' ');
							m |= b[k] == VillageBuildings.MARKET || b[k] == VillageBuildings.MARKET_SMALL;
						}
					}
					if (n > planned) {
						planned = n;
						decor = dec;
						market = m;
						found.setLength(0);
						found.append(f);
					}
				}
			}
		}
		LOG.info("[EW-GAMETEST] aldea: {} edificios nuevos ({}) en {} intentos, mercado {}, decoraciones {}", planned, found.toString().trim(), tries,
				market, decor);
		if (planned < 2 || !market || decor < 3) {
			throw new GameTestException("Las aldeas no reciben sus edificios nuevos: " + planned + " (" + found + ")");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void villagersShopOrWork(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		VillagerEntity shop = null, worker = null;
		for (int i = 0; i < 40 && (shop == null || worker == null); i++) {
			VillagerEntity v = EntityType.VILLAGER.create(world);
			v.setVillagerData(v.getVillagerData().withProfession(VillagerProfession.FARMER).withLevel(1));
			BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
			v.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			if (VillageShops.hasShop(v) && shop == null) {
				shop = v;
				world.spawnEntity(v);
			} else if (!VillageShops.hasShop(v) && worker == null) {
				worker = v;
				world.spawnEntity(v);
			}
		}
		player.refreshPositionAndAngles(shop.getX() + 1, shop.getY(), shop.getZ(), 0, 0);
		shop.interact(player, Hand.MAIN_HAND);
		boolean opened = player.currentScreenHandler instanceof TraderScreenHandler h && h.traderType() == TraderType.GROCER && !h.deals().isEmpty()
				&& h.cityLevel() < 0;
		player.closeHandledScreen();
		worker.interact(player, Hand.MAIN_HAND);
		boolean workerClosed = !(player.currentScreenHandler instanceof TraderScreenHandler);
		// el catálogo de aldea: sin lo raro, y más barato
		List<Deal> city = TraderType.GROCER.catalog(1, 77L, 0L, world.getRegistryManager(), false);
		List<Deal> village = TraderType.GROCER.catalog(1, 77L, 0L, world.getRegistryManager(), true);
		boolean basic = village.stream().allMatch(d -> d.tier().ordinal() <= Tier.UNCOMMON.ordinal()) && village.size() <= city.size();
		double cityTotal = 0, villageTotal = 0;
		for (Deal v : village) {
			if (!v.sell()) {
				continue;
			}
			for (Deal c : city) {
				if (c.sell() && c.item() == v.item()) {
					cityTotal += c.price() / (double) c.count();
					villageTotal += v.price() / (double) v.count();
					break;
				}
			}
		}
		boolean cheaper = villageTotal > 0 && villageTotal < cityTotal * 0.95;
		LOG.info("[EW-GAMETEST] aldeanos: tienda {}, el que trabaja no abre {}, catálogo básico {} ({} de {}), más barato {} ({} / {})", opened,
				workerClosed, basic, village.size(), city.size(), cheaper, villageTotal, cityTotal);
		shop.discard();
		worker.discard();
		if (!opened || !workerClosed || !basic || !cheaper) {
			throw new GameTestException("Los aldeanos no comercian bien: tienda " + opened + ", trabajador " + workerClosed + ", básico " + basic + ", barato " + cheaper);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void streetsGetStepsOnSlopes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos b = a.east();
		// una calle que sube un bloque: la de abajo en a, la de arriba en b
		for (BlockPos p : BlockPos.iterate(a.add(-1, 0, -1), b.add(1, 14, 1))) {
			world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState());
		}
		world.setBlockState(a, net.minecraft.block.Blocks.DIRT_PATH.getDefaultState());
		world.setBlockState(b, net.minecraft.block.Blocks.DIRT.getDefaultState());
		world.setBlockState(b.up(), net.minecraft.block.Blocks.DIRT_PATH.getDefaultState());
		int[] streets = {a.getX(), a.getZ(), b.getX(), b.getZ()};
		net.minecraft.util.math.BlockBox box = new net.minecraft.util.math.BlockBox(a.getX() - 2, a.getY() - 4, a.getZ() - 2, b.getX() + 2, b.getY() + 6, b.getZ() + 2);
		VillageExtrasPiece piece = new VillageExtrasPiece(com.evolvedwarfare.world.VillageStyle.PLAINS, box, new int[0], new int[0], new int[0], new int[0],
				new int[0], new int[0], streets);
		piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), net.minecraft.util.math.random.Random.create(1), box,
				new ChunkPos(a), a);
		var step = world.getBlockState(a.up());
		boolean ok = step.getBlock() instanceof net.minecraft.block.StairsBlock
				&& step.get(net.minecraft.block.StairsBlock.FACING) == net.minecraft.util.math.Direction.EAST;
		LOG.info("[EW-GAMETEST] escalones: {} (a {} b {}; altura a {}/{}, b {}/{}; en a {}, en b+1 {})", step, a, b,
				world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, a.getX(), a.getZ()),
				world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, a.getX(), a.getZ()),
				world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, b.getX(), b.getZ()),
				world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, b.getX(), b.getZ()), world.getBlockState(a), world.getBlockState(b.up()));
		if (!ok) {
			throw new GameTestException("La calle en cuesta no tiene su escalón: " + step);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void villageGroundIsFlattenedButNotPerfect(TestContext ctx) {
		int w = 90, h = 90;
		boolean[] region = new boolean[w * h];
		for (int z = 15; z < 75; z++) {
			for (int x = 15; x < 75; x++) {
				region[z * w + x] = true;
			}
		}
		// una ladera: sube un bloque cada 3
		com.evolvedwarfare.world.VillageExtras.Terrain slope = new com.evolvedwarfare.world.VillageExtras.Terrain() {
			@Override
			public int ground(int x, int z) {
				return 64 + x / 3;
			}

			@Override
			public boolean dry(int x, int z) {
				return true;
			}
		};
		com.evolvedwarfare.world.VillageFlattenPiece f = com.evolvedwarfare.world.VillageFlattenPiece.plan(slope, 0, 0, w, h, region, List.of(), 1234);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, maxStep = 0;
		for (int z = 15; z < 75; z++) {
			int prev = Integer.MIN_VALUE;
			for (int x = 15; x < 75; x++) {
				int y = f.flat(x, z, slope.ground(x, z));
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
				if (prev != Integer.MIN_VALUE) {
					maxStep = Math.max(maxStep, Math.abs(y - prev));
				}
				prev = y;
			}
		}
		int before = slope.ground(74, 0) - slope.ground(15, 0);
		boolean outsideKept = f.flat(89, 40, slope.ground(89, 40)) == slope.ground(89, 40);
		LOG.info("[EW-GAMETEST] aplanado: desnivel {} -> {} (de {} a {}), escalón mayor {}, fuera igual {}", before, hi - lo, lo, hi, maxStep, outsideKept);
		if (!(hi - lo < before / 2 && hi - lo >= 2 && maxStep <= 2 && outsideKept)) {
			throw new GameTestException("El aplanado de la aldea no va bien: " + before + " -> " + (hi - lo) + ", escalón " + maxStep);
		}
		ctx.complete();
	}
}
