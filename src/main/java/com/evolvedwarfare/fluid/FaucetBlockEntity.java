package com.evolvedwarfare.fluid;

import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** El grifo abierto: cada medio segundo pasa una capa de agua de la toma a lo que tenga debajo (3 capas = un nivel del caldero). */
public class FaucetBlockEntity extends BlockEntity {
	public static final int PERIOD = 10;
	private int cauldronUnits;
	private int timer;

	public FaucetBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.FAUCET_ENTITY, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, FaucetBlockEntity be) {
		if (!state.get(FaucetBlock.OPEN) || ++be.timer < PERIOD) {
			return;
		}
		be.timer = 0;
		be.pour(world, pos, state);
	}

	/** Una capa: si hay toma y sitio debajo. */
	public boolean pour(World world, BlockPos pos, BlockState state) {
		BlockPos out = pos.down();
		if (!Plumbing.canReceive(world, out) || !Plumbing.take(world, pos, 1)) {
			return false;
		}
		BlockState below = world.getBlockState(out);
		if (below.isOf(Blocks.CAULDRON) || below.isOf(Blocks.WATER_CAULDRON)) {
			if (++cauldronUnits >= 3) {
				cauldronUnits = 0;
				int level = below.isOf(Blocks.WATER_CAULDRON) ? below.get(LeveledCauldronBlock.LEVEL) + 1 : 1;
				world.setBlockState(out, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, Math.min(3, level)));
				world.playSound(null, out, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.4F, 1.3F);
			}
			markDirty();
		} else {
			FiniteFluids.add(world, out, Fluids.WATER, 1);
		}
		if (world instanceof ServerWorld sw) {
			Direction f = state.get(FaucetBlock.FACING);
			double x = pos.getX() + 0.5 + f.getOffsetX() * 0.2;
			double z = pos.getZ() + 0.5 + f.getOffsetZ() * 0.2;
			sw.spawnParticles(ParticleTypes.FALLING_WATER, x, pos.getY() + 0.2, z, 3, 0.03, 0.0, 0.03, 0.0);
			if (world.random.nextInt(3) == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, SoundCategory.BLOCKS, 0.6F,
						0.8F + world.random.nextFloat() * 0.4F);
			}
		}
		return true;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("CauldronUnits", cauldronUnits);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		cauldronUnits = nbt.getInt("CauldronUnits");
	}
}
