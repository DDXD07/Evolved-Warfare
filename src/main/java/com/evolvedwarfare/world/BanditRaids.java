package com.evolvedwarfare.world;

import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.NpcRole;
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
 * Asaltos de bandidos a aldeas y ciudades: suena un cuerno, aparece una barra con los bandidos que quedan y
 * el grupo marcha hacia el centro. Acaba al derrotarlos a todos o a los 10 minutos (entonces se retiran).
 */
public final class BanditRaids {
	private static final int DURATION = 12000;
	private static final double BAR_RANGE_SQ = 128.0 * 128.0;
	private static final Map<RegistryKey<World>, Raid> ACTIVE = new HashMap<>();

	private BanditRaids() {
	}

	private static final class Raid {
		final BlockPos center;
		final Set<UUID> remaining;
		final int total;
		final long until;
		final ServerBossBar bar = new ServerBossBar(Text.translatable("event.evolvedwarfare.raid"), BossBar.Color.RED,
				BossBar.Style.NOTCHED_10);

		Raid(BlockPos center, Set<UUID> raiders, long until) {
			this.center = center;
			this.remaining = raiders;
			this.total = raiders.size();
			this.until = until;
		}
	}

	public static boolean hasActive(ServerWorld world) {
		return ACTIVE.containsKey(world.getRegistryKey());
	}

	public static void clear() {
		for (Raid raid : ACTIVE.values()) {
			raid.bar.clearPlayers();
		}
		ACTIVE.clear();
	}

	/** Lo llama un bandido al morir. */
	public static void onRaiderDeath(BanditEntity bandit) {
		if (bandit.getWorld() instanceof ServerWorld world) {
			Raid raid = ACTIVE.get(world.getRegistryKey());
			if (raid != null) {
				raid.remaining.remove(bandit.getUuid());
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
		raid.bar.setName(Text.translatable("event.evolvedwarfare.raid.left", left));
		if (left == 0) {
			tellNear(world, raid.center, Text.translatable("message.evolvedwarfare.raid.victory").formatted(Formatting.GOLD));
			world.playSound(null, raid.center.getX(), raid.center.getY(), raid.center.getZ(),
					SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 3.0F, 1.0F);
			raid.bar.clearPlayers();
			ACTIVE.remove(world.getRegistryKey());
		} else if (world.getTime() > raid.until) {
			for (UUID id : raid.remaining) {
				if (world.getEntity(id) instanceof BanditEntity bandit) {
					bandit.endRaid();
				}
			}
			tellNear(world, raid.center, Text.translatable("message.evolvedwarfare.raid.retreat").formatted(Formatting.GRAY));
			raid.bar.clearPlayers();
			ACTIVE.remove(world.getRegistryKey());
		}
	}

	/** Busca un jugador dentro de una aldea o ciudad y, a veces, lanza el asalto. */
	static boolean tryStart(ServerWorld world, Random random) {
		List<StructureStart> targets = new ArrayList<>();
		for (ServerPlayerEntity player : BanditSpawner.activePlayers(world)) {
			StructureStart start = world.getStructureAccessor().getStructureContaining(player.getBlockPos(), BanditSpawner.RAIDABLE);
			if (start.hasChildren()) {
				targets.add(start);
			}
		}
		if (targets.isEmpty() || random.nextInt(3) != 0) {
			return false;
		}
		StructureStart target = targets.get(random.nextInt(targets.size()));
		BlockBox box = target.getBoundingBox();
		BlockPos boxCenter = box.getCenter();
		BlockPos center = BanditSpawner.surface(world, boxCenter.getX(), boxCenter.getZ());
		if (center == null) {
			center = boxCenter;
		}
		int radius = MathHelper.clamp(Math.max(box.getBlockCountX(), box.getBlockCountZ()) / 2 + 16, 40, 80);
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
			BanditEntity bandit = BanditSpawner.spawnBandit(world,
					spot.add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), role, SpawnReason.EVENT, random);
			if (bandit != null) {
				bandit.startRaid(center, until);
				raiders.add(bandit.getUuid());
			}
		}
		if (raiders.isEmpty()) {
			return false;
		}
		ACTIVE.put(world.getRegistryKey(), new Raid(center, raiders, until));
		world.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE,
				64.0F, 1.0F);
		tellNear(world, center, Text.translatable("message.evolvedwarfare.raid.start").formatted(Formatting.RED));
		return true;
	}

	@Nullable
	private static BlockPos findEdge(ServerWorld world, BlockPos center, int radius, Random random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = center.getX() + MathHelper.floor(Math.cos(angle) * radius);
			int z = center.getZ() + MathHelper.floor(Math.sin(angle) * radius);
			BlockPos pos = BanditSpawner.surface(world, x, z);
			if (pos != null && !BanditSpawner.inRaidable(world, pos)) {
				return pos;
			}
		}
		return null;
	}
}
