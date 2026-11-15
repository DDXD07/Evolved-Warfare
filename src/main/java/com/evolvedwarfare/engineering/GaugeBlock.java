package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** El velocímetro y el medidor de fuerza: un eje pasa por dentro (de lado a lado) y la aguja marca arriba. */
public class GaugeBlock extends KineticBlock {
	public static final MapCodec<GaugeBlock> CODEC = createCodec(s -> new GaugeBlock(s, false));
	private final boolean stress;

	public GaugeBlock(Settings settings, boolean stress) {
		super(settings);
		this.stress = stress;
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X));
	}

	public boolean measuresStress() {
		return stress;
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
		builder.add(Properties.HORIZONTAL_AXIS);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction.Axis a = ctx.getSide().getAxis().isHorizontal() ? ctx.getSide().getAxis() : ctx.getHorizontalPlayerFacing().getAxis();
		return getDefaultState().with(Properties.HORIZONTAL_AXIS, a);
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == state.get(Properties.HORIZONTAL_AXIS);
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return state.get(Properties.HORIZONTAL_AXIS);
	}
}
