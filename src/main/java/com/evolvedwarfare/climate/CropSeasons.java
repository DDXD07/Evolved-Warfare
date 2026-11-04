package com.evolvedwarfare.climate;

import com.evolvedwarfare.registry.ModBlocks;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Cada cultivo crece en su estación (o estaciones). Fuera de ella, al raso no crece (ni con harina de hueso); bajo techo
 * (cualquier bloque sólido encima, también el cristal: un invernadero, un granero, un sótano con luz) crece, pero a un
 * tercio de lo normal. Solo en el mundo normal y
 * con las estaciones activadas.
 *
 * <ul>
 * <li>Primavera y otoño: trigo, zanahoria, remolacha, cebolla.</li>
 * <li>Primavera y verano: patata, maíz, caña de azúcar, cacao.</li>
 * <li>Verano: tomate, sandía. Verano y otoño: calabaza, bayas dulces.</li>
 * <li>Otoño e invierno: col, ajo. La flor de antorcha, en primavera.</li>
 * <li>La verruga del Nether no entiende de estaciones.</li>
 * </ul>
 */
public final class CropSeasons {
	private static final Map<Block, Set<Season>> SEASONS = new IdentityHashMap<>();

	private CropSeasons() {
	}

	public static void init() {
		put(Blocks.WHEAT, Season.SPRING, Season.AUTUMN);
		put(Blocks.CARROTS, Season.SPRING, Season.AUTUMN);
		put(Blocks.BEETROOTS, Season.SPRING, Season.AUTUMN);
		put(Blocks.POTATOES, Season.SPRING, Season.SUMMER);
		put(Blocks.SUGAR_CANE, Season.SPRING, Season.SUMMER);
		put(Blocks.COCOA, Season.SPRING, Season.SUMMER);
		put(Blocks.MELON_STEM, Season.SUMMER);
		put(Blocks.PUMPKIN_STEM, Season.SUMMER, Season.AUTUMN);
		put(Blocks.SWEET_BERRY_BUSH, Season.SUMMER, Season.AUTUMN);
		put(Blocks.TORCHFLOWER_CROP, Season.SPRING);
		put(ModBlocks.TOMATOES, Season.SUMMER);
		put(ModBlocks.CORN, Season.SPRING, Season.SUMMER);
		put(ModBlocks.CABBAGES, Season.AUTUMN, Season.WINTER);
		put(ModBlocks.ONIONS, Season.SPRING, Season.AUTUMN);
		put(ModBlocks.GARLIC, Season.AUTUMN, Season.WINTER);
	}

	/** Para otros bloques que crecen por estaciones (los frutales). */
	public static void register(Block block, Set<Season> seasons) {
		SEASONS.put(block, EnumSet.copyOf(seasons));
	}

	private static void put(Block block, Season first, Season... rest) {
		SEASONS.put(block, EnumSet.of(first, rest));
	}

	/** Sus estaciones (null: cualquiera). */
	@Nullable
	public static Set<Season> of(Block block) {
		return SEASONS.get(block);
	}

	/** ¿Cuentan las estaciones aquí? (en el cliente, según lo que manda el servidor) */
	private static boolean active(World world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		return world.isClient ? Climate.tintSeason != null : Climate.seasonsEnabled();
	}

	@Nullable
	private static Season now(World world) {
		return world.isClient ? Climate.tintSeason : Climate.season(world);
	}

	/** ¿Está fuera de su estación? */
	public static boolean outOfSeason(World world, Block block) {
		Set<Season> set = of(block);
		if (set == null || !active(world)) {
			return false;
		}
		Season season = now(world);
		return season != null && !set.contains(season);
	}

	/** En cada tic al azar: ¿puede crecer? (fuera de temporada, solo bajo techo y a un tercio) */
	public static boolean canGrow(World world, BlockPos pos, Block block, Random random) {
		boolean out = outOfSeason(world, block);
		return canGrow(out, out && roofed(world, pos), random);
	}

	/** La regla: en su estación siempre; fuera, solo con techo y una de cada tres veces. */
	public static boolean canGrow(boolean outOfSeason, boolean roofed, Random random) {
		return !outOfSeason || (roofed && random.nextInt(3) == 0);
	}

	/** ¿Tiene techo encima (cualquier bloque sólido, también de cristal: un invernadero)? */
	public static boolean roofed(WorldView world, BlockPos pos) {
		return world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 1;
	}

	/** ¿Se le puede echar harina de hueso? (fuera de temporada y al raso, no) */
	public static boolean canFertilize(WorldView view, BlockPos pos, Block block) {
		return !(view instanceof World world) || !outOfSeason(world, block) || roofed(world, pos);
	}
}
