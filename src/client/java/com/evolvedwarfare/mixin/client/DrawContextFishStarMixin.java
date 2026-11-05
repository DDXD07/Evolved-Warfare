package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.fishing.FishData;
import com.evolvedwarfare.registry.ModComponents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La estrella de calidad de los peces (bronce, plata, oro o platino), abajo a la izquierda del icono. */
@Mixin(DrawContext.class)
public abstract class DrawContextFishStarMixin {
	private static final Identifier STARS = EvolvedWarfare.id("textures/gui/fish_stars.png");

	@Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void evolvedwarfare$star(TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
		FishData data = stack.isEmpty() ? null : stack.get(ModComponents.FISH);
		if (data == null) {
			return;
		}
		DrawContext self = (DrawContext) (Object) this;
		int q = Math.max(1, Math.min(4, data.quality()));
		self.getMatrices().push();
		self.getMatrices().translate(0.0F, 0.0F, 200.0F);
		self.drawTexture(STARS, x, y + 9, (q - 1) * 7, 0, 7, 7, 28, 7);
		self.getMatrices().pop();
	}
}
