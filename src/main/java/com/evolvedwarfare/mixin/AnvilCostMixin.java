package com.evolvedwarfare.mixin;

import com.evolvedwarfare.difficulty.Enchanting;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** El yunque cuesta más niveles (fase 100); con 40 o más, «¡Demasiado caro!». */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilCostMixin extends ForgingScreenHandler {
	@Shadow
	@Final
	private Property levelCost;

	protected AnvilCostMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
		super(type, syncId, playerInventory, context);
	}

	@Inject(method = "updateResult", at = @At("TAIL"))
	private void evolvedwarfare$harderAnvil(CallbackInfo ci) {
		int cost = levelCost.get();
		int harder = Enchanting.anvilCost(cost);
		if (harder != cost) {
			levelCost.set(harder);
			if (harder >= 40 && !player.getAbilities().creativeMode) {
				output.setStack(0, ItemStack.EMPTY);
			}
			sendContentUpdates();
		}
	}
}
