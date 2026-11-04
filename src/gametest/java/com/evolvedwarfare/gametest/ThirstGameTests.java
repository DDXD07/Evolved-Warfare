package com.evolvedwarfare.gametest;

import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La sed: lo que quita cada cosa, que se gasta más con calor, la deshidratación, beber del agua del mundo con las manos
 * (un segundo, y los parásitos), hervir el agua (horno, fogata, caldero al fuego), la cantimplora y los parásitos.
 */
public class ThirstGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static ServerPlayerEntity player(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		return player;
	}

	private static ItemStack waterBottle() {
		return PotionContentsComponent.createStack(Items.POTION, Potions.WATER);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void foodAndDrinksQuenchThirst(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		Thirst.set(player, 6.0F);
		Thirst.onConsume(player, new ItemStack(Items.MELON_SLICE));
		float melon = Thirst.get(player);
		Thirst.onConsume(player, new ItemStack(Items.DRIED_KELP));
		float kelp = Thirst.get(player);
		Thirst.onConsume(player, waterBottle());
		float bottle = Thirst.get(player);
		ItemStack boiled = new ItemStack(ModItems.BOILED_WATER);
		ItemStack left = boiled.finishUsing(ctx.getWorld(), player);
		float afterBoiled = Thirst.get(player);
		LOG.info("[EW-GAMETEST] sed: 6 -> melón {} -> alga seca {} -> frasco {} -> agua hervida {} (deja {})", melon, kelp, bottle, afterBoiled, left);
		check(Math.abs(melon - 10.0F) < 0.1F, "El melón no quita 4 de sed: " + melon);
		check(Math.abs(kelp - 9.0F) < 0.1F, "El alga seca no da sed: " + kelp);
		check(Math.abs(bottle - 14.0F) < 0.1F, "El frasco de agua no quita 5 de sed: " + bottle);
		check(Math.abs(afterBoiled - 20.0F) < 0.1F, "El agua hervida no quita 7 de sed (hasta 20): " + afterBoiled);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void heatAndWorkDryYouOut(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		float mild = Thirst.drain(15.0F, false, 37.0F);
		float desert = Thirst.drain(40.0F, false, 37.0F);
		float running = Thirst.drain(15.0F, true, 37.0F);
		Thirst.set(player, 10.0F);
		Thirst.exhaust(player, 8.0F);
		float worked = Thirst.get(player);
		check(desert > mild * 2.0F, "El calor no da más sed: " + mild + " / " + desert);
		check(running > mild, "Correr no da más sed");
		check(Math.abs(worked - 9.0F) < 0.2F, "El cansancio no da sed: " + worked);
		// sin nada de sed, la deshidratación quita vida (pasada la invulnerabilidad del recién llegado)
		ctx.waitAndRun(80, () -> {
			ServerWorld world = ctx.getWorld();
			Thirst.set(player, 0.0F);
			float before = player.getHealth();
			Thirst.second(world, player, 15.0F, 0L);
			float after = player.getHealth();
			LOG.info("[EW-GAMETEST] sed: gasto templado {}, desierto {}, corriendo {}; cansado {}; deshidratado {} -> {} ({})", mild, desert,
					running, worked, before, after, world.getDifficulty());
			check(world.getDifficulty() == Difficulty.PEACEFUL || after < before, "Sin nada de sed no pierde vida");
			check(Thirst.parched(player), "Sin sed no cuenta como sediento");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void drinkingFromARiver(TestContext ctx) {
		floor(ctx);
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 1, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(2, 1, 2, Blocks.WATER);
		ServerPlayerEntity player = player(ctx);
		Vec3d at = Vec3d.of(ctx.getAbsolutePos(new BlockPos(2, 2, 1))).add(0.5, 0.0, 0.5);
		// de pie al lado, mirando abajo hacia el agua (hacia el sur)
		player.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 60.0F);
		player.setSneaking(true);
		Thirst.set(player, 10.0F);
		BlockPos seen = Thirst.waterInSight(player);
		Thirst.request(player, com.evolvedwarfare.network.DrinkRequestPayload.WATER);
		boolean drinking = Thirst.drinking(player);
		ctx.waitAndRun(Thirst.DRINK_TICKS + 4, () -> {
			float after = Thirst.get(player);
			player.removeStatusEffect(ModEffects.PARASITES);
			// a la suerte: por encima de la probabilidad no pasa nada; por debajo, parásitos
			Thirst.finish(player, Thirst.Source.WORLD, null, 0.99F);
			boolean lucky = !player.hasStatusEffect(ModEffects.PARASITES);
			Thirst.finish(player, Thirst.Source.BOILED_CAULDRON, null, 0.0F);
			boolean boiledSafe = !player.hasStatusEffect(ModEffects.PARASITES);
			Thirst.set(player, 5.0F);
			Thirst.finish(player, Thirst.Source.WORLD, null, 0.0F);
			boolean unlucky = player.hasStatusEffect(ModEffects.PARASITES);
			LOG.info("[EW-GAMETEST] beber del río: agua vista {}, bebiendo {}, sed 10 -> {}; suerte {}, hervida sin parásitos {}, parásitos {}", seen,
					drinking, after, lucky, boiledSafe, unlucky);
			check(ctx.getAbsolutePos(new BlockPos(2, 1, 2)).equals(seen), "No ve el agua: " + seen);
			check(drinking, "Agachado y con la mano vacía no empieza a beber");
			check(after > 12.5F && !Thirst.drinking(player), "Al segundo no ha bebido: " + after);
			check(lucky, "Con buena suerte le dan parásitos");
			check(boiledSafe, "El agua hervida da parásitos");
			check(unlucky, "Con mala suerte no le dan parásitos");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void boilingInFurnaceAndCampfire(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack smelted = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(waterBottle()), world)
				.map(e -> e.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ItemStack campfire = world.getRecipeManager().getFirstMatch(RecipeType.CAMPFIRE_COOKING, new SingleStackRecipeInput(waterBottle()), world)
				.map(e -> e.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ItemStack canteen = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING,
				new SingleStackRecipeInput(new ItemStack(ModItems.WATER_CANTEEN)), world)
				.map(e -> e.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
		boolean awkward = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING,
				new SingleStackRecipeInput(PotionContentsComponent.createStack(Items.POTION, Potions.AWKWARD)), world).isPresent();
		LOG.info("[EW-GAMETEST] hervir: horno {}, fogata {}, cantimplora {}, poción rara {}", smelted, campfire, canteen, awkward);
		check(smelted.isOf(ModItems.BOILED_WATER), "El horno no hierve el frasco de agua: " + smelted);
		check(campfire.isOf(ModItems.BOILED_WATER), "La fogata no hierve el frasco de agua: " + campfire);
		check(canteen.isOf(ModItems.BOILED_CANTEEN), "El horno no hierve la cantimplora: " + canteen);
		check(!awkward, "El horno 'hierve' cualquier poción");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cauldronOverAFireBoils(TestContext ctx) {
		floor(ctx);
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(1, 1, 1, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		ctx.setBlockState(1, 2, 1, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
		ctx.setBlockState(3, 1, 1, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
		BlockPos hot = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		BlockPos cold = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
		ServerPlayerEntity player = player(ctx);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
		UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(hot), Direction.UP, hot, false));
		boolean gotBoiled = player.getInventory().count(ModItems.BOILED_WATER) == 1;
		int level = world.getBlockState(hot).get(LeveledCauldronBlock.LEVEL);
		var coldResult = UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND,
				new BlockHitResult(Vec3d.ofCenter(cold), Direction.UP, cold, false));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CANTEEN));
		UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(hot), Direction.UP, hot, false));
		// (el jugador de prueba cuenta como creativo: lo lleno va al inventario)
		boolean canteen = player.getInventory().count(ModItems.BOILED_CANTEEN) == 1;
		LOG.info("[EW-GAMETEST] caldero al fuego: hierve {} / frío {}; frasco hervido {}, nivel 3 -> {}, el frío deja a vanilla {}, cantimplora {}",
				Thirst.heated(world, hot), Thirst.heated(world, cold), gotBoiled, level, coldResult, canteen);
		check(Thirst.heated(world, hot) && !Thirst.heated(world, cold), "No sabe qué caldero tiene fuego debajo");
		check(gotBoiled, "Del caldero al fuego no sale agua hervida");
		check(level == 2, "El caldero no baja al llenar el frasco: " + level);
		check(coldResult == net.minecraft.util.ActionResult.PASS, "El caldero frío no deja el frasco a vanilla");
		check(canteen, "La cantimplora no sale hervida del caldero al fuego");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void canteenHasFourSips(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		Thirst.set(player, 0.0F);
		ItemStack canteen = new ItemStack(ModItems.BOILED_CANTEEN);
		int sips = 0;
		// (el jugador de prueba cuenta como creativo: los tragos se cuentan aparte)
		while (canteen.isOf(ModItems.BOILED_CANTEEN) && sips < 10) {
			canteen.finishUsing(ctx.getWorld(), player);
			canteen = com.evolvedwarfare.climate.CanteenItem.afterSip(canteen);
			sips++;
		}
		float thirst = Thirst.get(player);
		LOG.info("[EW-GAMETEST] cantimplora: {} tragos, sed 0 -> {}, queda {}", sips, thirst, canteen);
		check(sips == 4, "La cantimplora no tiene cuatro tragos: " + sips);
		check(canteen.isOf(ModItems.CANTEEN), "Vacía no vuelve a ser la cantimplora vacía: " + canteen);
		check(thirst > 19.0F, "Cuatro tragos de agua hervida no quitan la sed: " + thirst);
		check(!player.hasStatusEffect(ModEffects.PARASITES), "El agua hervida de la cantimplora da parásitos");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260)
	public void parasitesHurt(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		Thirst.infect(player);
		float start = player.getHealth();
		ctx.waitAndRun(190, () -> {
			LOG.info("[EW-GAMETEST] parásitos: vida {} -> {}", start, player.getHealth());
			check(player.hasStatusEffect(ModEffects.PARASITES), "Los parásitos se van enseguida");
			check(player.getHealth() < start, "Los parásitos no quitan vida");
			ctx.complete();
		});
	}
}
