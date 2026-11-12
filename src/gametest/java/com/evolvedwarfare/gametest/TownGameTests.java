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
}
