package com.evolvedwarfare.world.dungeon;

import com.evolvedwarfare.EvolvedWarfare;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Una mazmorra: elige una de sus plantillas (variantes) y un giro, busca el suelo en el centro del chunk y pone allí la
 * entrada. Las de superficie piden terreno no muy empinado; ninguna sale en el agua.
 */
public class DungeonStructure extends Structure {
	/** Una variante: la plantilla y dónde está la entrada dentro de ella. */
	public record Variant(Identifier id, int x, int y, int z) {
		public static final Codec<Variant> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("id").forGetter(Variant::id),
				Codec.INT.fieldOf("x").forGetter(Variant::x),
				Codec.INT.fieldOf("y").forGetter(Variant::y),
				Codec.INT.fieldOf("z").forGetter(Variant::z)).apply(i, Variant::new));
	}

	public static final MapCodec<DungeonStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			configCodecBuilder(i),
			Variant.CODEC.listOf().fieldOf("templates").forGetter(s -> s.variants),
			Codec.BOOL.fieldOf("underground").forGetter(s -> s.underground),
			Codec.INT.optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
			Codec.BOOL.optionalFieldOf("allow_water", false).forGetter(s -> s.allowWater)).apply(i, DungeonStructure::new));

	private final List<Variant> variants;
	private final boolean underground;
	private final int maxSlope;
	private final boolean allowWater;

	public DungeonStructure(Config config, List<Variant> variants, boolean underground, int maxSlope, boolean allowWater) {
		super(config);
		this.variants = variants;
		this.underground = underground;
		this.maxSlope = maxSlope;
		this.allowWater = allowWater;
	}

	public List<Variant> variants() {
		return variants;
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		try {
			ChunkPos chunk = context.chunkPos();
			int x = chunk.getCenterX();
			int z = chunk.getCenterZ();
			Variant v = variants.get(context.random().nextInt(variants.size()));
			Optional<StructureTemplate> template = context.structureTemplateManager().getTemplate(v.id());
			if (template.isEmpty()) {
				return Optional.empty();
			}
			Vec3i size = template.get().getSize();
			BlockRotation rotation = BlockRotation.random(context.random());
			int ground = height(context, x, z);
			VerticalBlockSample column = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
			if (!allowWater && (!column.getState(ground - 1).getFluidState().isEmpty() || !column.getState(ground).getFluidState().isEmpty())) {
				return Optional.empty();
			}
			if (!underground) {
				// el terreno bajo la planta (girada) no puede ser muy empinado
				int min = ground, max = ground;
				int[][] corners = {{-v.x(), -v.z()}, {size.getX() - 1 - v.x(), -v.z()}, {-v.x(), size.getZ() - 1 - v.z()},
						{size.getX() - 1 - v.x(), size.getZ() - 1 - v.z()}};
				for (int[] c : corners) {
					BlockPos r = new BlockPos(c[0] / 2, 0, c[1] / 2).rotate(rotation);
					int h = height(context, x + r.getX(), z + r.getZ());
					min = Math.min(min, h);
					max = Math.max(max, h);
				}
				if (max - min > maxSlope) {
					return Optional.empty();
				}
			}
			BlockPos corner = new BlockPos(x - v.x(), ground - v.y(), z - v.z());
			if (corner.getY() < context.world().getBottomY() + 4) {
				return Optional.empty();
			}
			BlockPos pivot = new BlockPos(v.x(), v.y(), v.z());
			return Optional.of(new StructurePosition(new BlockPos(x, ground, z),
					collector -> collector.addPiece(new DungeonPiece(context.structureTemplateManager(), v.id(), corner, rotation, pivot))));
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo preparar una mazmorra", e);
			return Optional.empty();
		}
	}

	private static int height(Context context, int x, int z) {
		return context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
	}

	@Override
	public StructureType<?> getType() {
		return Dungeons.STRUCTURE;
	}
}
