package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.util.math.Direction;

/** La caja de engranajes: pasa el giro a sus seis caras (para doblar esquinas). */
public class GearboxBlock extends KineticBlock {
	public static final MapCodec<GearboxBlock> CODEC = createCodec(GearboxBlock::new);

	public GearboxBlock(Settings settings) {
		super(settings);
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
	public boolean hasAxle(BlockState state, Direction face) {
		return true;
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return null;
	}

	@Override
	public float maxSpeed(BlockState state) {
		return Kinetic.WOOD_MAX;
	}
}
