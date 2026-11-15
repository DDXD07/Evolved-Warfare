package com.evolvedwarfare.engineering;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

/** Las máquinas que miran a un lado (el frente, lejos de quien la pone) y toman el eje por los dos costados. */
public abstract class SideShaftMachineBlock extends MachineBlock {
	protected SideShaftMachineBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)));
	}

	public static Direction facing(BlockState state) {
		return state.get(Properties.HORIZONTAL_FACING);
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == axis(state);
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return facing(state).rotateYClockwise().getAxis();
	}
}
