package com.evolvedwarfare.client;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.HordeInfoPayload;
import com.evolvedwarfare.world.Hordes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * El contador de días y el reloj, en pequeño, arriba a la izquierda: el reloj de Minecraft (con el sol y la luna), "Día N"
 * y la hora (24 h). El día que viene una horda ({@link Hordes}), el día sale en rojo.
 */
public final class DayClockHud {
	private static final int MARGIN = 4;
	private static final float ICON_SCALE = 0.75F;
	private static final int RED = 0xFF5555;
	private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
	/** Cada cuántos días hay horda en este servidor (0: ninguna). */
	private static int every;

	private DayClockHud() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(HordeInfoPayload.ID, (payload, context) -> every = payload.every());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> every = 0);
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	/** La hora del día en formato 24 h ("06:00" al amanecer). */
	public static String clock(long timeOfDay) {
		long ticks = Math.floorMod(timeOfDay, Hordes.DAY);
		int hours = (int) ((ticks / 1000L + 6L) % 24L);
		int minutes = (int) (ticks % 1000L * 60L / 1000L);
		return String.format("%02d:%02d", hours, minutes);
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()
				|| !EwConfig.get().showDayClock) {
			return;
		}
		TextRenderer font = client.textRenderer;
		long timeOfDay = world.getTimeOfDay();
		long day = Hordes.day(timeOfDay);
		boolean horde = Hordes.isHordeDay(day, every);
		Text dayText = Text.translatable("hud.evolvedwarfare.day", day);
		String time = world.getDimension().hasFixedTime() ? "--:--" : clock(timeOfDay);
		int icon = MathHelper.ceil(16 * ICON_SCALE);
		int dayWidth = font.getWidth(dayText);
		int timeWidth = font.getWidth(time);
		int width = 3 + icon + 3 + dayWidth + 6 + timeWidth + 4;
		int height = icon + 4;
		int x = MARGIN;
		int y = MARGIN;
		context.fill(x, y, x + width, y + height, horde ? 0x70300000 : 0x60000000);
		if (horde) {
			context.fill(x, y, x + 1, y + height, 0xC0FF3030);
		}
		context.getMatrices().push();
		context.getMatrices().translate(x + 3, y + 2, 0.0F);
		context.getMatrices().scale(ICON_SCALE, ICON_SCALE, 1.0F);
		context.drawItem(CLOCK, 0, 0);
		context.getMatrices().pop();
		int textY = y + (height - font.fontHeight) / 2 + 1;
		int textX = x + 3 + icon + 3;
		context.drawTextWithShadow(font, dayText, textX, textY, horde ? RED : 0xFFFFFF);
		textX += dayWidth + 3;
		context.drawTextWithShadow(font, Text.literal("·").formatted(Formatting.DARK_GRAY), textX - 1, textY, 0x808080);
		context.drawTextWithShadow(font, time, textX + 3, textY, 0xC8C8C8);
	}
}
