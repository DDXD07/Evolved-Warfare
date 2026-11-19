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
			int preserved = FoodRot.preserved(stack);
			if ((preserved & FoodRot.SALTED) != 0) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.food.salted").formatted(Formatting.WHITE));
			}
			if ((preserved & FoodRot.SMOKED) != 0) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.food.smoked").formatted(Formatting.GOLD));
			}
			lines.addAll(timerLines(stack));
		});
	}

	/** Si a esta comida se le ve el contador. */
	public static boolean showsTimer(net.minecraft.item.ItemStack stack) {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null && FoodRot.enabled() && !stack.isEmpty() && !stack.isOf(com.evolvedwarfare.food.FoodItems.SALT)
				&& FoodRot.perishable(stack);
	}

	/** Las líneas del contador: cómo está y cuánto le queda (y, con más de una, cada cuánto se pudre una). */
	public static java.util.List<Text> timerLines(net.minecraft.item.ItemStack stack) {
		java.util.List<Text> lines = new java.util.ArrayList<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return lines;
		}
		long now = client.world.getTime();
		Long exp = FoodRot.expires(stack);
		if (exp == null) {
			lines.add(Text.literal("⌛ ~" + compact(FoodRot.shelf(stack))).formatted(Formatting.DARK_GREEN));
			return lines;
		}
		FoodRot.Stage stage = FoodRot.stage(stack, now);
		if (stage == FoodRot.Stage.ROTTEN) {
			lines.add(Text.translatable("tooltip.evolvedwarfare.food.rotten").formatted(Formatting.DARK_RED));
			return lines;
		}
		// corto (fase 122): el reloj y lo que le queda; el color dice cómo está (verde fresca, amarillo pasada, naranja pocha)
		Formatting color = switch (stage) {
			case FRESH -> Formatting.GREEN;
			case STALE -> Formatting.YELLOW;
			default -> Formatting.GOLD;
		};
		String text = "⌛ " + compact(Math.max(0L, exp - now));
		if (stack.getCount() > 1) {
			// con más de una: lo que tarda en pudrirse el montón entero (de una en una, como en ARK)
			text += " · ×" + stack.getCount() + " " + compact(Math.max(0L, FoodRot.allGone(stack) - now));
		}
		lines.add(Text.literal(text).formatted(color));
		return lines;
	}

	/** «5d 18h», «18h», «<1h» (del juego). */
	private static String compact(long ticks) {
		long hours = ticks / 1000L;
		long days = hours / 24L;
		hours %= 24L;
		if (days > 0) {
			return hours > 0 ? days + "d " + hours + "h" : days + "d";
		}
		return hours > 0 ? hours + "h" : "<1h";
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
