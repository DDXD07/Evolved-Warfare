package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgeMinigame;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.ForgedToolRecipe;
import com.evolvedwarfare.smithing.Smithing;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La herrería: las recetas de mesa quitadas, el minijuego, montar la pieza con su mango y lo que hace la calidad. */
public class SmithingGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static float play(boolean skilled, int level, long seed) {
		ForgeMinigame g = new ForgeMinigame(7, level, 1.0F, Random.create(seed));
		Random r = Random.create(seed + 99);
		for (int i = 0; i < 2000 && !g.done(); i++) {
			g.tick();
			boolean strike = skilled ? Math.abs(g.hammer - g.target) < g.halfWidth * 0.3F : r.nextInt(25) == 0;
			if (strike) {
				g.strike();
			}
		}
		return g.score();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void forgingMinigame(TestContext ctx) {
		float good = 0;
		float random = 0;
		for (long s = 0; s < 20; s++) {
			good += play(true, 0, s);
			random += play(false, 0, s);
		}
		good /= 20;
		random /= 20;
		LOG.info("[EW-GAMETEST] forja: buen herrero {} ({}), a lo loco {} ({})", good, ForgeMinigame.quality(good), random, ForgeMinigame.quality(random));
		check(ForgeMinigame.quality(good) == 4, "Acertando siempre no sale obra maestra");
		check(ForgeMinigame.quality(random) <= 2, "Golpeando a lo loco sale buena calidad");
		ctx.complete();
	}

	private static CraftingRecipeInput grid(ItemStack... stacks) {
		List<ItemStack> list = new ArrayList<>(List.of(stacks));
		while (list.size() < 9) {
			list.add(ItemStack.EMPTY);
		}
		return CraftingRecipeInput.create(3, 3, list);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void assembleTools(TestContext ctx) {
		var world = ctx.getWorld();
		var recipes = world.getRecipeManager();
		boolean ironPick = recipes.get(Identifier.ofVanilla("iron_pickaxe")).isPresent();
		boolean diamondSword = recipes.get(Identifier.ofVanilla("diamond_sword")).isPresent();
		boolean modHalberd = recipes.get(Identifier.of("evolvedwarfare", "iron_halberd")).isPresent();
		boolean stonePick = recipes.get(Identifier.ofVanilla("stone_pickaxe")).isPresent();
		boolean hammer = recipes.get(Identifier.of("evolvedwarfare", "smithing_hammer")).isPresent();
		ItemStack head = Smithing.part(ForgedPart.PICKAXE_HEAD, ForgeMaterial.IRON, 4);
		ItemStack stick = new ItemStack(Items.STICK);
		var match = recipes.getFirstMatch(RecipeType.CRAFTING, grid(head, stick.copy(), stick.copy()), world);
		ItemStack pick = match.map(r -> r.value().craft(grid(head, stick.copy(), stick.copy()), world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ForgedToolRecipe recipe = new ForgedToolRecipe(CraftingRecipeCategory.EQUIPMENT);
		boolean oneStick = recipe.matches(grid(head, stick.copy()), world);
		ItemStack blade = Smithing.part(ForgedPart.LONGSWORD_BLADE, ForgeMaterial.DIAMOND, 1);
		ItemStack sword = recipe.craft(grid(blade, stick.copy(), new ItemStack(Items.LEATHER)), world.getRegistryManager());
		boolean goldHalberd = recipe.matches(grid(Smithing.part(ForgedPart.HALBERD_HEAD, ForgeMaterial.GOLD, 2), stick.copy(), stick.copy()), world);
		int pickMax = pick.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
		int swordMax = sword.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
		int baseSword = ModItems.weapon(com.evolvedwarfare.weapon.WeaponType.LONGSWORD, "diamond").getComponents().getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
		LOG.info("[EW-GAMETEST] herrería: recetas de mesa: pico de hierro {}, espada de diamante {}, alabarda {}, pico de piedra {}, martillo {}; "
				+ "cabeza + 2 palos = {} (durabilidad {}, de serie 250, calidad {}); con 1 palo {}; espada larga de diamante tosca: {} ({} de {}); alabarda de oro {}",
				ironPick, diamondSword, modHalberd, stonePick, hammer, pick.getItem(), pickMax, pick.get(ModComponents.FORGE_QUALITY), oneStick,
				sword.getItem(), swordMax, baseSword, goldHalberd);
		check(!ironPick && !diamondSword && !modHalberd && stonePick && hammer, "Las recetas de mesa no están bien");
		check(pick.isOf(Items.IRON_PICKAXE) && pickMax == 400, "La cabeza de pico no se monta bien");
		check(!oneStick && !goldHalberd, "Se monta con lo que no toca");
		check(swordMax < baseSword && swordMax > 0, "La calidad tosca no baja la durabilidad");
		ctx.complete();
	}

	/** La animación de forjar y pescar: el servidor sabe qué hace cada jugador y lo olvida al acabar. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void activityIsTracked(TestContext ctx) {
		var player = (net.minecraft.server.network.ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.FORGING);
		byte forging = com.evolvedwarfare.network.ActivityPayload.current(player);
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.FISHING);
		byte fishing = com.evolvedwarfare.network.ActivityPayload.current(player);
		com.evolvedwarfare.network.ActivityPayload.set(player, com.evolvedwarfare.network.ActivityPayload.NONE);
		byte none = com.evolvedwarfare.network.ActivityPayload.current(player);
		LOG.info("[EW-GAMETEST] animaciones: forjando {}, pescando {}, al acabar {}", forging, fishing, none);
		check(forging == com.evolvedwarfare.network.ActivityPayload.FORGING && fishing == com.evolvedwarfare.network.ActivityPayload.FISHING
				&& none == com.evolvedwarfare.network.ActivityPayload.NONE, "No se guarda lo que hace el jugador");
		player.discard();
		ctx.complete();
	}

	/** El Manual del Herrero: lo venden los armeros a 20 Glory Coins justas, en cualquier ciudad. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void armorerSellsManual(TestContext ctx) {
		var world = ctx.getWorld();
		int[] prices = new int[2];
		int[] levels = {1, 10};
		for (int k = 0; k < levels.length; k++) {
			prices[k] = -1;
			for (com.evolvedwarfare.trade.Deal deal : com.evolvedwarfare.trade.TraderType.ARMORER.catalog(levels[k], 77L + k, 3L, world.getRegistryManager())) {
				if (deal.sell() && deal.prototype().isOf(ModItems.SMITHING_MANUAL)) {
					prices[k] = deal.count() == 1 ? deal.price() : -2;
				}
			}
		}
		LOG.info("[EW-GAMETEST] manual del herrero: ciudad de nivel 1 {} monedas, de nivel 10 {}", prices[0], prices[1]);
		check(prices[0] == 20 && prices[1] == 20, "El armero no vende el manual a 20 Glory Coins");
		ctx.complete();
	}

	/**
	 * La fundición entera: el crisol sobre una hoguera funde 3 lingotes al rojo (y sin fuego no funde), el molde tallado se
	 * llena con lo justo, cuaja, se enfría y a 3 golpes suelta la pieza en bruto, que no se monta en la mesa (antes al yunque).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
	public void castingFlow(TestContext ctx) {
		var world = ctx.getWorld();
		net.minecraft.util.math.BlockPos fire = ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1));
		world.setBlockState(fire, net.minecraft.block.Blocks.CAMPFIRE.getDefaultState());
		world.setBlockState(fire.up(), com.evolvedwarfare.registry.ModBlocks.CRUCIBLE.getDefaultState());
		net.minecraft.util.math.BlockPos cold = ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 1, 3));
		world.setBlockState(cold, com.evolvedwarfare.registry.ModBlocks.CRUCIBLE.getDefaultState());
		net.minecraft.util.math.BlockPos moldPos = ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 1, 1));
		world.setBlockState(moldPos, com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD.getDefaultState());
		var crucible = (com.evolvedwarfare.smithing.CrucibleBlockEntity) world.getBlockEntity(fire.up());
		var unheated = (com.evolvedwarfare.smithing.CrucibleBlockEntity) world.getBlockEntity(cold);
		boolean in = crucible.insert(ForgeMaterial.IRON) && crucible.insert(ForgeMaterial.IRON) && crucible.insert(ForgeMaterial.IRON);
		boolean mixed = crucible.insert(ForgeMaterial.GOLD);
		unheated.insert(ForgeMaterial.IRON);
		ctx.waitAndRun(3 * com.evolvedwarfare.smithing.CrucibleBlockEntity.MELT_TICKS + 10, () -> {
			float molten = crucible.contents().molten();
			int coldSolid = unheated.contents().solid();
			var mold = (com.evolvedwarfare.smithing.MoldBlockEntity) world.getBlockEntity(moldPos);
			boolean carved = mold.carve(ForgedPart.PICKAXE_HEAD, new long[] {-1L, 0L, 0L, 0L}, 0.9F);
			boolean goldRefused = mold.accepts(ForgeMaterial.GOLD); // el pico sí admite oro (antes de verter hierro)
			float used = mold.pour(ForgeMaterial.IRON, molten);
			LOG.info("[EW-GAMETEST] fundición: fundido {} (sin fuego, sin fundir {}), mezcla de metales {}, tallado {}, vertido {}", molten, coldSolid,
					mixed, carved, used);
			check(in && !mixed && Math.abs(molten - 3.0F) < 0.01F && coldSolid == 1, "El crisol no funde bien");
			check(carved && goldRefused && Math.abs(mold.fill() - 3.0F) < 0.01F, "El molde no se llena");
			ctx.waitAndRun(com.evolvedwarfare.smithing.MoldBlockEntity.SET_TICKS + com.evolvedwarfare.smithing.MoldBlockEntity.COOL_TICKS + 10, () -> {
				int stage = mold.stage();
				ItemStack a = mold.crack();
				ItemStack b = mold.crack();
				ItemStack piece = mold.crack();
				Float cast = piece == null ? null : piece.get(ModComponents.CAST_SCORE);
				int quality = cast == null ? -1 : Smithing.finalQuality(cast, 1.0F);
				ForgedToolRecipe recipe = new ForgedToolRecipe(CraftingRecipeCategory.EQUIPMENT);
				boolean roughCrafts = piece != null && recipe.matches(grid(piece, new ItemStack(Items.STICK), new ItemStack(Items.STICK)), world);
				LOG.info("[EW-GAMETEST] fundición: etapa {}, pieza {} (nota {}, con afilado perfecto calidad {}), en bruto se monta {}", stage,
						piece == null ? "ninguna" : piece.getName().getString(), cast, quality, roughCrafts);
				check(stage == com.evolvedwarfare.smithing.MoldBlockEntity.SOLID && a == null && b == null && piece != null, "El molde no suelta la pieza");
				check(cast != null && cast > 0.9F && quality == 4 && !roughCrafts, "La pieza en bruto no va bien");
				ctx.complete();
			});
		});
	}

	/** El molde de arcilla: frasco de agua + arcilla + palo, y se queda el frasco vacío. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void clayMoldRecipe(TestContext ctx) {
		var world = ctx.getWorld();
		ItemStack water = net.minecraft.component.type.PotionContentsComponent.createStack(Items.POTION, net.minecraft.potion.Potions.WATER);
		ItemStack awkward = net.minecraft.component.type.PotionContentsComponent.createStack(Items.POTION, net.minecraft.potion.Potions.AWKWARD);
		var recipe = new com.evolvedwarfare.smithing.ClayMoldRecipe(CraftingRecipeCategory.MISC);
		var input = grid(water, new ItemStack(Items.CLAY_BALL), new ItemStack(Items.STICK));
		boolean ok = recipe.matches(input, world);
		boolean wrong = recipe.matches(grid(awkward, new ItemStack(Items.CLAY_BALL), new ItemStack(Items.STICK)), world);
		boolean bottle = recipe.getRemainder(input).get(0).isOf(Items.GLASS_BOTTLE);
		boolean result = recipe.craft(input, world.getRegistryManager()).isOf(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD.asItem());
		LOG.info("[EW-GAMETEST] molde: receta {}, con otra poción {}, queda el frasco {}, sale el molde {}", ok, wrong, bottle, result);
		check(ok && !wrong && bottle && result, "La receta del molde no va");
		ctx.complete();
	}

	/** Lo bien vertido: lo justo, perfecto; de menos o de más, peor; derramar, aún peor. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pourScores(TestContext ctx) {
		float exact = com.evolvedwarfare.smithing.MoldBlockEntity.pourScore(3.0F, 3.0F, false);
		float little = com.evolvedwarfare.smithing.MoldBlockEntity.pourScore(2.4F, 3.0F, false);
		float much = com.evolvedwarfare.smithing.MoldBlockEntity.pourScore(3.3F, 3.0F, false);
		float spilled = com.evolvedwarfare.smithing.MoldBlockEntity.pourScore(3.3F, 3.0F, true);
		LOG.info("[EW-GAMETEST] vertido: justo {}, de menos {}, de más {}, derramado {}", exact, little, much, spilled);
		check(exact == 1.0F && little < 0.9F && much < 0.9F && spilled < much, "Las notas del vertido no van");
		ctx.complete();
	}
}
