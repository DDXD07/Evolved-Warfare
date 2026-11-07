package com.evolvedwarfare.gametest;

import com.evolvedwarfare.inventory.StackLimit;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El límite por hueco: con 5000, las pilas llegan a 5000, se guardan y se cargan (también si luego se vuelve a 64). */
public class StackLimitGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bigStacksWork(TestContext ctx) {
		int before = StackLimit.get();
		String problem = null;
		try {
			StackLimit.setFromServer(5000);
			ItemStack diamonds = new ItemStack(Items.DIAMOND, 5000);
			int max = diamonds.getMaxCount(), sword = new ItemStack(Items.DIAMOND_SWORD).getMaxCount(), pearls = new ItemStack(Items.ENDER_PEARL).getMaxCount();
			SimpleInventory inv = new SimpleInventory(3);
			ItemStack rest = inv.addStack(new ItemStack(Items.COBBLESTONE, 4000));
			var nbt = diamonds.encode(ctx.getWorld().getRegistryManager());
			StackLimit.setFromServer(64);
			ItemStack loaded = ItemStack.fromNbt(ctx.getWorld().getRegistryManager(), nbt).orElse(ItemStack.EMPTY);
			LOG.info("[EW-GAMETEST] pilas grandes: máximo {}, espada {}, perlas {}, sobra {}, en el hueco {}, guardado {}, cargado con 64: {}", max, sword,
					pearls, rest.getCount(), inv.getStack(0).getCount(), nbt, loaded.getCount());
			if (max != 5000 || sword != 1 || pearls != 5000 || !rest.isEmpty() || inv.getStack(0).getCount() != 4000 || loaded.getCount() != 5000) {
				problem = "máximo " + max + ", espada " + sword + ", perlas " + pearls + ", sobra " + rest.getCount() + ", cargado " + loaded.getCount();
			}
			if (new ItemStack(Items.DIAMOND).getMaxCount() != 64) {
				problem = "con Estándar no vuelve a 64";
			}
		} finally {
			StackLimit.setFromServer(before);
		}
		if (problem != null) {
			throw new GameTestException("Límite por hueco: " + problem);
		}
		ctx.complete();
	}
}
