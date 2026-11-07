package com.evolvedwarfare.ores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Un yacimiento como los de verdad, en vez de menas sueltas: un manto de carbón (capa ancha y fina, como los estratos), una
 * lente de hierro, un cuerpo de cobre diseminado (pórfido: grande y pobre), una veta de oro o de esmeralda (fina y larga,
 * de cuarzo), o una chimenea de kimberlita (un tubo vertical de toba con diamantes dispersos). Encima de algunos, en la
 * superficie, deja guijarros del mineral como pista.
 */
public class DepositFeature extends Feature<DepositFeature.Config> {
	public record Config(List<OreFeatureConfig.Target> targets, String shape, IntProvider size, float density, Optional<BlockState> host,
			Optional<String> indicator) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.list(OreFeatureConfig.Target.CODEC).fieldOf("targets").forGetter(Config::targets),
				Codec.STRING.fieldOf("shape").forGetter(Config::shape),
				IntProvider.createValidatingCodec(1, 16).fieldOf("size").forGetter(Config::size),
				Codec.floatRange(0.0F, 1.0F).fieldOf("density").forGetter(Config::density),
				BlockState.CODEC.optionalFieldOf("host").forGetter(Config::host),
				Codec.STRING.optionalFieldOf("indicator").forGetter(Config::indicator)).apply(i, Config::new));
	}

	/** Lo más lejos (en horizontal) del origen que se toca: la generación solo puede escribir en los chunks de alrededor. */
	private static final int REACH = 14;

	public DepositFeature() {
		super(Config.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		Config c = context.getConfig();
		int size = c.size().get(random);
		int[] placed = {0};
		int top = origin.getY();
		BlockPos.Mutable m = new BlockPos.Mutable();
		switch (c.shape()) {
			case "seam" -> top = ellipsoid(world, random, origin, c, size, 1.0F + random.nextFloat() * 0.8F, size * (0.7F + random.nextFloat() * 0.5F),
					random.nextFloat() * 360.0F, (random.nextFloat() - 0.5F) * 20.0F, placed, m);
			case "lens" -> top = ellipsoid(world, random, origin, c, size, size * 0.4F, size * 0.6F, random.nextFloat() * 360.0F,
					(random.nextFloat() - 0.5F) * 60.0F, placed, m);
			case "disseminated" -> top = ellipsoid(world, random, origin, c, size, size * 0.8F, size, random.nextFloat() * 360.0F, 0.0F, placed, m);
			case "vein" -> top = vein(world, random, origin, c, size, placed, m);
			case "pipe" -> top = pipe(world, random, origin, c, size, placed, m);
			default -> {
				return false;
			}
		}
		if (placed[0] > 0 && c.indicator().isPresent()) {
			indicate(world, random, origin, top, c.indicator().get(), c.shape().equals("pipe"));
		}
		return placed[0] > 0;
	}

	/** Pone mena (si la roca vale) en esa posición, con la densidad del yacimiento. */
	private static void ore(StructureWorldAccess world, Random random, BlockPos pos, Config c, float density, int[] placed) {
		BlockState state = world.getBlockState(pos);
		if (random.nextFloat() >= density) {
			if (c.host().isPresent() && state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
				world.setBlockState(pos, c.host().get(), 2);
			}
			return;
		}
		for (OreFeatureConfig.Target t : c.targets()) {
			if (t.target.test(state, random)) {
				world.setBlockState(pos, t.state, 2);
				placed[0]++;
				return;
			}
		}
		if (c.host().isPresent() && state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
			world.setBlockState(pos, c.host().get(), 2);
		}
	}

	private static boolean inReach(BlockPos origin, int x, int z) {
		return Math.abs(x - origin.getX()) <= REACH && Math.abs(z - origin.getZ()) <= REACH;
	}

	/** Un elipsoide girado (a lo largo, b de alto, c de ancho), con el borde irregular; devuelve su punto más alto. */
	private static int ellipsoid(StructureWorldAccess world, Random random, BlockPos o, Config c, float a, float b, float cc, float yawDeg, float pitchDeg,
			int[] placed, BlockPos.Mutable m) {
		a = Math.min(a, REACH);
		cc = Math.min(cc, REACH);
		float yaw = yawDeg * MathHelper.RADIANS_PER_DEGREE, pitch = pitchDeg * MathHelper.RADIANS_PER_DEGREE;
		float cy = MathHelper.cos(yaw), sy = MathHelper.sin(yaw), cp = MathHelper.cos(pitch), sp = MathHelper.sin(pitch);
		int r = (int) Math.ceil(Math.max(a, Math.max(b, cc)));
		int top = o.getY();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (!inReach(o, o.getX() + dx, o.getZ() + dz)) {
					continue;
				}
				for (int dy = -r; dy <= r; dy++) {
					// a coordenadas del yacimiento
					float lx = dx * cy + dz * sy, lz = -dx * sy + dz * cy;
					float ly = dy * cp - lx * sp;
					lx = lx * cp + dy * sp;
					float d = (lx * lx) / (a * a) + (ly * ly) / (b * b) + (lz * lz) / (cc * cc);
					float edge = 0.75F + random.nextFloat() * 0.35F;
					if (d > edge) {
						continue;
					}
					m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					if (world.isOutOfHeightLimit(m.getY())) {
						continue;
					}
					// más rico en el centro
					float density = c.density() * (1.15F - 0.4F * d);
					ore(world, random, m, c, density, placed);
					top = Math.max(top, m.getY());
				}
			}
		}
		return top;
	}

	/** Una veta: una línea fina que serpentea, casi siempre muy inclinada. */
	private static int vein(StructureWorldAccess world, Random random, BlockPos o, Config c, int size, int[] placed, BlockPos.Mutable m) {
		float yaw = random.nextFloat() * MathHelper.TAU;
		float pitch = (random.nextFloat() - 0.5F) * 2.2F;
		float x = o.getX() + 0.5F, y = o.getY() + 0.5F, z = o.getZ() + 0.5F;
		int length = size * 3;
		float radius = 0.9F + random.nextFloat() * 0.6F;
		int top = o.getY();
		for (int step = 0; step < length * 2; step++) {
			yaw += (random.nextFloat() - 0.5F) * 0.3F;
			pitch = MathHelper.clamp(pitch + (random.nextFloat() - 0.5F) * 0.2F, -1.4F, 1.4F);
			x += MathHelper.cos(pitch) * MathHelper.cos(yaw) * 0.5F;
			z += MathHelper.cos(pitch) * MathHelper.sin(yaw) * 0.5F;
			y += MathHelper.sin(pitch) * 0.5F;
			int r = (int) Math.ceil(radius);
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dy * dy + dz * dz > radius * radius) {
							continue;
						}
						int bx = MathHelper.floor(x) + dx, by = MathHelper.floor(y) + dy, bz = MathHelper.floor(z) + dz;
						if (!inReach(o, bx, bz) || world.isOutOfHeightLimit(by)) {
							continue;
						}
						m.set(bx, by, bz);
						ore(world, random, m, c, c.density(), placed);
						top = Math.max(top, by);
					}
				}
			}
		}
		return top;
	}

	/** Una chimenea de kimberlita: un tubo vertical de toba, más ancho arriba, desde el fondo hasta el origen, con diamantes dispersos. */
	private static int pipe(StructureWorldAccess world, Random random, BlockPos o, Config c, int size, int[] placed, BlockPos.Mutable m) {
		int bottom = world.getBottomY() + 5;
		float lean = random.nextFloat() * MathHelper.TAU, drift = random.nextFloat() * 0.08F;
		for (int y = bottom; y <= o.getY(); y++) {
			float t = (y - bottom) / (float) Math.max(1, o.getY() - bottom);
			float radius = size * (0.55F + 0.45F * t) + (random.nextFloat() - 0.5F) * 0.6F;
			float cx = o.getX() + 0.5F + MathHelper.cos(lean) * drift * (o.getY() - y), cz = o.getZ() + 0.5F + MathHelper.sin(lean) * drift * (o.getY() - y);
			int r = (int) Math.ceil(radius);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					int bx = MathHelper.floor(cx) + dx, bz = MathHelper.floor(cz) + dz;
					if (!inReach(o, bx, bz)) {
						continue;
					}
					m.set(bx, y, bz);
					ore(world, random, m, c, c.density(), placed);
				}
			}
		}
		return o.getY();
	}

	/** Guijarros del mineral en la superficie, encima del yacimiento (si no está muy hondo: las chimeneas, siempre). */
	private static void indicate(StructureWorldAccess world, Random random, BlockPos o, int top, String mineral, boolean always) {
		MineralPebblesBlock.Mineral kind = MineralPebblesBlock.Mineral.byName(mineral);
		if (kind == null) {
			return;
		}
		int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, o.getX(), o.getZ());
		if (!always && surface - top > 48) {
			return;
		}
		int count = 3 + random.nextInt(5);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i < count * 3 && count > 0; i++) {
			int x = o.getX() + random.nextInt(9) - 4, z = o.getZ() + random.nextInt(9) - 4;
			int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
			m.set(x, y, z);
			BlockState below = world.getBlockState(m.down());
			if (world.isAir(m) && below.isSideSolidFullSquare(world, m.down(), net.minecraft.util.math.Direction.UP) && below.getFluidState().isEmpty()) {
				world.setBlockState(m, Ores.PEBBLES.getDefaultState().with(MineralPebblesBlock.MINERAL, kind)
						.with(MineralPebblesBlock.TURN, random.nextInt(4)), 2);
				count--;
			}
		}
	}
}
