package com.evolvedwarfare.engineering;

import com.evolvedwarfare.fluid.FiniteFluids;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * El tornillo de Arquímedes: se apilan en columna y suben, del pie a lo alto, el agua (de debajo o de al lado del de abajo:
 * un cubo cada medio segundo a 16 rpm), los objetos sueltos que haya al pie y lo que haya en un cofre o tolva debajo. Lo
 * echan encima del de arriba (a un cofre si lo hay). Gasta 1 por rpm cada uno.
 */
public class ArchimedesScrewBlock extends MachineBlock {
	public static final MapCodec<ArchimedesScrewBlock> CODEC = createCodec(ArchimedesScrewBlock::new);
	public static final float IMPACT = 1.0F;
	/** El trabajo (rpm·tic) por cada vez que sube algo: a 16 rpm, cada 10 tics. */
	public static final float CYCLE = 160.0F;
	public static final int MAX_HEIGHT = 64;
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

	public ArchimedesScrewBlock(Settings settings) {
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

	@Override
	public boolean hasAxle(BlockState state, Direction face) {
		return face.getAxis() == Direction.Axis.Y;
	}

	@Override
	public Direction.Axis axis(BlockState state) {
		return Direction.Axis.Y;
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		if (rpm < 0.01F || world.getBlockState(pos.down()).isOf(this)) {
			return;
		}
		be.progress += rpm;
		if (be.progress < CYCLE) {
			return;
		}
		be.progress -= CYCLE;
		BlockPos top = pos;
		for (int n = 0; n < MAX_HEIGHT && world.getBlockState(top.up()).isOf(this); n++) {
			top = top.up();
		}
		BlockPos out = top.up();
		boolean moved = pumpWater(world, pos, out);
		moved |= liftItems(world, pos, out);
		if (moved && world.random.nextInt(3) == 0) {
			world.playSound(null, out, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 0.2F, 1.4F);
		}
	}

	/** Sube agua: de debajo del pie o de al lado. */
	static boolean pumpWater(ServerWorld world, BlockPos foot, BlockPos out) {
		List<BlockPos> sources = new ArrayList<>();
		sources.add(foot.down());
		for (Direction d : Direction.Type.HORIZONTAL) {
			sources.add(foot.offset(d));
		}
		if (!FiniteFluids.enabled()) {
			if (!world.getBlockState(out).isReplaceable() || !world.getFluidState(out).isEmpty()) {
				return false;
			}
			for (BlockPos s : sources) {
				if (world.getFluidState(s).isOf(Fluids.WATER)) {
					world.setBlockState(out, Blocks.WATER.getDefaultState());
					return true;
				}
			}
			return false;
		}
		int have = FiniteFluids.amountAt(world, out, Fluids.WATER);
		if (have < 0 || have >= FiniteFluids.FULL) {
			return false;
		}
		int units = Math.min(FiniteFluids.BUCKET, FiniteFluids.FULL - have);
		for (BlockPos s : sources) {
			if (world.getFluidState(s).isIn(net.minecraft.registry.tag.FluidTags.WATER) && FiniteFluids.amountAt(world, s, Fluids.WATER) > 0
					&& FiniteFluids.drain(world, s, Fluids.WATER, units)) {
				FiniteFluids.add(world, out, Fluids.WATER, units);
				return true;
			}
		}
		return false;
	}

	/** Sube los objetos sueltos del pie y una cosa del cofre de debajo. */
	static boolean liftItems(ServerWorld world, BlockPos foot, BlockPos out) {
		boolean moved = false;
		Box intake = new Box(foot).expand(1.0, 0.0, 1.0).stretch(0.0, -1.0, 0.0);
		for (ItemEntity e : world.getEntitiesByClass(ItemEntity.class, intake, ItemEntity::isAlive)) {
			ItemStack rest = deliver(world, out, e.getStack().copy());
			if (rest.getCount() != e.getStack().getCount()) {
				moved = true;
				if (rest.isEmpty()) {
					e.discard();
				} else {
					e.setStack(rest);
				}
			}
		}
		Inventory below = HopperBlockEntity.getInventoryAt(world, foot.down());
		if (below != null) {
			for (int i = 0; i < below.size(); i++) {
				ItemStack s = below.getStack(i);
				if (!s.isEmpty()) {
					ItemStack rest = deliver(world, out, s.copyWithCount(1));
					if (rest.isEmpty()) {
						below.removeStack(i, 1);
						below.markDirty();
						moved = true;
					}
					break;
				}
			}
		}
		return moved;
	}

	/** Echa algo arriba: a un cofre, o encima si hay sitio. Devuelve lo que no cabe. */
	static ItemStack deliver(ServerWorld world, BlockPos out, ItemStack stack) {
		Inventory inv = HopperBlockEntity.getInventoryAt(world, out);
		if (inv != null) {
			return HopperBlockEntity.transfer(null, inv, stack, Direction.DOWN);
		}
		if (!world.getBlockState(out).getCollisionShape(world, out).isEmpty()) {
			return stack;
		}
		ItemEntity e = new ItemEntity(world, out.getX() + 0.5, out.getY() + 0.1, out.getZ() + 0.5, stack, 0.0, 0.1, 0.0);
		e.setToDefaultPickupDelay();
		world.spawnEntity(e);
		return ItemStack.EMPTY;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
