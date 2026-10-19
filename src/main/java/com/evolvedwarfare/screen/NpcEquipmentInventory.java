package com.evolvedwarfare.screen;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/** Expone el equipo de un NPC (cabeza, pecho, piernas, pies, mano, segunda mano) como inventario de 6 ranuras. */
public class NpcEquipmentInventory implements Inventory {
	public static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private final LivingEntity entity;

	public NpcEquipmentInventory(LivingEntity entity) {
		this.entity = entity;
	}

	@Override
	public int size() {
		return SLOTS.length;
	}

	@Override
	public boolean isEmpty() {
		for (EquipmentSlot slot : SLOTS) {
			if (!entity.getEquippedStack(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int index) {
		return index >= 0 && index < SLOTS.length ? entity.getEquippedStack(SLOTS[index]) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int index, int amount) {
		ItemStack stack = getStack(index);
		if (stack.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = stack.split(amount);
		setStack(index, stack);
		return taken;
	}

	@Override
	public ItemStack removeStack(int index) {
		ItemStack stack = getStack(index);
		setStack(index, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setStack(int index, ItemStack stack) {
		if (index >= 0 && index < SLOTS.length) {
			entity.equipStack(SLOTS[index], stack);
		}
	}

	@Override
	public void markDirty() {
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return entity.isAlive() && player.squaredDistanceTo(entity) < 64.0;
	}

	@Override
	public void clear() {
		for (int i = 0; i < SLOTS.length; i++) {
			setStack(i, ItemStack.EMPTY);
		}
	}
}
