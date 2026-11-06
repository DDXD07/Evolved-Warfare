package com.evolvedwarfare.mixin;

import com.evolvedwarfare.backpack.BackpackInventory;
import com.evolvedwarfare.backpack.BackpackItem;
import com.evolvedwarfare.backpack.BackpackSlots;
import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * El inventario del jugador (fase 38): la barra de 5 baja debajo del personaje, aparece el hueco de la espalda y debajo los
 * huecos de la mochila (los que tenga la puesta). Los 27 de arriba y los 4 últimos de la barra se cierran (SlotLockMixin).
 * Mayúsculas + clic reparte entre barra, mochila, armadura y espalda.
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerBackpackMixin {
	/** Índices: 36-40 barra, 45 mano izquierda, 46 espalda, 47-100 mochila. */
	private static final int HOTBAR0 = 36, BACK = 46, PACK0 = 47;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void evolvedwarfare$backpackSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
		ScreenHandler self = (ScreenHandler) (Object) this;
		for (int i = 0; i < Backpacks.HOTBAR; i++) {
			Slot moved = new Slot(inventory, i, 44 + i * 18, Backpacks.HOTBAR_Y);
			moved.id = HOTBAR0 + i;
			self.slots.set(HOTBAR0 + i, moved);
		}
		ScreenHandlerInvoker inv = (ScreenHandlerInvoker) self;
		inv.evolvedwarfare$addSlot(new BackpackSlots.Back(owner, 77, 44));
		BackpackInventory pack = Backpacks.inventory(owner);
		for (int i = 0; i < BackpackInventory.MAX; i++) {
			inv.evolvedwarfare$addSlot(new BackpackSlots.Pack(pack, i, 8 + (i % 9) * 18, Backpacks.PACK_Y + (i / 9) * 18, owner, -1));
		}
	}

	@Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$quickMove(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!Backpacks.limited(player)) {
			return;
		}
		ScreenHandler self = (ScreenHandler) (Object) this;
		ScreenHandlerInvoker inv = (ScreenHandlerInvoker) self;
		Slot slot = self.slots.get(index);
		if (!slot.hasStack()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack stack = slot.getStack();
		ItemStack copy = stack.copy();
		int packEnd = PACK0 + Backpacks.size(player);
		int hotbarEnd = HOTBAR0 + Backpacks.HOTBAR;
		boolean ok;
		if (index == 0) {
			ok = inv.evolvedwarfare$insertItem(stack, HOTBAR0, hotbarEnd, true) | inv.evolvedwarfare$insertItem(stack, PACK0, packEnd, false);
			if (ok) {
				slot.onQuickTransfer(stack, copy);
			}
		} else if (index >= HOTBAR0 && index < hotbarEnd) {
			ok = false;
			if (stack.getItem() instanceof BackpackItem) {
				ok = inv.evolvedwarfare$insertItem(stack, BACK, BACK + 1, false);
			} else {
				EquipmentSlot eq = player.getPreferredEquipmentSlot(stack);
				if (eq.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
					int armor = 8 - eq.getEntitySlotId();
					ok = inv.evolvedwarfare$insertItem(stack, armor, armor + 1, false);
				}
			}
			if (!ok) {
				ok = inv.evolvedwarfare$insertItem(stack, PACK0, packEnd, false);
			}
		} else if (index >= PACK0) {
			ok = inv.evolvedwarfare$insertItem(stack, HOTBAR0, hotbarEnd, false);
		} else {
			// la mesa, la armadura, la mano izquierda, la espalda o lo que quede en los huecos cerrados
			ok = inv.evolvedwarfare$insertItem(stack, HOTBAR0, hotbarEnd, false) || inv.evolvedwarfare$insertItem(stack, PACK0, packEnd, false);
		}
		if (!ok && stack.getCount() == copy.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY, copy);
		} else {
			slot.markDirty();
		}
		if (stack.getCount() == copy.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		slot.onTakeItem(player, stack);
		if (index == 0) {
			player.dropItem(stack, false);
		}
		cir.setReturnValue(copy);
	}
}
