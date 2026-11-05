package com.evolvedwarfare.client.fishing;

import com.evolvedwarfare.fishing.FishData;
import com.evolvedwarfare.fishing.FishingPayloads;
import com.evolvedwarfare.registry.ModComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** El minijuego de pesca en el cliente: abre la pantalla al picar, la cierra si el servidor la acaba, y el peso y la calidad de los peces. */
public final class ClientFishing {
	private ClientFishing() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FishingPayloads.Start.ID, (payload, context) -> context.client().setScreen(new FishingScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(FishingPayloads.End.ID, (payload, context) -> {
			if (context.client().currentScreen instanceof FishingScreen screen && screen.session() == payload.session()) {
				screen.abort();
			}
		});
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			FishData data = stack.get(ModComponents.FISH);
			if (data == null) {
				return;
			}
			int q = data.quality();
			Text stars = Text.literal("★".repeat(q) + "☆".repeat(4 - q)).withColor(FishData.color(q));
			lines.add(1, Text.empty().append(stars).append(" ").append(Text.translatable("quality.evolvedwarfare." + FishData.key(q))
					.withColor(FishData.color(q))).append(Text.literal(String.format(" · %.2f kg", data.kg())).formatted(Formatting.GRAY)));
			lines.add(2, Text.translatable("tooltip.evolvedwarfare.fish.mailbox").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		});
	}

	public static boolean inGame() {
		return MinecraftClient.getInstance().currentScreen instanceof FishingScreen;
	}
}
