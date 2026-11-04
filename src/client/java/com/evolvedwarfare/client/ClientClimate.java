package com.evolvedwarfare.client;

import com.evolvedwarfare.climate.Climate;
import com.evolvedwarfare.climate.Season;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.ClimatePayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * El clima en el cliente: lo que manda el servidor cada segundo (cuerpo, aire, estación), la temperatura del cuerpo
 * encima de los corazones (azul con frío, roja con calor, parpadeando en hipotermia o hipertermia), un velo rojo con el
 * golpe de calor y los colores de la estación (al cambiar de estación se vuelven a pintar los chunks).
 */
public final class ClientClimate {
	@Nullable
	private static ClimatePayload last;

	private ClientClimate() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ClimatePayload.ID, (payload, context) -> receive(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			last = null;
			Climate.tintSeason = null;
			Climate.biomeOffset = 0.0F;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	@Nullable
	public static ClimatePayload last() {
		return last;
	}

	private static void receive(MinecraftClient client, ClimatePayload payload) {
		last = payload;
		Season season = Season.values()[Math.floorMod(payload.season(), 4)];
		Season tint = payload.seasons() ? season : null;
		Climate.biomeOffset = payload.seasons() ? season.biomeOffset : 0.0F;
		if (tint != Climate.tintSeason) {
			Climate.tintSeason = tint;
			if (client.worldRenderer != null) {
				client.worldRenderer.reload();
			}
		}
	}

	/** Una temperatura con su unidad (°C o, si lo pide la configuración, °F). */
	public static String format(float celsius, int decimals) {
		boolean f = EwConfig.get().temperatureFahrenheit;
		float value = f ? Climate.fahrenheit(celsius) : celsius;
		String number = decimals == 0 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%." + decimals + "f", value);
		return number + (f ? " °F" : " °C");
	}

	/** Color de la temperatura del cuerpo: azul por debajo de 36, blanco normal, naranja y rojo por encima de 38. */
	public static int bodyColor(float body) {
		if (body < Climate.HYPOTHERMIA) {
			return 0x55AAFF;
		}
		if (body < 36.3F) {
			return 0xA8D8FF;
		}
		if (body > Climate.HYPERTHERMIA) {
			return 0xFF5040;
		}
		if (body > 37.8F) {
			return 0xFFB060;
		}
		return 0xFFFFFF;
	}

	/** Color de la temperatura del aire. */
	public static int airColor(float air) {
		if (air < 0.0F) {
			return 0x9AD8FF;
		}
		if (air < 12.0F) {
			return 0xC8E8FF;
		}
		if (air > 32.0F) {
			return 0xFF8050;
		}
		if (air > 25.0F) {
			return 0xFFD080;
		}
		return 0xE8E8E8;
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClimatePayload c = last;
		PlayerEntity player = client.player;
		if (c == null || !c.enabled() || player == null || client.options.hudHidden || client.interactionManager == null
				|| !client.interactionManager.hasStatusBars()) {
			return;
		}
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		// golpe de calor: un velo rojo que late
		if (c.body() > Climate.HYPERTHERMIA) {
			float pulse = 0.5F + 0.5F * MathHelper.sin(player.age * 0.15F);
			int alpha = (int) (MathHelper.clamp((c.body() - Climate.HYPERTHERMIA) / 2.0F, 0.2F, 1.0F) * (40 + 40 * pulse));
			context.fill(0, 0, w, h, (alpha << 24) | 0xB01800);
		}
		if (!EwConfig.get().showBodyTemperature) {
			return;
		}
		// encima de los corazones (y de la armadura, si la lleva)
		TextRenderer font = client.textRenderer;
		float hearts = player.getMaxHealth() + player.getAbsorptionAmount();
		int rows = MathHelper.ceil(hearts / 2.0F / 10.0F);
		int spacing = Math.max(10 - (rows - 2), 3);
		int top = h - 39 - (rows - 1) * spacing;
		if (player.getArmor() > 0) {
			top -= 10;
		}
		int x = w / 2 - 91;
		int y = top - 10;
		boolean danger = c.body() < Climate.HYPOTHERMIA || c.body() > Climate.HYPERTHERMIA;
		if (danger && (player.age / 10) % 2 == 0) {
			return;
		}
		String text = format(c.body(), 1);
		Text label = danger ? Text.translatable(c.body() < Climate.HYPOTHERMIA ? "hud.evolvedwarfare.body.hypothermia"
				: "hud.evolvedwarfare.body.hyperthermia").append(" " + text) : Text.literal(text);
		int width = font.getWidth(label);
		context.fill(x - 1, y - 1, x + width + 8, y + font.fontHeight, 0x50000000);
		// termómetro: un palito con su bulbo, lleno según la temperatura
		int fill = (int) MathHelper.clamp((c.body() - 33.0F) / 8.0F * 6.0F, 1.0F, 6.0F);
		int color = 0xFF000000 | bodyColor(c.body());
		context.fill(x + 1, y, x + 3, y + 6, 0xFF404040);
		context.fill(x + 1, y + 6 - fill, x + 3, y + 6, color);
		context.fill(x, y + 6, x + 4, y + 8, color);
		context.drawTextWithShadow(font, label, x + 6, y, bodyColor(c.body()));
	}
}
