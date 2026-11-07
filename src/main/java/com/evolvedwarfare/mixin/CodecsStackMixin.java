package com.evolvedwarfare.mixin;

import net.minecraft.util.dynamic.Codecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Las pilas de más de 99 se tienen que poder guardar: la cantidad de una pila (y el tamaño máximo de pila) se guardan con
 * un número de 1 a 99; ese rango (y solo ese) se abre a cualquier número.
 */
@Mixin(Codecs.class)
public abstract class CodecsStackMixin {
	@ModifyVariable(method = "rangedInt(II)Lcom/mojang/serialization/Codec;", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private static int evolvedwarfare$bigStacks(int max, int min) {
		return min == 1 && max == 99 ? Integer.MAX_VALUE : max;
	}
}
