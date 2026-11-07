package com.evolvedwarfare.mixin;

import com.evolvedwarfare.inventory.StackLimit;
import org.spongepowered.asm.mixin.Mixin;

/** Acepta pilas tan grandes como el límite de la configuración (de normal, 99 como mucho). */
@Mixin(net.minecraft.inventory.DoubleInventory.class)
public abstract class DoubleInventoryLimitMixin {
	public int getMaxCountPerStack() {
		return StackLimit.inventory(99);
	}
}
