package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.cart.CartEntity;
import com.evolvedwarfare.cart.CartModule;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La carreta: sus recetas, lo que se le pone encima (la cama ocupa dos huecos, lo de dentro cae al quitarlo), el horno
 * que funde, tirar de ella (tú y un caballo), dormir en ella y romperla.
 */
public class CartGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static CartEntity cart(TestContext ctx, float x, float z) {
		for (int ix = 0; ix < 8; ix++) {
			for (int iz = 0; iz < 8; iz++) {
				ctx.setBlockState(ix, 0, iz, Blocks.STONE);
			}
		}
		CartEntity cart = ctx.spawnEntity(ModEntities.CART, x, 1.0F, z);
		cart.setYaw(0.0F);
		return cart;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cartRecipesExist(TestContext ctx) {
		for (String id : new String[] {"cart_wheel", "cart_bed", "cart"}) {
			if (ctx.getWorld().getRecipeManager().get(EvolvedWarfare.id(id)).isEmpty()) {
				throw new GameTestException("Falta la receta " + id);
			}
		}
		ctx.complete();
	}

	/** La cama ocupa su hueco y el de detrás; no se puede poner nada en sus pies; lo de dentro del cofre cae al quitarlo. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cartModulesAndBed(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		cart.cells.setStack(0, new ItemStack(Items.CHEST));
		cart.cells.setStack(4, new ItemStack(Items.RED_BED));
		CartModule bed = cart.module(4);
		LOG.info("[EW-GAMETEST] carreta: cofre en 0 = {}, cama en 4 con los pies en {}, pies ocupados = {}", cart.module(0) != null,
				bed == null ? "nada" : bed.partner, cart.taken(7));
		if (cart.module(0) == null || bed == null || bed.partner != 7 || cart.bedHeadOf(7) != 4) {
			throw new GameTestException("La cama no ocupa dos huecos");
		}
		if (cart.cells.isValid(7, new ItemStack(Items.BARREL)) || cart.cells.isValid(1, new ItemStack(Items.DIRT))) {
			throw new GameTestException("Se puede poner algo en los pies de la cama, o tierra");
		}
		if (!cart.cells.isValid(8, new ItemStack(Items.BLUE_BED))) {
			throw new GameTestException("Otra cama no cabe en 8 (tiene el 5 libre)");
		}
		cart.module(0).inventory.setStack(0, new ItemStack(Items.IRON_INGOT, 5));
		ItemStack chest = cart.cells.removeStack(0);
		Box around = cart.getBoundingBox().expand(3.0);
		int dropped = ctx.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> e.getStack().isOf(Items.IRON_INGOT)).stream()
				.mapToInt(e -> e.getStack().getCount()).sum();
		if (!chest.isOf(Items.CHEST) || dropped != 5) {
			throw new GameTestException("Al quitar el cofre no cae lo de dentro: " + chest + ", " + dropped);
		}
		cart.discard();
		ctx.complete();
	}

	/** El horno de la carreta funde (también en marcha). */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void cartFurnaceSmelts(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		cart.cells.setStack(3, new ItemStack(Items.FURNACE));
		CartModule furnace = cart.module(3);
		furnace.inventory.setStack(0, new ItemStack(Items.RAW_IRON));
		furnace.inventory.setStack(1, new ItemStack(Items.COAL));
		ctx.waitAndRun(230, () -> {
			ItemStack out = furnace.inventory.getStack(2);
			LOG.info("[EW-GAMETEST] horno de la carreta: sale {}, encendido = {}", out, furnace.lit());
			if (!out.isOf(Items.IRON_INGOT)) {
				throw new GameTestException("El horno de la carreta no funde: " + out);
			}
			cart.discard();
			ctx.complete();
		});
	}

	/** Con la rienda te pones entre las varas; al andar te sigue; agachado la sueltas y te devuelve la rienda. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void playerPullsCart(TestContext ctx) {
		CartEntity cart = cart(ctx, 2.0F, 1.5F);
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LEAD));
		ActionResult result = cart.interact(player, Hand.MAIN_HAND);
		Vec3d spot = cart.toWorld(new Vec3d(0, 0, CartEntity.PLAYER_HITCH));
		if (cart.puller() != player || player.getPos().squaredDistanceTo(spot.x, cart.getY(), spot.z) > 0.25 || !player.getMainHandStack().isEmpty()) {
			throw new GameTestException("No se engancha el jugador: " + result + ", tira " + cart.puller() + ", está en " + player.getPos());
		}
		Vec3d start = cart.getPos();
		player.setPosition(player.getX() + 3.0, player.getY(), player.getZ() + 1.0);
		ctx.waitAndRun(10, () -> {
			double moved = cart.getPos().distanceTo(start);
			double gap = Math.sqrt(cart.squaredDistanceTo(player.getX(), cart.getY(), player.getZ()));
			LOG.info("[EW-GAMETEST] carreta tirada por el jugador: se movió {}, queda a {} (debe ~{}), giro {}", moved, gap,
					CartEntity.PLAYER_HITCH, cart.getYaw());
			if (moved < 1.0 || Math.abs(gap - CartEntity.PLAYER_HITCH) > 0.5) {
				throw new GameTestException("La carreta no sigue al jugador: " + moved + ", " + gap);
			}
			player.setSneaking(true);
			ctx.waitAndRun(3, () -> {
				if (cart.puller() != null || player.getInventory().count(Items.LEAD) != 1) {
					throw new GameTestException("Agachado no se suelta (o no devuelve la rienda)");
				}
				cart.discard();
				ctx.complete();
			});
		});
	}

	/** Rienda en la mano y clic en un caballo domado junto a la carreta: se pone entre las varas y la arrastra. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void horsePullsCart(TestContext ctx) {
		CartEntity cart = cart(ctx, 2.0F, 1.5F);
		HorseEntity horse = ctx.spawnEntity(EntityType.HORSE, 5.0F, 1.0F, 5.0F);
		horse.setTame(true);
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LEAD));
		ActionResult result = UseEntityCallback.EVENT.invoker().interact(player, ctx.getWorld(), Hand.MAIN_HAND, horse, null);
		Vec3d spot = cart.toWorld(new Vec3d(0, 0, CartEntity.HORSE_HITCH));
		LOG.info("[EW-GAMETEST] caballo: {} -> tira {}, a {} de su sitio", result, cart.puller(), Math.sqrt(horse.squaredDistanceTo(spot.x, horse.getY(), spot.z)));
		if (result != ActionResult.SUCCESS || cart.puller() != horse || horse.squaredDistanceTo(spot.x, horse.getY(), spot.z) > 0.25) {
			throw new GameTestException("El caballo no se engancha");
		}
		Vec3d start = cart.getPos();
		horse.refreshPositionAndAngles(horse.getX() - 2.0, horse.getY(), horse.getZ() + 1.5, horse.getYaw(), 0.0F);
		ctx.waitAndRun(5, () -> {
			if (cart.getPos().distanceTo(start) < 0.5) {
				throw new GameTestException("El caballo no arrastra la carreta");
			}
			cart.unhitch(player);
			cart.discard();
			horse.discard();
			ctx.complete();
		});
	}

	/** La cama: de noche duermes (y sigues durmiendo, aunque no haya una cama de verdad) y reapareces en la carreta. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void cartBedSleeps(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		cart.cells.setStack(4, new ItemStack(Items.WHITE_BED));
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		long time = world.getTimeOfDay();
		world.setTimeOfDay(18000L);
		world.calculateAmbientDarkness();
		cart.use(4, player);
		BlockPos spawn = player.getSpawnPointPosition();
		boolean asleep = player.isSleeping();
		ctx.waitAndRun(5, () -> {
			LOG.info("[EW-GAMETEST] cama de la carreta: reaparición {} (carreta en {}), dormido {} -> {}", spawn, cart.getBlockPos(), asleep,
					player.isSleeping());
			world.setTimeOfDay(time);
			world.calculateAmbientDarkness();
			if (spawn == null || !spawn.isWithinDistance(cart.getBlockPos(), 2.0) || !asleep || !player.isSleeping()) {
				throw new GameTestException("La cama de la carreta no deja dormir o no fija la reaparición");
			}
			player.wakeUp(true, false);
			cart.discard();
			ctx.complete();
		});
	}

	/** Rota a golpes, suelta la carreta, lo de encima y lo de dentro. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void breakingCartDropsAll(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		cart.cells.setStack(1, new ItemStack(Items.BARREL));
		cart.cells.setStack(5, new ItemStack(Items.CRAFTING_TABLE));
		cart.module(1).inventory.setStack(3, new ItemStack(Items.WHEAT, 20));
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Box around = cart.getBoundingBox().expand(3.0);
		cart.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10.0F);
		List<ItemEntity> drops = ctx.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> true);
		LOG.info("[EW-GAMETEST] carreta rota: {}", drops.stream().map(e -> e.getStack().toString()).toList());
		boolean cartItem = drops.stream().anyMatch(e -> e.getStack().isOf(ModItems.CART));
		boolean barrel = drops.stream().anyMatch(e -> e.getStack().isOf(Items.BARREL));
		boolean table = drops.stream().anyMatch(e -> e.getStack().isOf(Items.CRAFTING_TABLE));
		int wheat = drops.stream().filter(e -> e.getStack().isOf(Items.WHEAT)).mapToInt(e -> e.getStack().getCount()).sum();
		if (!cart.isRemoved() || !cartItem || !barrel || !table || wheat != 20) {
			throw new GameTestException("Rota no suelta todo");
		}
		ctx.complete();
	}
}
