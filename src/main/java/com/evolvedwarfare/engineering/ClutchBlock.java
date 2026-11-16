package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * El embrague en T (fase 81): una caja pequeña con una entrada y dos salidas. El giro entra por la cara que se pone
 * contra el eje que lo mueve (FACING) y sale por los dos lados; con señal de redstone se sueltan las dos salidas (la
 * entrada sigue girando). Puesto en el suelo o el techo, las salidas quedan a la izquierda y la derecha de quien lo pone.
 */
public class ClutchBlock extends KineticBlock {
	public static final MapCodec<ClutchBlock> CODEC = createCodec(ClutchBlock::new);

	public ClutchBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.FACING, Direction.NORTH)
				.with(Properties.HORIZONTAL_AXIS, Direction.Axis.X).with(Properties.POWERED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected boolean drawnByRenderer() {
		return false;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.FACING, Properties.HORIZONTAL_AXIS, Properties.POWERED);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.FACING, ctx.getSide().getOpposite())
				.with(Properties.HORIZONTAL_AXIS, ctx.getHorizontalPlayerFacing().rotateYClockwise().getAxis())
				.with(Properties.POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		Direction f = state.get(Properties.FACING);
		Direction.Axis axis = state.get(Properties.HORIZONTAL_AXIS);
		if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
			axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		}
		return state.with(Properties.FACING, rotation.rotate(f)).with(Properties.HORIZONTAL_AXIS, axis);
	}

	/** La cara por la que entra el giro. */
	public static Direction input(BlockState state) {
		return state.get(Properties.FACING);
	}

	/** Las dos caras por las que sale. */
	public static Direction[] outputs(BlockState state) {
		Direction in = input(state);
		if (in.getAxis().isHorizontal()) {
			return new Direction[] {in.rotateYClockwise(), in.rotateYCounterclockwise()};
		}
		Direction.Axis axis = state.get(Properties.HORIZONTAL_AXIS);
		return new Direction[] {Direction.from(axis, Direction.AxisDirection.POSITIVE), Direction.from(axis, Direction.AxisDirection.NEGATIVE)};
	}

	public static boolean engaged(BlockState state) {
		return !state.get(Properties.POWERED);
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		if (face == input(state)) {
			return true;
		}
		if (!engaged(state)) {
			return false;
		}
		for (Direction d : outputs(state)) {
			if (d == face) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return null;
	}

	@Override
	public float maxSpeed(BlockState state) {
		return Kinetic.WOOD_MAX;
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
}
