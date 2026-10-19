package com.evolvedwarfare.mixin;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.world.VillageExtras;
import com.evolvedwarfare.world.VillageExtrasPiece;
import com.evolvedwarfare.world.VillageStyle;
import java.util.List;
import java.util.Optional;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cuando el juego monta una aldea vanilla, añade al final una {@link VillageExtrasPiece} con la empalizada,
 * las puertas y las farolas. Las demás estructuras jigsaw (incluidas las del mod) no se tocan.
 */
@Mixin(StructurePoolBasedGenerator.class)
public abstract class StructurePoolBasedGeneratorMixin {
	@Inject(
			method = "generate(Lnet/minecraft/world/gen/structure/Structure$Context;Lnet/minecraft/registry/entry/RegistryEntry;Ljava/util/Optional;ILnet/minecraft/util/math/BlockPos;ZLjava/util/Optional;ILnet/minecraft/structure/pool/alias/StructurePoolAliasLookup;Lnet/minecraft/world/gen/structure/DimensionPadding;Lnet/minecraft/structure/StructureLiquidSettings;)Ljava/util/Optional;",
			at = @At("RETURN"),
			cancellable = true)
	private static void evolvedwarfare$addVillageExtras(Structure.Context context, RegistryEntry<StructurePool> structurePool,
			Optional<Identifier> id, int size, BlockPos pos, boolean useExpansionHack,
			Optional<Heightmap.Type> projectStartToHeightmap, int maxDistanceFromCenter, StructurePoolAliasLookup aliasLookup,
			DimensionPadding dimensionPadding, StructureLiquidSettings liquidSettings,
			CallbackInfoReturnable<Optional<Structure.StructurePosition>> cir) {
		VillageStyle style = VillageStyle.fromStartPool(structurePool);
		Optional<Structure.StructurePosition> result = cir.getReturnValue();
		if (style == null || result == null || result.isEmpty()) {
			return;
		}
		Structure.StructurePosition original = result.get();
		StructureTemplateManager templates = context.structureTemplateManager();
		cir.setReturnValue(Optional.of(new Structure.StructurePosition(original.position(), collector -> {
			List<StructurePiece> pieces = original.generate().toList().pieces();
			for (StructurePiece piece : pieces) {
				collector.addPiece(piece);
			}
			try {
				VillageExtrasPiece extras = VillageExtras.create(style, pieces, templates);
				if (extras != null) {
					collector.addPiece(extras);
				}
			} catch (RuntimeException e) {
				EvolvedWarfare.LOGGER.error("No se pudieron calcular las mejoras de una aldea", e);
			}
		})));
	}
}
