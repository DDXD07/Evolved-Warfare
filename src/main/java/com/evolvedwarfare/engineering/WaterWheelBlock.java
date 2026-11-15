package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * La rueda hidráulica (pequeña, mediana o grande): el bloque es el cubo; la rueda se dibuja alrededor. Gira con el agua
 * que corre o cae por ella (hacia donde la empuja), más deprisa cuanta más agua la mueve; algo macizo dentro la para.
 */
public class WaterWheelBlock extends KineticBlock {
	public static final MapCodec<WaterWheelBlock> CODEC = createCodec(s -> new WaterWheelBlock(s, 3));
	private final int diameter;

	public WaterWheelBlock(Settings settings, int diameter) {
		super(settings);
		this.diameter = diameter;
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X));
	}

	public int diameter() {
		return diameter;
	}

	/** rpm y fuerza a pleno rendimiento: pequeña 16 y 256, mediana 12 y 768, grande 8 y 1536. */
	public float topSpeed() {
		return diameter <= 3 ? 16.0F : diameter <= 5 ? 12.0F : 8.0F;
	}

	public float topCapacity() {
		return diameter <= 3 ? 256.0F : diameter <= 5 ? 768.0F : 1536.0F;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_AXIS);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction.Axis a = ctx.getSide().getAxis().isHorizontal() ? ctx.getSide().getAxis() : ctx.getHorizontalPlayerFacing().rotateYClockwise().getAxis();
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

	@Override
	public int sourceInterval() {
		return 20;
	}

	/** La parte que mueve el agua (0..1) y el sentido; lo que estorba pone problem. */
	private float[] water(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		Direction.Axis axis = state.get(Properties.HORIZONTAL_AXIS);
		Direction u = Direction.UP;
		Direction v = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		Vec3d ax = axis == Direction.Axis.X ? new Vec3d(1, 0, 0) : new Vec3d(0, 0, 1);
		int r = diameter / 2;
		double torque = 0;
		int moving = 0;
		be.problem = 0;
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				if ((a == 0 && b == 0) || a * a + b * b > (r + 0.5) * (r + 0.5)) {
					continue;
				}
				BlockPos p = pos.offset(u, a).offset(v, b);
				BlockState s = world.getBlockState(p);
				FluidState f = s.getFluidState();
				if (!s.getCollisionShape(world, p).isEmpty() && f.isEmpty()) {
					be.problem = 1;
					return new float[] {0, 0};
				}
				if (f.isIn(FluidTags.WATER)) {
					Vec3d flow = f.getVelocity(world, p);
					if (flow.lengthSquared() > 1e-4) {
						Vec3d rel = new Vec3d(p.getX() - pos.getX(), p.getY() - pos.getY(), p.getZ() - pos.getZ());
						torque += rel.crossProduct(flow).dotProduct(ax);
						moving++;
					}
				}
			}
		}
		if (moving == 0 || Math.abs(torque) < 1e-3) {
			be.problem = 2;
			return new float[] {0, 0};
		}
		return new float[] {Math.min(1.0F, moving / (float) diameter), (float) Math.signum(torque)};
	}

	@Override
	public float generate(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		float[] w = water(world, pos, state, be);
		return topSpeed() * Math.max(0.5F, w[0]) * w[1];
	}

	@Override
	public float capacity(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		float[] w = water(world, pos, state, be);
		return topCapacity() * w[0];
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(Properties.HORIZONTAL_AXIS) == Direction.Axis.X ? Block.createCuboidShape(0, 4, 4, 16, 12, 12)
				: Block.createCuboidShape(4, 4, 0, 12, 12, 16);
	}
}
