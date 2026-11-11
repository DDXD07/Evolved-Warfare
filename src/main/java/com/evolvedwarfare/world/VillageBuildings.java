package com.evolvedwarfare.world;

import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Lo que se añade dentro de las aldeas vanilla (fase 54): la plaza del mercado (empedrada, con su pozo, puestos con toldo,
 * bancos, farolas y un atril de anuncios), la taberna (barra, barriles, mesas con sillas), el granero (heno, barriles,
 * compostador) y la atalaya (cuatro pilares, escalera de mano, plataforma con barandilla y campana).
 * <p>
 * Cada una se dibuja en sus coordenadas (lx a lo ancho, lz hacia dentro, ly desde el suelo; la entrada da al norte, lz = 0)
 * y la pieza la gira y la coloca donde toca.
 */
public final class VillageBuildings {
	public static final int MARKET = 0, TAVERN = 1, GRANARY = 2, TOWER = 3, MARKET_SMALL = 4;
	/** Las decoraciones de las calles (de 10 en adelante: pequeñas, con el frente a la calle). */
	public static final int BARRELS = 10, HAY = 11, WOODPILE = 12, FLOWERS = 13, BENCH = 14, SCARECROW = 15, CRATES = 16, POTS = 17, CAMPFIRE = 18;

	public static boolean decor(int type) {
		return type >= 10;
	}

	/** Las decoraciones que van en cada tipo de aldea. */
	public static int[] decorations(VillageStyle s) {
		return switch (s) {
			case DESERT -> new int[] {BARRELS, HAY, POTS, BENCH, CRATES, POTS, SCARECROW};
			case TAIGA, SNOWY -> new int[] {BARRELS, WOODPILE, CAMPFIRE, BENCH, CRATES, WOODPILE, FLOWERS};
			default -> new int[] {BARRELS, HAY, WOODPILE, FLOWERS, BENCH, SCARECROW, CRATES, FLOWERS};
		};
	}

	private VillageBuildings() {
	}

	/** Ancho (lx), fondo (lz) y alto de cada una. */
	public static int width(int type) {
		return switch (type) {
			case BARRELS, HAY, BENCH, CRATES, POTS -> 2;
			case WOODPILE, FLOWERS -> 3;
			case SCARECROW, CAMPFIRE -> 1;
			case MARKET -> 11;
			case TAVERN -> 9;
			case GRANARY -> 7;
			case TOWER -> 5;
			default -> 9;
		};
	}

	public static int depth(int type) {
		return switch (type) {
			case HAY -> 2;
			case BARRELS, WOODPILE, FLOWERS, BENCH, SCARECROW, CRATES, POTS, CAMPFIRE -> 1;
			case MARKET -> 11;
			case TAVERN -> 7;
			case GRANARY -> 7;
			case TOWER -> 5;
			default -> 9;
		};
	}

	public static int height(int type) {
		if (decor(type)) {
			return 3;
		}
		return switch (type) {
			case TAVERN -> 10;
			case GRANARY -> 9;
			case TOWER -> 14;
			default -> 5;
		};
	}

	/** ¿Va sobre la plaza (empedrado) en vez de sobre un suelo de tablas? */
	public static boolean paved(int type) {
		return type == MARKET || type == MARKET_SMALL;
	}

	public interface Put {
		void at(int lx, int ly, int lz, BlockState state);
	}

	// ------------------------------------------------------------------ los materiales de cada tipo de aldea

	static BlockState planks(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.SMOOTH_SANDSTONE.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_PLANKS.getDefaultState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_PLANKS.getDefaultState();
			default -> Blocks.OAK_PLANKS.getDefaultState();
		};
	}

	static BlockState floor(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.CUT_SANDSTONE.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_PLANKS.getDefaultState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_PLANKS.getDefaultState();
			default -> Blocks.SPRUCE_PLANKS.getDefaultState();
		};
	}

	static BlockState log(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.CUT_SANDSTONE.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_LOG.getDefaultState();
			case TAIGA -> Blocks.SPRUCE_LOG.getDefaultState();
			case SNOWY -> Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
			default -> Blocks.OAK_LOG.getDefaultState();
		};
	}

	static Block stairs(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.SANDSTONE_STAIRS;
			case SAVANNA -> Blocks.ACACIA_STAIRS;
			case TAIGA, SNOWY -> Blocks.SPRUCE_STAIRS;
			default -> Blocks.DARK_OAK_STAIRS;
		};
	}

	static Block slab(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.SMOOTH_SANDSTONE_SLAB;
			case SAVANNA -> Blocks.ACACIA_SLAB;
			case TAIGA, SNOWY -> Blocks.SPRUCE_SLAB;
			default -> Blocks.DARK_OAK_SLAB;
		};
	}

	static BlockState fence(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.JUNGLE_FENCE.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_FENCE.getDefaultState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_FENCE.getDefaultState();
			default -> Blocks.OAK_FENCE.getDefaultState();
		};
	}

	static Block door(VillageStyle s) {
		return switch (s) {
			case DESERT -> Blocks.JUNGLE_DOOR;
			case SAVANNA -> Blocks.ACACIA_DOOR;
			case TAIGA, SNOWY -> Blocks.SPRUCE_DOOR;
			default -> Blocks.OAK_DOOR;
		};
	}

	static BlockState stone(VillageStyle s) {
		return s == VillageStyle.DESERT ? Blocks.CUT_SANDSTONE.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
	}

	/** El empedrado de la plaza. */
	static BlockState paving(VillageStyle s, int lx, int lz) {
		int h = ((lx * 73856093) ^ (lz * 19349663)) & 0x7FFFFFFF;
		if (s == VillageStyle.DESERT) {
			return h % 4 == 0 ? Blocks.CUT_SANDSTONE.getDefaultState() : Blocks.SMOOTH_SANDSTONE.getDefaultState();
		}
		return switch (h % 8) {
			case 0, 1 -> Blocks.STONE_BRICKS.getDefaultState();
			case 2 -> Blocks.ANDESITE.getDefaultState();
			case 3 -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
			default -> Blocks.COBBLESTONE.getDefaultState();
		};
	}

	private static BlockState stair(VillageStyle s, Direction facing, boolean top) {
		return stairs(s).getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
	}

	private static BlockState slabState(VillageStyle s, boolean top) {
		return slab(s).getDefaultState().with(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, hanging);
	}

	/** Toldos de dos colores, a rayas. */
	private static BlockState awning(VillageStyle s, int stall, int stripe) {
		Block[][] colors = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL}, {Blocks.GREEN_WOOL, Blocks.YELLOW_WOOL},
				{Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL}};
		if (s == VillageStyle.DESERT) {
			colors = new Block[][] {{Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL}, {Blocks.RED_WOOL, Blocks.YELLOW_WOOL}, {Blocks.CYAN_WOOL, Blocks.WHITE_WOOL},
					{Blocks.BROWN_WOOL, Blocks.WHITE_WOOL}};
		}
		Block[] c = colors[stall % colors.length];
		return c[stripe & 1].getDefaultState();
	}

	// ------------------------------------------------------------------ dibujar

	public static void build(int type, VillageStyle s, Put put) {
		switch (type) {
			case MARKET -> market(s, put, 11);
			case MARKET_SMALL -> market(s, put, 9);
			case TAVERN -> tavern(s, put);
			case GRANARY -> granary(s, put);
			case TOWER -> tower(s, put);
			default -> decoration(type, s, put);
		}
	}

	private static final Block[] FLOWER_KINDS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
			Blocks.RED_TULIP, Blocks.ORANGE_TULIP};

	/** Las decoraciones (el frente, lz = 0, da a la calle). */
	private static void decoration(int type, VillageStyle s, Put put) {
		BlockState logX = log(s).contains(Properties.AXIS) ? log(s).with(Properties.AXIS, Direction.Axis.X) : log(s);
		switch (type) {
			case BARRELS -> {
				put.at(0, 1, 0, Blocks.BARREL.getDefaultState());
				put.at(1, 1, 0, Blocks.BARREL.getDefaultState());
				put.at(0, 2, 0, Blocks.BARREL.getDefaultState());
			}
			case HAY -> {
				put.at(0, 1, 0, Blocks.HAY_BLOCK.getDefaultState());
				put.at(1, 1, 0, Blocks.HAY_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
				put.at(0, 1, 1, Blocks.HAY_BLOCK.getDefaultState());
				put.at(1, 1, 1, Blocks.HAY_BLOCK.getDefaultState());
				put.at(0, 2, 1, Blocks.HAY_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
			}
			case WOODPILE -> {
				for (int lx = 0; lx < 3; lx++) {
					put.at(lx, 1, 0, logX);
				}
				put.at(0, 2, 0, logX);
				put.at(1, 2, 0, logX);
				put.at(2, 2, 0, Blocks.CHAIN.getDefaultState());
			}
			case FLOWERS -> {
				for (int lx = 0; lx < 3; lx++) {
					put.at(lx, 0, 0, Blocks.GRASS_BLOCK.getDefaultState());
					put.at(lx, 1, 0, FLOWER_KINDS[Math.floorMod(lx * 3 + s.ordinal() * 5, FLOWER_KINDS.length)].getDefaultState());
				}
			}
			case BENCH -> {
				put.at(0, 1, 0, stair(s, Direction.SOUTH, false));
				put.at(1, 1, 0, stair(s, Direction.SOUTH, false));
			}
			case SCARECROW -> {
				put.at(0, 1, 0, fence(s));
				put.at(0, 2, 0, Blocks.HAY_BLOCK.getDefaultState());
				put.at(0, 3, 0, Blocks.CARVED_PUMPKIN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
			}
			case CRATES -> {
				put.at(0, 1, 0, Blocks.COMPOSTER.getDefaultState());
				put.at(1, 1, 0, Blocks.BARREL.getDefaultState());
				put.at(1, 2, 0, lantern(false));
			}
			case POTS -> {
				put.at(0, 1, 0, Blocks.DECORATED_POT.getDefaultState());
				put.at(1, 1, 0, Blocks.TERRACOTTA.getDefaultState());
				put.at(1, 2, 0, Blocks.POTTED_CACTUS.getDefaultState());
			}
			case CAMPFIRE -> put.at(0, 1, 0, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true));
			default -> {
			}
		}
	}

	/** El escalón de las calles en cuesta. */
	public static BlockState streetStair(VillageStyle s, Direction up, int x, int z) {
		Block b = s == VillageStyle.DESERT ? Blocks.SMOOTH_SANDSTONE_STAIRS
				: (((x * 73856093) ^ (z * 19349663)) & 3) == 0 ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLESTONE_STAIRS;
		return b.getDefaultState().with(StairsBlock.FACING, up);
	}

	/** Un tejado a dos aguas a lo largo de lz (los aleros, a los lados lx = 0 y lx = w-1), desde la altura y0. */
	private static void gable(VillageStyle s, Put put, int w, int d, int y0) {
		for (int k = 0; k <= (w - 1) / 2; k++) {
			int y = y0 + k;
			for (int lz = 0; lz < d; lz++) {
				if (k == (w - 1) / 2 && w % 2 == 1) {
					put.at(k, y, lz, slabState(s, false));
				} else {
					put.at(k, y, lz, stair(s, Direction.EAST, false));
					put.at(w - 1 - k, y, lz, stair(s, Direction.WEST, false));
				}
				if (lz == 0 || lz == d - 1) {
					// los hastiales
					for (int lx = k + 1; lx < w - 1 - k; lx++) {
						put.at(lx, y, lz, planks(s));
					}
				}
			}
		}
	}

	/** Paredes de un edificio de w x d con esquinas de tronco, del suelo (ly 1) hasta ly top. */
	private static void walls(VillageStyle s, Put put, int w, int d, int top) {
		for (int ly = 1; ly <= top; ly++) {
			for (int lx = 0; lx < w; lx++) {
				for (int lz = 0; lz < d; lz++) {
					boolean edgeX = lx == 0 || lx == w - 1, edgeZ = lz == 0 || lz == d - 1;
					if (!edgeX && !edgeZ) {
						continue;
					}
					put.at(lx, ly, lz, edgeX && edgeZ ? log(s) : planks(s));
				}
			}
		}
	}

	private static void floorOf(VillageStyle s, Put put, int w, int d) {
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				put.at(lx, 0, lz, floor(s));
			}
		}
	}

	private static void door(VillageStyle s, Put put, int lx, int lz) {
		BlockState lower = door(s).getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		put.at(lx, 1, lz, lower);
		put.at(lx, 2, lz, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static void market(VillageStyle s, Put put, int size) {
		int c = size / 2;
		for (int lx = 0; lx < size; lx++) {
			for (int lz = 0; lz < size; lz++) {
				put.at(lx, 0, lz, paving(s, lx, lz));
			}
		}
		// el pozo: un brocal de 3 x 3 con agua honda, cuatro postes y su tejadillo
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					for (int ly = -3; ly <= 0; ly++) {
						put.at(c, ly, c, ly == -3 ? stone(s) : Blocks.WATER.getDefaultState());
					}
				} else {
					put.at(c + dx, 1, c + dz, stone(s));
					for (int ly = -3; ly <= 0; ly++) {
						put.at(c + dx, ly, c + dz, stone(s));
					}
				}
			}
		}
		for (int dx : new int[] {-1, 1}) {
			for (int dz : new int[] {-1, 1}) {
				put.at(c + dx, 2, c + dz, fence(s));
				put.at(c + dx, 3, c + dz, fence(s));
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put.at(c + dx, 4, c + dz, slabState(s, false));
			}
		}
		put.at(c, 3, c, lantern(true));
		// los puestos (3 x 2), con el mostrador hacia el pozo
		int far = size - 4;
		int[][] stalls = size >= 11 ? new int[][] {{1, 1, 0}, {far, 1, 0}, {1, far + 1, 1}, {far, far + 1, 1}} : new int[][] {{1, 1, 0}, {far, far + 1, 1}};
		Block[][] goods = {{Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.PUMPKIN}, {Blocks.BARREL, Blocks.SMITHING_TABLE, Blocks.BARREL},
				{Blocks.WHITE_WOOL, Blocks.BARREL, Blocks.LOOM}, {Blocks.MELON, Blocks.COMPOSTER, Blocks.HAY_BLOCK}};
		for (int i = 0; i < stalls.length; i++) {
			int sx = stalls[i][0], sz = stalls[i][1];
			boolean south = stalls[i][2] == 1;
			int back = south ? sz + 1 : sz, front = south ? sz : sz + 1;
			for (int k = 0; k < 3; k++) {
				put.at(sx + k, 1, front, slabState(s, true));
				put.at(sx + k, 1, back, goods[i % goods.length][k].getDefaultState());
				put.at(sx + k, 3, sz, awning(s, i, k));
				put.at(sx + k, 3, sz + 1, awning(s, i, k));
			}
			for (int k : new int[] {0, 2}) {
				put.at(sx + k, 2, back, fence(s));
				put.at(sx + k, 2, front, fence(s));
			}
		}
		// los bancos, mirando al pozo
		put.at(c - 3, 1, c, stair(s, Direction.WEST, false));
		put.at(c + 3, 1, c, stair(s, Direction.EAST, false));
		// el atril de los anuncios
		put.at(c, 1, 1, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.SOUTH));
		// las farolas de las esquinas
		for (int[] corner : new int[][] {{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}}) {
			put.at(corner[0], 1, corner[1], fence(s));
			put.at(corner[0], 2, corner[1], fence(s));
			put.at(corner[0], 3, corner[1], lantern(false));
		}
	}

	private static void tavern(VillageStyle s, Put put) {
		int w = 9, d = 7, top = 4;
		floorOf(s, put, w, d);
		walls(s, put, w, d, top);
		// las ventanas
		for (int lx : new int[] {2, 6}) {
			for (int ly = 2; ly <= 3; ly++) {
				put.at(lx, ly, 0, Blocks.GLASS_PANE.getDefaultState());
				put.at(lx, ly, d - 1, Blocks.GLASS_PANE.getDefaultState());
			}
		}
		for (int ly = 2; ly <= 3; ly++) {
			put.at(0, ly, 3, Blocks.GLASS_PANE.getDefaultState());
			put.at(w - 1, ly, 3, Blocks.GLASS_PANE.getDefaultState());
		}
		door(s, put, 4, 0);
		// las vigas del techo (y de ellas cuelgan los faroles)
		for (int lx = 1; lx < w - 1; lx++) {
			put.at(lx, top + 1, 2, log(s).contains(Properties.AXIS) ? log(s).with(Properties.AXIS, Direction.Axis.X) : log(s));
			put.at(lx, top + 1, 4, log(s).contains(Properties.AXIS) ? log(s).with(Properties.AXIS, Direction.Axis.X) : log(s));
		}
		put.at(2, top, 2, lantern(true));
		put.at(6, top, 4, lantern(true));
		// la barra y los barriles detrás
		for (int lx = 1; lx <= 4; lx++) {
			put.at(lx, 1, 4, slabState(s, true));
		}
		put.at(1, 1, 5, Blocks.BARREL.getDefaultState());
		put.at(2, 1, 5, Blocks.BARREL.getDefaultState());
		put.at(1, 2, 5, Blocks.BARREL.getDefaultState());
		put.at(3, 1, 5, Blocks.BREWING_STAND.getDefaultState());
		put.at(4, 2, 4, Blocks.FLOWER_POT.getDefaultState());
		// dos mesas con sus sillas
		for (int lz : new int[] {2, 4}) {
			put.at(6, 1, lz, fence(s));
			put.at(6, 2, lz, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
			put.at(5, 1, lz, stair(s, Direction.WEST, false));
			put.at(7, 1, lz, stair(s, Direction.EAST, false));
		}
		put.at(1, 1, 1, Blocks.CAULDRON.getDefaultState());
		gable(s, put, w, d, top + 1);
	}

	private static void granary(VillageStyle s, Put put) {
		int w = 7, d = 7, top = 4;
		floorOf(s, put, w, d);
		walls(s, put, w, d, top);
		// el portón (3 de ancho)
		for (int lx = 2; lx <= 4; lx++) {
			for (int ly = 1; ly <= 3; ly++) {
				put.at(lx, ly, 0, Blocks.AIR.getDefaultState());
			}
		}
		put.at(1, 2, d - 1, Blocks.AIR.getDefaultState());
		put.at(5, 2, d - 1, Blocks.AIR.getDefaultState());
		// el heno, apilado al fondo, y lo demás
		for (int lx = 1; lx <= 5; lx++) {
			for (int ly = 1; ly <= (lx == 3 ? 1 : 2); ly++) {
				put.at(lx, ly, 5, Blocks.HAY_BLOCK.getDefaultState());
			}
		}
		put.at(1, 1, 4, Blocks.HAY_BLOCK.getDefaultState());
		put.at(5, 1, 1, Blocks.BARREL.getDefaultState());
		put.at(5, 2, 1, Blocks.BARREL.getDefaultState());
		put.at(5, 1, 2, Blocks.COMPOSTER.getDefaultState());
		put.at(1, 1, 1, Blocks.PUMPKIN.getDefaultState());
		put.at(1, 1, 2, Blocks.BARREL.getDefaultState());
		put.at(3, top, 3, lantern(true));
		for (int lx = 1; lx < w - 1; lx++) {
			put.at(lx, top + 1, 3, log(s).contains(Properties.AXIS) ? log(s).with(Properties.AXIS, Direction.Axis.X) : log(s));
		}
		gable(s, put, w, d, top + 1);
	}

	private static void tower(VillageStyle s, Put put) {
		int n = 5, deck = 9;
		for (int lx = 0; lx < n; lx++) {
			for (int lz = 0; lz < n; lz++) {
				put.at(lx, 0, lz, floor(s));
			}
		}
		for (int[] p : new int[][] {{0, 0}, {n - 1, 0}, {0, n - 1}, {n - 1, n - 1}}) {
			for (int ly = 1; ly <= deck + 3; ly++) {
				put.at(p[0], ly, p[1], log(s));
			}
		}
		// la escalera de mano, contra una pared de tablas al fondo
		for (int ly = 1; ly <= deck; ly++) {
			put.at(2, ly, n - 1, planks(s));
			put.at(2, ly, n - 2, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
		}
		put.at(2, deck + 1, n - 2, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
		put.at(2, deck + 1, n - 1, planks(s));
		// la plataforma, con su hueco para la escalera, y la barandilla
		for (int lx = 0; lx < n; lx++) {
			for (int lz = 0; lz < n; lz++) {
				if (!(lx == 2 && lz == n - 2)) {
					put.at(lx, deck, lz, planks(s));
				}
				boolean edge = lx == 0 || lz == 0 || lx == n - 1 || lz == n - 1;
				boolean corner = (lx == 0 || lx == n - 1) && (lz == 0 || lz == n - 1);
				if (edge && !corner && !(lx == 2 && lz == n - 1)) {
					put.at(lx, deck + 1, lz, fence(s));
				}
			}
		}
		// el tejado y la campana colgada de él
		for (int lx = 0; lx < n; lx++) {
			for (int lz = 0; lz < n; lz++) {
				put.at(lx, deck + 4, lz, slabState(s, false));
			}
		}
		for (int lx = 1; lx < n - 1; lx++) {
			for (int lz = 1; lz < n - 1; lz++) {
				put.at(lx, deck + 5, lz, slabState(s, false));
			}
		}
		put.at(2, deck + 3, 2, Blocks.BELL.getDefaultState().with(BellBlock.ATTACHMENT, Attachment.CEILING).with(BellBlock.FACING, Direction.SOUTH));
		put.at(1, deck + 1, 1, lantern(false));
	}
}
