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
	/** Los edificios añadidos: de 5 en 5 (tipo, x y z de la esquina, giro, altura del suelo). */
	private final int[] buildings;

	/** Las casillas de las calles (para los escalones de las cuestas). */
	private final int[] streets;

	public VillageExtrasPiece(VillageStyle style, BlockBox box, int[] wall, int[] posts, int[] road, int[] lamps,
			int[] lanterns) {
		this(style, box, wall, posts, road, lamps, lanterns, new int[0], new int[0]);
	}

	public VillageExtrasPiece(VillageStyle style, BlockBox box, int[] wall, int[] posts, int[] road, int[] lamps,
			int[] lanterns, int[] buildings, int[] streets) {
		super(ModStructures.VILLAGE_EXTRAS, 0, box);
		this.style = style;
		this.wall = wall;
		this.posts = posts;
		this.road = road;
		this.lamps = lamps;
		this.lanterns = lanterns;
		this.buildings = buildings;
		this.streets = streets;
	}

	public int[] buildings() {
		return buildings;
	}

	public VillageExtrasPiece(NbtCompound nbt) {
		super(ModStructures.VILLAGE_EXTRAS, nbt);
		this.style = VillageStyle.byId(nbt.getString("Style"));
		this.wall = nbt.getIntArray("Wall");
		this.posts = nbt.getIntArray("Posts");
		this.road = nbt.getIntArray("Road");
		this.lamps = nbt.getIntArray("Lamps");
		this.lanterns = nbt.getIntArray("Lanterns");
		this.buildings = nbt.getIntArray("Buildings");
		this.streets = nbt.getIntArray("Streets");
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putString("Style", style.id());
		nbt.putIntArray("Wall", wall);
		nbt.putIntArray("Posts", posts);
		nbt.putIntArray("Road", road);
		nbt.putIntArray("Lamps", lamps);
		nbt.putIntArray("Lanterns", lanterns);
		nbt.putIntArray("Buildings", buildings);
		nbt.putIntArray("Streets", streets);
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
		// los escalones de las cuestas (en las calles y en los caminos de las puertas), antes que las farolas
		LongOpenHashSet steps = new LongOpenHashSet();
		for (int[] list : new int[][] {streets, road}) {
			for (int i = 0; i + 1 < list.length; i += 2) {
				steps.add(((long) list[i] << 32) | (list[i + 1] & 0xFFFFFFFFL));
			}
		}
		for (long k : steps) {
			int x = (int) (k >> 32), z = (int) k;
			if (inside(chunkBox, x, z)) {
				placeStep(world, x, z, steps, pos);
			}
		}
		for (int i = 0; i + 1 < lamps.length; i += 2) {
			if (inside(chunkBox, lamps[i], lamps[i + 1])) {
				placeLamp(world, lamps[i], lamps[i + 1], pos);
			}
		}
		for (int i = 0; i + 4 < buildings.length; i += 5) {
			placeBuilding(world, chunkBox, buildings[i], buildings[i + 1], buildings[i + 2], buildings[i + 3], buildings[i + 4], pos);
		}
	}

	/** Si la calle de al lado está un bloque más alta, un escalón (mirando hacia arriba) para subir sin saltar. */
	private void placeStep(StructureWorldAccess world, int x, int z, LongOpenHashSet steps, BlockPos.Mutable pos) {
		int[] waterTop = new int[1];
		int y = findGround(world, x, z, pos, waterTop);
		if (waterTop[0] != Integer.MIN_VALUE || !VillageStyle.isStreet(world.getBlockState(pos.set(x, y, z)))) {
			return;
		}
		if (!isFree(world.getBlockState(pos.set(x, y + 1, z))) || !isFree(world.getBlockState(pos.set(x, y + 2, z)))) {
			return;
		}
		for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
			int nx = x + d.getOffsetX(), nz = z + d.getOffsetZ();
			if (!steps.contains(((long) nx << 32) | (nz & 0xFFFFFFFFL))) {
				continue;
			}
			int ny = findGround(world, nx, nz, new BlockPos.Mutable(), new int[1]);
			BlockState there = world.getBlockState(new BlockPos(nx, ny, nz));
			if (ny == y + 1 && (VillageStyle.isStreet(there) || there.getBlock() instanceof net.minecraft.block.StairsBlock)) {
				set(world, pos.set(x, y + 1, z), VillageBuildings.streetStair(style, d, x, z));
				return;
			}
		}
	}

	private static final net.minecraft.util.BlockRotation[] ROTATIONS = {net.minecraft.util.BlockRotation.NONE,
			net.minecraft.util.BlockRotation.CLOCKWISE_90, net.minecraft.util.BlockRotation.CLOCKWISE_180,
			net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90};

	/** De las coordenadas del edificio (lx, lz) a las del mundo, con su giro (la entrada al norte, al este, al sur o al oeste). */
	static int[] toWorld(int rot, int w, int d, int x0, int z0, int lx, int lz) {
		return switch (rot & 3) {
			case 0 -> new int[] {x0 + lx, z0 + lz};
			case 1 -> new int[] {x0 + (d - 1 - lz), z0 + lx};
			case 2 -> new int[] {x0 + (w - 1 - lx), z0 + (d - 1 - lz)};
			default -> new int[] {x0 + lz, z0 + (w - 1 - lx)};
		};
	}

	/** Un edificio: el cimiento hasta el suelo, el hueco despejado encima y lo que lleva (solo lo de este chunk). */
	private void placeBuilding(StructureWorldAccess world, BlockBox chunkBox, int type, int x0, int z0, int rot, int y, BlockPos.Mutable pos) {
		int w = VillageBuildings.width(type), d = VillageBuildings.depth(type);
		int fw = (rot & 1) == 0 ? w : d, fd = (rot & 1) == 0 ? d : w;
		if (x0 > chunkBox.getMaxX() || x0 + fw - 1 < chunkBox.getMinX() || z0 > chunkBox.getMaxZ() || z0 + fd - 1 < chunkBox.getMinZ()) {
			return;
		}
		int height = VillageBuildings.height(type);
		BlockState foundation = VillageBuildings.stone(style);
		int[] waterTop = new int[1];
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int[] p = toWorld(rot, w, d, x0, z0, lx, lz);
				if (!inside(chunkBox, p[0], p[1])) {
					continue;
				}
				int ground = findGround(world, p[0], p[1], pos, waterTop);
				for (int yy = Math.max(ground + 1, y - 8); yy < y; yy++) {
					set(world, pos.set(p[0], yy, p[1]), foundation);
				}
				for (int yy = y + 1; yy <= y + height + 3; yy++) {
					BlockState old = world.getBlockState(pos.set(p[0], yy, p[1]));
					if (!old.isAir() && (!VillageBuildings.decor(type) || old.isReplaceable())) {
						set(world, pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
		net.minecraft.util.BlockRotation rotation = ROTATIONS[rot & 3];
		VillageBuildings.build(type, style, (lx, ly, lz, state) -> {
			int[] p = toWorld(rot, w, d, x0, z0, lx, lz);
			if (inside(chunkBox, p[0], p[1])) {
				set(world, pos.set(p[0], y + ly, p[1]), state.rotate(rotation));
			}
		});
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

	private static boolean isFree(BlockState state) {
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
			if (!isFree(state)) {
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
			if (isFree(world.getBlockState(pos.set(x, y, z)))) {
				set(world, pos, log);
			}
		}
		if (lantern && isFree(world.getBlockState(pos.set(x, top + 1, z)))) {
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
			if (!isFree(world.getBlockState(pos.set(x, y, z)))) {
				return;
			}
		}
		BlockState post = style.lampPost();
		set(world, pos.set(x, ground + 1, z), post);
		set(world, pos.set(x, ground + 2, z), post);
		set(world, pos.set(x, ground + 3, z), Blocks.LANTERN.getDefaultState());
	}
}
