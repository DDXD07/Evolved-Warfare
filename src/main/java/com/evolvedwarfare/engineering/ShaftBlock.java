package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** El eje y los engranajes (pequeño y grande): giran sobre su eje (como un tronco, según la cara donde se pongan). */
public class ShaftBlock extends KineticBlock {
	public static final MapCodec<ShaftBlock> CODEC = createCodec(s -> new ShaftBlock(s, 0));
	private final int gear;
	/** 0 madera (hasta 64 rpm), 1 hierro (256), 2 acero (1024). */
	private final int tier;

	public ShaftBlock(Settings settings, int gear) {
		this(settings, gear, 0);
	}

	public ShaftBlock(Settings settings, int gear, int tier) {
		super(settings);
		this.gear = gear;
		this.tier = tier;
		setDefaultState(getStateManager().getDefaultState().with(Properties.AXIS, Direction.Axis.Y));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.AXIS);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.AXIS, ctx.getSide().getAxis());
	}

	@Override
	protected BlockState rotate(BlockState state, net.minecraft.util.BlockRotation rotation) {
		return PillarBlock.changeRotation(state, rotation);
	}

	public int tier() {
		return tier;
	}

	@Override
	public float maxSpeed(BlockState state) {
		return tier == 0 ? Kinetic.WOOD_MAX : tier == 1 ? Kinetic.IRON_MAX : Kinetic.STEEL_MAX;
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
	public int gear(BlockState state) {
		return gear;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		double r = gear == 0 ? 2.5 : gear == 1 ? 6.5 : 8.0;
		double t = gear == 0 ? 8.0 : 2.5;
		return switch (state.get(Properties.AXIS)) {
			case X -> VoxelShapes.union(Block.createCuboidShape(0, 6, 6, 16, 10, 10), Block.createCuboidShape(8 - t, 8 - r, 8 - r, 8 + t, 8 + r, 8 + r));
			case Y -> VoxelShapes.union(Block.createCuboidShape(6, 0, 6, 10, 16, 10), Block.createCuboidShape(8 - r, 8 - t, 8 - r, 8 + r, 8 + t, 8 + r));
			case Z -> VoxelShapes.union(Block.createCuboidShape(6, 6, 0, 10, 10, 16), Block.createCuboidShape(8 - r, 8 - r, 8 - t, 8 + r, 8 + r, 8 + t));
		};
	}
}
