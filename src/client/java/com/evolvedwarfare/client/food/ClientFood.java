package com.evolvedwarfare.client.food;

import com.evolvedwarfare.food.FoodRot;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** La comida que se pudre en el cliente (fase 98): el reloj para la barra de frescura y cómo está en la descripción. */
public final class ClientFood {
	private ClientFood() {
	}

	public static void register() {
		FoodRot.clientTime = () -> {
			MinecraftClient client = MinecraftClient.getInstance();
			return client.world == null ? -1L : client.world.getTime();
		};
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || !FoodRot.enabled()) {
				return;
			}
			if (stack.isOf(com.evolvedwarfare.food.FoodItems.SALT) || !FoodRot.perishable(stack)) {
				return;
			}
			long now = client.world.getTime();
			int preserved = FoodRot.preserved(stack);
			if ((preserved & FoodRot.SALTED) != 0) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.food.salted").formatted(Formatting.WHITE));
			}
			if ((preserved & FoodRot.SMOKED) != 0) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.food.smoked").formatted(Formatting.GOLD));
			}
			Long exp = FoodRot.expires(stack);
			if (exp == null) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.food.lasts", time(FoodRot.shelf(stack))).formatted(Formatting.DARK_GREEN));
				return;
			}
			FoodRot.Stage stage = FoodRot.stage(stack, now);
			Formatting color = switch (stage) {
				case FRESH -> Formatting.GREEN;
				case STALE -> Formatting.YELLOW;
				case SPOILING -> Formatting.GOLD;
				case ROTTEN -> Formatting.DARK_RED;
			};
			String key = "tooltip.evolvedwarfare.food." + stage.name().toLowerCase(java.util.Locale.ROOT);
			lines.add(Text.translatable(key, time(Math.max(0L, exp - now))).formatted(color));
		});
	}

	/** «2 días y 5 h», «5 h», «menos de 1 h» (del juego). */
	private static Text time(long ticks) {
		long hours = ticks / 1000L;
		long days = hours / 24L;
		hours %= 24L;
		if (days > 0) {
			return hours > 0 ? Text.translatable("tooltip.evolvedwarfare.food.days_hours", days, hours)
					: Text.translatable("tooltip.evolvedwarfare.food.days", days);
		}
		return hours > 0 ? Text.translatable("tooltip.evolvedwarfare.food.hours", hours) : Text.translatable("tooltip.evolvedwarfare.food.under_hour");
	}
}
