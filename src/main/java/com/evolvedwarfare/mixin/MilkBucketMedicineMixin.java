package com.evolvedwarfare.mixin;

import com.evolvedwarfare.health.Medicine;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.MilkBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** La leche ya no cura la infección ni el veneno fuerte (fase 93). */
@Mixin(MilkBucketItem.class)
public abstract class MilkBucketMedicineMixin {
	@WrapOperation(method = "finishUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;clearStatusEffects()Z"))
	private boolean evolvedwarfare$keepIllness(LivingEntity user, Operation<Boolean> original) {
		List<StatusEffectInstance> kept = Medicine.milkResistant(user);
		boolean cleared = original.call(user);
		for (StatusEffectInstance effect : kept) {
			user.addStatusEffect(effect);
		}
		return cleared;
	}
}
