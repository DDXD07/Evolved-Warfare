package com.evolvedwarfare.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Materiales de la empalizada, las farolas y los caminos según el tipo de aldea vanilla. */
public enum VillageStyle {
	PLAINS("plains"),
	DESERT("desert"),
	SAVANNA("savanna"),
	TAIGA("taiga"),
	SNOWY("snowy");

	private final String id;

	VillageStyle(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static VillageStyle byId(String id) {
		for (VillageStyle style : values()) {
			if (style.id.equals(id)) {
				return style;
			}
		}
		return PLAINS;
	}

	/** Reconoce las aldeas vanilla por su pool inicial: minecraft:village/&lt;tipo&gt;/town_centers. */
	@Nullable
	public static VillageStyle fromStartPool(RegistryEntry<StructurePool> pool) {
		Identifier id = pool.getKey().map(key -> key.getValue()).orElse(null);
		if (id == null || !id.getNamespace().equals("minecraft") || !id.getPath().startsWith("village/")) {
			return null;
		}
		for (VillageStyle style : values()) {
			if (id.getPath().startsWith("village/" + style.id + "/")) {
				return style;
			}
		}
		return null;
	}

	/** Troncos de la empalizada. */
	public BlockState log() {
		return switch (this) {
			case DESERT -> Blocks.JUNGLE_LOG.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_LOG.getDefaultState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_LOG.getDefaultState();
			default -> Blocks.OAK_LOG.getDefaultState();
		};
	}

	/** Poste de las farolas. */
	public BlockState lampPost() {
		return switch (this) {
			case DESERT -> Blocks.SANDSTONE_WALL.getDefaultState();
			case SAVANNA -> Blocks.ACACIA_FENCE.getDefaultState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_FENCE.getDefaultState();
			default -> Blocks.OAK_FENCE.getDefaultState();
		};
	}

	/** Bloque con el que están hechas las calles en las piezas vanilla (antes de procesarlas). */
	public Block streetBlock() {
		return this == DESERT ? Blocks.SMOOTH_SANDSTONE : Blocks.DIRT_PATH;
	}

	/** Bloque de camino para los accesos a las puertas (empedrado variado, arenisca en el desierto). */
	public BlockState road(int x, int z) {
		if (this == DESERT) {
			return Blocks.SMOOTH_SANDSTONE.getDefaultState();
		}
		int h = ((x * 73856093) ^ (z * 19349663)) & 0x7FFFFFFF;
		return switch (h % 10) {
			case 6, 7 -> Blocks.ANDESITE.getDefaultState();
			case 8 -> Blocks.GRAVEL.getDefaultState();
			case 9 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
			default -> Blocks.COBBLESTONE.getDefaultState();
		};
	}

	/** ¿Es parte de una calle? (para colocar farolas solo sobre el camino). */
	public static boolean isStreet(BlockState state) {
		return state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE) || state.isOf(Blocks.ANDESITE)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.SMOOTH_SANDSTONE);
	}
}
