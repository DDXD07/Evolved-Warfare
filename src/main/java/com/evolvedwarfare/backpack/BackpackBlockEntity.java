package com.evolvedwarfare.backpack;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** Una mochila puesta en el suelo: guarda el objeto (para su tier y su color) y lo de dentro, como un cofre. */
public class BackpackBlockEntity extends BlockEntity implements Inventory {
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(BackpackInventory.MAX, ItemStack.EMPTY);
	private ItemStack backpack = ItemStack.EMPTY;

	public BackpackBlockEntity(BlockPos pos, BlockState state) {
		super(Backpacks.BACKPACK_ENTITY, pos, state);
	}

	/** Mete en el bloque la mochila (y saca lo de dentro a su inventario). */
	public void load(ItemStack stack) {
		backpack = stack.copyWithCount(1);
		ContainerComponent c = backpack.remove(DataComponentTypes.CONTAINER);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		if (c != null) {
			c.copyTo(items);
		}
		changed();
	}

	/** La mochila otra vez como objeto, con todo dentro. */
	public ItemStack toItem() {
		if (backpack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = backpack.copy();
		out.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(items.subList(0, slots())));
		return out;
	}

	public ItemStack backpack() {
		return backpack;
	}

	public int slots() {
		return Math.max(1, BackpackItem.slots(backpack));
	}

	private void changed() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, true, registries);
		if (!backpack.isEmpty()) {
			nbt.put("Backpack", backpack.encode(registries));
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		Inventories.readNbt(nbt, items, registries);
		backpack = nbt.contains("Backpack") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Backpack")) : ItemStack.EMPTY;
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		if (!backpack.isEmpty()) {
			nbt.put("Backpack", backpack.encode(registries));
		}
		return nbt;
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack s = Inventories.splitStack(items, slot, amount);
		markDirty();
		return s;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		items.clear();
	}
}
