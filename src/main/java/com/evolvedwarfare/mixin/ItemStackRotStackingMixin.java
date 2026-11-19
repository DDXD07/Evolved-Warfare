package com.evolvedwarfare.mixin;

import com.evolvedwarfare.food.FoodRot;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Objects;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * La comida se amontona aunque su contador vaya por otro sitio (fase 121, como en ARK): para juntar montones no cuenta
 * la caducidad (se queda la del montón de destino). Para saber si algo ha cambiado (lo que se manda al cliente) sí
 * cuenta, para que el contador se vea al día.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackRotStackingMixin {
	@ModifyReturnValue(method = "areItemsAndComponentsEqual", at = @At("RETURN"))
	private static boolean evolvedwarfare$stackIgnoringExpiry(boolean equal, ItemStack a, ItemStack b) {
		return equal || FoodRot.sameButExpiry(a, b);
	}

	@ModifyReturnValue(method = "areEqual", at = @At("RETURN"))
	private static boolean evolvedwarfare$changedExpiry(boolean equal, ItemStack a, ItemStack b) {
		return equal && Objects.equals(a.get(FoodRot.EXPIRES), b.get(FoodRot.EXPIRES));
	}
}
