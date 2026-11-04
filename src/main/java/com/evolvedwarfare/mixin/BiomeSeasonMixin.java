package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Climate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Las estaciones en los biomas: la temperatura (la que decide si llueve o nieva y si el agua se hiela) sube en verano y
 * baja en invierno (los colores de la hierba y las hojas, en el cliente: BiomeColorMixin).
 */
@Mixin(Biome.class)
public abstract class BiomeSeasonMixin {
	@Inject(method = "getTemperature(Lnet/minecraft/util/math/BlockPos;)F", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$season(BlockPos pos, CallbackInfoReturnable<Float> cir) {
		float offset = Climate.biomeOffset;
		if (offset != 0.0F) {
			cir.setReturnValue(cir.getReturnValueF() + offset);
		}
	}
}
