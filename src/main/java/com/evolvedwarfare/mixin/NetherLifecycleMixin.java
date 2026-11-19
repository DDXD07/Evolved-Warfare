package com.evolvedwarfare.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sin avisos de "opciones experimentales" (fase 118): el mod cambia el Nether (sus biomas, desde la 0.67.0) y Minecraft
 * marca como experimental cualquier mundo cuyo Nether no sea el de siempre, así que al crear el mundo salía "¡Advertencia!
 * Estas opciones utilizan características experimentales" y al volver a entrar "Los mundos con opciones de generación
 * experimentales no tienen soporte". El Nether del mod es estable: cuenta como el de siempre.
 */
@Mixin(DimensionOptionsRegistryHolder.class)
public abstract class NetherLifecycleMixin {
	@ModifyReturnValue(method = "getLifecycle(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/world/dimension/DimensionOptions;)Lcom/mojang/serialization/Lifecycle;",
			at = @At("RETURN"))
	private static Lifecycle evolvedwarfare$stableNether(Lifecycle lifecycle, RegistryKey<DimensionOptions> key, DimensionOptions options) {
		return DimensionOptions.NETHER.equals(key) ? Lifecycle.stable() : lifecycle;
	}
}
