package com.evolvedwarfare.mixin;

import com.evolvedwarfare.inventory.StackLimit;
import org.spongepowered.asm.mixin.Mixin;

/** Acepta pilas tan grandes como el límite de la configuración (de normal, 99 como mucho). */
@Mixin(net.minecraft.entity.player.PlayerInventory.class)
public abstract class PlayerInventoryLimitMixin {
	public int getMaxCountPerStack() {
		return StackLimit.inventory(99);
	}
}
