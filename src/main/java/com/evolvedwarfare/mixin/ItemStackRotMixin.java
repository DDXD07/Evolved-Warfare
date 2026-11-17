package com.evolvedwarfare.mixin;

import com.evolvedwarfare.food.FoodRot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La comida del inventario: se le pone la caducidad y, si toca, se pudre (fase 98). */
@Mixin(ItemStack.class)
public abstract class ItemStackRotMixin {
	@Inject(method = "inventoryTick", at = @At("HEAD"))
	private void evolvedwarfare$rot(World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
		if (world.isClient || !(entity instanceof PlayerEntity player) || (world.getTime() + slot) % 20 != 0) {
			return;
		}
		ItemStack self = (ItemStack) (Object) this;
		ItemStack rotten = FoodRot.check(self, world.getTime());
		if (rotten != null) {
			PlayerInventory inv = player.getInventory();
			for (int i = 0; i < inv.size(); i++) {
				if (inv.getStack(i) == self) {
					inv.setStack(i, rotten);
					break;
				}
			}
		}
	}
}
