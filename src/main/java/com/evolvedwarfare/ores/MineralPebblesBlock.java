package com.evolvedwarfare.ores;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/** Guijarros del mineral que hay debajo: la pista en la superficie de un yacimiento (óxido del hierro, verde del cobre...). */
public class MineralPebblesBlock extends Block {
	public enum Mineral implements StringIdentifiable {
		COAL, IRON, COPPER, GOLD, REDSTONE, LAPIS, DIAMOND, EMERALD;

		@Override
		public String asString() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Mineral byName(String name) {
			for (Mineral m : values()) {
				if (m.asString().equals(name)) {
					return m;
				}
			}
			return null;
		}
	}

	public static final EnumProperty<Mineral> MINERAL = EnumProperty.of("mineral", Mineral.class);
	public static final IntProperty TURN = IntProperty.of("turn", 0, 3);
	public static final MapCodec<MineralPebblesBlock> CODEC = createCodec(MineralPebblesBlock::new);
	private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 2, 14);

	public MineralPebblesBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(MINERAL, Mineral.IRON).with(TURN, 0));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(MINERAL, TURN);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		return direction == Direction.DOWN && !canPlaceAt(state, world, pos) ? net.minecraft.block.Blocks.AIR.getDefaultState()
				: super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}
}
