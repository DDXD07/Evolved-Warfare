package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * El torno del rastrillo: va encima de la fila de arriba de un rastrillo (a lo largo de su eje). Mientras gira, con señal
 * de redstone recoge el rastrillo fila a fila (lo abre) y sin señal lo vuelve a bajar hasta el suelo (lo cierra),
 * aplastando a quien pille. La fila de arriba no se mueve. Solo baja las filas que ha recogido.
 */
public class PortcullisWinchBlock extends SideShaftMachineBlock {
	public static final MapCodec<PortcullisWinchBlock> CODEC = createCodec(PortcullisWinchBlock::new);
	public static final float IMPACT = 3.0F, CRUSH = 6.0F;
	/** El trabajo (rpm·tic) por fila: a 16 rpm, una cada 10 tics. */
	public static final float STEP = 160.0F;
	public static final int MAX_WIDTH = 16, MAX_DEPTH = 24;

	public PortcullisWinchBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	/** La fila de arriba del rastrillo: debajo del torno y a lo largo de su eje. */
	static List<BlockPos> topRow(World world, BlockPos pos, BlockState state) {
		List<BlockPos> row = new ArrayList<>();
		BlockPos start = pos.down();
		if (!world.getBlockState(start).isOf(Engineering.PORTCULLIS)) {
			return row;
		}
		row.add(start);
		Direction along = Direction.from(state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).rotateYClockwise().getAxis(),
				Direction.AxisDirection.POSITIVE);
		for (Direction d : new Direction[] {along, along.getOpposite()}) {
			BlockPos p = start.offset(d);
			for (int i = 0; i < MAX_WIDTH && world.getBlockState(p).isOf(Engineering.PORTCULLIS); i++) {
				row.add(p);
				p = p.offset(d);
			}
		}
		return row;
	}

	private static BlockPos bottom(World world, BlockPos top) {
		BlockPos b = top;
		for (int i = 0; i < MAX_DEPTH && world.getBlockState(b.down()).isOf(Engineering.PORTCULLIS); i++) {
			b = b.down();
		}
		return b;
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		if (rpm < 0.01F) {
			return;
		}
		be.progress += rpm;
		if (be.progress < STEP) {
			return;
		}
		be.progress -= STEP;
		boolean open = world.isReceivingRedstonePower(pos);
		List<BlockPos> row = topRow(world, pos, state);
		boolean moved = false;
		for (BlockPos top : row) {
			BlockPos b = bottom(world, top);
			if (open) {
				if (!b.equals(top)) {
					world.setBlockState(b, Blocks.AIR.getDefaultState());
					be.stored++;
					moved = true;
				}
			} else if (be.stored > 0 && top.getY() - b.getY() < MAX_DEPTH) {
				BlockPos next = b.down();
				if (!world.getBlockState(next).isReplaceable()) {
					continue;
				}
				List<LivingEntity> caught = world.getEntitiesByClass(LivingEntity.class, new Box(next), LivingEntity::isAlive);
				if (!caught.isEmpty()) {
					for (LivingEntity e : caught) {
						e.damage(world.getDamageSources().fallingAnvil(null), CRUSH);
					}
					continue;
				}
				world.setBlockState(next, Engineering.PORTCULLIS.getDefaultState());
				be.stored--;
				moved = true;
			}
		}
		if (moved) {
			be.markDirty();
			world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.8F, 0.7F);
		} else {
			be.progress = 0;
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MachineBlockEntity be && be.stored > 0) {
			// las filas recogidas salen del torno
			int n = be.stored;
			be.stored = 0;
			while (n > 0) {
				int c = Math.min(64, n);
				ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Engineering.PORTCULLIS, c));
				n -= c;
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
