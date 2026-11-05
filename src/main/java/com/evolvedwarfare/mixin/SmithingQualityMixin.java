package com.evolvedwarfare.mixin;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.smithing.ForgeQuality;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Al mejorar a netherita una herramienta forjada, la calidad se vuelve a poner sobre la de netherita (más durabilidad). */
@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingQualityMixin {
	@Inject(method = "craft(Lnet/minecraft/recipe/input/SmithingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
			at = @At("RETURN"))
	private void evolvedwarfare$quality(SmithingRecipeInput input, RegistryWrapper.WrapperLookup lookup, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack out = cir.getReturnValue();
		Integer q = out.get(ModComponents.FORGE_QUALITY);
		if (q != null) {
			ForgeQuality.apply(out, q);
		}
	}
}
