package com.evolvedwarfare.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Un caldero con agua encima de una fogata encendida hierve: burbujas y vapor (y se puede beber o llenar agua hervida). */
@Mixin(CampfireBlock.class)
public abstract class CampfireBoilMixin {
	@Inject(method = "randomDisplayTick", at = @At("TAIL"))
	private void evolvedwarfare$boil(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
		if (!CampfireBlock.isLitCampfire(state)) {
			return;
		}
		BlockState above = world.getBlockState(pos.up());
		if (!above.isOf(Blocks.WATER_CAULDRON)) {
			return;
		}
		double y = pos.getY() + 1 + (6.0 + 3.0 * above.get(LeveledCauldronBlock.LEVEL)) / 16.0;
		for (int i = 0; i < 3; i++) {
			double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
			double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
			world.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.04, 0.0);
		}
		world.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
		if (random.nextInt(6) == 0) {
			world.playSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.4F,
					0.9F + random.nextFloat() * 0.2F, false);
		}
	}
}
