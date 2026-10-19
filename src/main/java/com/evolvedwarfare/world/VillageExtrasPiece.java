package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModStructures;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Pieza extra que se añade a cada aldea vanilla: empalizada de troncos con faroles, puertas con postes
 * altos, caminos empedrados hasta las puertas y farolas en las calles. Coloca solo lo que cae dentro del
 * chunk que se está generando, a la altura real del terreno.
 */
public class VillageExtrasPiece extends StructurePiece {
	private static final int MAX_WATER_DEPTH = 6;
	private static final int POST_HEIGHT = 6;

	private final VillageStyle style;
	private final int[] wall;
	private final int[] posts;
	private final int[] road;
	private final int[] lamps;
	private final int[] lanterns;

	public VillageExtrasPiece(VillageStyle style, BlockBox box, int[] wall, int[] posts, int[] road, int[] lamps,
			int[] lanterns) {
		super(ModStructures.VILLAGE_EXTRAS, 0, box);
		this.style = style;
		this.wall = wall;
		this.posts = posts;
		this.road = road;
		this.lamps = lamps;
		this.lanterns = lanterns;
	}

	public VillageExtrasPiece(NbtCompound nbt) {
		super(ModStructures.VILLAGE_EXTRAS, nbt);
		this.style = VillageStyle.byId(nbt.getString("Style"));
		this.wall = nbt.getIntArray("Wall");
		this.posts = nbt.getIntArray("Posts");
		this.road = nbt.getIntArray("Road");
		this.lamps = nbt.getIntArray("Lamps");
		this.lanterns = nbt.getIntArray("Lanterns");
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putString("Style", style.id());
		nbt.putIntArray("Wall", wall);
		nbt.putIntArray("Posts", posts);
		nbt.putIntArray("Road", road);
		nbt.putIntArray("Lamps", lamps);
		nbt.putIntArray("Lanterns", lanterns);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator,
			Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		try {
			place(world, chunkBox);
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo colocar la empalizada/farolas de una aldea", e);
		}
	}

	private void place(StructureWorldAccess world, BlockBox chunkBox) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		LongOpenHashSet lanternCells = new LongOpenHashSet();
		for (int i = 0; i + 1 < lanterns.length; i += 2) {
			lanternCells.add(((long) lanterns[i] << 32) | (lanterns[i + 1] & 0xFFFFFFFFL));
		}
		for (int i = 0; i + 1 < road.length; i += 2) {
			if (inside(chunkBox, road[i], road[i + 1])) {
				placeRoad(world, road[i], road[i + 1], pos);
			}
		}
		for (int i = 0; i + 1 < wall.length; i += 2) {
			int x = wall[i];
			int z = wall[i + 1];
			if (inside(chunkBox, x, z)) {
				boolean lantern = lanternCells.contains(((long) x << 32) | (z & 0xFFFFFFFFL));
				placeColumn(world, x, z, 4 + ((x + z) & 1), lantern, pos);
			}
		}
		for (int i = 0; i + 1 < posts.length; i += 2) {
			if (inside(chunkBox, posts[i], posts[i + 1])) {
				placeColumn(world, posts[i], posts[i + 1], POST_HEIGHT, true, pos);
			}
		}
		for (int i = 0; i + 1 < lamps.length; i += 2) {
			if (inside(chunkBox, lamps[i], lamps[i + 1])) {
				placeLamp(world, lamps[i], lamps[i + 1], pos);
			}
		}
	}

	/**
	 * Solo lo usa el juego para adaptar el terreno a las piezas ("beard"): devolvemos false para que la caja
	 * de esta pieza (que abarca toda la aldea) no aplane ni excave el terreno. La colocación de bloques usa
	 * getBoundingBox(), así que no le afecta.
	 */
	@Override
	public boolean intersectsChunk(ChunkPos pos, int offset) {
		return false;
	}

	private static boolean inside(BlockBox box, int x, int z) {
		return x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ();
	}

	private static boolean canReplace(BlockState state) {
		return state.isAir() || state.isReplaceable();
	}

	/**
	 * Altura del suelo: primer bloque macizo bajando desde la superficie. Devuelve también (en waterTop)
	 * la altura de la superficie del agua si hay agua encima del suelo, o Integer.MIN_VALUE si no.
	 */
	private static int findGround(StructureWorldAccess world, int x, int z, BlockPos.Mutable pos, int[] waterTop) {
		waterTop[0] = Integer.MIN_VALUE;
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) + 3;
		int bottom = world.getBottomY();
		for (; y > bottom; y--) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (!state.getFluidState().isEmpty()) {
				if (waterTop[0] == Integer.MIN_VALUE) {
					waterTop[0] = y;
				}
				continue;
			}
			if (!canReplace(state)) {
				return y;
			}
		}
		return bottom;
	}

	private void set(StructureWorldAccess world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}

	/** Columna de la empalizada (o poste de puerta). En agua poco profunda arranca del fondo. */
	private void placeColumn(StructureWorldAccess world, int x, int z, int height, boolean lantern, BlockPos.Mutable pos) {
		int[] waterTop = new int[1];
		int ground = findGround(world, x, z, pos, waterTop);
		int top = ground + height;
		if (waterTop[0] != Integer.MIN_VALUE) {
			if (waterTop[0] - ground > MAX_WATER_DEPTH) {
				return;
			}
			top = Math.max(top, waterTop[0] + height - 1);
		}
		BlockState log = style.log();
		for (int y = ground + 1; y <= top; y++) {
			if (canReplace(world.getBlockState(pos.set(x, y, z)))) {
				set(world, pos, log);
			}
		}
		if (lantern && canReplace(world.getBlockState(pos.set(x, top + 1, z)))) {
			set(world, pos, Blocks.LANTERN.getDefaultState());
		}
	}

	/** Camino de acceso a la puerta: sustituye la hierba/tierra/arena de la superficie. */
	private void placeRoad(StructureWorldAccess world, int x, int z, BlockPos.Mutable pos) {
		int[] waterTop = new int[1];
		int ground = findGround(world, x, z, pos, waterTop);
		if (waterTop[0] != Integer.MIN_VALUE) {
			return;
		}
		BlockState state = world.getBlockState(pos.set(x, ground, z));
		if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL)
				|| state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.DIRT_PATH)) {
			set(world, pos, style.road(x, z));
			BlockState above = world.getBlockState(pos.set(x, ground + 1, z));
			if (!above.isAir() && above.isReplaceable() && above.getFluidState().isEmpty()) {
				set(world, pos, Blocks.AIR.getDefaultState());
			}
		}
	}

	/** Farola: dos postes y un farol, solo sobre el empedrado de la calle y con hueco libre encima. */
	private void placeLamp(StructureWorldAccess world, int x, int z, BlockPos.Mutable pos) {
		int[] waterTop = new int[1];
		int ground = findGround(world, x, z, pos, waterTop);
		if (waterTop[0] != Integer.MIN_VALUE || !VillageStyle.isStreet(world.getBlockState(pos.set(x, ground, z)))) {
			return;
		}
		for (int y = ground + 1; y <= ground + 3; y++) {
			if (!canReplace(world.getBlockState(pos.set(x, y, z)))) {
				return;
			}
		}
		BlockState post = style.lampPost();
		set(world, pos.set(x, ground + 1, z), post);
		set(world, pos.set(x, ground + 2, z), post);
		set(world, pos.set(x, ground + 3, z), Blocks.LANTERN.getDefaultState());
	}
}
