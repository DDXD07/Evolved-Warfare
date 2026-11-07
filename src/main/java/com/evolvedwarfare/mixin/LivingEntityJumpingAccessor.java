package com.evolvedwarfare.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Si está pulsando saltar (para el silbato de la locomotora). */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpingAccessor {
	@Accessor("jumping")
	boolean evolvedwarfare$jumping();
}
