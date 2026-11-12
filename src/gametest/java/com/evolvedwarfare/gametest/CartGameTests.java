package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.cart.CartEntity;
import com.evolvedwarfare.voxel.VoxelBuild;
import com.evolvedwarfare.voxel.VoxelGrid;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.inventory.Inventories;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.property.Properties;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
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
 * La carreta: sus recetas, construir encima bloque a bloque (cómo quedan, quitarlos a golpes, las carretas viejas), el
 * horno que funde, tirar de ella (tú y un caballo), dormir en ella y romperla.
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

	/** Un jugador encima de la carreta, mirando hacia abajo a ese sitio de la tabla (coordenadas de la carreta). */
	private static ServerPlayerEntity lookingDown(TestContext ctx, CartEntity cart, double x, double z, double above) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		Vec3d at = cart.toWorld(new Vec3d(x, 0, z));
		player.refreshPositionAndAngles(at.x, cart.getY() + above, at.z, 0.0F, 90.0F);
		// de pie (en el aire se pica 5 veces más despacio)
		player.setOnGround(true);
		return player;
	}

	/** Bloque a bloque: en la tabla, encima de lo puesto, hasta 3 de alto; y a golpes se quita y se recupera. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cartBuildsBlockByBlock(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		VoxelGrid g = cart.grid();
		ServerPlayerEntity player = lookingDown(ctx, cart, 0.0, -2.0, 2.0);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 5));
		ActionResult first = cart.interact(player, Hand.MAIN_HAND);
		ActionResult second = cart.interact(player, Hand.MAIN_HAND);
		LOG.info("[EW-GAMETEST] carreta bloque a bloque: {} {} -> (1,0,0) {} (1,1,0) {}, quedan {}", first, second, g.get(g.index(1, 0, 0)),
				g.get(g.index(1, 1, 0)), player.getMainHandStack().getCount());
		if (!g.get(g.index(1, 0, 0)).isOf(Blocks.OAK_PLANKS) || !g.get(g.index(1, 1, 0)).isOf(Blocks.OAK_PLANKS)
				|| player.getMainHandStack().getCount() != 3) {
			throw new GameTestException("No se ponen los bloques en la tabla y encima");
		}
		// lo macizo crece: se puede andar por encima
		double top = cart.worldBoxes().stream().mapToDouble(b -> b.maxY).max().orElse(0) - cart.getY();
		if (Math.abs(top - (CartEntity.DECK + 2.0)) > 0.01) {
			throw new GameTestException("Lo construido no es macizo: " + top);
		}
		// a mano, unos cuantos golpes; cae la madera
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		int hits = 0;
		while (!g.isEmpty(g.index(1, 1, 0)) && hits < 30) {
			cart.handleAttack(player);
			hits++;
		}
		Box around = cart.getBoundingBox().expand(3.0);
		int planks = ctx.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> e.getStack().isOf(Items.OAK_PLANKS)).stream()
				.mapToInt(e -> e.getStack().getCount()).sum();
		LOG.info("[EW-GAMETEST] carreta: quitar un bloque a mano: {} golpes, suelta {}", hits, planks);
		if (!g.isEmpty(g.index(1, 1, 0)) || hits < 3 || hits > 15 || planks != 1 || cart.isRemoved()) {
			throw new GameTestException("No se quita bien a golpes: " + hits + " golpes, " + planks);
		}
		// con un hacha, muchos menos
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
		int axeHits = 0;
		while (!g.isEmpty(g.index(1, 0, 0)) && axeHits < 30) {
			cart.handleAttack(player);
			axeHits++;
		}
		if (axeHits > 2) {
			throw new GameTestException("Con hacha cuesta igual: " + axeHits);
		}
		// no más de 3 de alto
		for (int y = 0; y < 3; y++) {
			cart.putBlock(0, y, 4, Blocks.STONE.getDefaultState());
		}
		Vec3d topHit = new Vec3d(-1.0, CartEntity.DECK + 3.0, 2.0);
		if (VoxelBuild.plan(g, new VoxelGrid.Hit(g.index(0, 2, 4), Direction.UP, topHit), new ItemStack(Items.STONE), new Vec3d(0, -1, 0)) != null) {
			throw new GameTestException("Se puede poner un cuarto bloque de alto");
		}
		cart.discard();
		ctx.complete();
	}

	/** Cómo quedan: escaleras y losas según se apunte, losa + losa = entera, puertas y camas de dos, vallas unidas; y qué no vale. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cartBlockShapes(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		VoxelGrid g = cart.grid();
		Vec3d south = new Vec3d(0, 0, 1);
		VoxelGrid.Hit deck = new VoxelGrid.Hit(-1, Direction.UP, new Vec3d(0.0, CartEntity.DECK, 0.0));
		var stairs = VoxelBuild.plan(g, deck, new ItemStack(Items.OAK_STAIRS), south);
		if (stairs == null || stairs.states()[0].get(Properties.HORIZONTAL_FACING) != Direction.SOUTH
				|| stairs.states()[0].get(Properties.BLOCK_HALF) != BlockHalf.BOTTOM || stairs.cells()[0] != g.index(1, 0, 2)) {
			throw new GameTestException("Escalera mal: " + (stairs == null ? null : stairs.states()[0]));
		}
		// losa por debajo de un bloque: arriba; otra igual encima de una de abajo: entera
		cart.putBlock(1, 2, 2, Blocks.STONE.getDefaultState());
		var slabTop = VoxelBuild.plan(g, new VoxelGrid.Hit(g.index(1, 2, 2), Direction.DOWN, new Vec3d(0, CartEntity.DECK + 2.0, 0)),
				new ItemStack(Items.OAK_SLAB), south);
		cart.putBlock(0, 0, 0, Blocks.OAK_SLAB.getDefaultState());
		var merge = VoxelBuild.plan(g, new VoxelGrid.Hit(g.index(0, 0, 0), Direction.UP, new Vec3d(-1.0, CartEntity.DECK + 0.5, -2.0)),
				new ItemStack(Items.OAK_SLAB), south);
		if (slabTop == null || slabTop.states()[0].get(Properties.SLAB_TYPE) != SlabType.TOP || merge == null
				|| merge.states()[0].get(Properties.SLAB_TYPE) != SlabType.DOUBLE) {
			throw new GameTestException("Losas mal");
		}
		var door = VoxelBuild.plan(g, deck, new ItemStack(Items.OAK_DOOR), south);
		var bed = VoxelBuild.plan(g, deck, new ItemStack(Items.RED_BED), south);
		if (door == null || door.cells().length != 2 || door.cells()[1] != g.index(1, 1, 2) || bed == null || bed.cells()[1] != g.index(1, 0, 3)) {
			throw new GameTestException("Puerta o cama no ocupan dos");
		}
		// vallas unidas
		cart.putBlock(2, 0, 4, Blocks.OAK_FENCE.getDefaultState());
		cart.putBlock(1, 0, 4, Blocks.OAK_FENCE.getDefaultState());
		BlockState fence = g.get(g.index(2, 0, 4));
		LOG.info("[EW-GAMETEST] carreta: escalera {}, losa {}, valla {}", stairs.states()[0], merge.states()[0], fence);
		if (!fence.get(Properties.WEST)) {
			throw new GameTestException("Las vallas no se unen: " + fence);
		}
		for (var no : new net.minecraft.item.Item[] {Items.SAND, Items.WATER_BUCKET, Items.OAK_SAPLING, Items.REDSTONE, Items.PISTON, Items.OAK_SIGN}) {
			if (VoxelBuild.blockOf(new ItemStack(no)) != null) {
				throw new GameTestException("Se puede poner " + no);
			}
		}
		for (var yes : new net.minecraft.item.Item[] {Items.GLASS, Items.LANTERN, Items.TORCH, Items.CHEST, Items.CAMPFIRE, Items.OAK_TRAPDOOR, Items.STONE_BRICK_WALL}) {
			if (VoxelBuild.blockOf(new ItemStack(yes)) == null) {
				throw new GameTestException("No se puede poner " + yes);
			}
		}
		// se guarda y se lee igual
		VoxelGrid copy = new VoxelGrid(3, 3, 5, -1.5, CartEntity.DECK, -2.5);
		copy.read(g.write(ctx.getWorld().getRegistryManager(), true), ctx.getWorld().getRegistryManager());
		for (int i = 0; i < g.size(); i++) {
			if (copy.get(i) != g.get(i)) {
				throw new GameTestException("No se guarda igual la celda " + i);
			}
		}
		cart.discard();
		ctx.complete();
	}

	/** Las carretas de antes: lo que llevaban en sus huecos pasa a bloques, con lo de dentro. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void oldCartsBecomeBlocks(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		var reg = ctx.getWorld().getRegistryManager();
		NbtCompound chest = new NbtCompound();
		chest.putInt("Cell", 0);
		chest.put("Item", new ItemStack(Items.CHEST).encode(reg));
		DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
		stacks.set(0, new ItemStack(Items.IRON_INGOT, 7));
		Inventories.writeNbt(chest, stacks, reg);
		NbtCompound bed = new NbtCompound();
		bed.putInt("Cell", 4);
		bed.putInt("Partner", 7);
		bed.put("Item", new ItemStack(Items.WHITE_BED).encode(reg));
		NbtList list = new NbtList();
		list.add(chest);
		list.add(bed);
		NbtCompound nbt = cart.writeNbt(new NbtCompound());
		nbt.remove("Grid");
		nbt.put("Modules", list);
		cart.readNbt(nbt);
		VoxelGrid g = cart.grid();
		int at = g.index(2, 0, 3);
		var m = g.machine(at);
		LOG.info("[EW-GAMETEST] carreta vieja: {} con {}, cama {} / {}", g.get(at), m == null ? null : m.inventory.getStack(0), g.get(g.index(1, 0, 2)),
				g.get(g.index(1, 0, 1)));
		if (!g.get(at).isOf(Blocks.CHEST) || m == null || m.inventory.getStack(0).getCount() != 7 || !g.get(g.index(1, 0, 2)).isOf(Blocks.WHITE_BED)
				|| !g.get(g.index(1, 0, 1)).isOf(Blocks.WHITE_BED)) {
			throw new GameTestException("La carreta vieja no pasa a bloques");
		}
		cart.discard();
		ctx.complete();
	}

	/** El horno de la carreta funde (y se ve encendido). */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void cartFurnaceSmelts(TestContext ctx) {
		CartEntity cart = cart(ctx, 4.0F, 4.0F);
		cart.putBlock(0, 0, 2, Blocks.FURNACE.getDefaultState());
		var furnace = cart.grid().machine(cart.grid().index(0, 0, 2));
		furnace.inventory.setStack(0, new ItemStack(Items.RAW_IRON));
		furnace.inventory.setStack(1, new ItemStack(Items.COAL));
		ctx.waitAndRun(20, () -> {
			boolean lit = cart.grid().get(cart.grid().index(0, 0, 2)).get(Properties.LIT);
			ctx.waitAndRun(210, () -> {
				ItemStack out = furnace.inventory.getStack(2);
				LOG.info("[EW-GAMETEST] horno de la carreta: sale {}, se veía encendido = {}", out, lit);
				if (!out.isOf(Items.IRON_INGOT) || !lit) {
					throw new GameTestException("El horno de la carreta no funde: " + out + ", " + lit);
				}
				cart.discard();
				ctx.complete();
			});
		});
	}

	/** Con la rienda te pones entre las varas; al andar te sigue; agachado la sueltas y te devuelve la rienda. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void playerPullsCart(TestContext ctx) {
		CartEntity cart = cart(ctx, 2.0F, 2.5F);
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
		CartEntity cart = cart(ctx, 2.0F, 2.5F);
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
		VoxelGrid g = cart.grid();
		VoxelBuild.apply(cart, VoxelBuild.plan(g, new VoxelGrid.Hit(-1, Direction.UP, new Vec3d(0.0, CartEntity.DECK, 0.0)), new ItemStack(Items.WHITE_BED),
				new Vec3d(0, 0, 1)));
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		long time = world.getTimeOfDay();
		world.setTimeOfDay(18000L);
		world.calculateAmbientDarkness();
		cart.sleepOn(VoxelBuild.bedHead(g, g.index(1, 0, 2)), player);
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
		cart.putBlock(1, 0, 1, Blocks.BARREL.getDefaultState());
		cart.putBlock(2, 1, 4, Blocks.CRAFTING_TABLE.getDefaultState());
		cart.grid().machine(cart.grid().index(1, 0, 1)).inventory.setStack(3, new ItemStack(Items.WHEAT, 20));
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
