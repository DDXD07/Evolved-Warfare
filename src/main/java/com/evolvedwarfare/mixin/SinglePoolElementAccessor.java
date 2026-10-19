package com.evolvedwarfare.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Permite saber qué plantilla usa una pieza (para encontrar las calles de una aldea). */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
	@Accessor("location")
	Either<Identifier, StructureTemplate> evolvedwarfare$getLocation();
}
