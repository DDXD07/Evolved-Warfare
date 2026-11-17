package com.evolvedwarfare.mixin;

import com.evolvedwarfare.food.FoodRot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lo que sale del ahumador sale <b>ahumado</b> (aguanta 3 veces más; fase 98). Para que la salida siga llenándose, al
 * comparar y al juntar se le quita un momento lo de ahumado a lo que ya hay en la salida.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceSmokeMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;canAcceptRecipeOutput(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/recipe/RecipeEntry;Lnet/minecraft/util/collection/DefaultedList;I)Z"))
	private static boolean evolvedwarfare$canAcceptSmoked(DynamicRegistryManager registries, RecipeEntry<?> recipe, DefaultedList<ItemStack> slots,
			int count, Operation<Boolean> original, @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
		if (!(furnace instanceof SmokerBlockEntity)) {
			return original.call(registries, recipe, slots, count);
		}
		ItemStack out = slots.get(2);
		Long exp = out.get(FoodRot.EXPIRES);
		Integer pres = out.get(FoodRot.PRESERVED);
		out.remove(FoodRot.EXPIRES);
		out.remove(FoodRot.PRESERVED);
		try {
			return original.call(registries, recipe, slots, count);
		} finally {
			if (exp != null) {
				out.set(FoodRot.EXPIRES, exp);
			}
			if (pres != null) {
				out.set(FoodRot.PRESERVED, pres);
			}
		}
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;craftRecipe(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/recipe/RecipeEntry;Lnet/minecraft/util/collection/DefaultedList;I)Z"))
	private static boolean evolvedwarfare$craftSmoked(DynamicRegistryManager registries, RecipeEntry<?> recipe, DefaultedList<ItemStack> slots,
			int count, Operation<Boolean> original, @Local(argsOnly = true) World world, @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
		if (!(furnace instanceof SmokerBlockEntity)) {
			return original.call(registries, recipe, slots, count);
		}
		ItemStack out = slots.get(2);
		out.remove(FoodRot.EXPIRES);
		out.remove(FoodRot.PRESERVED);
		boolean done = original.call(registries, recipe, slots, count);
		if (FoodRot.enabled()) {
			FoodRot.smoked(slots.get(2), world.getTime());
		}
		return done;
	}
}
