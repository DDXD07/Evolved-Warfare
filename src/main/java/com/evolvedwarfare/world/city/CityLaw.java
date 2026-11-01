package com.evolvedwarfare.world.city;

import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.world.CityLevels;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * La ley de las ciudades: si un jugador hace daño a alguien de una ciudad (un mercader, un guardia o un vecino libre),
 * esa ciudad le guarda rencor un día de juego y sus guardias van a por él. Al terminar el día, le perdonan.
 */
public final class CityLaw {
	/** Cuánto dura el rencor (un día de juego). */
	public static final long GRUDGE_TICKS = 24000L;
	/** Hasta dónde se enteran los guardias al momento. */
	private static final double ALERT_RANGE = 64.0;

	private CityLaw() {
	}

	/** Un rencor: hasta cuándo (hora del mundo) y el nombre de la ciudad (para los avisos). */
	record Grudge(long until, String city) {
	}

	/** Los rencores (se guardan con el mundo): ciudad -> jugador -> rencor. */
	public static final class State extends PersistentState {
		private static final String ID = "evolvedwarfare_city_law";
		static final Type<State> TYPE = new Type<>(State::new, State::fromNbt, null);
		final Map<Long, Map<UUID, Grudge>> grudges = new HashMap<>();

		static State get(MinecraftServer server) {
			return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
		}

		private static State fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			State state = new State();
			NbtList list = nbt.getList("Grudges", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound g = list.getCompound(i);
				state.grudges.computeIfAbsent(g.getLong("City"), k -> new HashMap<>()).put(g.getUuid("Player"),
						new Grudge(g.getLong("Until"), g.getString("Name")));
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			NbtList list = new NbtList();
			grudges.forEach((city, players) -> players.forEach((player, grudge) -> {
				NbtCompound g = new NbtCompound();
				g.putLong("City", city);
				g.putUuid("Player", player);
				g.putLong("Until", grudge.until());
				g.putString("Name", grudge.city());
				list.add(g);
			}));
			nbt.put("Grudges", list);
			return nbt;
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 100 == 0) {
				forgive(server);
			}
		});
	}

	/** Lo llama el mixin del daño cuando un golpe entra: si es un jugador contra alguien de una ciudad, delito. */
	public static void onDamaged(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (source.getAttacker() instanceof ServerPlayerEntity player && entity.getWorld() instanceof ServerWorld world) {
			long city = cityOf(world, entity);
			if (city != 0L) {
				offend(world, city, player, entity);
			}
		}
	}

	/** ¿Juega en creativo o espectador? (por su modo de juego en el servidor) */
	private static boolean exempt(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity server) {
			net.minecraft.world.GameMode mode = server.interactionManager.getGameMode();
			return mode == net.minecraft.world.GameMode.CREATIVE || mode == net.minecraft.world.GameMode.SPECTATOR;
		}
		return player.isCreative() || player.isSpectator();
	}

	private static long now(MinecraftServer server) {
		return server.getOverworld().getTimeOfDay();
	}

	/** ¿Le guarda rencor esa ciudad a ese jugador? (los de creativo y espectador, nunca) */
	public static boolean wanted(World world, long city, PlayerEntity player) {
		if (city == 0L || exempt(player) || !(world instanceof ServerWorld server)) {
			return false;
		}
		Map<UUID, Grudge> players = State.get(server.getServer()).grudges.get(city);
		Grudge grudge = players == null ? null : players.get(player.getUuid());
		return grudge != null && grudge.until() > now(server.getServer());
	}

	/** La ciudad de alguien (0 si no es de ninguna): mercaderes y guardias la llevan; los vecinos, la de donde estén. */
	public static long cityOf(ServerWorld world, LivingEntity entity) {
		if (entity instanceof TraderEntity trader && trader.getCityKey() != 0L) {
			return trader.getCityKey();
		}
		if (entity instanceof CityGuardEntity guard) {
			return guard.getCityKey();
		}
		boolean citizen = (entity instanceof RecruitEntity recruit && recruit.isForHire()) || entity instanceof VillagerEntity
				|| entity instanceof TraderEntity;
		if (!citizen) {
			return 0L;
		}
		StructureStart start = world.getStructureAccessor().getStructureContaining(entity.getBlockPos(), CityLevels.CITIES);
		return CityPiece.of(start).map(CityPiece::cityKey).orElse(0L);
	}

	/** El nombre de la ciudad donde está algo (o «la ciudad»). */
	private static String nameAt(ServerWorld world, Entity entity) {
		StructureStart start = world.getStructureAccessor().getStructureContaining(entity.getBlockPos(), CityLevels.CITIES);
		return CityPiece.of(start).map(CityPiece::cityName).orElse("");
	}

	/** El jugador ha hecho daño a alguien de la ciudad: un día de rencor y los guardias de cerca, a por él. */
	public static void offend(ServerWorld world, long city, ServerPlayerEntity player, Entity victim) {
		if (exempt(player)) {
			return;
		}
		State state = State.get(world.getServer());
		Map<UUID, Grudge> players = state.grudges.computeIfAbsent(city, k -> new HashMap<>());
		long now = now(world.getServer());
		Grudge old = players.get(player.getUuid());
		String name = old != null && !old.city().isEmpty() ? old.city() : nameAt(world, victim);
		players.put(player.getUuid(), new Grudge(now + GRUDGE_TICKS, name));
		state.markDirty();
		if (old == null || old.until() <= now) {
			Text cityName = name.isEmpty() ? Text.translatable("message.evolvedwarfare.city_law.the_city") : Text.literal(name);
			player.sendMessage(Text.translatable("message.evolvedwarfare.city_law.wanted", cityName).formatted(Formatting.RED), false);
		}
		Box area = victim.getBoundingBox().expand(ALERT_RANGE, 24.0, ALERT_RANGE);
		for (CityGuardEntity guard : world.getEntitiesByClass(CityGuardEntity.class, area, g -> g.isAlive() && g.getCityKey() == city)) {
			if (guard.getTarget() == null || !guard.getTarget().isAlive()) {
				guard.setTarget(player);
			}
		}
	}

	/** Quita los rencores que ya han pasado y se lo dice al jugador si está conectado. */
	private static void forgive(MinecraftServer server) {
		State state = State.get(server);
		if (state.grudges.isEmpty()) {
			return;
		}
		long now = now(server);
		boolean changed = false;
		for (Iterator<Map.Entry<Long, Map<UUID, Grudge>>> cities = state.grudges.entrySet().iterator(); cities.hasNext(); ) {
			Map<UUID, Grudge> players = cities.next().getValue();
			for (Iterator<Map.Entry<UUID, Grudge>> it = players.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Grudge> e = it.next();
				if (e.getValue().until() <= now) {
					it.remove();
					changed = true;
					ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
					if (player != null) {
						String name = e.getValue().city();
						Text cityName = name.isEmpty() ? Text.translatable("message.evolvedwarfare.city_law.the_city") : Text.literal(name);
						player.sendMessage(Text.translatable("message.evolvedwarfare.city_law.forgiven", cityName).formatted(Formatting.GREEN), false);
					}
				}
			}
			if (players.isEmpty()) {
				cities.remove();
			}
		}
		if (changed) {
			state.markDirty();
		}
	}

	/** Para las pruebas y los comandos: quita todos los rencores de un jugador. */
	public static void pardon(MinecraftServer server, UUID player) {
		State state = State.get(server);
		state.grudges.values().forEach(players -> players.remove(player));
		state.markDirty();
	}
}
