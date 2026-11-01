package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.trade.TraderType;
import com.evolvedwarfare.world.city.CityChecks;
import com.evolvedwarfare.world.city.CityDesigner;
import com.evolvedwarfare.world.city.CityLaw;
import com.evolvedwarfare.world.city.CityPiece;
import com.evolvedwarfare.world.city.CityPlan;
import com.evolvedwarfare.world.city.WalledCityStructure;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Las ciudades amuralladas: cada semilla da una ciudad bien hecha (todos los mercaderes llegan a su cama, todas las
 * puertas llevan a la plaza, todos los bloques existen), las tres ciudades se cargan como el tipo nuevo, los mercaderes
 * duermen de noche y vuelven al mostrador de día, y la ciudad guarda rencor a quien hace daño a su gente.
 */
public class CityGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everySeedMakesAGoodCity(TestContext ctx) {
		Set<String> states = new HashSet<>();
		int cities = 0;
		for (CityDesigner.Style style : CityDesigner.Style.values()) {
			for (long seed = 1; seed <= 12; seed++) {
				CityPlan plan = CityDesigner.design(seed * 0x9E3779B97F4A7C15L + style.ordinal() * 7919L, style);
				cities++;
				check(plan.traders().size() == CityDesigner.tradesOf(style).size(), style + " " + seed + ": solo " + plan.traders().size() + " tiendas");
				check(CityChecks.strandedTraders(plan) == 0, style + " " + seed + ": algún mercader no llega a su cama");
				check(CityChecks.closedGates(plan) == 0, style + " " + seed + ": alguna puerta no lleva a la plaza");
				check(plan.guards().size() >= 8, style + " " + seed + ": pocos guardias (" + plan.guards().size() + ")");
				for (CityPlan.Trader t : plan.traders()) {
					check(TraderType.byId(t.type()) != null, "Oficio desconocido: " + t.type());
				}
				for (CityPlan.Block b : plan.blocks()) {
					states.add(b.state());
				}
			}
		}
		for (String state : states) {
			if (!state.equals("minecraft:air")) {
				check(!CityPiece.state(state).isAir(), "Bloque que no existe en las ciudades: " + state);
			}
		}
		LOG.info("[EW-GAMETEST] ciudades: {} diseñadas bien, {} bloques distintos", cities, states.size());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void citiesLoadAsWalledCities(TestContext ctx) {
		var registry = ctx.getWorld().getRegistryManager().get(RegistryKeys.STRUCTURE);
		String[] names = {"city", "desert_city", "snow_city"};
		CityDesigner.Style[] styles = {CityDesigner.Style.MEDIEVAL, CityDesigner.Style.DESERT, CityDesigner.Style.SNOW};
		for (int i = 0; i < names.length; i++) {
			Structure structure = registry.get(EvolvedWarfare.id(names[i]));
			check(structure instanceof WalledCityStructure, names[i] + " no es una ciudad amurallada: " + structure);
			check(((WalledCityStructure) structure).style() == styles[i], names[i] + " tiene el estilo " + ((WalledCityStructure) structure).style());
		}
		ctx.complete();
	}

	/** Una tiendecita: suelo, la cama a 4 bloques del mostrador. */
	private static TraderEntity shopkeeper(TestContext ctx, BlockPos counterRel, BlockPos bedRel) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(bedRel.south(), Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
		ctx.setBlockState(bedRel, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
		ServerWorld world = ctx.getWorld();
		TraderEntity trader = ModEntities.TRADER.create(world);
		BlockPos counter = ctx.getAbsolutePos(counterRel);
		trader.setTraderType(TraderType.GROCER);
		trader.setCityHome(counter, ctx.getAbsolutePos(bedRel), 4242L);
		trader.refreshPositionAndAngles(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5, 0.0F, 0.0F);
		trader.initialize(world, world.getLocalDifficulty(counter), SpawnReason.STRUCTURE, null);
		world.spawnEntity(trader);
		return trader;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
	public void tradersSleepAtNightAndWorkByDay(TestContext ctx) {
		TraderEntity trader = shopkeeper(ctx, new BlockPos(1, 1, 1), new BlockPos(5, 1, 5));
		TraderEntity.forceBedtime = true;
		final int[] slept = {-1};
		watchSleep(ctx, trader, slept, 0);
		ctx.waitAndRun(400, () -> {
			TraderEntity.forceBedtime = null;
			BlockPos counter = trader.getCounter();
			double dist = Math.sqrt(trader.squaredDistanceTo(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5));
			LOG.info("[EW-GAMETEST] mercader: se durmió en el tic {}; luego, despierto={} a {} del mostrador", slept[0], !trader.isSleeping(), dist);
			check(slept[0] >= 0, "El mercader no se acostó de noche");
			check(!trader.isSleeping(), "El mercader no se levantó de día");
			check(dist < 1.5, "El mercader no volvió a su mostrador (" + dist + ")");
			trader.discard();
			ctx.complete();
		});
	}

	/** Mira cada tic si ya duerme; en cuanto se duerme, se hace de día. */
	private static void watchSleep(TestContext ctx, TraderEntity trader, int[] slept, int tick) {
		if (slept[0] < 0 && trader.isSleeping()) {
			slept[0] = tick;
			TraderEntity.forceBedtime = false;
			return;
		}
		if (tick < 380) {
			ctx.waitAndRun(1, () -> watchSleep(ctx, trader, slept, tick + 1));
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void guardsChaseWhoHurtsCitizens(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerWorld world = ctx.getWorld();
		long key = 0x5EEDL + world.getTime();
		TraderEntity trader = ModEntities.TRADER.create(world);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		trader.setCityHome(at, at.east(3), key);
		trader.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		world.spawnEntity(trader);
		CityGuardEntity guard = ModEntities.CITY_GUARD.create(world);
		BlockPos post = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
		guard.presetRole(NpcRole.WARRIOR);
		guard.setCity(key, post, 40, CityDesigner.Style.MEDIEVAL);
		guard.refreshPositionAndAngles(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.0F, 0.0F);
		guard.initialize(world, world.getLocalDifficulty(post), SpawnReason.STRUCTURE, null);
		world.spawnEntity(guard);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 90.0F, 0.0F);
		boolean before = CityLaw.wanted(world, key, player);
		boolean forHire = guard.isForHire();
		ctx.waitAndRun(2, () -> {
			trader.damage(world.getDamageSources().playerAttack(player), 1.0F);
			boolean after = CityLaw.wanted(world, key, player);
			boolean other = CityLaw.wanted(world, key + 1, player);
			LOG.info("[EW-GAMETEST] ley de la ciudad: antes {} después {} (otra ciudad {}); guardia persigue a {}; se contrata={}", before, after,
					other, guard.getTarget(), forHire);
			try {
				check(!before, "La ciudad ya le guardaba rencor antes de hacer nada");
				check(after, "Hacer daño a un mercader no hace que la ciudad le guarde rencor");
				check(!other, "El rencor alcanza a otra ciudad");
				check(guard.getTarget() == player, "El guardia no va a por él: " + guard.getTarget());
				check(!forHire, "Los guardias se pueden contratar");
			} finally {
				CityLaw.pardon(world.getServer(), player.getUuid());
			}
			check(!CityLaw.wanted(world, key, player), "El perdón no quita el rencor");
			trader.discard();
			guard.discard();
			ctx.complete();
		});
	}
}
