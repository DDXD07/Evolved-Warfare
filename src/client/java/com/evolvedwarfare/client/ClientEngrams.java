package com.evolvedwarfare.client;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.network.EngramPayloads;
import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Los engramas en el cliente (fase 113): lo que desbloquea cada uno (lo manda el servidor), cuáles tiene aprendidos el
 * jugador propio (para que el cliente pique y use igual que el servidor) y la línea roja en la descripción de lo que aún
 * no puedes fabricar ni usar.
 */
public final class ClientEngrams {
	private ClientEngrams() {
	}

	public static void register() {
		Engrams.load();
		ClientPlayNetworking.registerGlobalReceiver(EngramPayloads.Catalog.ID, (payload, context) -> Engrams.readCatalog(payload.data()));
		ClientBridge.clientEngramLearned = (player, engram) -> player != MinecraftClient.getInstance().player
				|| ClientSkills.data.engrams.contains(engram);
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return;
			}
			Engram engram = Engrams.missing(client.player, stack);
			if (engram != null) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.engrams.needed", Text.translatable(engram.translationKey()), engram.level)
						.formatted(Formatting.RED));
			}
		});
	}
}
