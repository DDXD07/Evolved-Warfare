package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.world.VillageExtrasPiece;
import com.evolvedwarfare.world.tower.WatchtowerPiece;
import com.evolvedwarfare.world.tower.WatchtowerStructure;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

/** Tipos de estructura y de pieza propios (para que se guarden y se carguen con el mundo). */
public final class ModStructures {
	public static final StructurePieceType VILLAGE_EXTRAS = Registry.register(Registries.STRUCTURE_PIECE,
			EvolvedWarfare.id("village_extras"), (StructurePieceType.Simple) VillageExtrasPiece::new);
	/** Torre de vigilancia generada por semilla (cada una distinta). */
	public static final StructurePieceType WATCHTOWER_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
			EvolvedWarfare.id("watchtower"), (StructurePieceType.Simple) WatchtowerPiece::new);
	public static final StructureType<WatchtowerStructure> WATCHTOWER = Registry.register(Registries.STRUCTURE_TYPE,
			EvolvedWarfare.id("watchtower"), () -> WatchtowerStructure.CODEC);

	private ModStructures() {
	}

	public static void init() {
	}
}
