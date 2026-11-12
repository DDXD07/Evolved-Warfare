package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModStructures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * El terreno de las aldeas vanilla, aplanado a medias (fase 54.2): dentro de la empalizada el suelo se acerca a la altura
 * media de la aldea (queda un 30 % del desnivel, como mucho 4 bloques arriba o abajo) con ondulaciones suaves de un bloque,
 * y en los 8 bloques de fuera se va volviendo al terreno de verdad. No se toca el agua ni lo de debajo de las casas.
 * <p>
 * Funciona en dos partes. Al trazar la aldea se calcula ese terreno y se suben o bajan las casas (y sus uniones) a su nueva
 * altura, antes de que se genere el terreno, para que Minecraft les ajuste el suelo alrededor. Y esta pieza, que va justo
 * detrás del centro de la aldea, recorta y rellena el terreno de cada chunk antes que el resto de piezas (así las calles,
 * que siguen el suelo, ya van sobre el nuevo).
 */
public class VillageFlattenPiece extends StructurePiece {
	/** Cuánto desnivel queda (0 llano, 1 igual que antes) y hasta cuánto. */
	public static final double KEEP = 0.3;
	public static final int MAX_DEVIATION = 4, FADE = 8;
	private static final int SAMPLE = 8, CELL = 2;

	private final int gx0, gz0, cw, ch, h0, seed;
	/** El peso del aplanado (0..16) en casillas de 2 x 2. */
	private final byte[] weights;
	/** Las casas (de 4 en 4: x0, z0, x1, z1): debajo no se toca nada. */
	private final int[] houses;

	private VillageFlattenPiece(BlockBox box, int gx0, int gz0, int cw, int ch, int h0, int seed, byte[] weights, int[] houses) {
		super(ModStructures.VILLAGE_FLATTEN, 0, box);
		this.gx0 = gx0;
		this.gz0 = gz0;
		this.cw = cw;
		this.ch = ch;
		this.h0 = h0;
		this.seed = seed;
		this.weights = weights;
		this.houses = houses;
	}

	public VillageFlattenPiece(NbtCompound nbt) {
		super(ModStructures.VILLAGE_FLATTEN, nbt);
		this.gx0 = nbt.getInt("X");
		this.gz0 = nbt.getInt("Z");
		this.cw = nbt.getInt("W");
		this.ch = nbt.getInt("H");
		this.h0 = nbt.getInt("Y");
		this.seed = nbt.getInt("Seed");
		byte[] wts = nbt.getByteArray("Weights");
		this.weights = wts.length == cw * ch ? wts : new byte[cw * ch];
		this.houses = nbt.getIntArray("Houses");
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putInt("X", gx0);
		nbt.putInt("Z", gz0);
		nbt.putInt("W", cw);
		nbt.putInt("H", ch);
		nbt.putInt("Y", h0);
		nbt.putInt("Seed", seed);
		nbt.putByteArray("Weights", weights);
		nbt.putIntArray("Houses", houses);
	}

	public int baseHeight() {
		return h0;
	}

	// ------------------------------------------------------------------ el plan

	/**
	 * Calcula el aplanado de una aldea: su rejilla (gx0, gz0, w x h) con lo que queda dentro de la empalizada (region), las
	 * casas y el terreno de la generación.
	 */
	public static VillageFlattenPiece plan(VillageExtras.Terrain terrain, int gx0, int gz0, int w, int h, boolean[] region, List<BlockBox> houseBoxes, int seed) {
		// el terreno, a muestras cada 8 bloques (lo justo para la altura media y para no tocar el agua)
		int sw = w / SAMPLE + 2, sh = h / SAMPLE + 2;
		int[] ground = new int[sw * sh];
		boolean[] wet = new boolean[sw * sh];
		List<Integer> inside = new ArrayList<>();
		for (int sz = 0; sz < sh; sz++) {
			for (int sx = 0; sx < sw; sx++) {
				int x = gx0 + Math.min(w - 1, sx * SAMPLE), z = gz0 + Math.min(h - 1, sz * SAMPLE);
				ground[sz * sw + sx] = terrain.ground(x, z);
				wet[sz * sw + sx] = !terrain.dry(x, z);
				int gi = (z - gz0) * w + (x - gx0);
				if (region[gi] && !wet[sz * sw + sx]) {
					inside.add(ground[sz * sw + sx]);
				}
			}
		}
		if (inside.isEmpty()) {
			return null;
		}
		int[] sorted = inside.stream().mapToInt(Integer::intValue).toArray();
		Arrays.sort(sorted);
		int h0 = sorted[sorted.length / 2];
		// el peso: 1 dentro de la empalizada, bajando a 0 en los 8 bloques de fuera; 0 en el agua
		float[] dist = VillageExtras.chamfer(w, h, region);
		int cw = (w + CELL - 1) / CELL, ch = (h + CELL - 1) / CELL;
		byte[] weights = new byte[cw * ch];
		for (int cz = 0; cz < ch; cz++) {
			for (int cx = 0; cx < cw; cx++) {
				int x = Math.min(w - 1, cx * CELL), z = Math.min(h - 1, cz * CELL);
				double wgt = MathHelper.clamp(1.0 - dist[z * w + x] / FADE, 0.0, 1.0);
				int sx = Math.min(sw - 1, Math.round(x / (float) SAMPLE)), sz = Math.min(sh - 1, Math.round(z / (float) SAMPLE));
				if (wet[sz * sw + sx]) {
					wgt = 0;
				}
				weights[cz * cw + cx] = (byte) Math.round(wgt * 16);
			}
		}
		int[] houses = new int[houseBoxes.size() * 4];
		for (int i = 0; i < houseBoxes.size(); i++) {
			BlockBox b = houseBoxes.get(i);
			houses[i * 4] = b.getMinX() - 1;
			houses[i * 4 + 1] = b.getMinZ() - 1;
			houses[i * 4 + 2] = b.getMaxX() + 1;
			houses[i * 4 + 3] = b.getMaxZ() + 1;
		}
		BlockBox box = new BlockBox(gx0, h0 - 40, gz0, gx0 + w - 1, h0 + 40, gz0 + h - 1);
		return new VillageFlattenPiece(box, gx0, gz0, cw, ch, h0, seed, weights, houses);
	}

	/** Cuánto se aplana ahí (0 nada, 1 del todo). */
	public double weight(int x, int z) {
		int cx = Math.floorDiv(x - gx0, CELL), cz = Math.floorDiv(z - gz0, CELL);
		if (cx < 0 || cz < 0 || cx >= cw || cz >= ch) {
			return 0;
		}
		return weights[cz * cw + cx] / 16.0;
	}

	boolean underHouse(int x, int z) {
		for (int i = 0; i + 3 < houses.length; i += 4) {
			if (x >= houses[i] && x <= houses[i + 2] && z >= houses[i + 1] && z <= houses[i + 3]) {
				return true;
			}
		}
		return false;
	}

	/** Las ondulaciones: ruido suave de ±1,4 bloques (cada 9). */
	private double bump(int x, int z) {
		double fx = x / 9.0, fz = z / 9.0;
		int ix = MathHelper.floor(fx), iz = MathHelper.floor(fz);
		double tx = fx - ix, tz = fz - iz;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = corner(ix, iz), b = corner(ix + 1, iz), c = corner(ix, iz + 1), d = corner(ix + 1, iz + 1);
		return MathHelper.lerp(tz, MathHelper.lerp(tx, a, b), MathHelper.lerp(tx, c, d));
	}

	private double corner(int x, int z) {
		long h = it.unimi.dsi.fastutil.HashCommon.mix((long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL ^ seed);
		return ((h >>> 11) / (double) (1L << 53) * 2.0 - 1.0) * 1.4;
	}

	/** La altura del suelo aplanado en (x, z), si la de antes era orig. */
	public int flat(int x, int z, int orig) {
		double wgt = weight(x, z);
		if (wgt <= 0) {
			return orig;
		}
		int dev = MathHelper.clamp((int) Math.round((orig - h0) * KEEP), -MAX_DEVIATION, MAX_DEVIATION);
		double target = h0 + dev + bump(x, z);
		return (int) Math.round(orig + (target - orig) * wgt);
	}

	/** El terreno ya aplanado (para buscar sitio a los edificios y las decoraciones). */
	VillageExtras.Terrain flattened(VillageExtras.Terrain base) {
		return new VillageExtras.Terrain() {
			@Override
			public int ground(int x, int z) {
				return flat(x, z, base.ground(x, z));
			}

			@Override
			public boolean dry(int x, int z) {
				return base.dry(x, z);
			}
		};
	}

	/** Sube o baja las casas (las piezas rígidas) y las uniones de todas a la altura del suelo aplanado. */
	void shift(List<StructurePiece> pieces, VillageExtras.Terrain base) {
		for (StructurePiece piece : pieces) {
			if (!(piece instanceof PoolStructurePiece pool)) {
				continue;
			}
			if (pool.getPoolElement().getProjection() == StructurePool.Projection.RIGID) {
				BlockBox b = piece.getBoundingBox();
				int cx = (b.getMinX() + b.getMaxX()) / 2, cz = (b.getMinZ() + b.getMaxZ()) / 2;
				int o = base.ground(cx, cz);
				int dy = flat(cx, cz, o) - o;
				if (dy != 0) {
					piece.translate(0, dy, 0);
				}
			}
			List<JigsawJunction> junctions = pool.getJunctions();
			for (int i = 0; i < junctions.size(); i++) {
				JigsawJunction j = junctions.get(i);
				int o = base.ground(j.getSourceX(), j.getSourceZ());
				int dy = flat(j.getSourceX(), j.getSourceZ(), o) - o;
				if (dy != 0) {
					junctions.set(i, new JigsawJunction(j.getSourceX(), j.getSourceGroundY() + dy, j.getSourceZ(), j.getDeltaY(), j.getDestProjection()));
				}
			}
		}
	}

	// ------------------------------------------------------------------ al generar cada chunk

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
			ChunkPos chunkPos, BlockPos pivot) {
		try {
			place(world, chunkBox, chunkPos);
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo aplanar el terreno de una aldea", e);
		}
	}

	/** Lo que se pone al rellenar debajo de lo de arriba (tierra bajo la hierba, arenisca bajo la arena...). */
	private static BlockState filler(BlockState top) {
		if (top.isOf(Blocks.SAND)) {
			return Blocks.SANDSTONE.getDefaultState();
		}
		if (top.isOf(Blocks.RED_SAND)) {
			return Blocks.RED_SANDSTONE.getDefaultState();
		}
		if (top.isIn(BlockTags.DIRT) || top.isOf(Blocks.DIRT_PATH)) {
			return Blocks.DIRT.getDefaultState();
		}
		return top;
	}

	private static boolean surface(BlockState s) {
		return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.SAND) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SNOW_BLOCK) || s.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| s.isIn(BlockTags.TERRACOTTA) || s.isOf(Blocks.SANDSTONE) || s.isOf(Blocks.CLAY);
	}

	private void place(StructureWorldAccess world, BlockBox chunkBox, ChunkPos chunkPos) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean changed = false;
		for (int x = chunkBox.getMinX(); x <= chunkBox.getMaxX(); x++) {
			for (int z = chunkBox.getMinZ(); z <= chunkBox.getMaxZ(); z++) {
				if (weight(x, z) <= 0 || underHouse(x, z)) {
					continue;
				}
				int orig = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
				if (world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1 != orig) {
					continue;
				}
				BlockState top = world.getBlockState(pos.set(x, orig, z));
				if (!surface(top)) {
					continue;
				}
				int t = MathHelper.clamp(flat(x, z, orig), orig - 12, orig + 12);
				if (t == orig) {
					continue;
				}
				if (t < orig) {
					for (int y = t + 1; y <= orig; y++) {
						world.setBlockState(pos.set(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
					world.setBlockState(pos.set(x, t, z), top, Block.NOTIFY_LISTENERS);
				} else {
					BlockState fill = filler(top);
					world.setBlockState(pos.set(x, orig, z), fill, Block.NOTIFY_LISTENERS);
					for (int y = orig + 1; y < t; y++) {
						world.setBlockState(pos.set(x, y, z), fill, Block.NOTIFY_LISTENERS);
					}
					world.setBlockState(pos.set(x, t, z), top, Block.NOTIFY_LISTENERS);
				}
				changed = true;
			}
		}
		if (changed) {
			// los mapas de altura de la generación no se ponen al día solos: las calles, que siguen el suelo, los usan
			Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
			Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.WORLD_SURFACE_WG, Heightmap.Type.OCEAN_FLOOR_WG));
		}
	}
}
