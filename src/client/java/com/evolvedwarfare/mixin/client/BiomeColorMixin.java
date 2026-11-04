package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.climate.Climate;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Las estaciones en los colores: hojas naranjas en otoño, hierba y hojas pardas en invierno, más amarillas en verano. */
@Mixin(Biome.class)
public abstract class BiomeColorMixin {
	@Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$foliage(CallbackInfoReturnable<Integer> cir) {
		if (Climate.tintSeason != null) {
			cir.setReturnValue(Climate.tintFoliage(cir.getReturnValueI()));
		}
	}

	@Inject(method = "getGrassColorAt", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$grass(double x, double z, CallbackInfoReturnable<Integer> cir) {
		if (Climate.tintSeason != null) {
			cir.setReturnValue(Climate.tintGrass(cir.getReturnValueI()));
		}
	}
}
