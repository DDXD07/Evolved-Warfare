package com.evolvedwarfare.mixin;

import com.evolvedwarfare.difficulty.Enchanting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** La mesa de encantamientos cuesta más y a veces maldice (fase 100). */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentTableMixin {
	@Shadow
	@Final
	private Inventory inventory;

	@Unique
	private ItemStack evolvedwarfare$before = ItemStack.EMPTY;

	@Inject(method = "onButtonClick", at = @At("HEAD"))
	private void evolvedwarfare$remember(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
		evolvedwarfare$before = inventory.getStack(0).copy();
	}

	@Inject(method = "onButtonClick", at = @At("RETURN"))
	private void evolvedwarfare$harder(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
		ItemStack after = inventory.getStack(0);
		if (cir.getReturnValueZ() && id >= 0 && id <= 2 && !player.getWorld().isClient && !after.isEmpty()
				&& !ItemStack.areEqual(evolvedwarfare$before, after)) {
			Enchanting.afterTableEnchant(player, after, inventory.getStack(1), id);
			inventory.markDirty();
		}
		evolvedwarfare$before = ItemStack.EMPTY;
	}
}
