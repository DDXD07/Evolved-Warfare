package com.evolvedwarfare.gametest;

import com.evolvedwarfare.food.FoodItems;
import com.evolvedwarfare.food.FoodRot;
import com.evolvedwarfare.food.IceboxBlockEntity;
import com.evolvedwarfare.food.SaltingRecipe;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La comida que se pudre (fase 98): caducidad, se pasa y se pudre, salar, ahumar y la nevera. */
public class FoodRotGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "foodrot")
	public void foodGoesStaleAndRots(TestContext ctx) {
		long now = ctx.getWorld().getTime();
		check(FoodRot.baseShelf(new ItemStack(Items.BEEF)) < FoodRot.baseShelf(new ItemStack(Items.COOKED_BEEF)), "La carne cruda aguanta como la cocinada");
		check(!FoodRot.perishable(new ItemStack(Items.GOLDEN_CARROT)) && !FoodRot.perishable(new ItemStack(Items.STONE)), "Se pudre lo que no debe");
		ItemStack bread = new ItemStack(Items.BREAD, 5);
		check(FoodRot.check(bread, now) == null && FoodRot.expires(bread) != null, "No se le pone la caducidad");
		ItemStack other = new ItemStack(Items.BREAD, 3);
		FoodRot.check(other, now);
		check(ItemStack.areItemsAndComponentsEqual(bread, other), "Dos panes de la misma hora no se apilan");
		check(FoodRot.stage(bread, now) == FoodRot.Stage.FRESH, "El pan nuevo no está fresco");
		long shelf = FoodRot.shelf(bread);
		FoodRot.Stage stale = FoodRot.stage(bread, FoodRot.expires(bread) - shelf / 3);
		FoodRot.Stage spoiling = FoodRot.stage(bread, FoodRot.expires(bread) - shelf / 10);
		ItemStack rotten = FoodRot.check(bread, FoodRot.expires(bread) + 1);
		ItemStack rottenBeef = FoodRot.rotten(new ItemStack(Items.BEEF, 2));
		LOG.info("[EW-GAMETEST] pudrirse: pan aguanta {} ticks, a 1/3 {}, a 1/10 {}, al final {}, carne {}", shelf, stale, spoiling, rotten, rottenBeef);
		check(stale == FoodRot.Stage.STALE && spoiling == FoodRot.Stage.SPOILING, "No se pasa por las etapas");
		check(rotten != null && rotten.isOf(FoodItems.ROTTEN_FOOD) && rotten.getCount() == 5, "El pan caducado no se pudre");
		check(rottenBeef.isOf(Items.ROTTEN_FLESH), "La carne no se pudre en carne podrida");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "foodrot")
	public void saltingPreserves(TestContext ctx) {
		long now = ctx.getWorld().getTime();
		ItemStack beef = new ItemStack(Items.BEEF);
		FoodRot.check(beef, now);
		SaltingRecipe recipe = new SaltingRecipe(CraftingRecipeCategory.MISC);
		CraftingRecipeInput input = CraftingRecipeInput.create(2, 2,
				List.of(beef.copy(), new ItemStack(FoodItems.SALT), beef.copy(), ItemStack.EMPTY));
		check(recipe.matches(input, ctx.getWorld()), "La sal y la carne no casan");
		ItemStack salted = recipe.craft(input, ctx.getWorld().getRegistryManager());
		LOG.info("[EW-GAMETEST] salar: {} carne, caduca {} -> {}", salted.getCount(), FoodRot.expires(beef), FoodRot.expires(salted));
		check(salted.getCount() == 2 && (FoodRot.preserved(salted) & FoodRot.SALTED) != 0, "No sale salada");
		check(FoodRot.expires(salted) - now > (FoodRot.expires(beef) - now) * 3, "La carne salada no aguanta mucho más");
		CraftingRecipeInput twice = CraftingRecipeInput.create(2, 1, List.of(salted.copyWithCount(1), new ItemStack(FoodItems.SALT)));
		check(!recipe.matches(twice, ctx.getWorld()), "Se sala dos veces");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "foodrot")
	public void iceboxSlowsRot(TestContext ctx) {
		long now = ctx.getWorld().getTime();
		ItemStack fish = new ItemStack(Items.COD, 4);
		FoodRot.check(fish, now);
		long before = FoodRot.expires(fish);
		DefaultedList<ItemStack> items = DefaultedList.ofSize(3, ItemStack.EMPTY);
		items.set(0, fish);
		items.set(1, new ItemStack(Items.STONE));
		IceboxBlockEntity.chill(items, 100 * IceboxBlockEntity.SAVED_PER_SECOND);
		long after = FoodRot.expires(fish);
		ctx.setBlockState(1, 1, 1, FoodItems.ICEBOX);
		check(ctx.getBlockEntity(new BlockPos(1, 1, 1)) instanceof IceboxBlockEntity, "La nevera no tiene inventario");
		LOG.info("[EW-GAMETEST] nevera: caduca {} -> {} en 100 s", before, after);
		check(after - before == 1800, "La nevera no retrasa la caducidad");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "foodrot", tickLimit = 400)
	public void smokerSmokesFood(TestContext ctx) {
		BlockPos at = new BlockPos(2, 1, 2);
		ctx.setBlockState(at, Blocks.SMOKER);
		SmokerBlockEntity smoker = (SmokerBlockEntity) ctx.getBlockEntity(at);
		smoker.setStack(0, new ItemStack(Items.BEEF, 2));
		smoker.setStack(1, new ItemStack(Items.COAL, 1));
		ctx.waitAndRun(260, () -> {
			ItemStack out = smoker.getStack(2);
			LOG.info("[EW-GAMETEST] ahumador: sale {} ahumada {} caduca {}", out, FoodRot.preserved(out), FoodRot.expires(out));
			check(out.isOf(Items.COOKED_BEEF) && out.getCount() == 2, "El ahumador no cocina las dos (se atasca la salida): " + out);
			check((FoodRot.preserved(out) & FoodRot.SMOKED) != 0, "No sale ahumada");
			ctx.complete();
		});
	}
}
