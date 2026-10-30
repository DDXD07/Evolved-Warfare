package com.evolvedwarfare.mixin;

import com.evolvedwarfare.weapon.MedievalWeapons;
import com.evolvedwarfare.weapon.MeleeSwing;
import com.evolvedwarfare.weapon.WeaponType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lo que dura el golpe con cada arma cuerpo a cuerpo (la animación; el daño va igual que siempre): la daga es más
 * rápida que el puño y el martillo de guerra casi el doble de lento. Va en los dos lados para que coincidan.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	@Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$weaponSwing(CallbackInfoReturnable<Integer> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		Hand hand = self.preferredHand == null ? Hand.MAIN_HAND : self.preferredHand;
		WeaponType type = MedievalWeapons.typeOf(self.getStackInHand(hand));
		if (type != null) {
			cir.setReturnValue(Math.max(2, Math.round(cir.getReturnValueI() * MeleeSwing.ticks(type) / 6.0F)));
		}
	}
}
