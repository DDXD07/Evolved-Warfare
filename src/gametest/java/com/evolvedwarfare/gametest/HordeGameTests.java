package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.siege.SiegeRules;
import com.evolvedwarfare.world.FactionSpawner;
import com.evolvedwarfare.world.Hordes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Las hordas: el calendario (cada 7 días, la semana), que se endurecen cada semana, que van a por ti aunque seas aliado
 * de su facción, que la base es tu ayuntamiento y que al derrotarlas se cobra. Los guerreros de las pruebas se quedan
 * quietos (sin IA) para no meterse en las demás pruebas.
 */
public class HordeGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hordeCalendar(TestContext ctx) {
		check(Hordes.day(0L) == 1L && Hordes.day(23999L) == 1L && Hordes.day(24000L) == 2L, "El día 1 no va de amanecer a amanecer");
		check(!Hordes.isHordeDay(6, 7) && Hordes.isHordeDay(7, 7) && !Hordes.isHordeDay(8, 7) && Hordes.isHordeDay(14, 7),
				"La horda no cae cada 7 días");
		check(!Hordes.isHordeDay(7, 0), "Con 0 días sigue habiendo hordas");
		check(Hordes.week(7, 7) == 1 && Hordes.week(14, 7) == 2 && Hordes.week(35, 7) == 5 && Hordes.week(20, 7) == 2,
				"Las semanas no cuentan bien");
		Hordes.Strength first = Hordes.strength(1, Difficulty.NORMAL, 1);
		Hordes.Strength third = Hordes.strength(3, Difficulty.NORMAL, 1);
		Hordes.Strength sixth = Hordes.strength(6, Difficulty.NORMAL, 1);
		Hordes.Strength crowd = Hordes.strength(1, Difficulty.NORMAL, 3);
		LOG.info("[EW-GAMETEST] hordas: semana 1 {}, semana 3 {}, semana 6 {}; con 3 jugadores {}", first, third, sixth, crowd);
		check(sixth.perWave() > third.perWave() && third.perWave() > first.perWave(), "No vienen más por oleada cada semana");
		check(first.waves() == 1 && sixth.waves() > first.waves(), "No hay más oleadas con las semanas");
		check(sixth.health() > third.health() && third.health() > first.health() && first.health() == 1.0, "No ganan vida con las semanas");
		check(sixth.damage() > first.damage() && sixth.elite() > first.elite(), "No pegan más ni llevan mejor equipo con las semanas");
		check(first.engines() == 0 && third.engines() >= 1 && sixth.engines() >= 2, "Las máquinas de asedio no llegan con las semanas");
		check(crowd.perWave() > first.perWave(), "Con más jugadores no vienen más");
		check(Hordes.reward(5) > Hordes.reward(1), "La recompensa no sube con las semanas");
		ctx.complete();
	}

	/** Aunque seas aliado de los vikingos, la horda vikinga va a por los tuyos (y no cuenta como aliada). */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void hordeIgnoresAlliance(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = player(ctx);
		Reputation.set(world.getServer(), player.getUuid(), Faction.VIKINGS, 80);
		Reputation.setAllied(world.getServer(), player.getUuid(), Faction.VIKINGS, true);
		SoldierEntity soldier = ctx.spawnEntity(ModEntities.SOLDIER, 2, 1, 2);
		soldier.setAiDisabled(true);
		soldier.setOwner(player);
		FactionNpcEntity normal = FactionSpawner.spawnMember(world, Faction.VIKINGS, ctx.getAbsolutePos(new BlockPos(3, 1, 3)), null,
				SpawnReason.EVENT, world.getRandom());
		Hordes.Horde horde = start(ctx, Faction.VIKINGS, 1, List.of(player.getUuid()));
		FactionNpcEntity member = first(world, horde);
		boolean normalAttacks = normal != null && normal.shouldAttack(soldier);
		boolean hordeAttacks = member.shouldAttack(soldier);
		boolean normalAllies = normal != null && NpcSides.allies(normal, soldier);
		boolean hordeAllies = NpcSides.allies(member, soldier);
		LOG.info("[EW-GAMETEST] horda y alianza: vikingo normal ataca={} aliado={}; de la horda ataca={} aliado={} ({} guerreros)",
				normalAttacks, normalAllies, hordeAttacks, hordeAllies, horde.remaining());
		Hordes.cancel(world, horde);
		if (normal != null) {
			normal.discard();
		}
		soldier.discard();
		Reputation.setAllied(world.getServer(), player.getUuid(), Faction.VIKINGS, false);
		check(normal != null && !normalAttacks && normalAllies, "Un vikingo normal ataca a los de un aliado");
		check(hordeAttacks && !hordeAllies && member.isHordeRaider(), "La horda respeta la alianza");
		check(horde.remaining() == 0, "Al quitar la horda quedan guerreros contados");
		ctx.complete();
	}

	/** La horda de la sexta semana es más numerosa y sus guerreros tienen más vida que la de la primera. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void hordeScalesWithWeeks(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = player(ctx);
		// sin máquinas de asedio aquí (la de la sexta semana las traería): solo cuenta la gente
		var siegeRaids = world.getGameRules().get(SiegeRules.SIEGE_RAIDS);
		boolean siegeBefore = siegeRaids.get();
		siegeRaids.set(false, world.getServer());
		Hordes.Horde weak;
		Hordes.Horde strong;
		try {
			weak = start(ctx, Faction.BANDITS, 1, List.of(player.getUuid()));
			strong = start(ctx, Faction.BANDITS, 6, List.of(player.getUuid()));
		} finally {
			siegeRaids.set(siegeBefore, world.getServer());
		}
		float weakHealth = healthFactor(first(world, weak));
		float strongHealth = healthFactor(first(world, strong));
		int weakCount = weak.remaining();
		int strongCount = strong.remaining();
		LOG.info("[EW-GAMETEST] hordas por semana: semana 1 {} guerreros (vida x{}), semana 6 {} guerreros (vida x{})", weakCount,
				weakHealth, strongCount, strongHealth);
		Hordes.cancel(world, weak);
		Hordes.cancel(world, strong);
		check(strongCount > weakCount, "La horda de la semana 6 no es más numerosa");
		check(Math.abs(weakHealth - 1.0F) < 0.01F && strongHealth > 1.5F, "La vida no sube con las semanas: " + weakHealth + " -> " + strongHealth);
		ctx.complete();
	}

	/** La base es tu ayuntamiento; derrotar la horda entera da Glory Coins a quien estaba allí. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void hordeBaseAndReward(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = player(ctx);
		BlockPos hallPos = new BlockPos(4, 1, 4);
		ctx.setBlockState(hallPos, ModBlocks.TOWN_HALL);
		if (ctx.getBlockEntity(hallPos) instanceof TownHallBlockEntity hall) {
			hall.setOwner(player.getUuid());
		}
		BlockPos home = Hordes.home(world, player);
		Hordes.Horde horde = start(ctx, Faction.PIRATES, 2, List.of(player.getUuid()));
		int before = player.getInventory().count(ModItems.GLORY_COIN);
		for (UUID id : new ArrayList<>(horde.members())) {
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
		ctx.waitAndRun(30, () -> {
			int after = player.getInventory().count(ModItems.GLORY_COIN);
			boolean ended = !Hordes.active(world).contains(horde);
			LOG.info("[EW-GAMETEST] horda: base {} (ayuntamiento {}); derrotada={}, Glory Coins {} -> {} (recompensa {})", home,
					ctx.getAbsolutePos(hallPos), ended, before, after, Hordes.reward(2));
			if (!ended) {
				Hordes.cancel(world, horde);
			}
			check(ctx.getAbsolutePos(hallPos).equals(home), "La base no es el ayuntamiento del jugador: " + home);
			check(ended, "La horda no acaba al derrotarlos a todos");
			check(after - before >= Hordes.reward(2), "Derrotar la horda no da Glory Coins (" + before + " -> " + after + ")");
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ ayudas

	private static Hordes.Horde start(TestContext ctx, Faction faction, int week, List<UUID> defenders) {
		ServerWorld world = ctx.getWorld();
		BlockPos center = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		Hordes.Horde horde = Hordes.start(world, world.getRandom(), faction, center, null, week, defenders, 8);
		if (horde == null) {
			throw new GameTestException("No aparece la horda de la semana " + week);
		}
		for (UUID id : horde.members()) {
			if (world.getEntity(id) instanceof FactionNpcEntity member) {
				member.setAiDisabled(true);
			}
		}
		return horde;
	}

	private static FactionNpcEntity first(ServerWorld world, Hordes.Horde horde) {
		for (UUID id : horde.members()) {
			if (world.getEntity(id) instanceof FactionNpcEntity member) {
				return member;
			}
		}
		throw new GameTestException("La horda no tiene guerreros");
	}

	private static float healthFactor(FactionNpcEntity member) {
		EntityAttributeInstance health = member.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		return health == null ? 0.0F : (float) (health.getValue() / health.getBaseValue());
	}

	private static ServerPlayerEntity player(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d pos = ctx.getAbsolute(new Vec3d(4.5, 1.0, 4.5));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		return player;
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
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
