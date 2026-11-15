package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.engineering.MechanicalCrossbowItem;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** En tercera persona, las ballestas mecánicas cargadas se apuntan con las dos manos, como la del juego (fase 80). */
@Mixin(PlayerEntityRenderer.class)
public abstract class MechanicalCrossbowPoseMixin {
	@Redirect(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"),
			require = 0)
	private static boolean evolvedwarfare$crossbowPose(ItemStack stack, Item item) {
		return item == Items.CROSSBOW ? stack.isOf(Items.CROSSBOW) || stack.getItem() instanceof MechanicalCrossbowItem : stack.isOf(item);
	}
}
