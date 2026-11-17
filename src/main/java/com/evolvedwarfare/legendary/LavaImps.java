package com.evolvedwarfare.legendary;

import com.evolvedwarfare.config.EwConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Los diablitos de la lava (fase 110): cada 10 segundos, si estás a menos de 8 bloques de un pozo de lava de 2x2 o más,
 * hay una probabilidad ({@code lavaImpChance}) de que salga uno de la lava, riéndose (como mucho dos a la vez cerca).
 */
public final class LavaImps {
	private LavaImps() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 200 != 0 || !BlockMobs.automatic() || EwConfig.get().lavaImpChance <= 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				ServerWorld world = player.getServerWorld();
				if (player.isCreative() || player.isSpectator() || world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
						|| world.random.nextDouble() >= EwConfig.get().lavaImpChance) {
					continue;
				}
				BlockPos pool = findPool(world, player.getBlockPos(), 8);
				if (pool != null && world.getEntitiesByType(Legendaries.LAVA_IMP, player.getBoundingBox().expand(32), e -> true).size() < 2) {
					emerge(world, pool, player);
				}
			}
		});
	}

	private static boolean lava(ServerWorld world, BlockPos pos) {
		return world.getFluidState(pos).isIn(FluidTags.LAVA) && world.getFluidState(pos).isStill();
	}

	/** Un pozo de lava de 2x2 o más cerca (devuelve una de sus casillas de la superficie), o null. */
	@Nullable
	public static BlockPos findPool(ServerWorld world, BlockPos center, int radius) {
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int dy = -4; dy <= 2; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (lava(world, p) && lava(world, p.east()) && lava(world, p.south()) && lava(world, p.east().south())
							&& !world.getFluidState(p.up()).isIn(FluidTags.LAVA)) {
						return p.toImmutable();
					}
				}
			}
		}
		return null;
	}

	/** Sale un diablito de la lava, riéndose, y va a por el jugador. */
	@Nullable
	public static LavaImpEntity emerge(ServerWorld world, BlockPos pool, ServerPlayerEntity player) {
		LavaImpEntity imp = Legendaries.LAVA_IMP.create(world);
		if (imp == null) {
			return null;
		}
		imp.refreshPositionAndAngles(pool.getX() + 1.0, pool.getY() + 1, pool.getZ() + 1.0, world.random.nextFloat() * 360, 0);
		imp.initialize(world, world.getLocalDifficulty(pool), SpawnReason.EVENT, null);
		imp.setTarget(player);
		world.spawnEntity(imp);
		world.spawnParticles(ParticleTypes.LAVA, imp.getX(), imp.getY(), imp.getZ(), 20, 0.5, 0.2, 0.5, 0.1);
		world.playSound(null, pool, SoundEvents.BLOCK_LAVA_POP, SoundCategory.HOSTILE, 1.5F, 0.6F);
		world.playSound(null, pool, SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.HOSTILE, 1.2F, 0.6F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.lava_imp").formatted(Formatting.GOLD, Formatting.ITALIC), true);
		return imp;
	}
}
