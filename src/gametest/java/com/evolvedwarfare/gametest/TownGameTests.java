package com.evolvedwarfare.gametest;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.town.Hiring;
import com.evolvedwarfare.town.NpcJob;
import com.evolvedwarfare.town.NpcProfile;
import com.evolvedwarfare.town.Town;
import com.evolvedwarfare.trade.TraderType;
import com.evolvedwarfare.world.city.CityDesigner;
import com.evolvedwarfare.world.city.CityPlan;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La gente de los pueblos (fase 58): un NPC por cama en las ciudades, su perfil (oficio, carácter, familia y amistades),
 * buscar cama libre y contratar a cualquiera: vecinos, guardias (con monedas), mercaderes (con más monedas) y aldeanos.
 */
public class TownGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static <T extends AbstractNpcEntity> T npc(TestContext ctx, EntityType<T> type, int x, int z) {
		ServerWorld world = ctx.getWorld();
		T npc = type.create(world);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(x, 1, z));
		npc.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		npc.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
		world.spawnEntity(npc);
		return npc;
	}

	private static ServerPlayerEntity player(TestContext ctx, int coins) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.RECRUITMENT_CONTRACT, 3));
		if (coins > 0) {
			player.getInventory().insertStack(new ItemStack(ModItems.GLORY_COIN, coins));
		}
		return player;
	}

	/** Las ciudades: cada cama de casa o de cuartel tiene dueño (las de las tiendas, su mercader); ya no hay vecinos sueltos. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everyCityBedHasSomeone(TestContext ctx) {
		int houses = 0, barracks = 0;
		for (CityDesigner.Style style : CityDesigner.Style.values()) {
			for (long seed = 1; seed <= 6; seed++) {
				CityPlan plan = CityDesigner.design(seed * 0x9E3779B97F4A7C15L + style.ordinal() * 7919L, style);
				check(plan.residents().isEmpty(), "Quedan vecinos sin cama");
				int heads = 0;
				for (CityPlan.Block b : plan.blocks()) {
					if (b.state().endsWith("_bed[facing=north,part=head]") || b.state().contains("_bed[") && b.state().contains("part=head")) {
						heads++;
					}
				}
				for (CityPlan.Bed bed : plan.beds()) {
					String state = plan.get(bed.x(), bed.y(), bed.z());
					check(state != null && state.contains("_bed[") && state.contains("part=head"), "La cama apuntada no es una cabecera: " + state);
					if ("house".equals(bed.role())) {
						houses++;
					} else {
						barracks++;
					}
				}
				check(plan.beds().size() + plan.traders().size() == heads,
						style + " " + seed + ": " + heads + " camas y " + plan.beds().size() + " + " + plan.traders().size() + " dueños");
			}
		}
		LOG.info("[EW-GAMETEST] pueblo: {} camas de vecinos y {} de guardias en 18 ciudades", houses, barracks);
		check(houses > 18 && barracks >= 18, "Pocas camas: " + houses + " / " + barracks);
		ctx.complete();
	}

	/** Perfil: oficio y carácter al nacer, la familia de la misma casa, los vecinos según su carácter; y se guarda. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void neighborsKnowEachOther(TestContext ctx) {
		floor(ctx);
		RecruitEntity a = npc(ctx, ModEntities.RECRUIT, 1, 1);
		RecruitEntity b = npc(ctx, ModEntities.RECRUIT, 2, 1);
		RecruitEntity c = npc(ctx, ModEntities.RECRUIT, 5, 5);
		for (RecruitEntity r : List.of(a, b, c)) {
			r.ewProfile().community = 777L;
		}
		a.ewProfile().household = 9L;
		b.ewProfile().household = 9L;
		c.ewProfile().household = 10L;
		Town.meet(a, ctx.getWorld());
		Town.meet(c, ctx.getWorld());
		NpcProfile pa = a.ewProfile();
		LOG.info("[EW-GAMETEST] pueblo: {} es {}; con su familia {}, con el vecino {}", a.getName().getString(), pa.describe().getString(),
				pa.affinity(b.getUuid()), pa.affinity(c.getUuid()));
		check(pa.rolled && (a.ewProfileBits() & 0xFFFF) == pa.packed(), "No tiene oficio y carácter (o no los ve el cliente)");
		check(pa.isFamily(b.getUuid()) && b.ewProfile().isFamily(a.getUuid()), "Los de la misma casa no son familia");
		check(pa.affinity(b.getUuid()) >= NpcProfile.FAMILY, "No quiere a su familia");
		check(pa.knows(c.getUuid()) && c.ewProfile().knows(a.getUuid()) && !pa.isFamily(c.getUuid()), "No conoce a su vecino");
		// se guarda y se lee igual
		RecruitEntity copy = ModEntities.RECRUIT.create(ctx.getWorld());
		copy.readNbt(a.writeNbt(new net.minecraft.nbt.NbtCompound()));
		check(copy.ewProfile().job == pa.job && copy.ewProfile().personality == pa.personality && copy.ewProfile().isFamily(b.getUuid())
				&& copy.ewProfile().affinity(c.getUuid()) == pa.affinity(c.getUuid()), "El perfil no se guarda");
		a.discard();
		b.discard();
		c.discard();
		ctx.complete();
	}

	/** Sin cama, busca una libre cerca; la de otro no la coge. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void recruitsFindAFreeBed(TestContext ctx) {
		floor(ctx);
		ctx.setBlockState(new BlockPos(6, 1, 6), Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
		ctx.setBlockState(new BlockPos(6, 1, 7), Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
		RecruitEntity a = npc(ctx, ModEntities.RECRUIT, 2, 2);
		RecruitEntity b = npc(ctx, ModEntities.RECRUIT, 3, 2);
		BlockPos head = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
		ctx.waitAndRun(220, () -> {
			BlockPos ba = a.ewProfile().bed, bb = b.ewProfile().bed;
			LOG.info("[EW-GAMETEST] pueblo: camas buscadas {} / {} (la libre: {})", ba, bb, head);
			check(head.equals(ba) != head.equals(bb), "La cama no es de uno solo: " + ba + " / " + bb);
			check(ba == null || bb == null, "Hay dos camas para dos y solo había una");
			a.discard();
			b.discard();
			ctx.complete();
		});
	}

	/** Contratar: un guardia (con 15 monedas) pasa a ser tu soldado; sin monedas no; un mercader (25), un aldeano (gratis). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everyoneCanBeHired(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		// guardia sin monedas: no
		CityGuardEntity guard = ModEntities.CITY_GUARD.create(world);
		BlockPos post = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		guard.presetRole(NpcRole.CROSSBOWMAN);
		guard.setCity(5150L, post, 40, CityDesigner.Style.MEDIEVAL);
		guard.refreshPositionAndAngles(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.0F, 0.0F);
		guard.initialize(world, world.getLocalDifficulty(post), SpawnReason.STRUCTURE, null);
		world.spawnEntity(guard);
		ServerPlayerEntity poor = player(ctx, 0);
		guard.interact(poor, Hand.MAIN_HAND);
		check(guard.isAlive() && poor.getMainHandStack().getCount() == 3, "Se contrata a un guardia sin pagar");
		// con monedas: sí, y se lleva su clase y su nombre
		ServerPlayerEntity rich = player(ctx, 60);
		String name = guard.getName().getString();
		SoldierEntity soldier = Hiring.hireGuard(guard, rich, rich.getMainHandStack());
		check(soldier != null && soldier.isOwner(rich) && soldier.getRole() == NpcRole.CROSSBOWMAN && soldier.getName().getString().equals(name)
				&& soldier.ewProfile().job == NpcJob.GUARD, "El guardia no pasa a ser tu soldado");
		check(com.evolvedwarfare.logistics.Market.countCoins(rich.getInventory()) == 45 && rich.getMainHandStack().getCount() == 2,
				"No cobra bien al guardia");
		// mercader
		TraderEntity trader = ModEntities.TRADER.create(world);
		BlockPos counter = ctx.getAbsolutePos(new BlockPos(5, 1, 2));
		trader.setTraderType(TraderType.ARMORER);
		trader.refreshPositionAndAngles(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5, 0.0F, 0.0F);
		trader.initialize(world, world.getLocalDifficulty(counter), SpawnReason.STRUCTURE, null);
		world.spawnEntity(trader);
		trader.interactMob(rich, Hand.MAIN_HAND);
		List<RecruitEntity> recruits = world.getEntitiesByClass(RecruitEntity.class, new Box(counter).expand(2.0), r -> r.isOwner(rich));
		check(!trader.isAlive() && recruits.size() == 1 && recruits.get(0).ewProfile().job == NpcJob.MERCHANT && recruits.get(0).hasCustomName(),
				"El mercader no se va contigo: " + recruits);
		check(com.evolvedwarfare.logistics.Market.countCoins(rich.getInventory()) == 20, "No cobra bien al mercader");
		// aldeano
		VillagerEntity villager = EntityType.VILLAGER.create(world);
		BlockPos home = ctx.getAbsolutePos(new BlockPos(5, 1, 5));
		villager.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0F, 0.0F);
		villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FARMER).withLevel(2));
		world.spawnEntity(villager);
		RecruitEntity farmer = Hiring.hireVillager(villager, rich, rich.getMainHandStack());
		LOG.info("[EW-GAMETEST] pueblo: contratados el guardia {} ({}), el mercader {} y el aldeano {} ({})", soldier.getName().getString(),
				soldier.ewProfile().describe().getString(), recruits.get(0).getName().getString(), farmer == null ? null : farmer.getName().getString(),
				farmer == null ? null : farmer.ewProfile().describe().getString());
		check(farmer != null && farmer.isOwner(rich) && farmer.ewProfile().job == NpcJob.FARMER && !villager.isAlive(), "El aldeano no se va contigo");
		soldier.discard();
		recruits.forEach(RecruitEntity::discard);
		farmer.discard();
		ctx.complete();
	}

	// ------------------------------------------------------------------ fase 2: la rutina diaria

	/** Un vecino libre en su casa, con su cama (si se le da). */
	private static RecruitEntity resident(TestContext ctx, int x, int z, com.evolvedwarfare.town.Personality who, @org.jetbrains.annotations.Nullable BlockPos bedRel) {
		RecruitEntity r = npc(ctx, ModEntities.RECRUIT, x, z);
		NpcProfile p = r.ewProfile();
		p.personality = who;
		p.job = NpcJob.LABORER;
		p.bedSearched = true;
		p.center = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		p.range = 3;
		p.bed = bedRel == null ? null : ctx.getAbsolutePos(bedRel);
		return r;
	}

	private static void bed(TestContext ctx, BlockPos head) {
		ctx.setBlockState(head, Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
		ctx.setBlockState(head.south(), Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
	}

	/** El horario: desayuno, trabajo, plaza, cena, cama; los perezosos se levantan más tarde; los guardias por turnos. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "town_routine")
	public void dailySchedule(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		RecruitEntity a = resident(ctx, 1, 1, com.evolvedwarfare.town.Personality.CHEERFUL, new BlockPos(6, 1, 1));
		RecruitEntity lazy = resident(ctx, 2, 1, com.evolvedwarfare.town.Personality.LAZY, new BlockPos(6, 1, 1));
		StringBuilder log = new StringBuilder();
		long[] times = {500, 3000, 10000, 12000, 15000};
		com.evolvedwarfare.town.Activity[] expected = {com.evolvedwarfare.town.Activity.BREAKFAST, com.evolvedwarfare.town.Activity.WORK,
				com.evolvedwarfare.town.Activity.LEISURE, com.evolvedwarfare.town.Activity.DINNER, com.evolvedwarfare.town.Activity.SLEEP};
		for (int i = 0; i < times.length; i++) {
			a.forcedTime = times[i];
			var got = com.evolvedwarfare.town.Routine.plan(a, world);
			log.append(times[i]).append('=').append(got).append(' ');
			check(got == expected[i], "A las " + times[i] + " le toca " + got + " y no " + expected[i]);
		}
		lazy.forcedTime = 500L;
		check(com.evolvedwarfare.town.Routine.plan(lazy, world) == com.evolvedwarfare.town.Activity.SLEEP, "El perezoso no se queda en la cama");
		// guardia: según su turno
		RecruitEntity guard = resident(ctx, 3, 1, com.evolvedwarfare.town.Personality.BRAVE, new BlockPos(6, 1, 1));
		guard.ewProfile().job = NpcJob.GUARD;
		guard.forcedTime = 3000L;
		var day = com.evolvedwarfare.town.Routine.plan(guard, world);
		guard.forcedTime = 18000L;
		var night = com.evolvedwarfare.town.Routine.plan(guard, world);
		boolean nightShift = com.evolvedwarfare.town.Routine.nightShift(guard);
		check(nightShift ? day == com.evolvedwarfare.town.Activity.SLEEP && night == com.evolvedwarfare.town.Activity.PATROL
				: day == com.evolvedwarfare.town.Activity.PATROL && night == com.evolvedwarfare.town.Activity.SLEEP,
				"El guardia no sigue su turno: " + day + " / " + night + " (noche " + nightShift + ")");
		// contratado: siguiéndote, nada; en casa con cama, solo dormir
		ServerPlayerEntity owner = player(ctx, 0);
		a.signContract(owner);
		a.forcedTime = 15000L;
		check(com.evolvedwarfare.town.Routine.plan(a, world) == com.evolvedwarfare.town.Activity.NONE, "Un contratado que te sigue hace su rutina");
		a.setHomeHere();
		a.ewProfile().bed = ctx.getAbsolutePos(new BlockPos(6, 1, 1));
		check(com.evolvedwarfare.town.Routine.plan(a, world) == com.evolvedwarfare.town.Activity.SLEEP, "Un contratado en casa no duerme de noche");
		a.forcedTime = 3000L;
		check(com.evolvedwarfare.town.Routine.plan(a, world) == com.evolvedwarfare.town.Activity.NONE, "Un contratado en casa hace la rutina de día");
		LOG.info("[EW-GAMETEST] rutina: {}; guardia de {}: día {} noche {}", log, nightShift ? "noche" : "día", day, night);
		a.discard();
		lazy.discard();
		guard.discard();
		ctx.complete();
	}

	/** De noche va a su cama y se acuesta; por la mañana se levanta y desayuna (con pan en la mano). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "town_routine", tickLimit = 600)
	public void sleepsInItsBedAndWakesUp(TestContext ctx) {
		floor(ctx);
		BlockPos head = new BlockPos(6, 1, 2);
		bed(ctx, head);
		RecruitEntity r = resident(ctx, 1, 6, com.evolvedwarfare.town.Personality.KIND, head);
		r.forcedTime = 15000L;
		BlockPos abs = ctx.getAbsolutePos(head);
		ctx.waitAndRun(250, () -> {
			boolean slept = r.isSleeping() && ctx.getWorld().getBlockState(abs).get(BedBlock.OCCUPIED);
			r.forcedTime = 300L;
			boolean[] sawBread = {false};
			for (int t = 1; t < 120; t++) {
				ctx.runAtTick(ctx.getTick() + t, () -> sawBread[0] |= r.getMainHandStack().isOf(Items.BREAD));
			}
			ctx.waitAndRun(150, () -> {
				LOG.info("[EW-GAMETEST] rutina: dormido en su cama {}; por la mañana despierto {} y con pan {} ({})", slept, !r.isSleeping(), sawBread[0],
						r.getActivity());
				check(slept, "No se acuesta en su cama: " + r.getActivity() + " en " + r.getBlockPos());
				check(!r.isSleeping() && !ctx.getWorld().getBlockState(abs).get(BedBlock.OCCUPIED), "No se levanta por la mañana");
				check(sawBread[0], "No desayuna");
				r.discard();
				ctx.complete();
			});
		});
	}

	/** El herrero va a su puesto (la mesa de herrería) y trabaja con el martillo en la mano; al morir no lo suelta. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "town_routine", tickLimit = 500)
	public void blacksmithGoesToWork(TestContext ctx) {
		floor(ctx);
		BlockPos table = new BlockPos(6, 1, 6);
		ctx.setBlockState(table, Blocks.SMITHING_TABLE);
		RecruitEntity r = resident(ctx, 1, 1, com.evolvedwarfare.town.Personality.CHEERFUL, null);
		r.ewProfile().job = NpcJob.BLACKSMITH;
		r.forcedTime = 3000L;
		BlockPos abs = ctx.getAbsolutePos(table);
		ctx.waitAndRun(300, () -> {
			double d = r.getPos().distanceTo(net.minecraft.util.math.Vec3d.ofBottomCenter(abs));
			LOG.info("[EW-GAMETEST] rutina: herrero en {} (puesto {}), a {} del puesto, en la mano {}", r.getActivity(), r.ewProfile().workplace,
					String.format(java.util.Locale.ROOT, "%.1f", d), r.getMainHandStack());
			check(abs.equals(r.ewProfile().workplace), "No encuentra su puesto: " + r.ewProfile().workplace);
			check(d < 3.0, "No va a su puesto: está a " + d);
			check(r.getMainHandStack().isOf(Items.IRON_PICKAXE), "No trabaja con su herramienta: " + r.getMainHandStack());
			r.damage(ctx.getWorld().getDamageSources().generic(), 1000.0F);
			ctx.waitAndRun(5, () -> {
				int dropped = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(abs).expand(8.0),
						e -> e.getStack().isOf(Items.IRON_PICKAXE)).size();
				check(dropped == 0, "Suelta la herramienta de la rutina al morir");
				ctx.complete();
			});
		});
	}

	/** Con un monstruo a la vista corre a casa (el valiente lo ve más tarde). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "town_danger", tickLimit = 200)
	public void residentsHideFromDanger(TestContext ctx) {
		floor(ctx);
		RecruitEntity r = resident(ctx, 2, 2, com.evolvedwarfare.town.Personality.FEARFUL, null);
		r.forcedTime = 10000L;
		net.minecraft.entity.mob.HuskEntity husk = EntityType.HUSK.create(ctx.getWorld());
		BlockPos at = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
		husk.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		husk.setAiDisabled(true);
		ctx.getWorld().spawnEntity(husk);
		ctx.waitAndRun(60, () -> {
			var now = r.getActivity();
			var plan = com.evolvedwarfare.town.Routine.plan(r, ctx.getWorld());
			LOG.info("[EW-GAMETEST] rutina: con un monstruo cerca el vecino hace {} ({})", now, plan);
			husk.discard();
			check(plan == com.evolvedwarfare.town.Activity.HIDE, "No se esconde del monstruo: " + plan);
			r.discard();
			ctx.complete();
		});
	}
}
