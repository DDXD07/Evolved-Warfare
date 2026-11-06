package com.evolvedwarfare.backpack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/** Los huecos nuevos del inventario: el de la espalda y los de la mochila. */
public final class BackpackSlots {
	private BackpackSlots() {
	}

	/** El hueco de la espalda: solo mochilas. */
	public static class Back extends Slot {
		private final PlayerEntity player;

		public Back(PlayerEntity player, int x, int y) {
			super(new BackInventory(player), 0, x, y);
			this.player = player;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return stack.getItem() instanceof BackpackItem;
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}

		@Override
		public boolean isEnabled() {
			return Backpacks.limited(player);
		}
	}

	/** Un hueco de la mochila: solo los que tiene la puesta, y nada de mochilas dentro de mochilas. */
	public static class Pack extends Slot {
		private final PlayerEntity player;
		private final int limit;

		/** limit &lt; 0: los que tenga la mochila puesta; si no, ese número fijo (la del suelo, la pantalla de B). */
		public Pack(Inventory inventory, int index, int x, int y, PlayerEntity player, int limit) {
			super(inventory, index, x, y);
			this.player = player;
			this.limit = limit;
		}

		private int slots() {
			return limit >= 0 ? limit : Backpacks.size(player);
		}

		@Override
		public boolean isEnabled() {
			return getIndex() < slots() && (limit >= 0 || Backpacks.limited(player));
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return isEnabled() && !(stack.getItem() instanceof BackpackItem) && stack.getItem().canBeNested();
		}
	}

	/** El "inventario" de un hueco que es la espalda del jugador. */
	static class BackInventory implements Inventory {
		private final PlayerEntity player;

		BackInventory(PlayerEntity player) {
			this.player = player;
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public boolean isEmpty() {
			return Backpacks.worn(player).isEmpty();
		}

		@Override
		public ItemStack getStack(int slot) {
			Backpacks.inventory(player).flush();
			return Backpacks.worn(player);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			if (amount <= 0) {
				return ItemStack.EMPTY;
			}
			return removeStack(slot);
		}

		@Override
		public ItemStack removeStack(int slot) {
			Backpacks.inventory(player).flush();
			ItemStack s = Backpacks.worn(player);
			Backpacks.setWornRaw(player, ItemStack.EMPTY);
			return s;
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			Backpacks.setWorn(player, stack);
		}

		@Override
		public int getMaxCountPerStack() {
			return 1;
		}

		@Override
		public void markDirty() {
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return true;
		}

		@Override
		public void clear() {
			Backpacks.setWornRaw(player, ItemStack.EMPTY);
		}
	}
}
