package com.evolvedwarfare.mixin;

import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.util.BlockRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Giro con el que se colocó una pieza jigsaw (para situar sus calles en el mundo). */
@Mixin(PoolStructurePiece.class)
public interface PoolStructurePieceAccessor {
	@Accessor("rotation")
	BlockRotation evolvedwarfare$getRotation();
}
