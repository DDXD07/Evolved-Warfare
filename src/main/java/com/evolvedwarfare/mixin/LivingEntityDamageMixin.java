package com.evolvedwarfare.mixin;

import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.combat.LocationalDamage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Daño locacional: multiplica el daño según la parte del cuerpo donde da (antes de la armadura). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@Unique
	private HitZone evolvedwarfare$zone;

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float evolvedwarfare$locational(float amount, DamageSource source) {
		HitZone zone = LocationalDamage.zoneOf((LivingEntity) (Object) this, source);
		evolvedwarfare$zone = zone;
		return zone == null ? amount : amount * zone.multiplier();
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void evolvedwarfare$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		HitZone zone = evolvedwarfare$zone;
		evolvedwarfare$zone = null;
		if (zone == HitZone.HEAD && cir.getReturnValueZ()) {
			LocationalDamage.headshot((LivingEntity) (Object) this, source);
		}
	}
}
