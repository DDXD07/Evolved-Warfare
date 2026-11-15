package com.evolvedwarfare.engineering;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Lo común al embrague y al inversor: un eje en una caja que cambia con una señal de redstone. */
public abstract class RedstoneShaftBlock extends KineticBlock {
	protected RedstoneShaftBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.AXIS, Direction.Axis.X).with(Properties.POWERED, false));
	}

	@Override
	protected boolean drawnByRenderer() {
		return false;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.AXIS, Properties.POWERED);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.AXIS, ctx.getSide().getAxis())
				.with(Properties.POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return state.get(Properties.AXIS);
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
		boolean powered = world.isReceivingRedstonePower(pos);
		if (powered != state.get(Properties.POWERED) && world instanceof ServerWorld sw) {
			world.setBlockState(pos, state.with(Properties.POWERED, powered), Block.NOTIFY_LISTENERS);
			KineticNetwork.markDirty(sw, pos);
		}
	}

	@Override
	public float maxSpeed(BlockState state) {
		return Kinetic.WOOD_MAX;
	}
}
