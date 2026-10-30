package com.evolvedwarfare.client;

import com.evolvedwarfare.network.PlaceNoticePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * El nombre del sitio al que llegas (ciudad, asentamiento…) en pequeño, encima de los corazones y la barra de hambre
 * (y de la línea de avisos de vanilla), en vez del título grande en mitad de la pantalla. Aparece, se queda unos 3 s y
 * se va desvaneciendo, como el título de vanilla.
 */
public final class PlaceNoticeHud {
	private static final long FADE_IN_MS = 500L;
	private static final long STAY_MS = 3000L;
	private static final long FADE_OUT_MS = 1000L;
	/** El nombre, un poco más grande que el texto normal (el título de vanilla va a ×4 y su línea de abajo a ×2). */
	private static final float TITLE_SCALE = 1.5F;

	@Nullable
	private static Text title;
	@Nullable
	private static Text subtitle;
	private static long since;

	private PlaceNoticeHud() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlaceNoticePayload.ID, (payload, context) -> show(payload.title(), payload.subtitle()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> title = null);
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	public static void show(Text title, Text subtitle) {
		PlaceNoticeHud.title = title;
		PlaceNoticeHud.subtitle = subtitle;
		since = System.currentTimeMillis();
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (title == null || client.options.hudHidden) {
			return;
		}
		long age = System.currentTimeMillis() - since;
		if (age > FADE_IN_MS + STAY_MS + FADE_OUT_MS) {
			title = null;
			return;
		}
		float alpha = age < FADE_IN_MS ? age / (float) FADE_IN_MS
				: age > FADE_IN_MS + STAY_MS ? 1.0F - (age - FADE_IN_MS - STAY_MS) / (float) FADE_OUT_MS : 1.0F;
		int a = MathHelper.clamp((int) (alpha * 255.0F), 0, 255);
		if (a < 8) {
			return; // con menos, el texto de vanilla sale opaco
		}
		int color = (a << 24) | 0xFFFFFF;
		TextRenderer font = client.textRenderer;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		// la línea de avisos de vanilla va de height-72 a height-63; esto, justo encima
		int subtitleY = height - 72 - 4 - font.fontHeight;
		int titleY = subtitleY - 2 - MathHelper.ceil(font.fontHeight * TITLE_SCALE);
		if (subtitle != null && !subtitle.getString().isEmpty()) {
			context.drawTextWithShadow(font, subtitle, width / 2 - font.getWidth(subtitle) / 2, subtitleY, color);
		} else {
			titleY = subtitleY - 2;
		}
		context.getMatrices().push();
		context.getMatrices().translate(width / 2.0F, titleY, 0.0F);
		context.getMatrices().scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
		context.drawTextWithShadow(font, title, -font.getWidth(title) / 2, 0, color);
		context.getMatrices().pop();
	}
}
