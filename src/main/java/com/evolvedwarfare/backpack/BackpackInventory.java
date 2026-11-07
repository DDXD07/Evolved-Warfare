package com.evolvedwarfare.backpack;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Lo que lleva dentro la mochila que el jugador lleva a la espalda, como inventario (para los huecos del inventario y la
 * pantalla de la mochila). Guarda una copia viva y la devuelve al objeto (a su componente) cada vez que cambia.
 */
public class BackpackInventory implements Inventory {
	@Override
	public int getMaxCountPerStack() {
		return com.evolvedwarfare.inventory.StackLimit.inventory(99);
	}

	public static final int MAX = 54;
	private final PlayerEntity player;
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(MAX, ItemStack.EMPTY);
	private ItemStack loaded = ItemStack.EMPTY;

	public BackpackInventory(PlayerEntity player) {
		this.player = player;
	}

	/** Si ha cambiado la mochila que lleva puesta, carga lo suyo. */
	private void sync() {
		ItemStack worn = Backpacks.worn(player);
		if (worn != loaded) {
			loaded = worn;
			for (int i = 0; i < MAX; i++) {
				items.set(i, ItemStack.EMPTY);
			}
			ContainerComponent c = worn.get(DataComponentTypes.CONTAINER);
			if (c != null) {
				c.copyTo(items);
			}
		}
	}

	/** Cuántos huecos tiene la mochila puesta (0 sin mochila). */
	public int slots() {
		return BackpackItem.slots(Backpacks.worn(player));
	}

	/** Devuelve lo de dentro al objeto (si ha cambiado). */
	public void flush() {
		sync();
		ItemStack worn = loaded;
		if (worn.isEmpty()) {
			return;
		}
		ContainerComponent now = ContainerComponent.fromStacks(items.subList(0, Math.min(MAX, Math.max(1, slots()))));
		if (!now.equals(worn.get(DataComponentTypes.CONTAINER))) {
			ItemStack copy = worn.copy();
			copy.set(DataComponentTypes.CONTAINER, now);
			loaded = copy;
			Backpacks.setWornRaw(player, copy);
		}
	}

	@Override
	public int size() {
		return MAX;
	}

	@Override
	public boolean isEmpty() {
		sync();
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		sync();
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		sync();
		ItemStack s = items.get(slot).split(amount);
		markDirty();
		return s;
	}

	@Override
	public ItemStack removeStack(int slot) {
		sync();
		ItemStack s = items.get(slot);
		items.set(slot, ItemStack.EMPTY);
		markDirty();
		return s;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		sync();
		items.set(slot, stack);
		markDirty();
	}

	@Override
	public void markDirty() {
		flush();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return player == this.player && slots() > 0;
	}

	@Override
	public void clear() {
		sync();
		for (int i = 0; i < MAX; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		markDirty();
	}

	/** Mete lo que quepa (en los huecos de la mochila); devuelve si ha entrado algo. */
	public boolean insert(ItemStack stack) {
		sync();
		int n = slots();
		int before = stack.getCount();
		for (int i = 0; i < n && !stack.isEmpty(); i++) {
			ItemStack s = items.get(i);
			if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, stack) && s.getCount() < s.getMaxCount()) {
				int move = Math.min(stack.getCount(), s.getMaxCount() - s.getCount());
				s.increment(move);
				stack.decrement(move);
			}
		}
		for (int i = 0; i < n && !stack.isEmpty(); i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack.split(stack.getCount()));
			}
		}
		if (stack.getCount() != before) {
			markDirty();
			return true;
		}
		return false;
	}

	/** Lo lleno que va (0 a 1), contando cada hueco por lo lleno de su montón. */
	public float fill() {
		sync();
		int n = slots();
		if (n == 0) {
			return 0.0F;
		}
		float f = 0.0F;
		for (int i = 0; i < n; i++) {
			ItemStack s = items.get(i);
			if (!s.isEmpty()) {
				f += s.getCount() / (float) s.getMaxCount();
			}
		}
		return f / n;
	}
}
