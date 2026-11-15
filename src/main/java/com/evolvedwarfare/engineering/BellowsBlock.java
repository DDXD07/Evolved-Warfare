package com.evolvedwarfare.engineering;

import com.evolvedwarfare.smithing.CrucibleBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * El fuelle: sopla al horno (o alto horno, ahumador) o al crisol que tiene delante (o al crisol que hay encima del fuego
 * de delante), y lo hace ir más deprisa: a 16 rpm, el doble; a 32, el triple. El horno también quema el combustible más
 * deprisa.
 */
public class BellowsBlock extends SideShaftMachineBlock {
	public static final MapCodec<BellowsBlock> CODEC = createCodec(BellowsBlock::new);
	public static final float IMPACT = 1.0F;
	/** Las rpm que dan un tic de más por tic. */
	public static final float RPM_PER_TICK = 16.0F;
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 12, 16);

	public BellowsBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		if (rpm < 0.01F) {
			be.boost = 0;
			return;
		}
		Direction f = facing(state);
		BlockPos target = pos.offset(f);
		be.boost = Math.min(be.boost + rpm / RPM_PER_TICK, 4.0F);
		boolean blew = false;
		while (be.boost >= 1.0F) {
			be.boost -= 1.0F;
			blew |= blow(world, target);
		}
		if (world.getTime() % 8 == 0) {
			double x = pos.getX() + 0.5 + f.getOffsetX() * 0.6, z = pos.getZ() + 0.5 + f.getOffsetZ() * 0.6;
			world.spawnParticles(blew ? ParticleTypes.FLAME : ParticleTypes.CLOUD, x, pos.getY() + 0.3, z, 1, 0.02, 0.02, 0.02, 0.01);
			if (world.getTime() % 40 == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5F, 0.6F);
			}
		}
	}

	/** Un tic de más a lo que haya delante; true si ha soplado a algo encendido. */
	static boolean blow(ServerWorld world, BlockPos target) {
		BlockState ts = world.getBlockState(target);
		BlockEntity t = world.getBlockEntity(target);
		if (t instanceof AbstractFurnaceBlockEntity furnace) {
			if (ts.contains(Properties.LIT) && ts.get(Properties.LIT)) {
				AbstractFurnaceBlockEntity.tick(world, target, ts, furnace);
				return true;
			}
			return false;
		}
		for (BlockPos p : new BlockPos[] {target, target.up()}) {
			if (world.getBlockEntity(p) instanceof CrucibleBlockEntity crucible && CrucibleBlockEntity.heatBelow(world, p)) {
				CrucibleBlockEntity.tick(world, p, world.getBlockState(p), crucible);
				return true;
			}
		}
		return false;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
