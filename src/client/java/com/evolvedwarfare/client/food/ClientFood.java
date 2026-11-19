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
		net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(data ->
				data instanceof com.evolvedwarfare.food.FoodTimerTooltipData timer ? new TimerComponent(timerLines(timer.stack())) : null);
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
		});
	}

	/** Si a esta comida se le ve el contador (en un trozo aparte de la descripción, al 80 %; fase 122). */
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
			lines.add(Text.translatable("tooltip.evolvedwarfare.food.lasts", time(FoodRot.shelf(stack))).formatted(Formatting.DARK_GREEN));
			return lines;
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
		if (stack.getCount() > 1) {
			// el contador es el de la primera; las demás van detrás (fase 121, como en ARK)
			lines.add(Text.translatable("tooltip.evolvedwarfare.food.stack", time(FoodRot.shelf(stack)),
				time(Math.max(0L, FoodRot.allGone(stack) - now))).formatted(Formatting.DARK_GRAY));
		}
		return lines;
	}

	/** El trozo de la descripción con el contador, con la letra al 80 %. */
	private record TimerComponent(java.util.List<Text> lines) implements net.minecraft.client.gui.tooltip.TooltipComponent {
		private static final float SCALE = 0.8F;

		@Override
		public int getHeight() {
			return (int) Math.ceil(lines.size() * 10 * SCALE) + 2;
		}

		@Override
		public int getWidth(net.minecraft.client.font.TextRenderer textRenderer) {
			int w = 0;
			for (Text t : lines) {
				w = Math.max(w, textRenderer.getWidth(t));
			}
			return (int) Math.ceil(w * SCALE);
		}

		@Override
		public void drawItems(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, net.minecraft.client.gui.DrawContext context) {
			context.getMatrices().push();
			context.getMatrices().translate(x, y, 400.0F);
			context.getMatrices().scale(SCALE, SCALE, 1.0F);
			for (int i = 0; i < lines.size(); i++) {
				context.drawTextWithShadow(textRenderer, lines.get(i), 0, i * 10, 0xFFFFFFFF);
			}
			context.getMatrices().pop();
		}
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
