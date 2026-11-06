package com.evolvedwarfare.mixin;

import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En supervivencia, las teclas 6 a 9 sobre un hueco no cambian con los huecos cerrados de la barra. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSwapMixin {
	@Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$noLockedSwap(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		if (actionType == SlotActionType.SWAP && button >= Backpacks.HOTBAR && button < 9 && Backpacks.limited(player)) {
			ci.cancel();
		}
	}
}
