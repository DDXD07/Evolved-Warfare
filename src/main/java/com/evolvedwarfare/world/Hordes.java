package com.evolvedwarfare.world;

import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.logistics.Logistics;
import com.evolvedwarfare.network.HordeInfoPayload;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.siege.SiegeRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Hordas: cada {@code hordeEveryDays} días de juego (7 por defecto) el contador de días se pone rojo y, a media mañana,
 * llega a la base de cada jugador una horda de bandidos, vikingos o piratas (al azar). La base es su ayuntamiento; si no
 * tiene, su cama; y si está lejos de los dos, la horda va a por él allá donde esté. Viene en oleadas y cada semana es más
 * dura: más guerreros por oleada, más oleadas, más capitanes, más vida y daño, más equipo de élite y, desde la tercera
 * semana, máquinas de asedio. Al anochecer se retira; si la derrotáis entera, Glory Coins para los que la resistieron.
 *
 * <p>La horda no mira la reputación (ataca aunque seáis amigos de su facción) y matarlos no la baja.</p>
 */
public final class Hordes {
	public static final long DAY = 24000L;
	/** A qué hora llega (tics desde las 6:00): las 9:00. */
	public static final int ARRIVAL = 3000;
	/** Al anochecer (18:00) se retira; pasadas las 17:00 ya no empieza. */
	public static final int DUSK = 12000;
	private static final int LAST_START = DUSK - 1000;
	/** Si un jugador está más lejos de su base, la horda va a por él (la base podría no estar cargada). */
	public static final int BASE_RANGE = 96;
	private static final double BAR_RANGE_SQ = 160.0 * 160.0;
	private static final double MERGE_RANGE_SQ = 96.0 * 96.0;
	private static final double REWARD_RANGE_SQ = 128.0 * 128.0;
	/** A qué distancia del centro aparece. */
	public static final int EDGE = 44;
	/** Si la oleada no cae en 3 minutos, llega la siguiente igual. */
	private static final int WAVE_TIMEOUT = 3600;
	private static final int SYNC_INTERVAL = 200;

	private Hordes() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(Hordes::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HordeInfoPayload.send(handler.player,
				every(server.getOverworld())));
	}

	// ------------------------------------------------------------------ calendario

	/** Cada cuántos días hay horda ahora (0: ninguna; desactivadas, en pacífico o sin criaturas). */
	public static int every(ServerWorld world) {
		if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
			return 0;
		}
		return Math.max(0, EwConfig.get().hordeEveryDays);
	}

	/** El día de juego (el 1 empieza al primer amanecer). */
	public static long day(long timeOfDay) {
		return Math.floorDiv(timeOfDay, DAY) + 1L;
	}

	public static boolean isHordeDay(long day, int every) {
		return every > 0 && day > 0 && day % every == 0;
	}

	/** Qué horda es (1 la primera semana, 2 la segunda...). */
	public static int week(long day, int every) {
		return every <= 0 ? 1 : (int) MathHelper.clamp(day / every, 1L, 1000L);
	}

	// ------------------------------------------------------------------ fuerza

	/**
	 * Lo dura que es una horda: guerreros por oleada, oleadas, capitanes por oleada, probabilidad de equipo de élite,
	 * vida y daño (x) y máquinas de asedio.
	 */
	public record Strength(int perWave, int waves, int captains, float elite, double health, double damage, int engines) {
	}

	public static Strength strength(int week, Difficulty difficulty, int players) {
		double g = Math.max(0, week - 1) * EwConfig.get().hordeGrowth;
		boolean hard = difficulty == Difficulty.HARD;
		int base = hard ? 8 : difficulty == Difficulty.NORMAL ? 6 : 4;
		int perWave = Math.min(24, (int) Math.round(base + 1.5 * g) + 2 * Math.max(0, players - 1));
		int waves = Math.min(4, 1 + (int) Math.floor(g / 2.0));
		int captains = Math.min(3, (int) Math.floor((g + 1.0) / 3.0) + (hard ? 1 : 0));
		float elite = (float) Math.min(1.0, 0.15 * g);
		double health = 1.0 + Math.min(2.0, 0.12 * g);
		double damage = 1.0 + Math.min(1.0, 0.07 * g);
		int engines = g < 2.0 ? 0 : g < 4.0 ? 1 : g < 7.0 ? 2 : 3;
		return new Strength(perWave, waves, captains, elite, health, damage, engines);
	}

	// ------------------------------------------------------------------ una horda

	public static final class Horde {
		final Faction faction;
		BlockPos center;
		/** El jugador al que persigue (si no estaba en su base), o null si ataca una base. */
		@Nullable
		final UUID hunted;
		final int week;
		final Strength strength;
		final long until;
		/** Oleadas que ya han llegado. */
		int wave;
		/** Los que quedan (de todas las oleadas) y los de la última. */
		final Set<UUID> alive = new HashSet<>();
		final Set<UUID> current = new HashSet<>();
		int waveSize;
		long waveStart;
		int failed;
		final List<UUID> engines = new ArrayList<>();
		/** Los jugadores de esa base (la recompensa es para ellos y para quien ande cerca). */
		final Set<UUID> defenders = new HashSet<>();
		final ServerBossBar bar;

		Horde(Faction faction, BlockPos center, @Nullable UUID hunted, int week, Strength strength, long until) {
			this.faction = faction;
			this.center = center;
			this.hunted = hunted;
			this.week = week;
			this.strength = strength;
			this.until = until;
			this.bar = new ServerBossBar(Text.translatable("event.evolvedwarfare.horde." + faction.id()), BossBar.Color.RED,
					BossBar.Style.NOTCHED_10);
			this.bar.setDarkenSky(true);
		}

		public Faction faction() {
			return faction;
		}

		public BlockPos center() {
			return center;
		}

		@Nullable
		public UUID hunted() {
			return hunted;
		}

		public int week() {
			return week;
		}

		public Strength strength() {
			return strength;
		}

		public int wave() {
			return wave;
		}

		public int remaining() {
			return alive.size();
		}

		public Set<UUID> members() {
			return alive;
		}

		NbtCompound toNbt() {
			NbtCompound nbt = new NbtCompound();
			nbt.putString("Faction", faction.id());
			nbt.putInt("X", center.getX());
			nbt.putInt("Y", center.getY());
			nbt.putInt("Z", center.getZ());
			if (hunted != null) {
				nbt.putUuid("Hunted", hunted);
			}
			nbt.putInt("Week", week);
			nbt.putInt("PerWave", strength.perWave());
			nbt.putInt("Waves", strength.waves());
			nbt.putInt("Captains", strength.captains());
			nbt.putFloat("Elite", strength.elite());
			nbt.putDouble("Health", strength.health());
			nbt.putDouble("Damage", strength.damage());
			nbt.putInt("Engines", strength.engines());
			nbt.putLong("Until", until);
			nbt.putInt("Wave", wave);
			nbt.putInt("WaveSize", waveSize);
			nbt.putLong("WaveStart", waveStart);
			nbt.put("Alive", uuids(alive));
			nbt.put("Current", uuids(current));
			nbt.put("EngineIds", uuids(engines));
			nbt.put("Defenders", uuids(defenders));
			return nbt;
		}

		static Horde fromNbt(NbtCompound nbt) {
			Strength strength = new Strength(nbt.getInt("PerWave"), nbt.getInt("Waves"), nbt.getInt("Captains"), nbt.getFloat("Elite"),
					nbt.getDouble("Health"), nbt.getDouble("Damage"), nbt.getInt("Engines"));
			Horde horde = new Horde(Faction.byId(nbt.getString("Faction")), new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z")),
					nbt.containsUuid("Hunted") ? nbt.getUuid("Hunted") : null, nbt.getInt("Week"), strength, nbt.getLong("Until"));
			horde.wave = nbt.getInt("Wave");
			horde.waveSize = nbt.getInt("WaveSize");
			horde.waveStart = nbt.getLong("WaveStart");
			readUuids(nbt.getList("Alive", NbtElement.COMPOUND_TYPE), horde.alive);
			readUuids(nbt.getList("Current", NbtElement.COMPOUND_TYPE), horde.current);
			readUuids(nbt.getList("EngineIds", NbtElement.COMPOUND_TYPE), horde.engines);
			readUuids(nbt.getList("Defenders", NbtElement.COMPOUND_TYPE), horde.defenders);
			return horde;
		}

		private static NbtList uuids(Collection<UUID> ids) {
			NbtList list = new NbtList();
			for (UUID id : ids) {
				NbtCompound entry = new NbtCompound();
				entry.putUuid("Id", id);
				list.add(entry);
			}
			return list;
		}

		private static void readUuids(NbtList list, Collection<UUID> into) {
			for (int i = 0; i < list.size(); i++) {
				NbtCompound entry = list.getCompound(i);
				if (entry.containsUuid("Id")) {
					into.add(entry.getUuid("Id"));
				}
			}
		}
	}

	public static List<Horde> active(ServerWorld world) {
		return HordeState.get(world).hordes;
	}

	public static boolean hasActive(ServerWorld world) {
		return world.getRegistryKey() == World.OVERWORLD && !HordeState.get(world).hordes.isEmpty();
	}

	/** Quita una horda de golpe (sin victoria ni retirada): sus guerreros y máquinas desaparecen. */
	public static void cancel(ServerWorld world, Horde horde) {
		HordeState state = HordeState.get(world);
		state.hordes.remove(horde);
		state.markDirty();
		horde.bar.clearPlayers();
		for (UUID id : List.copyOf(horde.alive)) {
			if (world.getEntity(id) != null) {
				world.getEntity(id).discard();
			}
		}
		for (UUID id : horde.engines) {
			if (world.getEntity(id) != null) {
				world.getEntity(id).discard();
			}
		}
		horde.alive.clear();
	}

	/** Lo llama un miembro de la horda al morir o desaparecer del todo. */
	public static void onGone(ServerWorld world, FactionNpcEntity member) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		HordeState state = HordeState.get(world);
		for (Horde horde : state.hordes) {
			if (horde.alive.remove(member.getUuid())) {
				state.markDirty();
			}
		}
	}

	// ------------------------------------------------------------------ cada tic

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		long time = world.getTime();
		if (time % SYNC_INTERVAL == 0) {
			int every = every(world);
			for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
				HordeInfoPayload.send(player, every);
			}
		}
		if (time % 20 != 0) {
			return;
		}
		HordeState state = HordeState.get(world);
		schedule(world, state);
		if (!state.hordes.isEmpty()) {
			update(world, state);
		}
	}

	/** Los avisos (la víspera y al amanecer) y la llegada de la horda el día que toca. */
	private static void schedule(ServerWorld world, HordeState state) {
		int every = every(world);
		if (every <= 0) {
			return;
		}
		long timeOfDay = world.getTimeOfDay();
		long day = day(timeOfDay);
		long hour = Math.floorMod(timeOfDay, DAY);
		if (isHordeDay(day + 1, every) && hour >= DUSK && state.eveWarned != day + 1) {
			state.eveWarned = day + 1;
			state.markDirty();
			broadcast(world, Text.translatable("message.evolvedwarfare.horde.tomorrow", day + 1).formatted(Formatting.RED));
		}
		if (!isHordeDay(day, every)) {
			return;
		}
		if (state.dawnWarned != day && hour < ARRIVAL) {
			state.dawnWarned = day;
			state.markDirty();
			broadcast(world, Text.translatable("message.evolvedwarfare.horde.today", day).formatted(Formatting.RED));
		}
		if (state.lastDay != day && hour >= ARRIVAL && hour < LAST_START) {
			List<ServerPlayerEntity> players = FactionSpawner.activePlayers(world);
			if (players.isEmpty()) {
				return; // nadie a quien atacar todavía: se vuelve a mirar dentro de un segundo
			}
			state.lastDay = day;
			state.markDirty();
			launch(world, players, week(day, every));
		}
	}

	private static void broadcast(ServerWorld world, Text message) {
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			player.sendMessage(message, false);
		}
	}

	/** La base de un jugador si está cerca: su ayuntamiento más cercano o, si no tiene, su cama. */
	@Nullable
	public static BlockPos home(ServerWorld world, ServerPlayerEntity player) {
		TownHallBlockEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (TownHallBlockEntity hall : Logistics.townHalls(world, player.getBlockPos(), BASE_RANGE, player.getUuid())) {
			if (player.getUuid().equals(hall.getOwner())) {
				double distance = hall.getPos().getSquaredDistance(player.getPos());
				if (distance < bestDistance) {
					best = hall;
					bestDistance = distance;
				}
			}
		}
		if (best != null) {
			return best.getPos();
		}
		BlockPos bed = player.getSpawnPointPosition();
		if (bed != null && player.getSpawnPointDimension() == world.getRegistryKey() && bed.isWithinDistance(player.getPos(), BASE_RANGE)) {
			return bed;
		}
		return null;
	}

	/** Una horda por base: los jugadores que estén juntos (o en la misma base) se defienden juntos. */
	private static void launch(ServerWorld world, List<ServerPlayerEntity> players, int week) {
		Random random = world.getRandom();
		List<BlockPos> centers = new ArrayList<>();
		List<UUID> hunted = new ArrayList<>();
		List<Set<UUID>> defenders = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			BlockPos home = home(world, player);
			BlockPos center = home != null ? home : player.getBlockPos();
			int group = -1;
			for (int i = 0; i < centers.size(); i++) {
				if (centers.get(i).getSquaredDistance(center) < MERGE_RANGE_SQ) {
					group = i;
					break;
				}
			}
			if (group < 0) {
				centers.add(center);
				hunted.add(home == null ? player.getUuid() : null);
				defenders.add(new HashSet<>());
				group = centers.size() - 1;
			} else if (home != null && hunted.get(group) != null) {
				// alguien del grupo sí está en su base: van a por la base
				centers.set(group, home);
				hunted.set(group, null);
			}
			defenders.get(group).add(player.getUuid());
		}
		for (int i = 0; i < centers.size(); i++) {
			Faction faction = Faction.values()[random.nextInt(Faction.values().length)];
			if (start(world, random, faction, centers.get(i), hunted.get(i), week, defenders.get(i), EDGE) == null) {
				start(world, random, faction, centers.get(i), hunted.get(i), week, defenders.get(i), EDGE / 2);
			}
		}
	}

	/** Hasta el anochecer de hoy (o, si ya es tarde, unos 7 minutos). */
	private static long until(ServerWorld world) {
		long hour = Math.floorMod(world.getTimeOfDay(), DAY);
		long left = hour < LAST_START ? DUSK - hour : 9000L;
		return world.getTime() + Math.max(2400L, left);
	}

	/**
	 * Lanza una horda contra un sitio ('hunted': el jugador al que persigue si no estaba en su base). Devuelve la horda o
	 * null si no ha podido aparecer (ningún sitio cargado y despejado alrededor).
	 */
	@Nullable
	public static Horde start(ServerWorld world, Random random, Faction faction, BlockPos center, @Nullable UUID hunted, int week,
			Collection<UUID> defenders, int radius) {
		Strength strength = strength(week, world.getDifficulty(), Math.max(1, defenders.size()));
		Horde horde = new Horde(faction, center.toImmutable(), hunted, week, strength, until(world));
		horde.defenders.addAll(defenders);
		if (!spawnWave(world, random, horde, radius)) {
			return null;
		}
		HordeState state = HordeState.get(world);
		state.hordes.add(horde);
		state.markDirty();
		Text message = Text.translatable("message.evolvedwarfare.horde." + (hunted != null ? "hunt." : "base.") + faction.id(), week)
				.formatted(Formatting.RED, Formatting.BOLD);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (horde.defenders.contains(player.getUuid()) || player.squaredDistanceTo(center.toCenterPos()) < BAR_RANGE_SQ) {
				player.sendMessage(message, false);
			}
		}
		return horde;
	}

	/** La siguiente oleada, por un sitio al azar alrededor del centro. */
	private static boolean spawnWave(ServerWorld world, Random random, Horde horde, int radius) {
		BlockPos spot = edge(world, horde.center, radius, random);
		if (spot == null) {
			return false;
		}
		Strength strength = horde.strength;
		boolean last = horde.wave + 1 >= strength.waves();
		int captains = strength.captains() + (last && horde.week >= 2 && strength.captains() == 0 ? 1 : 0);
		horde.current.clear();
		for (int i = 0; i < strength.perWave(); i++) {
			NpcRole role = i < captains ? NpcRole.CAPTAIN : null;
			FactionNpcEntity member = FactionSpawner.spawnMember(world, horde.faction,
					spot.add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), role, SpawnReason.EVENT, random);
			if (member != null) {
				enlist(horde, member, random);
			}
		}
		if (horde.current.isEmpty()) {
			return false;
		}
		// máquinas de asedio (desde la 3.ª semana), con la primera oleada
		if (horde.wave == 0 && strength.engines() > 0 && SiegeRules.siegeRaids(world)) {
			Set<UUID> crews = new HashSet<>();
			for (int i = 0; i < strength.engines(); i++) {
				if (i == 1) {
					BlockPos battery = FactionRaids.batterySpot(world, horde.center, spot);
					if (battery != null) {
						FactionRaids.spawnEngine(world, random, SiegeKind.CATAPULT, horde.faction, horde.center, battery, horde.until, crews,
								horde.engines);
					}
				} else {
					BlockPos at = FactionSpawner.surface(world, spot.getX() + random.nextInt(11) - 5, spot.getZ() + random.nextInt(11) - 5);
					if (at != null) {
						FactionRaids.spawnEngine(world, random, SiegeKind.RAM, horde.faction, horde.center, at, horde.until, crews, horde.engines);
					}
				}
			}
			for (UUID id : crews) {
				if (world.getEntity(id) instanceof FactionNpcEntity crew) {
					enlist(horde, crew, random);
				}
			}
			if (!horde.engines.isEmpty()) {
				tellNear(world, horde, Text.translatable("message.evolvedwarfare.raid.siege").formatted(Formatting.DARK_RED));
			}
		}
		horde.wave++;
		horde.waveSize = horde.current.size();
		horde.waveStart = world.getTime();
		world.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 64.0F,
				horde.faction == Faction.PIRATES ? 0.8F : 1.0F);
		return true;
	}

	private static void enlist(Horde horde, FactionNpcEntity member, Random random) {
		Strength strength = horde.strength;
		member.joinHorde(strength.health(), strength.damage(), random.nextFloat() < strength.elite(), random);
		member.startRaid(horde.center, horde.until);
		horde.current.add(member.getUuid());
		horde.alive.add(member.getUuid());
	}

	/** Un sitio despejado, cargado y con tics, a esa distancia (más o menos) del centro. */
	@Nullable
	private static BlockPos edge(ServerWorld world, BlockPos center, int radius, Random random) {
		for (int attempt = 0; attempt < 24; attempt++) {
			int distance = attempt < 16 ? Math.max(6, radius + random.nextInt(9) - 4) : Math.max(6, radius / 2);
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = center.getX() + MathHelper.floor(Math.cos(angle) * distance);
			int z = center.getZ() + MathHelper.floor(Math.sin(angle) * distance);
			BlockPos pos = FactionSpawner.surface(world, x, z);
			if (pos != null && world.shouldTickEntity(pos)) {
				return pos;
			}
		}
		return null;
	}

	private static void tellNear(ServerWorld world, Horde horde, Text message) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (horde.defenders.contains(player.getUuid()) || player.squaredDistanceTo(horde.center.toCenterPos()) < BAR_RANGE_SQ) {
				player.sendMessage(message, false);
			}
		}
	}

	/** Cada segundo: la barra, la persecución, las oleadas y el final (victoria o retirada). */
	private static void update(ServerWorld world, HordeState state) {
		long time = world.getTime();
		Random random = world.getRandom();
		for (Iterator<Horde> it = state.hordes.iterator(); it.hasNext(); ) {
			Horde horde = it.next();
			if (horde.hunted != null && time % 100 == 0) {
				ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(horde.hunted);
				if (target != null && target.getWorld() == world && target.isAlive()) {
					horde.center = target.getBlockPos();
					for (UUID id : horde.alive) {
						if (world.getEntity(id) instanceof FactionNpcEntity member && member.isRaiding() && member.getTarget() == null) {
							member.hordeMarch(horde.center);
						}
					}
				}
			}
			for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
				if (player.getWorld() == world && player.squaredDistanceTo(horde.center.toCenterPos()) < BAR_RANGE_SQ) {
					horde.bar.addPlayer(player);
				} else {
					horde.bar.removePlayer(player);
				}
			}
			horde.current.retainAll(horde.alive);
			int left = horde.alive.size();
			horde.bar.setPercent(horde.waveSize == 0 ? 0.0F : MathHelper.clamp(horde.current.size() / (float) horde.waveSize, 0.0F, 1.0F));
			horde.bar.setName(Text.translatable("event.evolvedwarfare.horde.bar", Text.translatable("event.evolvedwarfare.horde." + horde.faction.id()),
					horde.wave, horde.strength.waves(), left));
			// la siguiente oleada: cuando la de ahora está casi derrotada, o a los 3 minutos
			if (horde.wave < horde.strength.waves() && time < horde.until - 1200
					&& (horde.current.size() <= Math.max(1, horde.waveSize / 4) || time - horde.waveStart >= WAVE_TIMEOUT)) {
				if (spawnWave(world, random, horde, EDGE) || spawnWave(world, random, horde, EDGE / 2)) {
					tellNear(world, horde, Text.translatable("message.evolvedwarfare.horde.wave", horde.wave, horde.strength.waves())
							.formatted(Formatting.RED));
				} else if (++horde.failed >= 10) {
					horde.wave = horde.strength.waves(); // no hay por dónde: no vienen más
				}
				left = horde.alive.size();
			}
			if (left == 0 && horde.wave >= horde.strength.waves()) {
				victory(world, horde);
				horde.bar.clearPlayers();
				it.remove();
			} else if (time > horde.until) {
				retreat(world, horde);
				horde.bar.clearPlayers();
				it.remove();
			}
		}
		state.markDirty();
	}

	/** Lo que se gana por resistir una horda entera. */
	public static int reward(int week) {
		return Math.min(64, 2 + 3 * week);
	}

	private static void victory(ServerWorld world, Horde horde) {
		int coins = reward(horde.week);
		Text message = Text.translatable("message.evolvedwarfare.horde.victory", horde.week, coins).formatted(Formatting.GOLD);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean near = player.squaredDistanceTo(horde.center.toCenterPos()) < REWARD_RANGE_SQ;
			if (near || horde.defenders.contains(player.getUuid())) {
				player.sendMessage(message, false);
				if (near && player.isAlive()) {
					player.getInventory().offerOrDrop(new ItemStack(ModItems.GLORY_COIN, coins));
				}
			}
		}
		world.playSound(null, horde.center.getX(), horde.center.getY(), horde.center.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
				SoundCategory.PLAYERS, 3.0F, 1.0F);
		int abandoned = 0;
		for (UUID id : horde.engines) {
			if (world.getEntity(id) instanceof SiegeEngineEntity engine && engine.isRaidEngine()) {
				engine.abandon();
				abandoned++;
			}
		}
		if (abandoned > 0) {
			tellNear(world, horde, Text.translatable("message.evolvedwarfare.raid.engines_left", abandoned).formatted(Formatting.GOLD));
		}
	}

	private static void retreat(ServerWorld world, Horde horde) {
		for (UUID id : horde.alive) {
			if (world.getEntity(id) instanceof FactionNpcEntity member) {
				member.endRaid();
			}
		}
		for (UUID id : horde.engines) {
			if (world.getEntity(id) instanceof SiegeEngineEntity engine && engine.isRaidEngine()) {
				engine.discard();
			}
		}
		tellNear(world, horde, Text.translatable("message.evolvedwarfare.horde.retreat").formatted(Formatting.GRAY));
	}
}
