package com.evolvedwarfare.gametest;

import com.evolvedwarfare.climate.Climate;
import com.evolvedwarfare.climate.CropSeasons;
import com.evolvedwarfare.climate.Crops;
import com.evolvedwarfare.climate.Season;
import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Los cultivos y las estaciones: solo crecen en la suya (fuera, bajo techo y despacio), y los cultivos nuevos. */
public class CropGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static final Block[] CROPS = {Blocks.WHEAT, Blocks.POTATOES, Blocks.MELON_STEM, ModBlocks.TOMATOES, ModBlocks.CABBAGES,
			ModBlocks.GARLIC, ModBlocks.CORN, Blocks.SWEET_BERRY_BUSH};

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cropsGrowOnlyInTheirSeason(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Season now = Climate.season(world);
		Block inSeason = null;
		Block outOfSeason = null;
		for (Block b : CROPS) {
			Set<Season> set = CropSeasons.of(b);
			check(set != null, "Sin estaciones: " + b);
			if (set.contains(now) && inSeason == null) {
				inSeason = b;
			} else if (!set.contains(now) && outOfSeason == null) {
				outOfSeason = b;
			}
		}
		check(inSeason != null && outOfSeason != null, "No hay un cultivo de cada en " + now);
		BlockPos open = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos covered = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
		ctx.setBlockState(3, 3, 1, Blocks.STONE);
		Random random = Random.create(42L);
		int inOpen = 0;
		int outOpen = 0;
		int outCovered = 0;
		for (int i = 0; i < 90; i++) {
			inOpen += CropSeasons.canGrow(world, open, inSeason, random) ? 1 : 0;
			outOpen += CropSeasons.canGrow(world, open, outOfSeason, random) ? 1 : 0;
			outCovered += CropSeasons.canGrow(world, covered, outOfSeason, random) ? 1 : 0;
		}
		boolean bonemealOpen = CropSeasons.canFertilize(world, open, outOfSeason);
		boolean bonemealCovered = CropSeasons.canFertilize(world, covered, outOfSeason);
		LOG.info("[EW-GAMETEST] estaciones ({}): {} en temporada {}/90; {} fuera al raso {}/90, bajo techo {}/90; hueso al raso {}, bajo techo {}",
				now, inSeason, inOpen, outOfSeason, outOpen, outCovered, bonemealOpen, bonemealCovered);
		check(inOpen == 90, "En su estación no siempre puede crecer");
		check(outOpen == 0, "Fuera de su estación y al raso crece");
		check(outCovered > 10 && outCovered < 60, "Fuera de su estación bajo techo no crece a un tercio: " + outCovered);
		check(!bonemealOpen && bonemealCovered, "La harina de hueso no respeta la estación");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void newCropsDropTheirHarvest(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Object[][] cases = {
				{ModBlocks.TOMATOES, ModItems.TOMATO, ModItems.TOMATO_SEEDS},
				{ModBlocks.CORN, ModItems.CORN, ModItems.CORN_SEEDS},
				{ModBlocks.CABBAGES, ModItems.CABBAGE, ModItems.CABBAGE_SEEDS},
				{ModBlocks.ONIONS, ModItems.ONION, ModItems.ONION},
				{ModBlocks.GARLIC, ModItems.GARLIC, ModItems.GARLIC}};
		StringBuilder log = new StringBuilder();
		for (Object[] c : cases) {
			CropBlock crop = (CropBlock) c[0];
			BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
			BlockState ripe = crop.withAge(crop.getMaxAge());
			List<ItemStack> mature = Block.getDroppedStacks(ripe, world, pos, null);
			List<ItemStack> young = Block.getDroppedStacks(crop.withAge(0), world, pos, null);
			boolean produce = mature.stream().anyMatch(s -> s.isOf((Item) c[1]));
			boolean seeds = young.stream().anyMatch(s -> s.isOf((Item) c[2])) && young.stream().noneMatch(s -> s.isOf((Item) c[1]) && c[1] != c[2]);
			boolean planted = ((Item) c[2]) instanceof net.minecraft.item.BlockItem bi && bi.getBlock() == crop;
			log.append(crop).append(" maduro ").append(mature).append(" joven ").append(young).append("; ");
			check(produce, "Maduro no da su cosecha: " + crop + " " + mature);
			check(seeds, "Joven no devuelve la semilla: " + crop + " " + young);
			check(planted, "Su semilla no lo planta: " + crop);
			check(Market.priceOf(new ItemStack((Item) c[1])) != null, "Sin precio en el mercado: " + c[1]);
		}
		LOG.info("[EW-GAMETEST] cultivos nuevos: {}", log);
		check(Crops.seedsFor(1.5F).contains(ModItems.TOMATO_SEEDS) && Crops.seedsFor(-0.5F).contains(ModItems.GARLIC),
				"La hierba no da las semillas del clima");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void garlicCuresAndStewQuenches(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Thirst.infect(player);
		new ItemStack(ModItems.GARLIC).finishUsing(ctx.getWorld(), player);
		boolean cured = !player.hasStatusEffect(ModEffects.PARASITES);
		Thirst.set(player, 4.0F);
		Thirst.onConsume(player, new ItemStack(ModItems.VEGETABLE_STEW));
		float thirst = Thirst.get(player);
		LOG.info("[EW-GAMETEST] ajo cura {}; guiso: sed 4 -> {}", cured, thirst);
		check(cured, "El ajo no quita los parásitos");
		check(Math.abs(thirst - 10.0F) < 0.1F, "El guiso no quita 6 de sed: " + thirst);
		ctx.complete();
	}
}
