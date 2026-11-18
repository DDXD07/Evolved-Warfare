package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.SurvivorAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** El atributo de metabolismo (fase 114): el hambre baja más despacio (la sed, en Thirst). */
@Mixin(PlayerEntity.class)
public abstract class PlayerMetabolismMixin {
	@ModifyArg(method = "addExhaustion", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;addExhaustion(F)V"))
	private float evolvedwarfare$metabolism(float exhaustion) {
		return exhaustion * SurvivorAttributes.metabolism((PlayerEntity) (Object) this);
	}
}
