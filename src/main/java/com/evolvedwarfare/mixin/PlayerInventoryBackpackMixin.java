package com.evolvedwarfare.mixin;

import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El inventario de 5 (fase 38): en supervivencia lo que recoges solo va a los 5 huecos de la barra (y a la mano izquierda),
 * y si no cabe, a la mochila. La rueda del ratón da la vuelta en 5, y coger un bloque con el botón central no usa los
 * huecos cerrados.
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryBackpackMixin {
	@Shadow
	@Final
	public PlayerEntity player;

	@Shadow
	@Final
	public DefaultedList<ItemStack> main;

	@Shadow
	public int selectedSlot;

	@Shadow
	public abstract ItemStack getStack(int slot);

	@Inject(method = "getEmptySlot", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$emptySlot(CallbackInfoReturnable<Integer> cir) {
		if (Backpacks.limited(player)) {
			for (int i = 0; i < Backpacks.HOTBAR; i++) {
				if (main.get(i).isEmpty()) {
					cir.setReturnValue(i);
					return;
				}
			}
			cir.setReturnValue(-1);
		}
	}

	@Inject(method = "getOccupiedSlotWithRoomForStack", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$roomFor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (!Backpacks.limited(player)) {
			return;
		}
		if (selectedSlot < Backpacks.HOTBAR && evolvedwarfare$room(getStack(selectedSlot), stack)) {
			cir.setReturnValue(selectedSlot);
			return;
		}
		if (evolvedwarfare$room(getStack(40), stack)) {
			cir.setReturnValue(40);
			return;
		}
		for (int i = 0; i < Backpacks.HOTBAR; i++) {
			if (evolvedwarfare$room(main.get(i), stack)) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	private static boolean evolvedwarfare$room(ItemStack existing, ItemStack stack) {
		return !existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.isStackable()
				&& existing.getCount() < existing.getMaxCount();
	}

	/** Lo que no cabe en la barra va a la mochila. */
	@Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$toBackpack(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (slot == -1 && !stack.isEmpty() && Backpacks.limited(player) && Backpacks.insert(player, stack)) {
			cir.setReturnValue(stack.isEmpty() || cir.getReturnValueZ());
		}
	}

	@Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$scroll(double amount, CallbackInfo ci) {
		if (Backpacks.limited(player)) {
			int d = (int) Math.signum(amount);
			selectedSlot = Math.floorMod(selectedSlot - d, Backpacks.HOTBAR);
			ci.cancel();
		}
	}

	@Inject(method = "getSwappableHotbarSlot", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$swappable(CallbackInfoReturnable<Integer> cir) {
		if (!Backpacks.limited(player)) {
			return;
		}
		for (int k = 0; k < Backpacks.HOTBAR; k++) {
			int i = (selectedSlot + k) % Backpacks.HOTBAR;
			if (main.get(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(Math.min(selectedSlot, Backpacks.HOTBAR - 1));
	}
}
