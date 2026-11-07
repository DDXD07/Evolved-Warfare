package com.evolvedwarfare.ores;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/** Oro de aluvión: en el lecho de los ríos, manchas de grava aurífera (el oro que el agua arrastró de las montañas). */
public class AlluvialGoldFeature extends Feature<DefaultFeatureConfig> {
	public AlluvialGoldFeature() {
		super(DefaultFeatureConfig.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos o = context.getOrigin();
		int radius = 2 + random.nextInt(3);
		int placed = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius || random.nextFloat() > 0.65F) {
					continue;
				}
				int x = o.getX() + dx, z = o.getZ() + dz;
				int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
				m.set(x, floor, z);
				if (!world.getFluidState(m.up()).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
					continue;
				}
				for (int k = 0; k < 2; k++) {
					BlockState s = world.getBlockState(m);
					if (s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SAND) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.COARSE_DIRT)) {
						world.setBlockState(m, Ores.GOLD_GRAVEL.getDefaultState(), 2);
						placed++;
					}
					m.move(0, -1, 0);
					if (random.nextBoolean()) {
						break;
					}
				}
			}
		}
		return placed > 0;
	}
}
