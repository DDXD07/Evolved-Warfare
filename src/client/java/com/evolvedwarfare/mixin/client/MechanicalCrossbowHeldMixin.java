package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.engineering.MechanicalCrossbowItem;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Las ballestas mecánicas (fase 80) se cogen y se tensan en primera persona como la ballesta del juego: el juego solo lo
 * hace con la suya ({@code isOf(Items.CROSSBOW)}); aquí cuenta también con las nuestras.
 */
@Mixin(HeldItemRenderer.class)
public abstract class MechanicalCrossbowHeldMixin {
	private static boolean evolvedwarfare$crossbow(ItemStack stack, Item item) {
		return item == Items.CROSSBOW ? stack.isOf(Items.CROSSBOW) || stack.getItem() instanceof MechanicalCrossbowItem : stack.isOf(item);
	}

	@Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"),
			require = 0)
	private boolean evolvedwarfare$firstPerson(ItemStack stack, Item item) {
		return evolvedwarfare$crossbow(stack, item);
	}

	@Redirect(method = "isChargedCrossbow", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"),
			require = 0)
	private static boolean evolvedwarfare$charged(ItemStack stack, Item item) {
		return evolvedwarfare$crossbow(stack, item);
	}

	@Redirect(method = "getHandRenderType", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"),
			require = 0)
	private static boolean evolvedwarfare$handType(ItemStack stack, Item item) {
		return evolvedwarfare$crossbow(stack, item);
	}

	@Redirect(method = "getUsingItemHandRenderType", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"), require = 0)
	private static boolean evolvedwarfare$usingHandType(ItemStack stack, Item item) {
		return evolvedwarfare$crossbow(stack, item);
	}
}
