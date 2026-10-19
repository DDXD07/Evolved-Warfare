package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKeys;
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
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Aparición de bandidos fuera de sus fuertes:
 * <ul>
 *   <li><b>Patrullas</b>: cada 10-20 min de juego, a veces (35 %), un grupo de 3-6 aparece a 28-44 bloques de
 *       un jugador y camina hacia donde estaba. Desde el segundo día de juego.</li>
 *   <li><b>Asaltos</b>: cada 3-5 días de juego (el primero, a partir del día 2), si un jugador está dentro de
 *       una aldea o ciudad, un grupo grande la ataca (ver {@link BanditRaids}).</li>
 * </ul>
 * Nada de esto ocurre en pacífico ni con doMobSpawning desactivado.
 */
public final class BanditSpawner {
	/** Estructuras que pueden sufrir asaltos: aldeas vanilla y ciudades del mod. */
	public static final TagKey<Structure> RAIDABLE = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("raidable"));

	private static final int CHECK_INTERVAL = 200;
	private static final int PATROL_MIN = 12000;
	private static final int PATROL_MAX = 24000;
	private static final int FIRST_RAID = 48000;
	private static final int RAID_MIN = 72000;
	private static final int RAID_MAX = 120000;

	private BanditSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(BanditSpawner::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BanditRaids.clear());
	}

	private static long between(Random random, int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		BanditRaids.tick(world);
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
		if (time >= state.nextRaid && !BanditRaids.hasActive(world) && BanditRaids.tryStart(world, random)) {
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
		int count = 3 + random.nextInt(3);
		if (world.getDifficulty() == Difficulty.HARD) {
			count++;
		} else if (world.getDifficulty() == Difficulty.EASY) {
			count--;
		}
		for (int i = 0; i < count; i++) {
			BanditEntity bandit = spawnBandit(world, spot.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2),
					i == 0 ? NpcRole.WARRIOR : null, SpawnReason.PATROL, random);
			if (bandit != null) {
				bandit.startPatrol(player.getBlockPos());
			}
		}
	}

	/** Superficie firme y despejada (sin agua) en (x, z), o null. */
	@Nullable
	static BlockPos surface(ServerWorld world, int x, int z) {
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
	static BlockPos findSpot(ServerWorld world, BlockPos center, int minDistance, int maxDistance, Random random) {
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

	@Nullable
	static BanditEntity spawnBandit(ServerWorld world, BlockPos near, @Nullable NpcRole role, SpawnReason reason, Random random) {
		BlockPos pos = surface(world, near.getX(), near.getZ());
		if (pos == null) {
			pos = near;
		}
		BanditEntity bandit = ModEntities.BANDIT.create(world);
		if (bandit == null) {
			return null;
		}
		bandit.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (role != null) {
			bandit.presetRole(role);
		}
		bandit.initialize(world, world.getLocalDifficulty(pos), reason, null);
		world.spawnEntityAndPassengers(bandit);
		return bandit;
	}
}
