package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skills;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Árbol de habilidades, Escudo: se gasta menos ("Escudo recio") y las hachas lo inutilizan menos ("Guardia rápida"). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySkillMixin {
	@ModifyVariable(method = "damageShield", at = @At("HEAD"), argsOnly = true)
	private float evolvedwarfare$sturdyShield(float amount) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) {
			return amount;
		}
		return amount * (1.0F - Skills.value(self, Perk.STURDY_SHIELD) / 100.0F);
	}

	@Inject(method = "disableShield", at = @At("TAIL"))
	private void evolvedwarfare$quickGuard(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient && Skills.has(self, Perk.QUICK_GUARD)) {
			self.getItemCooldownManager().set(Items.SHIELD, (int) (Skills.value(self, Perk.QUICK_GUARD) * 20));
		}
	}
}
