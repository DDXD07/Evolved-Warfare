package com.evolvedwarfare.research;

import com.evolvedwarfare.block.ResearchDeskBlockEntity;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.ResearchPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La investigación de cada jugador: qué tiene, qué puede empezar y cómo avanza (en su mesa de investigación, más
 * rápido con estanterías alrededor). Con la investigación obligatoria (configuración), lo que desbloquea una técnica
 * no se puede fabricar hasta tenerla; en creativo, sí.
 */
public final class Research {
	private static final double DESK_RANGE_SQ = 8.0 * 8.0;
	private static Map<Item, Tech> locks;
	/** Para que varias mesas del mismo jugador no sumen: una vez por tic. */
	private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

	private Research() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ResearchPayloads.Action.ID, (payload, context) -> handle(context.player(), payload));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer(), null, false));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_TICK.remove(handler.getPlayer().getUuid()));
	}

	@Nullable
	public static Tech lockOf(Item item) {
		if (locks == null) {
			Map<Item, Tech> map = new HashMap<>();
			for (Tech tech : Tech.values()) {
				for (Item unlocked : tech.unlocks()) {
					map.put(unlocked, tech);
				}
			}
			locks = map;
		}
		return locks.get(item);
	}

	public static boolean has(MinecraftServer server, UUID player, Tech tech) {
		ResearchState.Entry entry = ResearchState.get(server).peek(player);
		return entry != null && entry.done.contains(tech);
	}

	/** ¿Tiene el dueño esta mejora? (Solo en el servidor; en el cliente, no.) */
	public static boolean hasBonus(World world, @Nullable UUID owner, Tech tech) {
		return owner != null && world.getServer() != null && has(world.getServer(), owner, tech);
	}

	public static boolean available(ResearchState.Entry entry, Tech tech) {
		return !entry.done.contains(tech) && (tech.requires == null || entry.done.contains(tech.requires));
	}

	/** ¿Puede fabricar esto? */
	public static boolean canCraft(ServerPlayerEntity player, ItemStack result) {
		if (!EwConfig.get().researchRequired || player.getAbilities().creativeMode) {
			return true;
		}
		Tech tech = lockOf(result.getItem());
		return tech == null || has(player.getServer(), player.getUuid(), tech);
	}

	// ------------------------------------------------------------------ desde la mesa

	/** La mesa avanza la investigación de su dueño ('speed' ya cuenta las estanterías). */
	public static void progress(ServerWorld world, UUID owner, double speed) {
		long now = world.getServer().getTicks();
		Long last = LAST_TICK.get(owner);
		if (last != null && last == now) {
			return;
		}
		LAST_TICK.put(owner, now);
		ResearchState state = ResearchState.get(world.getServer());
		ResearchState.Entry entry = state.peek(owner);
		if (entry == null || entry.current == null) {
			return;
		}
		entry.progress += speed * EwConfig.get().researchSpeed;
		if (entry.progress >= entry.current.ticks()) {
			Tech done = entry.current;
			entry.done.add(done);
			entry.current = null;
			entry.progress = 0.0;
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
			if (player != null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.research.done", Text.translatable(done.translationKey()))
						.formatted(Formatting.GOLD), false);
				player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
				sync(player, null, false);
			}
		}
		state.markDirty();
	}

	private static void handle(ServerPlayerEntity player, ResearchPayloads.Action action) {
		BlockPos desk = action.desk();
		if (!(player.getWorld().getBlockEntity(desk) instanceof ResearchDeskBlockEntity table)
				|| player.squaredDistanceTo(desk.toCenterPos()) > DESK_RANGE_SQ || !table.canUse(player)) {
			return;
		}
		switch (action.action()) {
			case ResearchPayloads.Action.START -> {
				Tech tech = Tech.byId(action.tech());
				if (tech != null) {
					start(player, tech);
				}
			}
			case ResearchPayloads.Action.CANCEL -> cancel(player);
			default -> {
			}
		}
		sync(player, desk, false);
	}

	private static int count(PlayerEntity player, Tech.Cost cost) {
		int total = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (cost.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static void start(ServerPlayerEntity player, Tech tech) {
		ResearchState state = ResearchState.get(player.getServer());
		ResearchState.Entry entry = state.of(player.getUuid());
		if (entry.current != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.research.busy"), true);
			return;
		}
		if (!available(entry, tech)) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.research.unavailable"), true);
			return;
		}
		boolean free = player.getAbilities().creativeMode;
		if (!free) {
			for (Tech.Cost cost : tech.cost()) {
				if (count(player, cost) < cost.count()) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.research.missing").formatted(Formatting.RED), true);
					return;
				}
			}
			for (Tech.Cost cost : tech.cost()) {
				int left = cost.count();
				for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
					ItemStack stack = player.getInventory().getStack(i);
					if (cost.matches(stack)) {
						int take = Math.min(left, stack.getCount());
						stack.decrement(take);
						left -= take;
					}
				}
			}
		}
		entry.current = tech;
		entry.progress = 0.0;
		state.markDirty();
		player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.research.started", Text.translatable(tech.translationKey())), true);
	}

	/** Deja la investigación en curso: se devuelve la mitad de lo que costó. */
	public static void cancel(ServerPlayerEntity player) {
		ResearchState state = ResearchState.get(player.getServer());
		ResearchState.Entry entry = state.of(player.getUuid());
		if (entry.current == null) {
			return;
		}
		if (!player.getAbilities().creativeMode) {
			for (Tech.Cost cost : entry.current.cost()) {
				int half = cost.count() / 2;
				while (half > 0) {
					int n = Math.min(half, 64);
					player.getInventory().offerOrDrop(new ItemStack(cost.icon(), n));
					half -= n;
				}
			}
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.research.cancelled", Text.translatable(entry.current.translationKey())), true);
		entry.current = null;
		entry.progress = 0.0;
		state.markDirty();
	}

	public static void grant(MinecraftServer server, UUID player, Tech tech) {
		ResearchState state = ResearchState.get(server);
		ResearchState.Entry entry = state.of(player);
		entry.done.add(tech);
		if (entry.current == tech) {
			entry.current = null;
			entry.progress = 0.0;
		}
		state.markDirty();
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		if (online != null) {
			sync(online, null, false);
		}
	}

	public static void revoke(MinecraftServer server, UUID player, Tech tech) {
		ResearchState state = ResearchState.get(server);
		state.of(player).done.remove(tech);
		state.markDirty();
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		if (online != null) {
			sync(online, null, false);
		}
	}

	/** Manda al cliente su investigación ('desk' para la pantalla; si 'open', la abre). */
	public static void sync(ServerPlayerEntity player, @Nullable BlockPos desk, boolean open) {
		if (player.getServer() == null || !ServerPlayNetworking.canSend(player, ResearchPayloads.Data.ID)) {
			return;
		}
		ResearchState.Entry entry = ResearchState.get(player.getServer()).of(player.getUuid());
		List<String> done = new ArrayList<>();
		for (Tech tech : entry.done) {
			done.add(tech.id());
		}
		float speed = 1.0F;
		if (desk != null && player.getWorld().getBlockEntity(desk) instanceof ResearchDeskBlockEntity table) {
			speed = (float) (table.speed() * EwConfig.get().researchSpeed);
		}
		float progress = 0.0F;
		int remaining = 0;
		if (entry.current != null) {
			progress = (float) Math.min(1.0, entry.progress / entry.current.ticks());
			remaining = (int) Math.ceil((entry.current.ticks() - entry.progress) / Math.max(0.01, speed));
		}
		ServerPlayNetworking.send(player, new ResearchPayloads.Data(open, desk != null ? desk : BlockPos.ORIGIN, done,
				entry.current != null ? entry.current.id() : "", progress, remaining, speed, EwConfig.get().researchRequired));
	}
}
