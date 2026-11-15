package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La grúa: un mástil con una pluma de 3 bloques y un gancho que sube o baja según gira el eje de abajo (clic derecho
 * cambia). Agachado + clic derecho gira la pluma un cuarto de vuelta (con lo que lleve colgado). Clic derecho al gancho
 * cuando se apoya en un bloque: lo coge; con un bloque colgado: lo suelta debajo. Gasta 3 por rpm.
 */
public class CraneBlock extends MachineBlock {
	public static final MapCodec<CraneBlock> CODEC = createCodec(CraneBlock::new);
	public static final float IMPACT = 3.0F;
	public static final int REACH = 3;
	private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

	public CraneBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
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
		return getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face == Direction.DOWN;
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return Direction.Axis.Y;
	}

	/** Donde cuelga el gancho: la punta de la pluma (la x y la z) y la altura de la cuerda. */
	public static Vec3d tip(BlockPos pos, Direction facing) {
		return new Vec3d(pos.getX() + 0.5 + facing.getOffsetX() * REACH, pos.getY() + 2.1, pos.getZ() + 0.5 + facing.getOffsetZ() * REACH);
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		Vec3d tip = tip(pos, state.get(Properties.HORIZONTAL_FACING));
		if (be.hoist == null && world.getTime() % 20 == 0) {
			HoistEntity h = new HoistEntity(Engineering.CRANE_HOOK, world);
			h.link(pos, (float) tip.y);
			for (int d = 1; d <= 8; d++) {
				double y = tip.y - 0.5 - (d - 1);
				if (h.fitsAt(tip.x, y, tip.z)) {
					h.setPosition(tip.x, y, tip.z);
					world.spawnEntity(h);
					be.hoist = h.getUuid();
					be.markDirty();
					break;
				}
			}
		}
		HoistControl.drive(world, pos, be, rpm, tip);
	}

	/** Gira la pluma un cuarto de vuelta (si el gancho cabe en el nuevo sitio). */
	public static boolean turnJib(ServerWorld world, BlockPos pos, @Nullable PlayerEntity player) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof CraneBlock) || !(world.getBlockEntity(pos) instanceof MachineBlockEntity be)) {
			return false;
		}
		Direction next = state.get(Properties.HORIZONTAL_FACING).rotateYClockwise();
		Vec3d tip = tip(pos, next);
		HoistEntity h = HoistControl.hoist(world, be);
		if (h != null && !h.fitsAt(tip.x, h.getY(), tip.z)) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.hoist.jib_blocked").formatted(Formatting.YELLOW), true);
			}
			return false;
		}
		world.setBlockState(pos, state.with(Properties.HORIZONTAL_FACING, next));
		if (h != null) {
			h.setPosition(tip.x, h.getY(), tip.z);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.5F, 0.6F);
		return true;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			if (player.isSneaking()) {
				turnJib((ServerWorld) world, pos, player);
			} else {
				HoistControl.toggle(be, player);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			HoistControl.release(sw, state, be);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
