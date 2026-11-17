package com.evolvedwarfare.gametest;

import com.evolvedwarfare.legendary.DragonEntity;
import com.evolvedwarfare.legendary.Legendaries;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las criaturas legendarias (fase 101): los dragones de fuego y de hielo. */
public class LegendaryGameTests implements FabricGameTest {
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

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary")
	public void dragonsAreLegendary(TestContext ctx) {
		floor(ctx);
		DragonEntity fire = ctx.spawnEntity(Legendaries.FIRE_DRAGON, 2, 3, 2);
		DragonEntity ice = ctx.spawnEntity(Legendaries.ICE_DRAGON, 6, 3, 6);
		fire.setAiDisabled(true);
		ice.setAiDisabled(true);
		LOG.info("[EW-GAMETEST] dragones: vida {} / {}, fuego inmune {} / {}, vuelan {}, hielo se congela {}", fire.getMaxHealth(), ice.getMaxHealth(),
				fire.isFireImmune(), ice.isFireImmune(), fire.hasNoGravity(), ice.canFreeze());
		check(fire.getMaxHealth() >= 500.0F && ice.getMaxHealth() >= 500.0F, "Los dragones no tienen vida de leyenda");
		check(fire.isFireImmune() && !ice.isFireImmune(), "El de fuego se quema (o el de hielo no)");
		check(!ice.canFreeze() && fire.hasNoGravity() && ice.ice() && !fire.ice(), "No son cada uno de lo suyo");
		check(Legendaries.kindFor(0.0F) == Legendaries.Kind.ICE_DRAGON && Legendaries.kindFor(2.0F) == Legendaries.Kind.FIRE_DRAGON
				&& Legendaries.kindFor(0.7F) == null, "No sale cada dragón en su clima");
		fire.discard();
		ice.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary", tickLimit = 200)
	public void breathBurnsAndFreezes(TestContext ctx) {
		floor(ctx);
		DragonEntity fire = ctx.spawnEntity(Legendaries.FIRE_DRAGON, 1, 2, 1);
		fire.setAiDisabled(true);
		VillagerEntity burnt = ctx.spawnEntity(EntityType.VILLAGER, 6, 1, 6);
		burnt.setAiDisabled(true);
		float before = burnt.getHealth();
		for (int k = 0; k < 5; k++) {
			fire.age = k * 5;
			fire.breathTick(ctx.getWorld(), burnt);
		}
		float afterFire = burnt.getHealth();
		boolean onFire = burnt.isOnFire();
		fire.discard();
		DragonEntity ice = ctx.spawnEntity(Legendaries.ICE_DRAGON, 1, 2, 1);
		ice.setAiDisabled(true);
		VillagerEntity frozen = ctx.spawnEntity(EntityType.VILLAGER, 6, 1, 2);
		frozen.setAiDisabled(true);
		ice.age = 0;
		ice.breathTick(ctx.getWorld(), frozen);
		LOG.info("[EW-GAMETEST] aliento: fuego {} -> {} (ardiendo {}), hielo congelado {} lento {}", before, afterFire, onFire, frozen.getFrozenTicks(),
				frozen.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS));
		check(afterFire < before && onFire, "El aliento de fuego no quema");
		check(frozen.getFrozenTicks() > 0, "El aliento de hielo no congela");
		ice.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary", tickLimit = 100)
	public void dragonsDropTheirScales(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		DragonEntity dragon = ctx.spawnEntity(Legendaries.ICE_DRAGON, 3, 2, 3);
		dragon.setAiDisabled(true);
		BlockPos at = dragon.getBlockPos();
		dragon.damage(player.getDamageSources().playerAttack(player), 100000.0F);
		ctx.waitAndRun(30, () -> {
			List<ItemEntity> drops = ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(at).expand(6), e -> true);
			boolean scales = drops.stream().anyMatch(e -> e.getStack().isOf(Legendaries.ICE_DRAGON_SCALE));
			boolean heart = drops.stream().anyMatch(e -> e.getStack().isOf(Legendaries.ICE_DRAGON_HEART));
			LOG.info("[EW-GAMETEST] botín del dragón: {}", drops.stream().map(e -> e.getStack().toString()).toList());
			check(scales && heart, "El dragón no suelta sus escamas y su corazón");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary")
	public void dragonGearWorks(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		var fire = player.getDamageSources().onFire();
		float none = com.evolvedwarfare.legendary.DragonGear.modifyIncoming(player, fire, 10.0F);
		net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
				net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
		player.equipStack(slots[0], new net.minecraft.item.ItemStack(Legendaries.DRAGON_ARMOR.get(0)));
		float one = com.evolvedwarfare.legendary.DragonGear.modifyIncoming(player, fire, 10.0F);
		for (int k = 1; k < 4; k++) {
			player.equipStack(slots[k], new net.minecraft.item.ItemStack(Legendaries.DRAGON_ARMOR.get(k)));
		}
		float full = com.evolvedwarfare.legendary.DragonGear.modifyIncoming(player, fire, 10.0F);
		DragonEntity ice = ctx.spawnEntity(Legendaries.ICE_DRAGON, 4, 3, 4);
		ice.setAiDisabled(true);
		float flamed = Legendaries.FLAMED_DRAGONBONE_SWORD.getBonusAttackDamage(ice, 10.0F, player.getDamageSources().playerAttack(player));
		float iced = Legendaries.ICED_DRAGONBONE_SWORD.getBonusAttackDamage(ice, 10.0F, player.getDamageSources().playerAttack(player));
		LOG.info("[EW-GAMETEST] escamas: fuego 10 -> {} (una pieza) -> {} (completa); espada flamígera contra el de hielo +{}, helada +{}", one, full,
				flamed, iced);
		check(none == 10.0F && one < 10.0F && full == 0.0F, "Las escamas de fuego no protegen del fuego");
		check(flamed > 0.0F && iced == 0.0F, "Las espadas no hieren de más al dragón contrario");
		ice.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary", tickLimit = 100)
	public void frostGiantStompsAndThrows(TestContext ctx) {
		floor(ctx);
		com.evolvedwarfare.legendary.FrostGiantEntity giant = ctx.spawnEntity(Legendaries.FROST_GIANT, 2, 1, 2);
		giant.setAiDisabled(true);
		VillagerEntity near = ctx.spawnEntity(EntityType.VILLAGER, 5, 1, 2);
		near.setAiDisabled(true);
		float before = near.getHealth();
		int hit = giant.stomp(ctx.getWorld());
		boolean thrown = giant.throwBoulder(ctx.getWorld(), near);
		int boulders = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.FallingBlockEntity.class, giant.getBoundingBox().expand(4, 6, 4),
				e -> true).size();
		LOG.info("[EW-GAMETEST] gigante: vida {}, pisotón a {} ({} -> {}), lanza {} ({} bloques en el aire)", giant.getMaxHealth(), hit, before,
				near.getHealth(), thrown, boulders);
		check(giant.getMaxHealth() >= 600.0F, "El gigante no tiene vida de leyenda");
		check(hit >= 1 && near.getHealth() < before, "El pisotón no hace daño");
		check(thrown && boulders >= 1, "No lanza el bloque de hielo");
		giant.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary", tickLimit = 100)
	public void seaSerpentSmashesBoats(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					ctx.setBlockState(x, y, z, Blocks.WATER);
				}
			}
		}
		com.evolvedwarfare.legendary.SeaSerpentEntity serpent = ctx.spawnEntity(Legendaries.SEA_SERPENT, 3, 2, 3);
		serpent.setAiDisabled(true);
		net.minecraft.entity.vehicle.BoatEntity boat = ctx.spawnEntity(EntityType.BOAT, 5, 5, 5);
		boolean smashed = serpent.capsize(boat);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		var drown = player.getDamageSources().drown();
		for (int k = 0; k < 4; k++) {
			player.equipStack(new net.minecraft.entity.EquipmentSlot[] {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
					net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}[k], new net.minecraft.item.ItemStack(Legendaries.TIDE_ARMOR.get(k)));
		}
		float drowning = com.evolvedwarfare.legendary.DragonGear.modifyIncoming(player, drown, 10.0F);
		LOG.info("[EW-GAMETEST] serpiente: vida {}, respira en el agua {}, parte la barca {} (quitada {}); mareas: ahogarse 10 -> {}", serpent.getMaxHealth(),
				serpent.canBreatheInWater(), smashed, boat.isRemoved(), drowning);
		check(serpent.getMaxHealth() >= 450.0F && serpent.canBreatheInWater(), "La serpiente no es de leyenda o se ahoga");
		check(smashed && boat.isRemoved(), "La serpiente no parte la barca");
		check(drowning == 0.0F, "La armadura de las mareas no protege del agua");
		serpent.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "legendary", tickLimit = 100)
	public void mobsComeOutOfBlocks(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		var BM = com.evolvedwarfare.legendary.BlockMobs.class;
		check(com.evolvedwarfare.legendary.BlockMobs.source(Blocks.STONE.getDefaultState(), new BlockPos(0, 40, 0))
				== com.evolvedwarfare.legendary.BlockMobs.Source.STONE, "La piedra no es de piedra");
		check(com.evolvedwarfare.legendary.BlockMobs.source(Blocks.OAK_LOG.getDefaultState(), new BlockPos(0, 70, 0))
				== com.evolvedwarfare.legendary.BlockMobs.Source.LOG, "El tronco no es de árbol");
		check(com.evolvedwarfare.legendary.BlockMobs.source(Blocks.GLASS.getDefaultState(), new BlockPos(0, 70, 0)) == null, "Del cristal sale algo");
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		var stone = com.evolvedwarfare.legendary.BlockMobs.spawn(ctx.getWorld(), player, at, com.evolvedwarfare.legendary.BlockMobs.Source.STONE);
		var log = com.evolvedwarfare.legendary.BlockMobs.spawn(ctx.getWorld(), player, at, com.evolvedwarfare.legendary.BlockMobs.Source.LOG);
		int ambush = com.evolvedwarfare.legendary.BlockMobs.ambush(ctx.getWorld(), player);
		LOG.info("[EW-GAMETEST] sorpresas: piedra {}, tronco {}, al dormir {} ({})", stone.stream().map(m -> m.getType().getUntranslatedName()).toList(),
				log.stream().map(m -> m.getType().getUntranslatedName()).toList(), ambush, BM.getSimpleName());
		check(!stone.isEmpty() && stone.get(0).getTarget() == player, "De la piedra no sale nada (o no va a por el jugador)");
		check(!log.isEmpty(), "Del tronco no sale nada");
		stone.forEach(net.minecraft.entity.Entity::discard);
		log.forEach(net.minecraft.entity.Entity::discard);
		ctx.complete();
	}
}
