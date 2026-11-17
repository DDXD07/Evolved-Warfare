package com.evolvedwarfare.mixin;

import com.evolvedwarfare.difficulty.MendingRarity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.EnchantRandomlyLootFunction;
import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Los libros y objetos encantados al azar de los cofres y la pesca: la reparación, una entre 5000 (fase 100). */
@Mixin({EnchantRandomlyLootFunction.class, EnchantWithLevelsLootFunction.class})
public abstract class EnchantRandomlyMendingMixin {
	@Unique
	private static final ThreadLocal<Boolean> EVOLVEDWARFARE$BUSY = ThreadLocal.withInitial(() -> false);
	@Unique
	private static final ThreadLocal<ItemStack> EVOLVEDWARFARE$ORIGINAL = new ThreadLocal<>();

	@Inject(method = "process", at = @At("HEAD"))
	private void evolvedwarfare$remember(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
		if (!EVOLVEDWARFARE$BUSY.get()) {
			EVOLVEDWARFARE$ORIGINAL.set(stack.copy());
		}
	}

	@ModifyReturnValue(method = "process", at = @At("RETURN"))
	private ItemStack evolvedwarfare$rareMending(ItemStack result, ItemStack stack, LootContext context) {
		if (EVOLVEDWARFARE$BUSY.get()) {
			return result;
		}
		ItemStack original = EVOLVEDWARFARE$ORIGINAL.get();
		EVOLVEDWARFARE$ORIGINAL.remove();
		if (original == null) {
			return result;
		}
		ConditionalLootFunction self = (ConditionalLootFunction) (Object) this;
		return MendingRarity.filter(result, original, context.getRandom(), context.getWorld().getRegistryManager(), copy -> {
			EVOLVEDWARFARE$BUSY.set(true);
			try {
				return self.apply(copy, context);
			} finally {
				EVOLVEDWARFARE$BUSY.set(false);
			}
		});
	}
}
