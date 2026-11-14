package com.evolvedwarfare.world.dungeon;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * La reja de la mazmorra: abierta no se ve ni estorba (es como aire que no se puede romper); cuando despierta el jefe,
 * el Corazón la cierra (barrotes macizos) hasta que cae.
 */
public class DungeonGateBlock extends Block {
	public static final BooleanProperty CLOSED = BooleanProperty.of("closed");
	public static final MapCodec<DungeonGateBlock> CODEC = createCodec(DungeonGateBlock::new);

	public DungeonGateBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(CLOSED, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(CLOSED);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return state.get(CLOSED) ? BlockRenderType.MODEL : BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(CLOSED) ? VoxelShapes.fullCube() : VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(CLOSED) ? VoxelShapes.fullCube() : VoxelShapes.empty();
	}

	@Override
	protected boolean canPathfindThrough(BlockState state, NavigationType type) {
		return !state.get(CLOSED);
	}

	@Override
	protected boolean isSideInvisible(BlockState state, BlockState neighbor, Direction direction) {
		return neighbor.isOf(this) && neighbor.get(CLOSED) == state.get(CLOSED) || super.isSideInvisible(state, neighbor, direction);
	}
}
