package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.inventory.StackLimit;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Las pilas grandes se cuentan en corto para que quepan en la casilla: 1500 → "1.5k". */
@Mixin(DrawContext.class)
public abstract class DrawContextCountMixin {
	@ModifyVariable(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
			at = @At("HEAD"), argsOnly = true)
	private @Nullable String evolvedwarfare$shortCount(@Nullable String count, TextRenderer renderer, ItemStack stack, int x, int y) {
		if (count == null && stack.getCount() >= 1000) {
			return StackLimit.shortCount(stack.getCount());
		}
		return count;
	}
}
