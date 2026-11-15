package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * El molino de viento (pequeño, mediano o grande): el bloque es el cubo con el eje hacia atrás; las aspas giran delante.
 * Necesitan el aire libre (nada macizo donde barren); dan más fuerza cuanto más alto están, y más con lluvia o tormenta.
 */
public class WindmillBlock extends KineticBlock {
	public static final MapCodec<WindmillBlock> CODEC = createCodec(s -> new WindmillBlock(s, 5));
	private final int diameter;

	public WindmillBlock(Settings settings, int diameter) {
		super(settings);
		this.diameter = diameter;
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
	}

	public int diameter() {
		return diameter;
	}

	public float topSpeed() {
		return diameter <= 5 ? 16.0F : diameter <= 9 ? 12.0F : 8.0F;
	}

	public float topCapacity() {
		return diameter <= 5 ? 256.0F : diameter <= 9 ? 1024.0F : 2048.0F;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face == state.get(Properties.HORIZONTAL_FACING).getOpposite();
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return state.get(Properties.HORIZONTAL_FACING).getAxis();
	}

	@Override
	public int sourceInterval() {
		return 40;
	}

	/** ¿Barren las aspas sin tocar nada macizo? */
	private boolean free(ServerWorld world, BlockPos pos, BlockState state) {
		Direction f = state.get(Properties.HORIZONTAL_FACING);
		Direction side = f.rotateYClockwise();
		int r = diameter / 2;
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				if ((a == 0 && b == 0) || a * a + b * b > (r + 0.5) * (r + 0.5)) {
					continue;
				}
				BlockPos p = pos.offset(Direction.UP, a).offset(side, b);
				if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	/** La altura (de 0,4 junto al mar a 1,3 a unos 110) y el tiempo (lluvia ×1,25, tormenta ×1,5). */
	public static float windFactor(ServerWorld world, BlockPos pos) {
		float h = MathHelper.clamp(0.4F + (pos.getY() - 62) / 50.0F, 0.4F, 1.3F);
		return h * (world.isThundering() ? 1.5F : world.isRaining() ? 1.25F : 1.0F);
	}

	@Override
	public float generate(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		if (!free(world, pos, state)) {
			be.problem = 1;
			return 0.0F;
		}
		be.problem = 0;
		return topSpeed() * state.get(Properties.HORIZONTAL_FACING).getDirection().offset();
	}

	@Override
	public float capacity(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		return topCapacity() * windFactor(world, pos);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return Block.createCuboidShape(3, 3, 3, 13, 13, 13);
	}
}
