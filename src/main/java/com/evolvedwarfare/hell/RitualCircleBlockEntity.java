package com.evolvedwarfare.hell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** Las ofrendas puestas sobre un círculo de ritual (hasta 7, una de cada). */
public class RitualCircleBlockEntity extends BlockEntity {
	public static final int SLOTS = 7;
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);

	public RitualCircleBlockEntity(BlockPos pos, BlockState state) {
		super(Apocalypse.RITUAL_CIRCLE_ENTITY, pos, state);
	}

	public DefaultedList<ItemStack> items() {
		return items;
	}

	public List<ItemStack> offerings() {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	public int count() {
		return offerings().size();
	}

	/** Pone una (copia de una unidad); devuelve si cupo. */
	public boolean add(ItemStack stack) {
		for (int i = 0; i < SLOTS; i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack.copyWithCount(1));
				changed();
				return true;
			}
		}
		return false;
	}

	/** Quita la última. */
	public ItemStack pop() {
		for (int i = SLOTS - 1; i >= 0; i--) {
			if (!items.get(i).isEmpty()) {
				ItemStack s = items.get(i);
				items.set(i, ItemStack.EMPTY);
				changed();
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	public void clear() {
		for (int i = 0; i < SLOTS; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		changed();
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
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		for (int i = 0; i < SLOTS; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		Inventories.readNbt(nbt, items, registries);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}
}
