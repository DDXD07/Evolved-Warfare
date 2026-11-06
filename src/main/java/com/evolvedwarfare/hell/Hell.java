package com.evolvedwarfare.hell;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.FireRainPayload;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

/**
 * El Nether bíblico: sin techo, con montañas, colinas y lagos de fuego (eso lo hacen los datos: tools/ew_hell.py); sus
 * criaturas (demonios, diablillos y almas en pena) y la <b>lluvia de fuego y azufre</b>: de vez en cuando, durante un
 * minuto o dos, cae fuego del cielo alrededor de cada jugador del Nether, que quema lo que pilla al raso.
 */
public final class Hell {
	/** Bolas de fuego por jugador y segundo durante la lluvia. */
	private static final int PER_SECOND = 6;
	private static final int RADIUS = 28;
	private static long nextStart = -1L;
	private static long endsAt = -1L;
	private static boolean active;

	private Hell() {
	}

	public static void register() {
		FuelRegistry.INSTANCE.add(ModItems.BRIMSTONE, 1200);
		SpawnRestriction.register(ModEntities.DEMON, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.IMP, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.LOST_SOUL, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.LEGION, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.BEELZEBUB, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.MOLOCH, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(ModEntities.ABADDON, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
		DivineBlessing.register();
		HellFeatures.init();
		NetherClimate.register();
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.SOUL_SAND_VALLEY), SpawnGroup.MONSTER, ModEntities.LEGION, 2, 1, 1);
		// también en los biomas de siempre del Nether
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.NETHER_WASTES, BiomeKeys.CRIMSON_FOREST), SpawnGroup.MONSTER,
				ModEntities.IMP, 10, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.NETHER_WASTES, BiomeKeys.BASALT_DELTAS), SpawnGroup.MONSTER,
				ModEntities.DEMON, 3, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.SOUL_SAND_VALLEY), SpawnGroup.MONSTER, ModEntities.LOST_SOUL, 30, 1, 3);
		ServerTickEvents.END_WORLD_TICK.register(Hell::tick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> FireRainPayload.send(handler.player, active));
	}

	public static boolean raining() {
		return active;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.NETHER) {
			return;
		}
		long now = world.getTime();
		boolean enabled = EwConfig.get().fireRain && EwConfig.get().fireRainEveryMinutes > 0;
		if (!enabled) {
			if (active) {
				stop(world);
			}
			return;
		}
		if (nextStart < 0) {
			nextStart = now + gap(world.getRandom());
		}
		if (!active && now >= nextStart) {
			start(world, now);
		}
		if (active) {
			if (now >= endsAt) {
				stop(world);
				nextStart = now + gap(world.getRandom());
				return;
			}
			if (now % (20 / PER_SECOND) == 0) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					if (!player.isSpectator()) {
						drop(world, player.getBlockPos(), world.getRandom());
					}
				}
			}
			if (now % 100 == 0) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					world.playSound(null, player.getBlockPos().up(30), ModSounds.FIRE_RAIN, SoundCategory.WEATHER, 1.2F, 0.5F + world.random.nextFloat() * 0.3F);
				}
			}
		}
	}

	private static long gap(Random random) {
		long minutes = Math.max(1, EwConfig.get().fireRainEveryMinutes);
		return (long) (minutes * 1200L * (0.6 + random.nextDouble() * 0.8));
	}

	/** Empieza a llover fuego (de 1 a 2 minutos). */
	public static void start(ServerWorld world, long now) {
		active = true;
		endsAt = now + 1200 + world.getRandom().nextInt(1200);
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			FireRainPayload.send(player, true);
			if (player.getWorld() == world) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.fire_rain.start").formatted(Formatting.RED), true);
				world.playSound(null, player.getBlockPos(), ModSounds.FIRE_RAIN, SoundCategory.WEATHER, 2.0F, 0.4F);
			}
		}
	}

	public static void stop(ServerWorld world) {
		active = false;
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			FireRainPayload.send(player, false);
			if (player.getWorld() == world) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.fire_rain.end").formatted(Formatting.GOLD), true);
			}
		}
	}

	/** Deja caer una bola de fuego en un sitio al azar alrededor (solo donde se ve el cielo). Devuelve la bola, o null. */
	@Nullable
	public static SmallFireballEntity drop(ServerWorld world, BlockPos around, Random random) {
		int x = around.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
		int z = around.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		double y = Math.min(world.getTopY() - 2, Math.max(top, around.getY()) + 30);
		if (y <= top + 4) {
			return null;
		}
		Vec3d velocity = new Vec3d((random.nextDouble() - 0.5) * 0.2, -1.0, (random.nextDouble() - 0.5) * 0.2);
		SmallFireballEntity fireball = new SmallFireballEntity(world, x + 0.5, y, z + 0.5, velocity);
		fireball.setVelocity(velocity.multiply(0.8));
		world.spawnEntity(fireball);
		return fireball;
	}
}
