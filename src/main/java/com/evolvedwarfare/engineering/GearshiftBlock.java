package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** El inversor: un eje que, con señal de redstone, hace girar al revés lo que hay por su lado positivo (este, sur o arriba). */
public class GearshiftBlock extends RedstoneShaftBlock {
	public static final MapCodec<GearshiftBlock> CODEC = createCodec(GearshiftBlock::new);

	public GearshiftBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == state.get(Properties.AXIS);
	}

	@Override
	public float axleRatio(BlockState state, Direction face) {
		return state.get(Properties.POWERED) && face.getDirection() == Direction.AxisDirection.POSITIVE ? -1.0F : 1.0F;
	}
}
