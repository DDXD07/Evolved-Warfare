package com.evolvedwarfare.backpack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** La pantalla de una mochila (la que llevas, con B, o una puesta en el suelo): sus huecos y debajo tu barra de 5. */
public class BackpackScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final int count;

	public BackpackScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, int slots) {
		super(Backpacks.SCREEN, syncId);
		this.inventory = inventory;
		this.count = Math.max(1, Math.min(BackpackInventory.MAX, slots));
		int rows = rows(this.count);
		for (int i = 0; i < this.count; i++) {
			addSlot(new BackpackSlots.Pack(inventory, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18, playerInventory.player, this.count));
		}
		int y = 18 + rows * 18 + 14;
		for (int i = 0; i < Backpacks.HOTBAR; i++) {
			addSlot(new Slot(playerInventory, i, 44 + i * 18, y));
		}
	}

	public static int rows(int slots) {
		return (slots + 8) / 9;
	}

	public int count() {
		return count;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack copy = stack.copy();
		if (index < this.count) {
			if (!insertItem(stack, this.count, this.count + Backpacks.HOTBAR, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertItem(stack, 0, this.count, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return copy;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return inventory.canPlayerUse(player);
	}
}
