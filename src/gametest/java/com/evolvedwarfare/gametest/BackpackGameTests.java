package com.evolvedwarfare.gametest;

import com.evolvedwarfare.backpack.BackpackBlock;
import com.evolvedwarfare.backpack.BackpackBlockEntity;
import com.evolvedwarfare.backpack.BackpackItem;
import com.evolvedwarfare.backpack.Backpacks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El inventario nuevo (fase 38): 5 huecos, la espalda y las cinco mochilas. */
public class BackpackGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final Item[] STUFF = {Items.DIRT, Items.STONE, Items.OAK_LOG, Items.SAND, Items.GRAVEL, Items.COBBLESTONE, Items.APPLE, Items.BONE};

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ServerPlayerEntity survivor(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		Backpacks.setWorn(player, ItemStack.EMPTY);
		Vec3d at = ctx.getAbsolute(new Vec3d(1.5, 2, 1.5));
		player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
		return player;
	}

	private static int occupied(ServerPlayerEntity p) {
		int n = 0;
		for (ItemStack s : p.getInventory().main) {
			n += s.isEmpty() ? 0 : 1;
		}
		return n;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void onlyFiveSlotsThenTheBackpack(TestContext ctx) {
		ServerPlayerEntity player = survivor(ctx);
		int took = 0;
		for (Item item : STUFF) {
			took += player.getInventory().insertStack(new ItemStack(item, 3)) ? 1 : 0;
		}
		int without = occupied(player);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		int into = 0;
		for (int i = 0; i < 3; i++) {
			into += player.getInventory().insertStack(new ItemStack(STUFF[5 + i], 2)) ? 1 : 0;
		}
		int used = BackpackItem.used(Backpacks.worn(player));
		LOG.info("[EW-GAMETEST] mochilas: sin mochila {} de {} (huecos {}), con la básica {} a la mochila ({} huecos usados)", took, STUFF.length, without, into,
				used);
		// los bloques solo caben en los huecos 4 y 5 (los otros tres son de la carga)
		check(took == 2 && without == 2 && into == 3 && used == 3, "El inventario no es de 5 o no va a la mochila");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void inventoryScreenSlots(TestContext ctx) {
		ServerPlayerEntity player = survivor(ctx);
		var slots = player.playerScreenHandler.slots;
		Slot locked = slots.get(9), lockedBar = slots.get(42), bar = slots.get(39), back = slots.get(46), pack = slots.get(47), far = slots.get(47 + 12);
		boolean before = pack.isEnabled();
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		LOG.info("[EW-GAMETEST] pantalla: cerrado {} {}, barra en y {}, espalda acepta mochila {} y tierra {}, mochila {}->{}, hueco 13 {}", locked.isEnabled(),
				lockedBar.isEnabled(), bar.y, back.canInsert(new ItemStack(Backpacks.BACKPACKS[1])), back.canInsert(new ItemStack(Items.DIRT)), before,
				pack.isEnabled(), far.isEnabled());
		check(slots.size() == 47 + 54 && !locked.isEnabled() && !locked.canInsert(new ItemStack(Items.DIRT)) && !lockedBar.isEnabled() && bar.y == Backpacks.HOTBAR_Y
				&& back.canInsert(new ItemStack(Backpacks.BACKPACKS[1])) && !back.canInsert(new ItemStack(Items.DIRT)) && !before && pack.isEnabled()
				&& !far.isEnabled() && !pack.canInsert(new ItemStack(Backpacks.BACKPACKS[2])), "Los huecos del inventario no están bien");
		// mayúsculas + clic desde la barra: a la mochila
		player.getInventory().setStack(3, new ItemStack(Items.DIRT, 16));
		player.playerScreenHandler.quickMove(player, 39);
		LOG.info("[EW-GAMETEST] mayúsculas: barra {}, mochila {}", player.getInventory().getStack(3), Backpacks.inventory(player).getStack(0));
		check(player.getInventory().getStack(3).isEmpty() && Backpacks.inventory(player).getStack(0).isOf(Items.DIRT), "Mayúsculas + clic no lleva a la mochila");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tiersAndUpgradeKeepContents(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int[] sizes = new int[5];
		for (int t = 0; t < 5; t++) {
			sizes[t] = BackpackItem.slots(new ItemStack(Backpacks.BACKPACKS[t]));
		}
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		Backpacks.inventory(player).insert(new ItemStack(Items.DIAMOND, 7));
		ItemStack basic = Backpacks.worn(player).copy();
		var input = new SmithingRecipeInput(new ItemStack(Items.LEATHER), basic, new ItemStack(Items.IRON_INGOT));
		var recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMITHING, input, world);
		ItemStack up = recipe.map(r -> r.value().craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
		var contents = up.get(DataComponentTypes.CONTAINER);
		boolean kept = contents != null && contents.streamNonEmpty().anyMatch(s -> s.isOf(Items.DIAMOND) && s.getCount() == 7);
		LOG.info("[EW-GAMETEST] tiers {} {} {} {} {}; mejora a {} conserva {}", sizes[0], sizes[1], sizes[2], sizes[3], sizes[4], up.getItem(), kept);
		check(sizes[0] == 10 && sizes[1] == 18 && sizes[2] == 27 && sizes[3] == 36 && sizes[4] == 54 && up.isOf(Backpacks.BACKPACKS[1]) && kept,
				"Los tiers o la mejora de mochila no van");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void deathDropsTheWholeBackpack(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[2]));
		Backpacks.inventory(player).insert(new ItemStack(Items.GOLD_INGOT, 20));
		ItemStack dropped = Backpacks.dropOnDeath(player);
		var items = world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(4), e -> e.getStack().isOf(Backpacks.BACKPACKS[2]));
		boolean full = !items.isEmpty() && BackpackItem.used(items.get(0).getStack()) == 1;
		LOG.info("[EW-GAMETEST] al morir: cae {} (en el suelo {}, con lo de dentro {}), puesta ahora {}", dropped.getItem(), items.size(), full,
				Backpacks.worn(player).isEmpty());
		check(dropped.isOf(Backpacks.BACKPACKS[2]) && full && Backpacks.worn(player).isEmpty(), "La mochila no cae entera al morir");
		items.forEach(e -> e.discard());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void heavySlotsSlowYouDown(TestContext ctx) {
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		double light = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		// los 8 primeros huecos (ligeros) llenos: no pesa
		for (int i = 0; i < 8; i++) {
			Backpacks.inventory(player).setStack(i, new ItemStack(Items.STONE, 64));
		}
		Backpacks.weigh(player, Backpacks.heavyUsed(player));
		double still = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		// los 2 últimos (carga pesada): cada uno un 4 %
		Backpacks.inventory(player).setStack(8, new ItemStack(Items.STONE, 1));
		Backpacks.inventory(player).setStack(9, new ItemStack(Items.STONE, 1));
		int heavy = Backpacks.heavyUsed(player);
		Backpacks.weigh(player, heavy);
		double slow = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		LOG.info("[EW-GAMETEST] carga pesada: huecos {} {} {} {} {}; velocidad {} -> {} -> {} ({} pesados)", Backpacks.heavySlots(10), Backpacks.heavySlots(18),
				Backpacks.heavySlots(27), Backpacks.heavySlots(36), Backpacks.heavySlots(54), light, still, slow, heavy);
		check(Math.abs(still - light) < 1e-6 && heavy == 2 && Math.abs(slow - light * 0.92) < 1e-4, "Los huecos de carga pesada no frenan");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void loadoutSlots(TestContext ctx) {
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		var inv = player.getInventory();
		inv.insertStack(new ItemStack(Items.IRON_SWORD));
		inv.insertStack(new ItemStack(Items.IRON_PICKAXE));
		inv.insertStack(new ItemStack(Items.IRON_AXE));
		inv.insertStack(new ItemStack(Items.DIRT, 10));
		inv.insertStack(new ItemStack(Items.BREAD, 3));
		inv.insertStack(new ItemStack(Items.DIAMOND_SWORD));
		inv.insertStack(new ItemStack(com.evolvedwarfare.registry.ModItems.MUSKET));
		boolean placed = inv.getStack(0).isOf(Items.IRON_SWORD) && inv.getStack(1).isOf(Items.IRON_PICKAXE) && inv.getStack(2).isOf(Items.IRON_AXE)
				&& inv.getStack(3).isOf(Items.DIRT) && inv.getStack(4).isOf(Items.BREAD);
		boolean spare = Backpacks.inventory(player).getStack(0).isOf(Items.DIAMOND_SWORD) && Backpacks.inventory(player).getStack(1).isOf(
				com.evolvedwarfare.registry.ModItems.MUSKET);
		var slots = player.playerScreenHandler.slots;
		boolean rules = slots.get(36).canInsert(new ItemStack(Items.BOW)) && !slots.get(36).canInsert(new ItemStack(Items.DIRT))
				&& slots.get(37).canInsert(new ItemStack(Items.SHEARS)) && !slots.get(37).canInsert(new ItemStack(Items.IRON_SWORD))
				&& !slots.get(39).canInsert(new ItemStack(Items.IRON_SHOVEL)) && slots.get(40).canInsert(new ItemStack(Items.TORCH));
		// algo fuera de su sitio (un comando, un atajo) se recoloca
		inv.setStack(4, ItemStack.EMPTY);
		inv.setStack(3, new ItemStack(Items.STONE_SHOVEL));
		int fixed = com.evolvedwarfare.backpack.Loadout.fix(player);
		com.evolvedwarfare.backpack.Loadout.show(player);
		var shown = com.evolvedwarfare.backpack.Loadout.shown(player);
		LOG.info("[EW-GAMETEST] la carga: en su sitio {}, de repuesto en la mochila {}, reglas {}, recolocados {}, a la vista {}", placed, spare, rules, fixed,
				shown);
		check(placed && spare && rules && fixed == 1 && inv.getStack(3).isEmpty() && shown.size() == 3 && shown.get(0).isOf(Items.IRON_SWORD),
				"La carga (arma, herramientas, lo demás) no va");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void sortAndClassify(TestContext ctx) {
		var inv = new net.minecraft.inventory.SimpleInventory(9);
		inv.setStack(0, new ItemStack(Items.DIRT, 10));
		inv.setStack(2, new ItemStack(Items.APPLE, 2));
		inv.setStack(3, new ItemStack(Items.DIRT, 60));
		inv.setStack(5, new ItemStack(Items.IRON_SWORD));
		inv.setStack(7, new ItemStack(Items.IRON_CHESTPLATE));
		inv.setStack(8, new ItemStack(Items.IRON_PICKAXE));
		List<Slot> slots = new java.util.ArrayList<>();
		for (int i = 0; i < 9; i++) {
			slots.add(new Slot(inv, i, 0, 0));
		}
		com.evolvedwarfare.backpack.Sorting.sortSlots(slots);
		StringBuilder order = new StringBuilder();
		for (int i = 0; i < 9; i++) {
			order.append(inv.getStack(i).isEmpty() ? "-" : inv.getStack(i).getItem().toString() + "x" + inv.getStack(i).getCount()).append(' ');
		}
		LOG.info("[EW-GAMETEST] ordenar: {}", order);
		check(inv.getStack(0).isOf(Items.IRON_SWORD) && inv.getStack(1).isOf(Items.IRON_PICKAXE) && inv.getStack(2).isOf(Items.IRON_CHESTPLATE)
				&& inv.getStack(3).isOf(Items.APPLE) && inv.getStack(4).isOf(Items.DIRT) && inv.getStack(4).getCount() == 64 && inv.getStack(5).getCount() == 6
				&& inv.getStack(6).isEmpty(), "Ordenar no clasifica bien");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placedBackpackKeepsItsItems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[1]));
		Backpacks.inventory(player).insert(new ItemStack(Items.EMERALD, 5));
		Backpacks.inventory(player).flush();
		ItemStack stack = Backpacks.worn(player).copy();
		boolean placed = BackpackBlock.place(world, pos, stack, Direction.NORTH);
		boolean inside = world.getBlockEntity(pos) instanceof BackpackBlockEntity be && be.getStack(0).isOf(Items.EMERALD) && be.slots() == 18;
		world.breakBlock(pos, false);
		var drops = world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(2), e -> e.getStack().isOf(Backpacks.BACKPACKS[1]));
		boolean back = !drops.isEmpty() && BackpackItem.used(drops.get(0).getStack()) == 1;
		LOG.info("[EW-GAMETEST] en el suelo: puesta {}, dentro {}, al romperla vuelve con lo suyo {}", placed, inside, back);
		check(placed && inside && back, "La mochila en el suelo no guarda lo suyo");
		drops.forEach(e -> e.discard());
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void dyeAndTidy(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack pack = new ItemStack(Backpacks.BACKPACKS[0]);
		var input = CraftingRecipeInput.create(2, 1, List.of(pack, new ItemStack(Items.BLUE_DYE)));
		var dyed = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).map(r -> r.value().craft(input, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
		var color = dyed.get(DataComponentTypes.DYED_COLOR);
		ServerPlayerEntity player = survivor(ctx);
		Backpacks.setWorn(player, new ItemStack(Backpacks.BACKPACKS[0]));
		player.getInventory().main.set(20, new ItemStack(Items.FLINT, 4));
		int moved = Backpacks.tidy(player);
		LOG.info("[EW-GAMETEST] tinte {} (color {}); huecos cerrados recogidos {} -> mochila {}", dyed.getItem(), color, moved,
				Backpacks.inventory(player).getStack(0));
		check(dyed.isOf(Backpacks.BACKPACKS[0]) && color != null && color.rgb() != Backpacks.COLORS[0] && moved == 1 && player.getInventory().main.get(20).isEmpty()
				&& Backpacks.inventory(player).getStack(0).isOf(Items.FLINT), "El tinte o la recogida de los huecos cerrados no van");
		ctx.complete();
	}
}
