package com.evolvedwarfare.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pescador (zona 13x13): necesita al menos 4 bloques de agua (fuentes con aire encima) cerca del puesto. Pesca con
 * la tabla de pesca vanilla (peces y, a veces, basura). Con caña pesca más rápido; cada captura gasta un uso.
 */
public class FishermanJob implements Job {
	private static final int MIN_WATER = 4;
	private List<BlockPos> water = List.of();
	private long scannedAt = Long.MIN_VALUE;

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		long now = ctx.world.getTime();
		if (now - scannedAt > 200) {
			water = findWater(ctx.world, ctx.area());
			scannedAt = now;
		}
		if (water.size() < MIN_WATER) {
			return ctx.idle(WorkStatus.NO_WATER);
		}
		BlockPos spot = water.get(ctx.random().nextInt(water.size()));
		int ticks = ctx.ticks(200 + ctx.random().nextInt(200));
		return WorkAction.fromSite(ctx.sitePos, Vec3d.ofCenter(spot), ticks, c -> fish(c, spot))
				.effect((c, t) -> {
					if (t % 30 == 0) {
						c.world.spawnParticles(ParticleTypes.FISHING, spot.getX() + 0.5, spot.getY() + 0.95, spot.getZ() + 0.5,
								3, 0.25, 0.0, 0.25, 0.02);
					}
				});
	}

	private static List<BlockPos> findWater(ServerWorld world, BlockBox area) {
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.iterate(area.getMinX(), area.getMinY(), area.getMinZ(), area.getMaxX(), area.getMaxY(), area.getMaxZ())) {
			FluidState fluid = world.getFluidState(p);
			if (fluid.isIn(FluidTags.WATER) && fluid.isStill() && world.getBlockState(p.up()).isAir()) {
				found.add(p.toImmutable());
			}
		}
		return found;
	}

	private static boolean fish(WorkContext ctx, BlockPos spot) {
		ServerWorld world = ctx.world;
		if (!world.getFluidState(spot).isIn(FluidTags.WATER)) {
			return false;
		}
		LootContextParameterSet params = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(spot))
				.add(LootContextParameters.TOOL, ctx.tool())
				.luck(0.0F)
				.build(LootContextTypes.FISHING);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(LootTables.FISHING_GAMEPLAY);
		for (ItemStack stack : table.generateLoot(params)) {
			ctx.store(stack);
		}
		world.spawnParticles(ParticleTypes.SPLASH, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 12, 0.3, 0.0, 0.3, 0.1);
		ctx.sound(spot, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.5F, 1.0F);
		if (ctx.hasTool()) {
			ctx.damageTool(1);
		}
		return true;
	}
}
