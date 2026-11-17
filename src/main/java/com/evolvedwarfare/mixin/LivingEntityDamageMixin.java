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
	@Unique
	private net.minecraft.util.math.Vec3d evolvedwarfare$point;
	@Unique
	private float evolvedwarfare$lifeBefore = -1.0F;
	@Unique
	private int evolvedwarfare$depth;
	/** El tick (edad) en que empezó el golpe de fuera: si otro mod cancela el daño antes del RETURN, no se queda colgado. */
	@Unique
	private int evolvedwarfare$depthAge = -1;

	@Inject(method = "damage", at = @At("HEAD"))
	private void evolvedwarfare$lifeBefore(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (evolvedwarfare$depth > 0 && evolvedwarfare$depthAge != self.age) {
			evolvedwarfare$depth = 0;
		}
		if (evolvedwarfare$depth++ == 0) {
			evolvedwarfare$lifeBefore = self.getHealth() + self.getAbsorptionAmount();
			evolvedwarfare$depthAge = self.age;
		}
	}

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float evolvedwarfare$locational(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		LocationalDamage.lastPoint = null;
		HitZone zone = LocationalDamage.zoneOf(self, source);
		evolvedwarfare$zone = zone;
		evolvedwarfare$point = LocationalDamage.lastPoint;
		float out = zone == null ? amount : amount * zone.multiplier();
		out = SkillEffects.modifyDamage(self, source, out, zone);
		out = com.evolvedwarfare.weapon.HeavyAttack.modify(source, out);
		out = com.evolvedwarfare.armor.ClassArmorItem.modifyDamage(self, source, out);
		// las flechas y explosiones de los monstruos con nivel (fase 95)
		out = com.evolvedwarfare.difficulty.Difficulty.modifyOutgoing(source, out);
		// los golpes sin aguante hacen la mitad (fase 97)
		out = com.evolvedwarfare.health.Stamina.modifyMelee(source, out);
		// las escamas de dragón (fase 102)
		out = com.evolvedwarfare.legendary.DragonGear.modifyIncoming(self, source, out);
		evolvedwarfare$amount = out;
		return out;
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void evolvedwarfare$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		HitZone zone = evolvedwarfare$zone;
		float dealt = evolvedwarfare$amount;
		net.minecraft.util.math.Vec3d point = evolvedwarfare$point;
		evolvedwarfare$point = null;
		evolvedwarfare$zone = null;
		evolvedwarfare$amount = 0.0F;
		if (--evolvedwarfare$depth <= 0) {
			evolvedwarfare$depth = 0;
			LivingEntity self = (LivingEntity) (Object) this;
			float lost = evolvedwarfare$lifeBefore - (self.getHealth() + self.getAbsorptionAmount());
			if (cir.getReturnValueZ() && lost > 0.0F && self instanceof net.minecraft.entity.player.PlayerEntity) {
				// heridas por partes (fase 92)
				com.evolvedwarfare.health.Health.onHurt(self, source, zone, point, lost);
				// venenos (fase 93)
				com.evolvedwarfare.health.Medicine.onHurt(self, source, lost);
			}
			if (cir.getReturnValueZ() && lost > 0.0F) {
				// lo que hacen los élite al pegar (fase 95)
				com.evolvedwarfare.difficulty.Elites.onHit(self, source, lost);
			}
			if (self instanceof net.minecraft.entity.player.PlayerEntity player && player.isBlocking() && amount > 0.0F && lost <= 0.0F) {
				// parar con el escudo cansa (fase 97)
				com.evolvedwarfare.health.Stamina.onBlocked(player, amount);
			}
			if (cir.getReturnValueZ()) {
				// los monstruos se avisan (fase 96)
				com.evolvedwarfare.difficulty.SmartMobs.onHurt(self, source);
				// la armadura de escamas de hielo ralentiza al que pega (fase 102)
				com.evolvedwarfare.legendary.DragonGear.onHurt(self, source);
			}
		}
		if (zone == HitZone.HEAD && cir.getReturnValueZ()) {
			LocationalDamage.headshot((LivingEntity) (Object) this, source);
		}
		if (cir.getReturnValueZ()) {
			SkillEffects.afterDamage((LivingEntity) (Object) this, source, dealt, zone);
			com.evolvedwarfare.world.city.CityLaw.onDamaged((LivingEntity) (Object) this, source);
			com.evolvedwarfare.combat.Dismemberment.afterHit((LivingEntity) (Object) this, source, zone, dealt, point);
		}
	}
}
