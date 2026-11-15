package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/** La muela de molino: toma el giro por abajo (o por arriba) y muele lo que tenga dentro. */
public class MillstoneBlock extends MachineBlock {
	public static final MapCodec<MillstoneBlock> CODEC = createCodec(MillstoneBlock::new);
	public static final float IMPACT = 2.0F;
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 14, 16);

	public MillstoneBlock(Settings settings) {
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
	public boolean hasInventory() {
		return true;
	}

	@Nullable
	@Override
	public MachineRecipes.Recipe recipe(ItemStack stack) {
		return MachineRecipes.millstone(stack);
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == Direction.Axis.Y;
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return Direction.Axis.Y;
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		if (world.getTime() % 10 == 0) {
			be.suck(world, new Box(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1, pos.getY() + 1.6, pos.getZ() + 1));
		}
		ItemStack in = be.input().copy();
		boolean working = rpm > 0.01F && !in.isEmpty();
		if (be.process(rpm, world.random)) {
			world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.3F, 0.7F + world.random.nextFloat() * 0.2F);
		}
		if (working && world.getTime() % 5 == 0) {
			Object effect = in.getItem() instanceof BlockItem bi ? new BlockStateParticleEffect(ParticleTypes.BLOCK, bi.getBlock().getDefaultState())
					: new ItemStackParticleEffect(ParticleTypes.ITEM, in);
			world.spawnParticles((net.minecraft.particle.ParticleEffect) effect, pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5, 3, 0.3, 0.05, 0.3,
					0.02);
			if (world.getTime() % 40 == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.15F, 0.5F);
			}
		}
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
