package com.evolvedwarfare.mixin;

import com.evolvedwarfare.combat.Dismemberment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Desmembramiento: sin la mano de ese lado no se coge nada con ella (se cae); sin brazos no se golpea. */
@Mixin(MobEntity.class)
public abstract class MobEntityDismemberMixin {
	@Inject(method = "equipStack", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$noHand(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (!stack.isEmpty() && Dismemberment.isNpc(self) && Dismemberment.noHandFor(self, slot)) {
			if (!self.getWorld().isClient) {
				self.dropStack(stack.copy());
			}
			ci.cancel();
		}
	}

	@Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$noArms(Entity target, CallbackInfoReturnable<Boolean> cir) {
		MobEntity self = (MobEntity) (Object) this;
		if (Dismemberment.isNpc(self) && !Dismemberment.hasAnArm(self)) {
			cir.setReturnValue(false);
		}
	}
}
