package com.evolvedwarfare.train;

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

/** La caldera de la locomotora: tres huecos de combustible, cuánto le queda, la velocidad y el regulador (con sus botones). */
public class LocomotiveScreenHandler extends ScreenHandler {
	private final Inventory fuel;
	private final PropertyDelegate properties;
	@Nullable
	private final LocomotiveEntity loco;

	public LocomotiveScreenHandler(int syncId, PlayerInventory player) {
		this(syncId, player, new SimpleInventory(3), new ArrayPropertyDelegate(4), null);
	}

	public LocomotiveScreenHandler(int syncId, PlayerInventory player, Inventory fuel, PropertyDelegate properties, @Nullable LocomotiveEntity loco) {
		super(Trains.LOCOMOTIVE_SCREEN, syncId);
		this.fuel = fuel;
		this.properties = properties;
		this.loco = loco;
		fuel.onOpen(player.player);
		for (int i = 0; i < 3; i++) {
			addSlot(new Slot(fuel, i, 44 + i * 18, 53) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return LocomotiveEntity.fuelTime(stack) > 0;
				}
			});
		}
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(player, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
			}
		}
		for (int c = 0; c < 9; c++) {
			addSlot(new Slot(player, c, 8 + c * 18, 142));
		}
		addProperties(properties);
	}

	public int fuel() {
		return properties.get(0);
	}

	public int maxFuel() {
		return Math.max(1, properties.get(1));
	}

	public int throttle() {
		return properties.get(2) - 10;
	}

	/** km/h (con un decimal: el valor es por 10). */
	public int speedTenths() {
		return properties.get(3);
	}

	/** Botones: 0 baja el regulador, 1 lo sube, 2 lo cierra. */
	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (loco == null) {
			return false;
		}
		switch (id) {
			case 0 -> loco.setThrottle(loco.throttle() - 1);
			case 1 -> loco.setThrottle(loco.throttle() + 1);
			case 2 -> loco.setThrottle(0);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack copy = stack.copy();
		if (index < 3) {
			if (!insertItem(stack, 3, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (LocomotiveEntity.fuelTime(stack) <= 0 || !insertItem(stack, 0, 3, false)) {
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
		return fuel.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		fuel.onClose(player);
	}
}
