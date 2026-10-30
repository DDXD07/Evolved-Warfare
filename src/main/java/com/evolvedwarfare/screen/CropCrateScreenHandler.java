package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.CropCrateBlockEntity;
import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** El hueco de la Caja de cultivos (solo cultivos, hasta 64) y tu inventario. Mismas medidas que la tolva. */
public class CropCrateScreenHandler extends ScreenHandler {
	public static final int SLOT_X = 80;
	public static final int SLOT_Y = 20;
	public static final int INVENTORY_Y = 51;

	private final Inventory crate;

	/** Cliente. */
	public CropCrateScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleInventory(1) {
			@Override
			public int getMaxCountPerStack() {
				return CropCrateItem.CAPACITY;
			}

			@Override
			public boolean isValid(int slot, ItemStack stack) {
				return CropCrateItem.accepts(stack);
			}
		});
	}

	/** Servidor. */
	public CropCrateScreenHandler(int syncId, PlayerInventory playerInventory, CropCrateBlockEntity crate) {
		this(syncId, playerInventory, (Inventory) crate);
	}

	private CropCrateScreenHandler(int syncId, PlayerInventory playerInventory, Inventory crate) {
		super(ModScreens.CROP_CRATE, syncId);
		this.crate = crate;
		crate.onOpen(playerInventory.player);
		addSlot(new Slot(crate, 0, SLOT_X, SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return crate.isValid(0, stack);
			}

			@Override
			public int getMaxItemCount(ItemStack stack) {
				return CropCrateItem.capacity(stack.getItem());
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
		}
	}

	/** Lo que hay dentro ahora (para el contador de la pantalla). */
	public ItemStack contents() {
		return slots.get(0).getStack();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack before = stack.copy();
		if (index == 0) {
			if (!insertItem(stack, 1, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!crate.isValid(0, stack) || !insertItem(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return before;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return crate.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		crate.onClose(player);
	}
}
