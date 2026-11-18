package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Engramas (fase 113; antes, las ventajas de "forja"): si el resultado de la mesa de crafteo (o del inventario) es algo
 * cuyo engrama el jugador aún no ha aprendido, el resultado se queda vacío y se le dice qué le falta.
 */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
	@Inject(method = "updateResult", at = @At("TAIL"))
	private static void evolvedwarfare$requireResearch(ScreenHandler handler, World world, PlayerEntity player,
			RecipeInputInventory craftingInventory, CraftingResultInventory resultInventory, @Nullable RecipeEntry<CraftingRecipe> recipe,
			CallbackInfo ci) {
		if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		ItemStack result = resultInventory.getStack(0);
		Engram engram = result.isEmpty() ? null : Engrams.missing(serverPlayer, result);
		if (engram == null) {
			return;
		}
		resultInventory.setStack(0, ItemStack.EMPTY);
		handler.setPreviousTrackedSlot(0, ItemStack.EMPTY);
		serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
		Engrams.warn(serverPlayer, engram, "craft");
	}
}
