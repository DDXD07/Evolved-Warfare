package com.evolvedwarfare.work;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CaveVines;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Hortelano (zona 13x13): recoge todo lo que vuelve a crecer solo.
 * <ul>
 *   <li>Melones y calabazas que han salido de un tallo (no toca las calabazas sueltas de decoración).</li>
 *   <li>Caña de azúcar, cactus y bambú: corta todo menos el primer bloque, para que vuelvan a crecer.</li>
 *   <li>Arbustos de bayas dulces, bayas luminosas, cacao y verrugas del Nether (los dos últimos los replanta).</li>
 * </ul>
 */
public class GardenerJob implements Job {
	private static final int FRUIT = 1;
	private static final int TALL = 2;
	private static final int BERRIES = 3;
	private static final int COCOA = 4;
	private static final int WART = 5;
	private static final int GLOW_BERRIES = 6;
	private static final Predicate<ItemStack> LOOT = s -> s.isOf(Items.SUGAR_CANE) || s.isOf(Items.CACTUS) || s.isOf(Items.BAMBOO)
			|| s.isOf(Items.MELON_SLICE) || s.isOf(Items.PUMPKIN) || s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.COCOA_BEANS)
			|| s.isOf(Items.GLOW_BERRIES) || s.isOf(Items.NETHER_WART);

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ItemEntity item = Jobs.nearestItem(ctx, ctx.areaBox(2.0), LOOT);
		if (item != null) {
			return Jobs.pickup(item);
		}
		ServerWorld world = ctx.world;
		BlockBox area = ctx.area();
		Nearest ready = new Nearest();
		boolean plants = false;
		for (BlockPos pos : BlockPos.iterate(area.getMinX(), area.getMinY(), area.getMinZ(), area.getMaxX(), area.getMaxY(), area.getMaxZ())) {
			BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			Block block = state.getBlock();
			int kind = 0;
			if (state.isOf(Blocks.MELON) || state.isOf(Blocks.PUMPKIN)) {
				if (attachedToStem(world, pos)) {
					kind = FRUIT;
				}
			} else if (isTall(state)) {
				plants = true;
				if (!world.getBlockState(pos.down()).isOf(block) && world.getBlockState(pos.up()).isOf(block)) {
					ready.offer(ctx, pos.up(), TALL);
				}
				continue;
			} else if (block instanceof SweetBerryBushBlock) {
				plants = true;
				if (state.get(SweetBerryBushBlock.AGE) >= 2) {
					kind = BERRIES;
				}
			} else if (block instanceof CocoaBlock) {
				plants = true;
				if (state.get(CocoaBlock.AGE) >= 2) {
					kind = COCOA;
				}
			} else if (block instanceof NetherWartBlock) {
				plants = true;
				if (state.get(NetherWartBlock.AGE) >= 3) {
					kind = WART;
				}
			} else if (state.contains(CaveVines.BERRIES)) {
				plants = true;
				if (state.get(CaveVines.BERRIES)) {
					kind = GLOW_BERRIES;
				}
			} else if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
				plants = true;
			}
			if (kind != 0 && !ctx.ignored(pos)) {
				ready.offer(ctx, pos, kind);
			}
		}
		if (!ready.found()) {
			return ctx.idle(plants ? WorkStatus.WAITING : WorkStatus.NOTHING_HERE);
		}
		BlockPos target = ready.pos;
		int kind = ready.kind;
		WorkAction action = WorkAction.atBlock(target, ctx.ticks(kind == FRUIT || kind == TALL ? 20 : 12), c -> harvest(c, target, kind));
		return kind == FRUIT ? action.breaking(target) : action;
	}

	private static boolean isTall(BlockState state) {
		return state.isOf(Blocks.SUGAR_CANE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.BAMBOO);
	}

	/** ¿Ha salido de un tallo? (algún tallo de al lado apunta hacia él) */
	private static boolean attachedToStem(ServerWorld world, BlockPos fruit) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockState state = world.getBlockState(fruit.offset(direction));
			if (state.getBlock() instanceof AttachedStemBlock && state.get(AttachedStemBlock.FACING) == direction.getOpposite()) {
				return true;
			}
		}
		return false;
	}

	private static boolean harvest(WorkContext ctx, BlockPos pos, int kind) {
		ServerWorld world = ctx.world;
		BlockState state = world.getBlockState(pos);
		switch (kind) {
			case FRUIT -> {
				if (!(state.isOf(Blocks.MELON) || state.isOf(Blocks.PUMPKIN))) {
					return false;
				}
				ctx.harvest(pos);
				ctx.damageTool(1);
				return true;
			}
			case TALL -> {
				if (!isTall(state)) {
					return false;
				}
				Block block = state.getBlock();
				BlockPos top = pos;
				while (world.getBlockState(top.up()).isOf(block) && top.getY() - pos.getY() < 24) {
					top = top.up();
				}
				// de arriba abajo, para que no se caigan las partes de encima
				for (BlockPos p = top; p.getY() >= pos.getY(); p = p.down()) {
					ctx.harvest(p);
				}
				return true;
			}
			case BERRIES -> {
				if (!(state.getBlock() instanceof SweetBerryBushBlock)) {
					return false;
				}
				int age = state.get(SweetBerryBushBlock.AGE);
				if (age < 2) {
					return false;
				}
				int count = 1 + ctx.random().nextInt(2) + (age == 3 ? 1 : 0);
				ctx.store(new ItemStack(Items.SWEET_BERRIES, count));
				world.setBlockState(pos, state.with(SweetBerryBushBlock.AGE, 1), Block.NOTIFY_LISTENERS);
				ctx.sound(pos, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
				return true;
			}
			case GLOW_BERRIES -> {
				if (!state.contains(CaveVines.BERRIES) || !state.get(CaveVines.BERRIES)) {
					return false;
				}
				ctx.store(new ItemStack(Items.GLOW_BERRIES));
				world.setBlockState(pos, state.with(CaveVines.BERRIES, false), Block.NOTIFY_LISTENERS);
				ctx.sound(pos, SoundEvents.BLOCK_CAVE_VINES_PICK_BERRIES, 1.0F, 1.0F);
				return true;
			}
			case COCOA -> {
				if (!(state.getBlock() instanceof CocoaBlock) || state.get(CocoaBlock.AGE) < 2) {
					return false;
				}
				BlockState replant = Blocks.COCOA.getDefaultState().with(CocoaBlock.FACING, state.get(CocoaBlock.FACING));
				return harvestAndReplant(ctx, pos, state, Items.COCOA_BEANS, replant);
			}
			case WART -> {
				if (!(state.getBlock() instanceof NetherWartBlock) || state.get(NetherWartBlock.AGE) < 3) {
					return false;
				}
				return harvestAndReplant(ctx, pos, state, Items.NETHER_WART, Blocks.NETHER_WART.getDefaultState());
			}
			default -> {
				return false;
			}
		}
	}

	/** Recoge y vuelve a plantar con una de las semillas que ha soltado. */
	private static boolean harvestAndReplant(WorkContext ctx, BlockPos pos, BlockState state, Item seed, BlockState replant) {
		ServerWorld world = ctx.world;
		List<ItemStack> drops = ctx.drops(pos, state);
		world.breakBlock(pos, false, ctx.worker);
		if (world.getBlockState(pos).isAir() && replant.canPlaceAt(world, pos)) {
			for (ItemStack drop : drops) {
				if (!drop.isEmpty() && drop.isOf(seed)) {
					drop.decrement(1);
					world.setBlockState(pos, replant);
					break;
				}
			}
		}
		ctx.storeAll(drops);
		return true;
	}
}
