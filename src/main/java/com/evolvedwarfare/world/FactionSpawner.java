package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Aparición de las facciones fuera de sus estructuras. Qué facción sale depende de dónde estés: vikingos en tierras
 * frías, piratas en la costa y bandidos en el resto.
 * <ul>
 *   <li><b>Patrullas</b>: cada 10-20 min de juego, a veces (35 %), un grupo de 3-6 aparece a 28-44 bloques de un
 *       jugador. Si su facción es enemiga del jugador van a por él; si no, pasan de largo. Desde el segundo día.</li>
 *   <li><b>Asaltos</b>: cada 3-5 días de juego (el primero, a partir del día 2), a una aldea, ciudad o a tu pueblo
 *       (alrededor de tu ayuntamiento), si la facción de la zona es enemiga tuya (ver {@link FactionRaids}).</li>
 * </ul>
 * Nada de esto ocurre en pacífico ni con doMobSpawning desactivado.
 */
public final class FactionSpawner {
	/** Estructuras que pueden sufrir asaltos: aldeas vanilla y ciudades del mod. */
	public static final TagKey<Structure> RAIDABLE = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("raidable"));
	/** Biomas de los vikingos (fríos) y de los piratas (costas). */
	public static final TagKey<Biome> VIKING_LANDS = TagKey.of(RegistryKeys.BIOME, EvolvedWarfare.id("viking_lands"));
	public static final TagKey<Biome> PIRATE_COASTS = TagKey.of(RegistryKeys.BIOME, EvolvedWarfare.id("pirate_coasts"));

	private static final int CHECK_INTERVAL = 200;
	private static final int PATROL_MIN = 12000;
	private static final int PATROL_MAX = 24000;
	private static final int FIRST_RAID = 48000;
	private static final int RAID_MIN = 72000;
	private static final int RAID_MAX = 120000;

	private FactionSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(FactionSpawner::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FactionRaids.clear());
	}

	private static long between(Random random, int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		FactionRaids.tick(world);
		long time = world.getTime();
		if (time % CHECK_INTERVAL != 0) {
			return;
		}
		if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
			return;
		}
		Random random = world.getRandom();
		BanditWorldState state = BanditWorldState.get(world);
		if (state.nextPatrol < 0) {
			state.nextPatrol = time + between(random, PATROL_MIN, PATROL_MAX);
			state.markDirty();
		}
		if (state.nextRaid < 0) {
			state.nextRaid = time + FIRST_RAID;
			state.markDirty();
		}
		if (time >= state.nextPatrol) {
			state.nextPatrol = time + between(random, PATROL_MIN, PATROL_MAX);
			state.markDirty();
			if (random.nextFloat() < 0.35F) {
				spawnPatrol(world, random);
			}
		}
		if (time >= state.nextRaid && !FactionRaids.hasActive(world) && FactionRaids.tryStart(world, random, null)) {
			state.nextRaid = time + between(random, RAID_MIN, RAID_MAX);
			state.markDirty();
		}
	}

	static List<ServerPlayerEntity> activePlayers(ServerWorld world) {
		return world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
	}

	static boolean inRaidable(ServerWorld world, BlockPos pos) {
		return world.getStructureAccessor().getStructureContaining(pos, RAIDABLE).hasChildren();
	}

	/** La facción que manda en esa zona: vikingos en el frío, piratas en la costa y bandidos en el resto. */
	public static Faction factionAt(ServerWorld world, BlockPos pos) {
		if (world.getBiome(pos).isIn(VIKING_LANDS)) {
			return Faction.VIKINGS;
		}
		if (world.getBiome(pos).isIn(PIRATE_COASTS)) {
			return Faction.PIRATES;
		}
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * i / 4.0;
			BlockPos p = pos.add(MathHelper.floor(Math.cos(angle) * 28), 0, MathHelper.floor(Math.sin(angle) * 28));
			if (world.getBiome(p).isIn(BiomeTags.IS_OCEAN)) {
				return Faction.PIRATES;
			}
		}
		return Faction.BANDITS;
	}

	public static EntityType<? extends FactionNpcEntity> typeOf(Faction faction) {
		return switch (faction) {
			case BANDITS -> ModEntities.BANDIT;
			case VIKINGS -> ModEntities.VIKING;
			case PIRATES -> ModEntities.PIRATE;
		};
	}

	private static void spawnPatrol(ServerWorld world, Random random) {
		List<ServerPlayerEntity> players = activePlayers(world);
		if (players.isEmpty() || world.getTimeOfDay() / 24000L < 1L) {
			return;
		}
		ServerPlayerEntity player = players.get(random.nextInt(players.size()));
		BlockPos spot = findSpot(world, player.getBlockPos(), 28, 44, random);
		if (spot == null || inRaidable(world, spot)) {
			return;
		}
		Faction faction = factionAt(world, spot);
		boolean hostile = Reputation.hostile(world, player.getUuid(), faction);
		// si no sois enemigos, van de paso hacia otro sitio
		BlockPos target = hostile ? player.getBlockPos() : findSpot(world, spot, 48, 64, random);
		int count = 3 + random.nextInt(3);
		if (world.getDifficulty() == Difficulty.HARD) {
			count++;
		} else if (world.getDifficulty() == Difficulty.EASY) {
			count--;
		}
		for (int i = 0; i < count; i++) {
			FactionNpcEntity member = spawnMember(world, faction, spot.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2),
					i == 0 ? NpcRole.WARRIOR : null, SpawnReason.PATROL, random);
			if (member != null && target != null) {
				member.startPatrol(target);
			}
		}
	}

	/** Superficie firme y despejada (sin agua) en (x, z), o null. */
	@Nullable
	public static BlockPos surface(ServerWorld world, int x, int z) {
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		BlockPos below = pos.down();
		if (!world.getFluidState(below).isEmpty() || !world.getBlockState(below).isSolidBlock(world, below)) {
			return null;
		}
		return world.isAir(pos) && world.isAir(pos.up()) ? pos : null;
	}

	@Nullable
	public static BlockPos findSpot(ServerWorld world, BlockPos center, int minDistance, int maxDistance, Random random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
			int x = center.getX() + MathHelper.floor(Math.cos(angle) * distance);
			int z = center.getZ() + MathHelper.floor(Math.sin(angle) * distance);
			BlockPos pos = surface(world, x, z);
			if (pos != null) {
				return pos;
			}
		}
		return null;
	}

	/** Hace aparecer a un guerrero de una facción (con una clase concreta o al azar). */
	@Nullable
	public static FactionNpcEntity spawnMember(ServerWorld world, Faction faction, BlockPos near, @Nullable NpcRole role,
			SpawnReason reason, Random random) {
		BlockPos pos = surface(world, near.getX(), near.getZ());
		if (pos == null) {
			pos = near;
		}
		FactionNpcEntity member = typeOf(faction).create(world);
		if (member == null) {
			return null;
		}
		member.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (role != null) {
			member.presetRole(role);
		}
		member.initialize(world, world.getLocalDifficulty(pos), reason, null);
		world.spawnEntityAndPassengers(member);
		return member;
	}
}
