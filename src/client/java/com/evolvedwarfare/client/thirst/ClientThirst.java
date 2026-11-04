package com.evolvedwarfare.client.thirst;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.network.ThirstPayload;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * La sed en el cliente: el medidor (diez gotas encima del hambre, verdosas con parásitos, que tiemblan cuando queda
 * poca), lo que quita cada cosa en su descripción y el color de las bebidas en frasco.
 */
public final class ClientThirst {
	private static final Identifier EMPTY = EvolvedWarfare.id("textures/gui/thirst/empty.png");
	private static final Identifier FULL = EvolvedWarfare.id("textures/gui/thirst/full.png");
	private static final Identifier HALF = EvolvedWarfare.id("textures/gui/thirst/half.png");
	private static final Identifier SICK_FULL = EvolvedWarfare.id("textures/gui/thirst/sick_full.png");
	private static final Identifier SICK_HALF = EvolvedWarfare.id("textures/gui/thirst/sick_half.png");

	private static float level = Thirst.MAX;
	private static boolean enabled;
	private static final Random SHAKE = Random.create();

	private ClientThirst() {
	}

	public static float level() {
		return level;
	}

	public static boolean enabled() {
		return enabled;
	}

	/** ¿Tiene tanta sed que no puede correr? */
	public static boolean parched() {
		return enabled && level <= Thirst.PARCHED;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ThirstPayload.ID, (payload, context) -> {
			level = payload.level();
			enabled = payload.enabled();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			level = Thirst.MAX;
			enabled = false;
		});
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> tooltip(stack, lines));
		// el líquido de los frascos (capa 0) de su color; el frasco (capa 1), tal cual
		color(ModItems.BOILED_WATER, 0xA6DDF5);
		color(ModItems.MELON_JUICE, 0xE8454E);
		color(ModItems.APPLE_JUICE, 0xE6AE3C);
		color(ModItems.BERRY_JUICE, 0x8E1838);
		DrinkAnimation.register();
	}

	private static void color(Item item, int rgb) {
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | rgb : -1, item);
	}

	private static void tooltip(ItemStack stack, List<Text> lines) {
		if (!enabled) {
			return;
		}
		float value = Thirst.valueOf(stack);
		if (value > 0.0F) {
			lines.add(Text.translatable("tooltip.evolvedwarfare.thirst.quench", format(value)).formatted(Formatting.AQUA));
		} else if (value < 0.0F) {
			lines.add(Text.translatable("tooltip.evolvedwarfare.thirst.dry", format(-value)).formatted(Formatting.GOLD));
		}
		if (Thirst.risky(stack)) {
			lines.add(Text.translatable("tooltip.evolvedwarfare.thirst.unboiled").formatted(Formatting.DARK_GREEN));
		}
	}

	private static String format(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/** Diez gotas encima del hambre (de derecha a izquierda, como la comida); encima de las burbujas si está bajo el agua. */
	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (!enabled || player == null || client.options.hudHidden || client.interactionManager == null
				|| !client.interactionManager.hasStatusBars() || player.getVehicle() instanceof LivingEntity) {
			return;
		}
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		int y = h - 49;
		if (player.isSubmergedIn(FluidTags.WATER) || player.getAir() < player.getMaxAir()) {
			y -= 10;
		}
		boolean sick = player.hasStatusEffect(ModEffects.PARASITES);
		int points = Math.round(level);
		SHAKE.setSeed(player.age / 3L * 312871L);
		for (int i = 0; i < 10; i++) {
			int x = w / 2 + 91 - i * 8 - 9;
			int dy = level <= 4.0F && player.age % (points * 3 + 1) == 0 ? SHAKE.nextInt(3) - 1 : 0;
			context.drawTexture(EMPTY, x, y + dy, 0, 0, 9, 9, 9, 9);
			if (points >= i * 2 + 2) {
				context.drawTexture(sick ? SICK_FULL : FULL, x, y + dy, 0, 0, 9, 9, 9, 9);
			} else if (points == i * 2 + 1) {
				context.drawTexture(sick ? SICK_HALF : HALF, x, y + dy, 0, 0, 9, 9, 9, 9);
			}
		}
	}
}
