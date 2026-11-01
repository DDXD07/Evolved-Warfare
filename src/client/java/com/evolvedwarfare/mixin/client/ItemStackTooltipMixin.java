package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.EvolvedWarfare;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Los objetos y bloques del mod enseñan su descripción y sus estadísticas (daño, velocidad de ataque, armadura,
 * dureza...) solo manteniendo Control; si no, en su lugar sale una sola línea, «Mantén Control para más info». El nombre
 * y los encantamientos se ven siempre.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	/** Si ya se ha puesto la línea de Control en esta descripción (para no repetirla). */
	@Unique
	private boolean evolvedwarfare$hinted;

	@Shadow
	public abstract Item getItem();

	@Unique
	private boolean evolvedwarfare$folded() {
		return EvolvedWarfare.MOD_ID.equals(Registries.ITEM.getId(getItem()).getNamespace()) && !Screen.hasControlDown();
	}

	@Inject(method = "getTooltip", at = @At("HEAD"))
	private void evolvedwarfare$start(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type,
			CallbackInfoReturnable<List<Text>> cir) {
		evolvedwarfare$hinted = false;
	}

	/** La descripción de cada objeto (su appendTooltip). */
	@WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/item/Item;appendTooltip(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/Item$TooltipContext;Ljava/util/List;Lnet/minecraft/item/tooltip/TooltipType;)V"))
	private void evolvedwarfare$foldDescription(Item item, ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
			TooltipType type, Operation<Void> original) {
		if (!evolvedwarfare$folded()) {
			original.call(item, stack, context, tooltip, type);
			return;
		}
		List<Text> hidden = new ArrayList<>();
		original.call(item, stack, context, hidden, type);
		if (!hidden.isEmpty()) {
			evolvedwarfare$hint(tooltip::add);
		}
	}

	/** Las estadísticas (los atributos: «En la mano principal: 9 de daño»...). */
	@WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;appendAttributeModifiersTooltip(Ljava/util/function/Consumer;Lnet/minecraft/entity/player/PlayerEntity;)V"))
	private void evolvedwarfare$foldStats(ItemStack stack, Consumer<Text> textConsumer, @Nullable PlayerEntity player,
			Operation<Void> original) {
		if (!evolvedwarfare$folded()) {
			original.call(stack, textConsumer, player);
			return;
		}
		List<Text> hidden = new ArrayList<>();
		original.call(stack, (Consumer<Text>) hidden::add, player);
		if (!hidden.isEmpty()) {
			evolvedwarfare$hint(textConsumer);
		}
	}

	@Unique
	private void evolvedwarfare$hint(Consumer<Text> out) {
		if (!evolvedwarfare$hinted) {
			evolvedwarfare$hinted = true;
			out.accept(Text.translatable("tooltip.evolvedwarfare.hold_control").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		}
	}
}
