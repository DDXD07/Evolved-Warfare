package com.evolvedwarfare.mixin;

import com.evolvedwarfare.inventory.StackLimit;
import org.spongepowered.asm.mixin.Mixin;

/** Las vagonetas con cofre o tolva también aceptan pilas tan grandes como el límite de la configuración. */
@Mixin(net.minecraft.entity.vehicle.StorageMinecartEntity.class)
public abstract class StorageMinecartLimitMixin implements net.minecraft.inventory.Inventory {
	@Override
	public int getMaxCountPerStack() {
		return StackLimit.inventory(99);
	}
}
