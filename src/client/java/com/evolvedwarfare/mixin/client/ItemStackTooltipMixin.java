package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.armor.ClassArmorItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * La descripción de los objetos y bloques del mod (lo que escribe cada uno en su appendTooltip) solo se ve manteniendo
 * Control; si no, en su lugar sale «Mantén Control para más info». El nombre, los atributos, encantamientos, etc. se ven
 * siempre. En las armaduras de clase, la tecla de sus estadísticas (R) también la enseña.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/item/Item;appendTooltip(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/Item$TooltipContext;Ljava/util/List;Lnet/minecraft/item/tooltip/TooltipType;)V"))
	private void evolvedwarfare$hideDescription(Item item, ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
			TooltipType type, Operation<Void> original) {
		if (!EvolvedWarfare.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace()) || Screen.hasControlDown()
				|| (item instanceof ClassArmorItem && ClassArmorItem.statsKey.getAsBoolean())) {
			original.call(item, stack, context, tooltip, type);
			return;
		}
		List<Text> description = new ArrayList<>();
		original.call(item, stack, context, description, type);
		if (!description.isEmpty()) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.hold_control").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		}
	}
}
