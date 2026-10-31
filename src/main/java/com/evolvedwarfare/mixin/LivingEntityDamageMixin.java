package com.evolvedwarfare.mixin;

import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.combat.LocationalDamage;
import com.evolvedwarfare.skill.SkillEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Daño locacional (multiplica el daño según la parte del cuerpo donde da, antes de la armadura) y el árbol de
 * habilidades: los bonus de las ventajas antes del golpe y, si entra, la experiencia y lo que causa
 * ({@link SkillEffects}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@Unique
	private HitZone evolvedwarfare$zone;
	@Unique
	private float evolvedwarfare$amount;

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float evolvedwarfare$locational(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		HitZone zone = LocationalDamage.zoneOf(self, source);
		evolvedwarfare$zone = zone;
		float out = zone == null ? amount : amount * zone.multiplier();
		out = SkillEffects.modifyDamage(self, source, out, zone);
		out = com.evolvedwarfare.weapon.HeavyAttack.modify(source, out);
		out = com.evolvedwarfare.armor.ClassArmorItem.modifyDamage(self, source, out);
		evolvedwarfare$amount = out;
		return out;
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void evolvedwarfare$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		HitZone zone = evolvedwarfare$zone;
		float dealt = evolvedwarfare$amount;
		evolvedwarfare$zone = null;
		evolvedwarfare$amount = 0.0F;
		if (zone == HitZone.HEAD && cir.getReturnValueZ()) {
			LocationalDamage.headshot((LivingEntity) (Object) this, source);
		}
		if (cir.getReturnValueZ()) {
			SkillEffects.afterDamage((LivingEntity) (Object) this, source, dealt, zone);
		}
	}
}
