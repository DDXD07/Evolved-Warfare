package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.world.VillageExtrasPiece;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;

/** Tipos de pieza de estructura propios (para que se guarden y se carguen con el mundo). */
public final class ModStructures {
	public static final StructurePieceType VILLAGE_EXTRAS = Registry.register(Registries.STRUCTURE_PIECE,
			EvolvedWarfare.id("village_extras"), (StructurePieceType.Simple) VillageExtrasPiece::new);

	private ModStructures() {
	}

	public static void init() {
	}
}
