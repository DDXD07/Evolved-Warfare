package com.evolvedwarfare.cart;

import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * La carga de la carreta, vista desde arriba: 9 huecos (3x3, delante arriba) para cofres, barriles, hornos, mesas de
 * crafteo y camas (una cama ocupa su hueco y el de al lado, que queda marcado), y tu inventario debajo.
 */
public class CartScreenHandler extends ScreenHandler {
	public static final int WIDTH = 176;
	public static final int HEIGHT = 200;
	public static final int GRID_X = 52;
	public static final int GRID_Y = 26;
	public static final int CELL = 24;
	public static final int INVENTORY_Y = 118;
	public static final int HOTBAR_Y = 176;

	private final Inventory cells;
	private final PropertyDelegate links;
	@Nullable
	private final CartEntity cart;

	/** Cliente. */
	public CartScreenHandler(int syncId, PlayerInventory playerInventory, Integer entityId) {
		this(syncId, playerInventory, new SimpleInventory(CartEntity.CELLS) {
			@Override
			public int getMaxCountPerStack() {
				return 1;
			}
		}, new ArrayPropertyDelegate(CartEntity.CELLS), null);
	}

	/** Servidor. */
	public CartScreenHandler(int syncId, PlayerInventory playerInventory, CartEntity cart) {
		this(syncId, playerInventory, cart.cells, cart.links, cart);
	}

	private CartScreenHandler(int syncId, PlayerInventory playerInventory, Inventory cells, PropertyDelegate links, @Nullable CartEntity cart) {
		super(ModScreens.CART, syncId);
		this.cells = cells;
		this.links = links;
		this.cart = cart;
		for (int i = 0; i < CartEntity.CELLS; i++) {
			addSlot(new CellSlot(i, GRID_X + (i % 3) * CELL + 4, GRID_Y + (i / 3) * CELL + 4));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
		}
		addProperties(links);
	}

	/** Si en ese hueco están los pies de una cama: el hueco de la cabecera; si no, -1. */
	public int bedHeadOf(int cell) {
		return links.get(cell) - 1;
	}

	private boolean taken(int cell) {
		return !cells.getStack(cell).isEmpty() || links.get(cell) > 0;
	}

	/** Si se puede poner eso en ese hueco (la cama necesita otro libre al lado). */
	public boolean fits(int cell, ItemStack stack) {
		CartModule.Kind kind = CartModule.kindOf(stack);
		return kind != null && !taken(cell) && (kind != CartModule.Kind.BED || CartEntity.bedPartner(cell, i -> i == cell || taken(i)) >= 0);
	}

	private class CellSlot extends Slot {
		CellSlot(int index, int x, int y) {
			super(cells, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return fits(getIndex(), stack);
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}

		@Override
		public int getMaxItemCount(ItemStack stack) {
			return 1;
		}

		/** Los pies de una cama no son un hueco aparte. */
		@Override
		public boolean isEnabled() {
			return links.get(getIndex()) == 0;
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack before = stack.copy();
		if (index < CartEntity.CELLS) {
			if (!insertItem(stack, CartEntity.CELLS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < CartEntity.CELLS && !moved; i++) {
				if (fits(i, stack)) {
					slots.get(i).setStack(stack.split(1));
					moved = true;
				}
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return before.getCount() == stack.getCount() ? ItemStack.EMPTY : before;
	}

	/** Doble clic para juntar: no se lleva lo de la carreta. */
	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != cells && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return cart == null || cells.canPlayerUse(player);
	}
}
