package com.evolvedwarfare.food;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Lo de dentro de la nevera: 27 huecos; cada segundo, a la comida le corre la caducidad (solo pasa un 10 % del tiempo). */
public class IceboxBlockEntity extends LootableContainerBlockEntity {
	/** Lo que se retrasa la caducidad cada segundo (de 20 ticks, solo pasan 2). */
	public static final long SAVED_PER_SECOND = 18L;
	private DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);

	public IceboxBlockEntity(BlockPos pos, BlockState state) {
		super(FoodItems.ICEBOX_ENTITY, pos, state);
	}

	@Override
	public int size() {
		return 27;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("block.evolvedwarfare.icebox");
	}

	@Override
	protected DefaultedList<ItemStack> getHeldStacks() {
		return items;
	}

	@Override
	protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
		items = inventory;
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		items = DefaultedList.ofSize(size(), ItemStack.EMPTY);
		if (!readLootTable(nbt)) {
			Inventories.readNbt(nbt, items, lookup);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		if (!writeLootTable(nbt)) {
			Inventories.writeNbt(nbt, items, lookup);
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, IceboxBlockEntity box) {
		if (world.getTime() % 20 != 0 || !FoodRot.enabled()) {
			return;
		}
		chill(box.items, SAVED_PER_SECOND);
		box.markDirty();
	}

	/** Le corre la caducidad a toda la comida (lo mismo a todas: lo que se apilaba, se sigue apilando). */
	public static void chill(DefaultedList<ItemStack> items, long ticks) {
		for (ItemStack stack : items) {
			Long exp = FoodRot.expires(stack);
			if (exp != null) {
				stack.set(FoodRot.EXPIRES, exp + ticks);
			}
		}
	}
}
