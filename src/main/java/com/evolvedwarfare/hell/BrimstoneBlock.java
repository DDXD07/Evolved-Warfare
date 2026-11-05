package com.evolvedwarfare.hell;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** El azufre: humea (cuando tiene aire encima) y huele a infierno. Da trozos de azufre, que arden en el horno y hacen pólvora. */
public class BrimstoneBlock extends Block {
	public BrimstoneBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(6) == 0 && world.getBlockState(pos.up()).isAir()) {
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
		}
	}
}
