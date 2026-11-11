package com.evolvedwarfare.mixin;

import com.evolvedwarfare.inventory.StackLimit;
import org.spongepowered.asm.mixin.Mixin;

/** Acepta pilas tan grandes como el límite de la configuración (de normal, 99 como mucho). */
@Mixin(net.minecraft.entity.player.PlayerInventory.class)
public abstract class PlayerInventoryLimitMixin implements net.minecraft.inventory.Inventory {
	// implements Inventory + @Override: así el nombre se traduce al de Minecraft al compilar (sin eso, en el jar
	// el método se quedaba con su nombre de desarrollo, nadie lo llamaba y los huecos seguían en 99)
	@Override
	public int getMaxCountPerStack() {
		return StackLimit.inventory(99);
	}
}
