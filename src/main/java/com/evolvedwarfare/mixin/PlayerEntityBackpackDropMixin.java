package com.evolvedwarfare.mixin;

import com.evolvedwarfare.backpack.Backpacks;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al morir, la mochila cae entera (con todo dentro), salvo que se conserve el inventario. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBackpackDropMixin {
	@Inject(method = "dropInventory", at = @At("HEAD"))
	private void evolvedwarfare$dropBackpack(CallbackInfo ci) {
		Backpacks.dropOnDeath((PlayerEntity) (Object) this);
	}
}
