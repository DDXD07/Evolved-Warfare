package com.evolvedwarfare.mixin;

import com.evolvedwarfare.food.FoodRot;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** La comida pasada alimenta menos y sienta mal (fase 98). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEatRotMixin {
	@ModifyVariable(method = "eatFood", at = @At("HEAD"), argsOnly = true)
	private FoodComponent evolvedwarfare$spoiledFood(FoodComponent food, @Local(argsOnly = true) World world,
			@Local(argsOnly = true) ItemStack stack) {
		return FoodRot.nourishment(food, stack, world.getTime());
	}

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void evolvedwarfare$spoiledEffects(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
		FoodRot.afterEating((PlayerEntity) (Object) this, stack, world.getTime());
	}
}
