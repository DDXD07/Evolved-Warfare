package com.evolvedwarfare.world.city;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Ciudad amurallada (medieval, del desierto o del norte): cada una sale de su propia semilla ({@link CityDesigner}), así
 * que no hay dos iguales. Busca un sitio seco y no muy empinado; la coloca {@link CityPiece}.
 */
public class WalledCityStructure extends Structure {
	public static final MapCodec<WalledCityStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			configCodecBuilder(instance),
			Codec.STRING.fieldOf("style").forGetter(s -> s.style.name().toLowerCase(Locale.ROOT))
	).apply(instance, WalledCityStructure::new));

	/** Desnivel máximo dentro de la muralla. */
	private static final int MAX_SLOPE = 14;
	/** Hasta dónde se mira el terreno desde el centro. */
	private static final int REACH = 44;

	private final CityDesigner.Style style;

	public WalledCityStructure(Config config, String style) {
		super(config);
		CityDesigner.Style s;
		try {
			s = CityDesigner.Style.valueOf(style.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			s = CityDesigner.Style.MEDIEVAL;
		}
		this.style = s;
	}

	public CityDesigner.Style style() {
		return style;
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		try {
			ChunkPos chunk = context.chunkPos();
			int x = chunk.getCenterX();
			int z = chunk.getCenterZ();
			int[] heights = new int[25];
			int n = 0;
			for (int dx = -REACH; dx <= REACH; dx += REACH / 2) {
				for (int dz = -REACH; dz <= REACH; dz += REACH / 2) {
					int h = context.chunkGenerator().getHeightOnGround(x + dx, z + dz, Heightmap.Type.WORLD_SURFACE_WG, context.world(),
							context.noiseConfig());
					heights[n++] = h;
					if (Math.abs(dx) != REACH / 2 && Math.abs(dz) != REACH / 2) {
						// el centro y los bordes: nada de agua
						VerticalBlockSample column = context.chunkGenerator().getColumnSample(x + dx, z + dz, context.world(),
								context.noiseConfig());
						if (!column.getState(h).getFluidState().isEmpty() || !column.getState(h + 1).getFluidState().isEmpty()
								|| !column.getState(h - 1).getFluidState().isEmpty()) {
							return Optional.empty();
						}
					}
				}
			}
			int[] sorted = Arrays.copyOf(heights, n);
			Arrays.sort(sorted);
			if (sorted[n - 1] - sorted[0] > MAX_SLOPE) {
				return Optional.empty();
			}
			int y = sorted[n / 2];
			long seed = context.random().nextLong();
			BlockPos origin = new BlockPos(x, y, z);
			CityDesigner.Style cityStyle = style;
			return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new CityPiece(origin, seed, cityStyle))));
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo preparar una ciudad", e);
			return Optional.empty();
		}
	}

	@Override
	public StructureType<?> getType() {
		return ModStructures.WALLED_CITY;
	}
}
