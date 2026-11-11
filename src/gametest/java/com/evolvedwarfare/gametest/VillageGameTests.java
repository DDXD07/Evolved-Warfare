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
					int n = b.length / 5;
					if (n > planned) {
						planned = n;
						found.setLength(0);
						market = false;
						for (int k = 0; k < b.length; k += 5) {
							found.append(b[k]).append(' ');
							market |= b[k] == VillageBuildings.MARKET || b[k] == VillageBuildings.MARKET_SMALL;
						}
					}
				}
			}
		}
		LOG.info("[EW-GAMETEST] aldea: {} edificios nuevos ({}) en {} intentos, mercado {}", planned, found.toString().trim(), tries, market);
		if (planned < 2 || !market) {
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
}
