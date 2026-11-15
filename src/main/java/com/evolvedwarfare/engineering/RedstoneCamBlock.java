package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * La leva de redstone: un eje con cuatro levas. Con cada una da señal a su alrededor (media vuelta de cada cuarto), así
 * que da cuatro pulsos por vuelta: a 16 rpm, uno cada 19 tics; más deprisa, más seguidos. Para relojes y automatismos.
 */
public class RedstoneCamBlock extends MachineBlock {
	public static final MapCodec<RedstoneCamBlock> CODEC = createCodec(RedstoneCamBlock::new);
	public static final float IMPACT = 0.5F;
	public static final float LOBE = 90.0F;

	public RedstoneCamBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.AXIS, Direction.Axis.X).with(Properties.POWERED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.AXIS, Properties.POWERED);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.AXIS, ctx.getSide().getAxis());
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == state.get(Properties.AXIS);
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return state.get(Properties.AXIS);
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		boolean on = state.get(Properties.POWERED);
		if (rpm > 0.01F) {
			be.phase = (be.phase + rpm * 0.3F) % 360.0F;
			on = be.phase % LOBE < LOBE / 2;
		} else {
			on = false;
		}
		if (on != state.get(Properties.POWERED)) {
			world.setBlockState(pos, state.with(Properties.POWERED, on), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	@Override
	protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return state.get(Properties.POWERED) ? 15 : 0;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(Properties.AXIS)) {
			case X -> Block.createCuboidShape(0, 1, 1, 16, 15, 15);
			case Y -> Block.createCuboidShape(1, 0, 1, 15, 16, 15);
			case Z -> Block.createCuboidShape(1, 1, 0, 15, 15, 16);
		};
	}
}
