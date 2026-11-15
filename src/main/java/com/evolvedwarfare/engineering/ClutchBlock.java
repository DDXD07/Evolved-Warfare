package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** El embrague: un eje que, con señal de redstone, se suelta y corta el giro a un lado y a otro. */
public class ClutchBlock extends RedstoneShaftBlock {
	public static final MapCodec<ClutchBlock> CODEC = createCodec(ClutchBlock::new);

	public ClutchBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return !state.get(Properties.POWERED) && face.getAxis() == state.get(Properties.AXIS);
	}
}
