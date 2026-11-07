package com.evolvedwarfare.mixin;

import com.evolvedwarfare.inventory.StackLimit;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lo que se apila cabe hasta el límite elegido en la configuración; y una pila guardada más grande que el límite no se pierde al cargar. */
@Mixin(ItemStack.class)
public abstract class ItemStackLimitMixin {
	@Inject(method = "getMaxCount", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$limit(CallbackInfoReturnable<Integer> cir) {
		int base = cir.getReturnValueI();
		int limit = StackLimit.apply(base);
		if (limit != base) {
			cir.setReturnValue(limit);
		}
	}

	@Redirect(method = "validate", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I"), require = 0)
	private static int evolvedwarfare$keepBig(ItemStack stack) {
		return Integer.MAX_VALUE;
	}
}
