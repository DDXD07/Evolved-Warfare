package com.evolvedwarfare.world.tower;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * La torre de vigilancia: cada una sale de su propia semilla ({@link TowerDesigner}), así que no hay dos iguales. Busca
 * un sitio seco y no muy empinado en el centro del chunk, elige los materiales por el bioma (y si nieva) y la orienta
 * al azar. La coloca {@link WatchtowerPiece}.
 */
public class WatchtowerStructure extends Structure {
	public static final MapCodec<WatchtowerStructure> CODEC = createCodec(WatchtowerStructure::new);
	/** Desnivel máximo entre las esquinas del patio. */
	private static final int MAX_SLOPE = 7;

	public WatchtowerStructure(Config config) {
		super(config);
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		try {
			ChunkPos chunk = context.chunkPos();
			int x = chunk.getCenterX();
			int z = chunk.getCenterZ();
			int[] corners = getCornerHeights(context, x - 8, 17, z - 8, 17);
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (int h : corners) {
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
			if (max - min > MAX_SLOPE) {
				return Optional.empty();
			}
			int y = context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
			VerticalBlockSample column = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
			if (!column.getState(y).getFluidState().isEmpty() || !column.getState(y + 1).getFluidState().isEmpty()) {
				return Optional.empty();
			}
			RegistryEntry<Biome> biome = context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y),
					BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler());
			TowerDesigner.Style style = styleFor(biome);
			boolean snowy = biome.value().getTemperature() < 0.15F;
			long seed = context.random().nextLong();
			BlockRotation rotation = BlockRotation.random(context.random());
			BlockPos origin = new BlockPos(x, y, z);
			return Optional.of(new StructurePosition(origin,
					collector -> collector.addPiece(new WatchtowerPiece(origin, seed, style, snowy, rotation))));
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo preparar una torre de vigilancia", e);
			return Optional.empty();
		}
	}

	/** Materiales según el bioma: sabana, taiga (y nieve), montaña o costa rocosa, y el resto. */
	public static TowerDesigner.Style styleFor(RegistryEntry<Biome> biome) {
		if (biome.matchesKey(BiomeKeys.SAVANNA) || biome.matchesKey(BiomeKeys.SAVANNA_PLATEAU) || biome.matchesKey(BiomeKeys.WINDSWEPT_SAVANNA)) {
			return TowerDesigner.Style.SAVANNA;
		}
		if (biome.matchesKey(BiomeKeys.TAIGA) || biome.matchesKey(BiomeKeys.SNOWY_TAIGA) || biome.matchesKey(BiomeKeys.SNOWY_PLAINS)
				|| biome.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA) || biome.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA)) {
			return TowerDesigner.Style.BOREAL;
		}
		if (biome.matchesKey(BiomeKeys.WINDSWEPT_HILLS) || biome.matchesKey(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)
				|| biome.matchesKey(BiomeKeys.WINDSWEPT_FOREST) || biome.matchesKey(BiomeKeys.STONY_SHORE)) {
			return TowerDesigner.Style.ROCKY;
		}
		return TowerDesigner.Style.TEMPERATE;
	}

	@Override
	public StructureType<?> getType() {
		return ModStructures.WATCHTOWER;
	}
}
