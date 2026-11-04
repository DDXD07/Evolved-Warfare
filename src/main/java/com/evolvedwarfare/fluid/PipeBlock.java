package com.evolvedwarfare.fluid;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Tubería de cobre: se une sola con las tuberías, los grifos y las bombas de al lado, y con el agua que toque (es la
 * toma: por ahí entra el agua).
 */
public class PipeBlock extends ConnectingBlock {
	public static final MapCodec<PipeBlock> CODEC = createCodec(PipeBlock::new);

	public PipeBlock(Settings settings) {
		super(0.1875F, settings);
		BlockState state = getStateManager().getDefaultState();
		for (Direction d : Direction.values()) {
			state = state.with(FACING_PROPERTIES.get(d), false);
		}
		setDefaultState(state);
	}

	@Override
	protected MapCodec<? extends ConnectingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	public static boolean connects(BlockView world, BlockPos pos, Direction dir) {
		BlockPos n = pos.offset(dir);
		BlockState s = world.getBlockState(n);
		if (s.getBlock() instanceof PipeBlock || s.getBlock() instanceof HandPumpBlock) {
			return true;
		}
		if (s.getBlock() instanceof FaucetBlock) {
			// el grifo se une por detrás y por arriba
			return dir != Direction.UP && (dir == s.get(FaucetBlock.FACING) || dir == Direction.DOWN);
		}
		return world instanceof World w ? Plumbing.isWater(w, n) : !s.getFluidState().isEmpty();
	}

	private BlockState connected(BlockView world, BlockPos pos, BlockState state) {
		for (Direction d : Direction.values()) {
			state = state.with(FACING_PROPERTIES.get(d), connects(world, pos, d));
		}
		return state;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return connected(ctx.getWorld(), ctx.getBlockPos(), getDefaultState());
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos) {
		return state.with(FACING_PROPERTIES.get(direction), connects(world, pos, direction));
	}
}
