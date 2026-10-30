package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.weapon.MusketItem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apuntando con la mira de un arma de fuego, la cruz de vanilla se cambia por un punto diminuto en el centro (con la
 * misma mezcla que la cruz, así se ve claro sobre cualquier fondo). Con la pantalla de depuración abierta se deja la de
 * vanilla (los ejes).
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Unique
	private static final Identifier EVOLVEDWARFARE$DOT = Identifier.of("evolvedwarfare", "hud/musket_dot");

	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private DebugHud debugHud;

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$musketDot(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		ClientPlayerEntity player = client.player;
		if (player == null || !client.options.getPerspective().isFirstPerson() || debugHud.shouldShowDebugHud()
				|| !MusketItem.isAiming(player) || MusketItem.isReloading(player.getActiveItem())) {
			return;
		}
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
		// el píxel central de la cruz de vanilla (15x15)
		int x = (context.getScaledWindowWidth() - 15) / 2 + 7;
		int y = (context.getScaledWindowHeight() - 15) / 2 + 7;
		context.drawGuiTexture(EVOLVEDWARFARE$DOT, x, y, 1, 1);
		RenderSystem.defaultBlendFunc();
		ci.cancel();
	}
}
