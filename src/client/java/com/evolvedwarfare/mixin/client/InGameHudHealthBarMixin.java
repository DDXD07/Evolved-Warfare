package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.health.ClientHealthBar;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Con más de 15 corazones (fase 114) los corazones se juntan en una barra de vida: en vez de las filas de corazones se
 * dibuja la barra, y la armadura se queda justo encima (como con una sola fila).
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHealthBarMixin {
	@Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$healthBar(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex,
			float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
		if (ClientHealthBar.active(player)) {
			ClientHealthBar.render(context, player, x, y);
			ci.cancel();
		}
	}

	@WrapOperation(method = "renderStatusBars", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/InGameHud;renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V"))
	private void evolvedwarfare$armorAboveBar(DrawContext context, PlayerEntity player, int y, int lines, int spacing, int x, Operation<Void> original) {
		if (ClientHealthBar.active(player)) {
			original.call(context, player, y, 1, 10, x);
		} else {
			original.call(context, player, y, lines, spacing, x);
		}
	}
}
