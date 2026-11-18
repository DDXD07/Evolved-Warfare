package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La mesa de herrería (la netherita, las armaduras de escamas...) pide el engrama de lo que sale (fase 113). */
@Mixin(SmithingScreenHandler.class)
public abstract class SmithingEngramMixin {
	@Unique
	private PlayerEntity evolvedwarfare$player;

	@Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V", at = @At("RETURN"))
	private void evolvedwarfare$rememberPlayer(int syncId, PlayerInventory inventory, ScreenHandlerContext context, CallbackInfo ci) {
		evolvedwarfare$player = inventory.player;
	}

	@Inject(method = "updateResult", at = @At("TAIL"))
	private void evolvedwarfare$requireEngram(CallbackInfo ci) {
		PlayerEntity player = evolvedwarfare$player;
		if (player == null || player.getWorld().isClient) {
			return;
		}
		Slot out = ((ScreenHandler) (Object) this).getSlot(3);
		Engram engram = out.getStack().isEmpty() ? null : Engrams.missing(player, out.getStack());
		if (engram != null) {
			out.setStackNoCallbacks(ItemStack.EMPTY);
			Engrams.warn(player, engram, "craft");
		}
	}
}
