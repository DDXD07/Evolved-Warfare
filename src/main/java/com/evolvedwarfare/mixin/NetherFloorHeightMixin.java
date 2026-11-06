package com.evolvedwarfare.mixin;

import com.evolvedwarfare.hell.Hell;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Con el techo de roca (fase 37), la "altura del terreno" que usa la generación para poner estructuras en el suelo sería lo
 * alto de la bóveda: en el Nether, es el suelo de la caverna (buscando hacia abajo desde debajo del techo).
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class NetherFloorHeightMixin {
	@Shadow
	@Final
	private RegistryEntry<ChunkGeneratorSettings> settings;

	@Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$caveFloor(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig,
			CallbackInfoReturnable<Integer> cir) {
		if (!settings.matchesKey(ChunkGeneratorSettings.NETHER) || world.getHeight() < 300) {
			return;
		}
		var column = ((NoiseChunkGenerator) (Object) this).getColumnSample(x, z, world, noiseConfig);
		int y = Hell.floorY(column, heightmap.getBlockPredicate(), world.getBottomY(), Hell.CAVE_TOP);
		cir.setReturnValue(y < 0 ? 40 : y);
	}
}
