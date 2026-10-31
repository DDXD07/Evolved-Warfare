package com.evolvedwarfare.world;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.logistics.Logistics;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.siege.SiegeRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Asaltos de una facción a aldeas, ciudades y pueblos de jugadores (alrededor de un ayuntamiento): suena un cuerno,
 * aparece una barra con los que quedan y el grupo marcha hacia el centro. Acaba al derrotarlos a todos o a los
 * 10 minutos (entonces se retiran). Solo asalta la facción de la zona y solo si es enemiga de alguien de allí.
 *
 * <p>Asedio (fase 7): contra los pueblos de los jugadores y los castillos, a veces traen un ariete (que va hacia el
 * centro derribando lo que encuentra) o una catapulta (que bombardea desde lejos). Si los derrotas, las máquinas se
 * quedan ahí: súbete y son tuyas. Si se retiran, se las llevan.</p>
 */
public final class FactionRaids {
	private static final int DURATION = 12000;
	private static final double BAR_RANGE_SQ = 128.0 * 128.0;
	/** Distancia de un jugador a su ayuntamiento para que su pueblo pueda sufrir un asalto. */
	private static final int TOWN_RANGE = 48;
	private static final Map<RegistryKey<World>, Raid> ACTIVE = new HashMap<>();

	private FactionRaids() {
	}

	private static final class Raid {
		final Faction faction;
		final BlockPos center;
		final Set<UUID> remaining;
		final int total;
		final long until;
		final ServerBossBar bar;
		final List<UUID> engines;

		Raid(Faction faction, BlockPos center, Set<UUID> raiders, long until, List<UUID> engines) {
			this.engines = engines;
			this.faction = faction;
			this.center = center;
			this.remaining = raiders;
			this.total = raiders.size();
			this.until = until;
			this.bar = new ServerBossBar(Text.translatable("event.evolvedwarfare.raid." + faction.id()), faction.barColor(),
					BossBar.Style.NOTCHED_10);
		}
	}

	/** Un sitio que se puede asaltar, quién lo asaltaría y si merece máquinas de asedio. */
	private record Target(BlockPos center, int radius, Faction faction, boolean siege) {
	}

	/** Máquinas de asedio en un asalto: ninguna, quizá (según la dificultad) o seguro (comando). */
	public static final int SIEGE_NONE = 0;
	public static final int SIEGE_MAYBE = 1;
	public static final int SIEGE_FORCED = 2;

	public static boolean hasActive(ServerWorld world) {
		return ACTIVE.containsKey(world.getRegistryKey());
	}

	public static void clear() {
		for (Raid raid : ACTIVE.values()) {
			raid.bar.clearPlayers();
		}
		ACTIVE.clear();
	}

	/** Lo llama un asaltante al morir. */
	public static void onRaiderDeath(FactionNpcEntity raider) {
		if (raider.getWorld() instanceof ServerWorld world) {
			Raid raid = ACTIVE.get(world.getRegistryKey());
			if (raid != null) {
				raid.remaining.remove(raider.getUuid());
			}
		}
	}

	private static void tellNear(ServerWorld world, BlockPos center, Text message) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(center.toCenterPos()) < BAR_RANGE_SQ) {
				player.sendMessage(message, false);
			}
		}
	}

	static void tick(ServerWorld world) {
		Raid raid = ACTIVE.get(world.getRegistryKey());
		if (raid == null || world.getTime() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(raid.center.toCenterPos()) < BAR_RANGE_SQ) {
				raid.bar.addPlayer(player);
			} else {
				raid.bar.removePlayer(player);
			}
		}
		int left = raid.remaining.size();
		raid.bar.setPercent(raid.total == 0 ? 0.0F : left / (float) raid.total);
		raid.bar.setName(Text.translatable("event.evolvedwarfare.raid.left", raid.faction.displayName(), left));
		if (left == 0) {
			tellNear(world, raid.center, Text.translatable("message.evolvedwarfare.raid.victory", raid.faction.displayName())
					.formatted(Formatting.GOLD));
			world.playSound(null, raid.center.getX(), raid.center.getY(), raid.center.getZ(),
					SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 3.0F, 1.0F);
			// los enemigos de los asaltantes agradecen a quien los ha rechazado
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.squaredDistanceTo(raid.center.toCenterPos()) < 64.0 * 64.0) {
					for (Faction other : Faction.values()) {
						if (other.enemies(raid.faction)) {
							Reputation.change(world.getServer(), player.getUuid(), other, 3);
						}
					}
				}
			}
			int abandoned = 0;
			for (UUID id : raid.engines) {
				if (world.getEntity(id) instanceof SiegeEngineEntity engine && engine.isRaidEngine()) {
					engine.abandon();
					abandoned++;
				}
			}
			if (abandoned > 0) {
				tellNear(world, raid.center, Text.translatable("message.evolvedwarfare.raid.engines_left", abandoned).formatted(Formatting.GOLD));
			}
			raid.bar.clearPlayers();
			ACTIVE.remove(world.getRegistryKey());
		} else if (world.getTime() > raid.until) {
			for (UUID id : raid.remaining) {
				if (world.getEntity(id) instanceof FactionNpcEntity raider) {
					raider.endRaid();
				}
			}
			for (UUID id : raid.engines) {
				if (world.getEntity(id) instanceof SiegeEngineEntity engine && engine.isRaidEngine()) {
					engine.discard();
				}
			}
			tellNear(world, raid.center, Text.translatable("message.evolvedwarfare.raid.retreat", raid.faction.displayName())
					.formatted(Formatting.GRAY));
			raid.bar.clearPlayers();
			ACTIVE.remove(world.getRegistryKey());
		}
	}

	/** ¿Hay alguien cerca de ese sitio con quien esa facción esté enemistada? */
	private static boolean hostileNear(ServerWorld world, BlockPos center, Faction faction) {
		for (ServerPlayerEntity player : FactionSpawner.activePlayers(world)) {
			if (player.squaredDistanceTo(center.toCenterPos()) < BAR_RANGE_SQ && Reputation.hostile(world, player.getUuid(), faction)) {
				return true;
			}
		}
		return false;
	}

	/** Sitios que se pueden asaltar ahora: aldeas y ciudades con jugadores dentro, y pueblos de jugadores enemigos. */
	private static List<Target> targets(ServerWorld world, @Nullable Faction forced) {
		List<Target> targets = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (ServerPlayerEntity player : FactionSpawner.activePlayers(world)) {
			StructureStart start = world.getStructureAccessor().getStructureContaining(player.getBlockPos(), FactionSpawner.RAIDABLE);
			if (start.hasChildren()) {
				BlockBox box = start.getBoundingBox();
				BlockPos boxCenter = box.getCenter();
				BlockPos center = FactionSpawner.surface(world, boxCenter.getX(), boxCenter.getZ());
				if (center == null) {
					center = boxCenter;
				}
				if (seen.add(center)) {
					int radius = MathHelper.clamp(Math.max(box.getBlockCountX(), box.getBlockCountZ()) / 2 + 16, 40, 80);
					Faction faction = forced != null ? forced : FactionSpawner.factionAt(world, center);
					if (!hostileNear(world, center, faction) && forced == null && hostileNear(world, center, Faction.BANDITS)) {
						faction = Faction.BANDITS;
					}
					if (forced != null || hostileNear(world, center, faction)) {
						net.minecraft.util.Identifier id = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE)
								.getId(start.getStructure());
						targets.add(new Target(center, radius, faction, id != null && id.getPath().equals("castle")));
					}
				}
			}
			// el pueblo del jugador: su ayuntamiento
			for (TownHallBlockEntity hall : Logistics.townHalls(world, player.getBlockPos(), TOWN_RANGE, null)) {
				UUID owner = hall.getOwner();
				if (owner == null || !seen.add(hall.getPos())) {
					continue;
				}
				Faction faction = forced != null ? forced : FactionSpawner.factionAt(world, hall.getPos());
				if (forced != null || Reputation.hostile(world, owner, faction)) {
					BlockPos center = FactionSpawner.surface(world, hall.getPos().getX(), hall.getPos().getZ());
					targets.add(new Target(center != null ? center : hall.getPos(), 48, faction, true));
				}
			}
		}
		return targets;
	}

	/**
	 * Busca dónde asaltar y, a veces, lanza el asalto ('forced': una facción concreta, siempre; lo usan los comandos).
	 */
	public static boolean tryStart(ServerWorld world, Random random, @Nullable Faction forced) {
		return tryStart(world, random, forced, false);
	}

	public static boolean tryStart(ServerWorld world, Random random, @Nullable Faction forced, boolean forceSiege) {
		List<Target> targets = targets(world, forced);
		if (targets.isEmpty() || (forced == null && random.nextFloat() >= EwConfig.get().raidChance)) {
			return false;
		}
		Target target = targets.get(random.nextInt(targets.size()));
		int siege = forceSiege ? SIEGE_FORCED : target.siege() ? SIEGE_MAYBE : SIEGE_NONE;
		return start(world, random, target.faction(), target.center(), target.radius(), siege);
	}

	public static boolean start(ServerWorld world, Random random, Faction faction, BlockPos center, int radius) {
		return start(world, random, faction, center, radius, SIEGE_NONE);
	}

	/** Lanza un asalto de esa facción contra ese sitio. */
	public static boolean start(ServerWorld world, Random random, Faction faction, BlockPos center, int radius, int siege) {
		if (hasActive(world)) {
			return false;
		}
		BlockPos spot = findEdge(world, center, radius, random);
		if (spot == null) {
			return false;
		}
		int players = 0;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(center.toCenterPos()) < BAR_RANGE_SQ) {
				players++;
			}
		}
		Difficulty difficulty = world.getDifficulty();
		int count = difficulty == Difficulty.HARD ? 8 : difficulty == Difficulty.NORMAL ? 6 : 4;
		count = Math.min(12, count + 2 * Math.max(0, players - 1));
		boolean captain = difficulty == Difficulty.HARD || random.nextBoolean();
		long until = world.getTime() + DURATION;
		Set<UUID> raiders = new HashSet<>();
		for (int i = 0; i < count; i++) {
			NpcRole role = captain && i == 0 ? NpcRole.CAPTAIN : null;
			FactionNpcEntity raider = FactionSpawner.spawnMember(world, faction,
					spot.add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), role, SpawnReason.EVENT, random);
			if (raider != null) {
				raider.startRaid(center, until);
				raiders.add(raider.getUuid());
			}
		}
		if (raiders.isEmpty()) {
			return false;
		}
		List<UUID> engines = new ArrayList<>();
		EwConfig config = EwConfig.get();
		double chance = difficulty == Difficulty.HARD ? config.siegeChanceHard
				: difficulty == Difficulty.NORMAL ? config.siegeChanceNormal : config.siegeChanceEasy;
		if (siege == SIEGE_FORCED || (siege == SIEGE_MAYBE && SiegeRules.siegeRaids(world) && random.nextFloat() < chance)) {
			boolean both = siege == SIEGE_FORCED || difficulty == Difficulty.HARD && random.nextInt(3) == 0;
			boolean ram = both || random.nextFloat() < 0.55F;
			if (ram) {
				spawnEngine(world, random, SiegeKind.RAM, faction, center, spot, until, raiders, engines);
			}
			if (both || !ram) {
				BlockPos battery = batterySpot(world, center, spot);
				if (battery != null) {
					spawnEngine(world, random, SiegeKind.CATAPULT, faction, center, battery, until, raiders, engines);
				}
			}
			if (!engines.isEmpty()) {
				tellNear(world, center, Text.translatable("message.evolvedwarfare.raid.siege").formatted(Formatting.DARK_RED));
			}
		}
		ACTIVE.put(world.getRegistryKey(), new Raid(faction, center, raiders, until, engines));
		world.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE,
				64.0F, faction == Faction.PIRATES ? 0.8F : 1.0F);
		tellNear(world, center, Text.translatable("message.evolvedwarfare.raid.start." + faction.id()).formatted(Formatting.RED));
		return true;
	}

	/** Una máquina de asedio con su dotación (que cuenta como asaltante). */
	static void spawnEngine(ServerWorld world, Random random, SiegeKind kind, Faction faction, BlockPos center, BlockPos at,
			long until, Set<UUID> raiders, List<UUID> engines) {
		SiegeEngineEntity engine = ModEntities.engineType(kind).create(world);
		if (engine == null) {
			return;
		}
		float yaw = SiegeBallistics.yawTo(net.minecraft.util.math.Vec3d.ofBottomCenter(at), net.minecraft.util.math.Vec3d.ofBottomCenter(center));
		engine.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		if (!world.isSpaceEmpty(engine)) {
			return;
		}
		FactionNpcEntity crew = FactionSpawner.spawnMember(world, faction, at, null, SpawnReason.EVENT, random);
		if (crew == null) {
			return;
		}
		engine.setFaction(faction, true, center);
		world.spawnEntity(engine);
		crew.startRaid(center, until);
		crew.startRiding(engine, true);
		raiders.add(crew.getUuid());
		engines.add(engine.getUuid());
	}

	/** Dónde poner la catapulta: a tiro del centro (unos 38 bloques), del lado por el que llegan. */
	@Nullable
	static BlockPos batterySpot(ServerWorld world, BlockPos center, BlockPos edge) {
		double dx = edge.getX() - center.getX();
		double dz = edge.getZ() - center.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0) {
			return null;
		}
		for (int distance : new int[] {38, 34, 42, 30}) {
			int x = center.getX() + MathHelper.floor(dx / length * distance);
			int z = center.getZ() + MathHelper.floor(dz / length * distance);
			BlockPos pos = FactionSpawner.surface(world, x, z);
			if (pos != null) {
				return pos;
			}
		}
		return null;
	}

	@Nullable
	private static BlockPos findEdge(ServerWorld world, BlockPos center, int radius, Random random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = center.getX() + MathHelper.floor(Math.cos(angle) * radius);
			int z = center.getZ() + MathHelper.floor(Math.sin(angle) * radius);
			BlockPos pos = FactionSpawner.surface(world, x, z);
			if (pos != null && !FactionSpawner.inRaidable(world, pos)) {
				return pos;
			}
		}
		return null;
	}
}
