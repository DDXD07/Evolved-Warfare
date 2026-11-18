package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Survivor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabricar (o sacar del horno) algo por primera vez da experiencia de superviviente (fase 112). */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftedMixin {
	@Inject(method = "onCraftByPlayer", at = @At("HEAD"))
	private void evolvedwarfare$firstCraft(World world, PlayerEntity player, int amount, CallbackInfo ci) {
		if (!world.isClient && player instanceof ServerPlayerEntity sp) {
			Survivor.crafted(sp, (ItemStack) (Object) this);
		}
	}
}
