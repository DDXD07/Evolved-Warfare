package com.evolvedwarfare.mixin;

import com.evolvedwarfare.research.Research;
import com.evolvedwarfare.research.Tech;
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Investigación obligatoria: si el resultado de la mesa de crafteo (o del inventario) es algo que el jugador aún no ha
 * investigado, el resultado se queda vacío y se le dice qué le falta.
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
		if (result.isEmpty() || Research.canCraft(serverPlayer, result)) {
			return;
		}
		Tech tech = Research.lockOf(result.getItem());
		resultInventory.setStack(0, ItemStack.EMPTY);
		handler.setPreviousTrackedSlot(0, ItemStack.EMPTY);
		serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
		if (tech != null) {
			serverPlayer.sendMessage(Text.translatable("message.evolvedwarfare.research.locked", Text.translatable(tech.translationKey()))
					.formatted(Formatting.RED), true);
		}
	}
}
