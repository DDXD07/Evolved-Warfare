package com.evolvedwarfare.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Para saber si el que maneja una máquina de asedio está pulsando saltar (es el botón de disparar), y si un golpe lo
 * para el escudo (lo mismo que mira vanilla; lo usan las ventajas de Escudo).
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean evolvedwarfare$isJumping();

	@Invoker("blockedByShield")
	boolean evolvedwarfare$blockedByShield(DamageSource source);
}
