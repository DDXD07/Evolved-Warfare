package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** La manivela: mientras se le da (clic derecho mantenido) gira a 16 rpm con poca fuerza, y cansa. */
public class CrankBlock extends KineticBlock {
	public static final MapCodec<CrankBlock> CODEC = createCodec(CrankBlock::new);
	public static final float SPEED = 16.0F, CAPACITY = 48.0F;

	public CrankBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.FACING, ctx.getSide());
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face == state.get(Properties.FACING).getOpposite();
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return state.get(Properties.FACING).getAxis();
	}

	@Override
	public int sourceInterval() {
		return 4;
	}

	@Override
	public float generate(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		return be.crankTicks > 0 ? SPEED * state.get(Properties.FACING).getDirection().offset() : 0.0F;
	}

	@Override
	public float capacity(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		return CAPACITY;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.isSneaking()) {
			return super.onUse(state, world, pos, player, hit);
		}
		if (!world.isClient && world.getBlockEntity(pos) instanceof KineticBlockEntity be) {
			be.turnCrank(10);
			be.refreshSource();
			player.addExhaustion(0.08F);
			if (world.getTime() % 8 == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.4F, 0.6F);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(Properties.FACING)) {
			case UP -> Block.createCuboidShape(3, 0, 3, 13, 12, 13);
			case DOWN -> Block.createCuboidShape(3, 4, 3, 13, 16, 13);
			case NORTH -> Block.createCuboidShape(3, 3, 4, 13, 13, 16);
			case SOUTH -> Block.createCuboidShape(3, 3, 0, 13, 13, 12);
			case WEST -> Block.createCuboidShape(4, 3, 3, 16, 13, 13);
			case EAST -> Block.createCuboidShape(0, 3, 3, 12, 13, 13);
		};
	}
}
