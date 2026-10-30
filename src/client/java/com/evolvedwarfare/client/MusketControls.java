package com.evolvedwarfare.client;

import com.evolvedwarfare.network.FirearmPayload;
import com.evolvedwarfare.weapon.MusketItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Controles de las armas de fuego: clic izquierdo dispara, clic derecho (mantener) apunta y R recarga.
 *
 * <p>El clic izquierdo se recoge antes que vanilla (al principio del tick), así funciona también mientras se apunta
 * (vanilla no ataca mientras se usa un objeto) y nunca pega ni rompe bloques con el mosquete.</p>
 */
public final class MusketControls {
	public static KeyBinding reload;

	private MusketControls() {
	}

	public static void register() {
		reload = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.evolvedwarfare.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
				"key.categories.evolvedwarfare"));
		ClientTickEvents.START_CLIENT_TICK.register(MusketControls::tick);
		// con el mosquete en la mano no se pega ni se pican bloques
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> holdsMusket(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> holdsMusket(player) ? ActionResult.FAIL : ActionResult.PASS);
	}

	private static boolean holdsMusket(PlayerEntity player) {
		return player.getMainHandStack().getItem() instanceof MusketItem;
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.interactionManager == null) {
			return;
		}
		boolean musket = holdsMusket(player) && !player.isSpectator();
		if (musket && client.currentScreen == null) {
			while (client.options.attackKey.wasPressed()) {
				fire(player);
			}
		}
		while (reload.wasPressed()) {
			if (musket) {
				if (MusketItem.isAiming(player)) {
					client.interactionManager.stopUsingItem(player);
				}
				ClientPlayNetworking.send(new FirearmPayload(FirearmPayload.RELOAD));
			}
		}
	}

	private static void fire(ClientPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		ClientPlayNetworking.send(new FirearmPayload(FirearmPayload.FIRE));
		if (MusketItem.isLoaded(stack) && !MusketItem.isReloading(stack) && !player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			// lo que se ve al momento: el rastrillo cae, el arma retrocede y la vista sube un poco
			MusketItem.predictFire(stack, player);
			player.setPitch(MathHelper.clamp(player.getPitch() - MusketItem.KICK, -90.0F, 90.0F));
		}
	}
}
