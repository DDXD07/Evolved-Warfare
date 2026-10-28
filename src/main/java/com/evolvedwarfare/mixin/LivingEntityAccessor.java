package com.evolvedwarfare.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para saber si el que maneja una máquina de asedio está pulsando saltar (es el botón de disparar). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean evolvedwarfare$isJumping();
}
