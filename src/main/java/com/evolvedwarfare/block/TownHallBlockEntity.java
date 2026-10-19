package com.evolvedwarfare.block;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Ayuntamiento: un almacén de paga. Cada 5 segundos rellena las provisiones de los NPCs de su dueño que estén a
 * menos de {@value #RANGE} bloques, hasta {@value #TARGET_DAYS} días, con la comida o las monedas que tenga dentro.
 */
public class TownHallBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	public static final int SIZE = 27;
	public static final int RANGE = 48;
	public static final int TARGET_DAYS = 3;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
	@Nullable
	private UUID owner;

	public TownHallBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TOWN_HALL_ENTITY, pos, state);
	}

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		markDirty();
	}

	public boolean canAccess(PlayerEntity player) {
		return owner == null || owner.equals(player.getUuid()) || player.isCreative();
	}

	public static void tick(World world, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
		if (world.isClient || world.getTime() % 100L != 0L || hall.owner == null || hall.isEmpty()) {
			return;
		}
		UUID owner = hall.owner;
		List<AbstractNpcEntity> npcs = world.getEntitiesByClass(AbstractNpcEntity.class, new Box(pos).expand(RANGE),
				npc -> npc.isAlive() && owner.equals(npc.getOwnerUuid()));
		for (AbstractNpcEntity npc : npcs) {
			hall.supply(npc);
		}
	}

	private void supply(AbstractNpcEntity npc) {
		int missing = TARGET_DAYS - npc.getProvisionDays();
		for (int i = 0; i < SIZE && missing > 0; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty() || !AbstractNpcEntity.isUpkeepItem(stack)) {
				continue;
			}
			ItemStack offer = stack.copyWithCount(Math.min(missing, stack.getCount()));
			ItemStack rest = npc.getProvisions().addStack(offer);
			int moved = offer.getCount() - rest.getCount();
			if (moved <= 0) {
				continue;
			}
			stack.decrement(moved);
			missing -= moved;
			markDirty();
		}
	}

	// ------------------------------------------------------------------ Inventory

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(items, slot, amount);
		if (!result.isEmpty()) {
			markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack result = Inventories.removeStack(items, slot);
		markDirty();
		return result;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player) && canAccess(player);
	}

	@Override
	public void clear() {
		items.clear();
		markDirty();
	}

	// ------------------------------------------------------------------ menú (el de un cofre)

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}

	// ------------------------------------------------------------------ guardado

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
	}
}
