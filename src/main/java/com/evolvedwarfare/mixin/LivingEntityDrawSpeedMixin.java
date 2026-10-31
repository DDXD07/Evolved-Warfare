package com.evolvedwarfare.mixin;

import com.evolvedwarfare.armor.ArmorClass;
import com.evolvedwarfare.armor.ClassArmorItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.RangedWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armadura de arquero: tensa los arcos y carga las ballestas más rápido. Cada tic que se está usando uno, se suma la
 * ventaja (0,25 = +25 %) y, cuando junta un tic entero, el uso avanza uno de más. Va igual en el servidor y en el
 * cliente (para que el arco se vea tensarse al mismo ritmo).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDrawSpeedMixin {
	@Shadow
	protected int itemUseTimeLeft;

	@Unique
	private float evolvedwarfare$drawCarry;

	@Inject(method = "tickActiveItemStack", at = @At("TAIL"))
	private void evolvedwarfare$fasterDraw(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.isUsingItem() || !(self.getActiveItem().getItem() instanceof RangedWeaponItem)) {
			evolvedwarfare$drawCarry = 0.0F;
			return;
		}
		float bonus = ClassArmorItem.bonus(self, ArmorClass.Effect.DRAW_SPEED);
		if (bonus <= 0.0F) {
			return;
		}
		evolvedwarfare$drawCarry += bonus;
		if (evolvedwarfare$drawCarry >= 1.0F && itemUseTimeLeft > 1) {
			evolvedwarfare$drawCarry -= 1.0F;
			itemUseTimeLeft--;
		}
	}
}
