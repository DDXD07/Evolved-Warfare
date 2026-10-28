package com.evolvedwarfare.client;

import com.evolvedwarfare.client.screen.ResearchScreen;
import com.evolvedwarfare.network.ResearchPayloads;
import com.evolvedwarfare.research.Research;
import com.evolvedwarfare.research.Tech;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Lo investigado, tal como lo manda el servidor (para la pantalla y para avisar en la descripción de los objetos). */
public final class ClientResearch {
	public static final Set<String> DONE = new HashSet<>();
	public static String current = "";
	public static float progress;
	public static int remaining;
	public static float speed = 1.0F;
	public static boolean required = true;
	public static BlockPos desk = BlockPos.ORIGIN;
	/** Tic del cliente cuando llegaron los datos (para descontar el tiempo que queda). */
	public static long receivedAt;

	private ClientResearch() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ResearchPayloads.Data.ID, (payload, context) -> {
			DONE.clear();
			DONE.addAll(payload.done());
			current = payload.current();
			progress = payload.progress();
			remaining = payload.remaining();
			speed = payload.speed();
			required = payload.required();
			if (!payload.desk().equals(BlockPos.ORIGIN)) {
				desk = payload.desk();
			}
			receivedAt = context.client().world != null ? context.client().world.getTime() : 0L;
			if (payload.open()) {
				context.client().setScreen(new ResearchScreen(payload.desk()));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			Tech tech = Research.lockOf(stack.getItem());
			MinecraftClient client = MinecraftClient.getInstance();
			if (tech != null && required && !DONE.contains(tech.id()) && client.player != null && !client.player.isCreative()) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.research_needed", Text.translatable(tech.translationKey()))
						.formatted(Formatting.RED));
			}
		});
	}

	public static boolean done(Tech tech) {
		return DONE.contains(tech.id());
	}

	@Nullable
	public static Tech current() {
		return current.isEmpty() ? null : Tech.byId(current);
	}

	/** Tics que quedan, descontando lo que ha pasado desde que llegaron los datos. */
	public static int remainingNow() {
		MinecraftClient client = MinecraftClient.getInstance();
		long elapsed = client.world != null ? client.world.getTime() - receivedAt : 0L;
		return (int) Math.max(0L, remaining - elapsed);
	}
}
