package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.SkillEffects;
import com.evolvedwarfare.skill.Skills;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Árbol de habilidades: esquivar o devolver un golpe (no llega) e "Inamovible" (sin empujón mientras te cubres). */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySkillMixin {
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$avoid(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (SkillEffects.avoids((LivingEntity) (Object) this, source, amount)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$immovable(double strength, double x, double z, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof PlayerEntity player && !self.getWorld().isClient && player.isBlocking() && Skills.has(player, Perk.IMMOVABLE)) {
			ci.cancel();
		}
	}
}
