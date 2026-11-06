package com.evolvedwarfare.mixin;

import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** En supervivencia, los huecos del inventario del jugador del 5 al 35 están cerrados (no se ven ni se puede meter nada) en todas las pantallas. */
@Mixin(Slot.class)
public abstract class SlotLockMixin {
	@Shadow
	@Final
	public Inventory inventory;

	@Shadow
	public abstract int getIndex();

	private boolean evolvedwarfare$locked() {
		if (inventory instanceof PlayerInventory pi) {
			int i = getIndex();
			return i >= Backpacks.HOTBAR && i < pi.main.size() && Backpacks.limited(pi.player);
		}
		return false;
	}

	@Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$hide(CallbackInfoReturnable<Boolean> cir) {
		if (evolvedwarfare$locked()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$closed(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (evolvedwarfare$locked()) {
			cir.setReturnValue(false);
		} else if (inventory instanceof PlayerInventory pi && getIndex() < Backpacks.HOTBAR && Backpacks.limited(pi.player)
				&& !com.evolvedwarfare.backpack.Loadout.accepts(getIndex(), stack)) {
			// la carga: arma en el 1, herramientas en el 2 y el 3, lo demás en el 4 y el 5
			cir.setReturnValue(false);
		}
	}
}
