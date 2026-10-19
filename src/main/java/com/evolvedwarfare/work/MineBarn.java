package com.evolvedwarfare.work;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * El granero pequeño que el minero levanta encima de la entrada de su mina.
 *
 * <pre>
 *  planta (el puesto P mira hacia abajo; la mina queda arriba)
 *
 *    T ##### T          T = postes de tronco (3 de alto)
 *    # . . . #          # = pared de tablones (3 de alto)
 *    W . ~ . W          W = ventana con barrotes (valla) a media altura
 *    # . ~ . #          ~ = la escalera que baja a la mina
 *    T . ~ . T          . = suelo
 *       P               delante: portón de 3 de ancho y 2 de alto
 * </pre>
 *
 * <p>Tejado a dos aguas de escaleras de madera, con caballete de losas, y frontones de tablones delante y detrás.
 * Cada bloque gasta un tablón de los suministros; la madera del granero es la de esos tablones (roble, abeto...).
 * Solo construye en huecos libres (no rompe terreno) y si alguien rompe un trozo, lo repara.</p>
 */
final class MineBarn {
	static final Predicate<ItemStack> PLANKS = s -> s.isIn(ItemTags.PLANKS) && s.getItem() instanceof BlockItem;
	/** Fondo del granero (de 1 a 5 bloques detrás del puesto) y medio ancho (5 de ancho). */
	private static final int FIRST = 1;
	private static final int LAST = 5;
	private static final int HALF = 2;

	enum Part { POST, PLANKS, WINDOW, ROOF_LEFT, ROOF_RIGHT, RIDGE }

	/** Una pieza: su sitio, qué es y dónde está dentro del granero (fondo y lado, para saber desde dónde ponerla). */
	record Piece(BlockPos pos, Part part, int depth, int lateral) {
	}

	private MineBarn() {
	}

	/** Lado "positivo" del ancho del granero. */
	static Direction side(Direction back) {
		return back.rotateYClockwise();
	}

	private static BlockPos at(BlockPos site, Direction back, int depth, int lateral, int up) {
		return site.offset(back, depth).offset(side(back), lateral).up(up);
	}

	private static Piece piece(BlockPos site, Direction back, int depth, int lateral, int up, Part part) {
		return new Piece(at(site, back, depth, lateral, up), part, depth, lateral);
	}

	/**
	 * Desde dónde la pone: el suelo de dentro del granero más cercano (a los lados de la escalera), para no
	 * quedarse nunca de pie en el hueco que va a llenar.
	 */
	static BlockPos standFor(BlockPos site, Direction back, Piece piece) {
		int depth = Math.max(FIRST, Math.min(LAST - 1, piece.depth()));
		int lateral = piece.lateral() < 0 ? -1 : 1;
		return at(site, back, depth, lateral, 0);
	}

	/** Todas las piezas, de abajo arriba (paredes primero, luego el tejado). */
	static List<Piece> pieces(BlockPos site, Direction back) {
		List<Piece> pieces = new ArrayList<>();
		for (int y = 0; y <= 2; y++) {
			for (int d = FIRST; d <= LAST; d++) {
				for (int l = -HALF; l <= HALF; l++) {
					boolean corner = (d == FIRST || d == LAST) && Math.abs(l) == HALF;
					boolean sideWall = Math.abs(l) == HALF;
					boolean backWall = d == LAST;
					boolean lintel = d == FIRST && y == 2;
					if (corner) {
						pieces.add(piece(site, back, d, l, y, Part.POST));
					} else if (sideWall) {
						boolean window = y == 1 && d == (FIRST + LAST) / 2;
						pieces.add(piece(site, back, d, l, y, window ? Part.WINDOW : Part.PLANKS));
					} else if (backWall || lintel) {
						pieces.add(piece(site, back, d, l, y, Part.PLANKS));
					}
				}
			}
		}
		for (int d = FIRST; d <= LAST; d++) {
			boolean gable = d == FIRST || d == LAST;
			// primera hilera del tejado, sobre las paredes; frontón en medio
			pieces.add(piece(site, back, d, -HALF, 3, Part.ROOF_LEFT));
			pieces.add(piece(site, back, d, HALF, 3, Part.ROOF_RIGHT));
			if (gable) {
				for (int l = -HALF + 1; l <= HALF - 1; l++) {
					pieces.add(piece(site, back, d, l, 3, Part.PLANKS));
				}
			}
		}
		for (int d = FIRST; d <= LAST; d++) {
			pieces.add(piece(site, back, d, -1, 4, Part.ROOF_LEFT));
			pieces.add(piece(site, back, d, 1, 4, Part.ROOF_RIGHT));
			if (d == FIRST || d == LAST) {
				pieces.add(piece(site, back, d, 0, 4, Part.PLANKS));
			}
		}
		for (int d = FIRST; d <= LAST; d++) {
			pieces.add(piece(site, back, d, 0, 5, Part.RIDGE));
		}
		return pieces;
	}

	/** Dónde va la antorcha de dentro (en la pared de un lado) y hacia dónde mira. */
	static BlockPos torchSpot(BlockPos site, Direction back) {
		return at(site, back, LAST - 1, -HALF + 1, 1);
	}

	static Direction torchFacing(Direction back) {
		return side(back);
	}

	/** ¿Hay alguien (el propio minero, un jugador, un animal) ocupando ese hueco? */
	static boolean occupied(ServerWorld world, BlockPos pos) {
		return !world.getEntitiesByClass(LivingEntity.class, new Box(pos), Entity::isAlive).isEmpty();
	}

	/** El bloque de esa pieza en la madera de esos tablones (si falta alguna variante, de roble). */
	static BlockState stateFor(Piece piece, ItemStack planks, Direction back) {
		Block planksBlock = ((BlockItem) planks.getItem()).getBlock();
		Identifier id = Registries.BLOCK.getId(planksBlock);
		String wood = id.getPath().endsWith("_planks") ? id.getPath().substring(0, id.getPath().length() - "_planks".length()) : "oak";
		Direction side = side(back);
		return switch (piece.part()) {
			case POST -> {
				Block log = variant(id.getNamespace(), wood, "_log", Blocks.OAK_LOG);
				if (log == Blocks.OAK_LOG && !wood.equals("oak")) {
					log = variant(id.getNamespace(), wood, "_stem", variant(id.getNamespace(), wood, "_block", Blocks.OAK_LOG));
				}
				yield log.getDefaultState();
			}
			case PLANKS -> planksBlock.getDefaultState();
			case WINDOW -> variant(id.getNamespace(), wood, "_fence", Blocks.OAK_FENCE).getDefaultState();
			case ROOF_LEFT -> stairs(id.getNamespace(), wood, side);
			case ROOF_RIGHT -> stairs(id.getNamespace(), wood, side.getOpposite());
			case RIDGE -> variant(id.getNamespace(), wood, "_slab", Blocks.OAK_SLAB).getDefaultState();
		};
	}

	/** Escalera del tejado con la parte alta hacia el centro. */
	private static BlockState stairs(String namespace, String wood, Direction towardCenter) {
		BlockState state = variant(namespace, wood, "_stairs", Blocks.OAK_STAIRS).getDefaultState();
		return state.contains(Properties.HORIZONTAL_FACING) ? state.with(Properties.HORIZONTAL_FACING, towardCenter) : state;
	}

	private static Block variant(String namespace, String wood, String suffix, Block fallback) {
		Block block = Registries.BLOCK.get(Identifier.of(namespace, wood + suffix));
		return block == Blocks.AIR ? fallback : block;
	}

	/** Pone una pieza gastando un tablón. */
	static boolean build(WorkContext ctx, Piece piece) {
		ServerWorld world = ctx.world;
		BlockPos pos = piece.pos();
		if (!Jobs.isClear(world.getBlockState(pos)) || occupied(world, pos)) {
			return false;
		}
		ItemStack planks = ctx.site.find(PLANKS, false);
		if (planks == null) {
			return false;
		}
		BlockState state = Block.postProcessState(stateFor(piece, planks, ctx.facing.getOpposite()), world, pos);
		world.setBlockState(pos, state);
		planks.decrement(1);
		ctx.site.markDirty();
		world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.9F);
		return true;
	}
}
