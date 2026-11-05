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
}
