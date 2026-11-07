package com.evolvedwarfare.ores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.AbstractConditionalPlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/** Sale siempre en los biomas favoritos del mineral (su geología) y, en los demás, solo a veces ("elsewhere"). */
public class InBiomesPlacement extends AbstractConditionalPlacementModifier {
	public static final MapCodec<InBiomesPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("favored").forGetter(p -> p.favored),
			Codec.floatRange(0.0F, 1.0F).fieldOf("elsewhere").forGetter(p -> p.elsewhere)).apply(i, InBiomesPlacement::new));

	private final RegistryEntryList<Biome> favored;
	private final float elsewhere;

	public InBiomesPlacement(RegistryEntryList<Biome> favored, float elsewhere) {
		this.favored = favored;
		this.elsewhere = elsewhere;
	}

	@Override
	protected boolean shouldPlace(FeaturePlacementContext context, Random random, BlockPos pos) {
		return favored.contains(context.getWorld().getBiome(pos)) || random.nextFloat() < elsewhere;
	}

	@Override
	public PlacementModifierType<?> getType() {
		return Ores.IN_BIOMES;
	}
}
