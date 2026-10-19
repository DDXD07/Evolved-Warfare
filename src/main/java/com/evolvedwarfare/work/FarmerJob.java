package com.evolvedwarfare.work;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Granjero (zona 11x11): cosecha lo maduro y lo vuelve a sembrar, siembra la tierra arada vacía y ara la tierra
 * (hierba o tierra) que tiene agua a 4 bloques o menos, como necesita el cultivo en vanilla. Con harina de hueso en
 * los suministros abona lo que está creciendo.
 */
public class FarmerJob implements Job {
	/** Todo lo que se planta en tierra arada y crece por edades: semillas de trigo y remolacha, zanahorias, patatas... */
	static final Predicate<ItemStack> SEEDS = s -> s.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ServerWorld world = ctx.world;
		BlockBox area = ctx.area();
		boolean seeds = ctx.site.count(SEEDS, true) > 0;
		boolean boneMeal = ctx.site.count(Jobs.BONE_MEAL, false) > 0;
		List<BlockPos> water = findWater(world, area);
		Nearest harvest = new Nearest();
		Nearest plant = new Nearest();
		Nearest till = new Nearest();
		Nearest grow = new Nearest();
		boolean growing = false;
		boolean soil = false;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = area.getMinX(); x <= area.getMaxX(); x++) {
			for (int z = area.getMinZ(); z <= area.getMaxZ(); z++) {
				if (x == ctx.sitePos.getX() && z == ctx.sitePos.getZ()) {
					continue;
				}
				for (int y = area.getMaxY(); y >= area.getMinY(); y--) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (ctx.ignored(pos)) {
						break;
					}
					if (state.getBlock() instanceof CropBlock crop) {
						soil = true;
						if (crop.isMature(state)) {
							harvest.offer(ctx, pos);
						} else {
							growing = true;
							if (boneMeal) {
								grow.offer(ctx, pos);
							}
						}
						break;
					}
					if (state.isOf(Blocks.FARMLAND)) {
						soil = true;
						BlockPos above = pos.up();
						if (world.getBlockState(above).isAir() && !ctx.ignored(above)) {
							plant.offer(ctx, above);
						}
						break;
					}
					if (isTillable(state)) {
						if (Jobs.isClear(world, pos.up()) && nearWater(water, pos)) {
							soil = true;
							till.offer(ctx, pos);
						}
						break;
					}
					if (!Jobs.isClear(state)) {
						break;
					}
				}
			}
		}
		if (harvest.found()) {
			BlockPos target = harvest.pos;
			return WorkAction.atBlock(target, ctx.ticks(20), c -> harvest(c, target));
		}
		if (seeds && plant.found()) {
			BlockPos target = plant.pos;
			return WorkAction.atBlock(target, ctx.ticks(15), c -> plant(c, target));
		}
		if (seeds && till.found()) {
			BlockPos target = till.pos;
			return WorkAction.atBlock(target, ctx.ticks(25), c -> till(c, target)).breaking(target);
		}
		if (grow.found()) {
			BlockPos target = grow.pos;
			return WorkAction.atBlock(target, ctx.ticks(10), c -> Jobs.fertilize(c, target));
		}
		if (growing) {
			return ctx.idle(WorkStatus.WAITING);
		}
		if (!soil) {
			return ctx.idle(WorkStatus.NO_WATER);
		}
		return ctx.idle(seeds ? WorkStatus.WAITING : WorkStatus.NO_SUPPLIES);
	}

	private static boolean isTillable(BlockState state) {
		return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.DIRT_PATH);
	}

	/** Agua de la zona (ampliada 4 bloques), para saber dónde vale la pena arar. */
	private static List<BlockPos> findWater(ServerWorld world, BlockBox area) {
		List<BlockPos> water = new ArrayList<>();
		for (BlockPos p : BlockPos.iterate(area.getMinX() - 4, area.getMinY(), area.getMinZ() - 4,
				area.getMaxX() + 4, area.getMaxY() + 1, area.getMaxZ() + 4)) {
			if (world.getFluidState(p).isIn(FluidTags.WATER)) {
				water.add(p.toImmutable());
			}
		}
		return water;
	}

	/** Igual que la tierra arada vanilla: agua a 4 bloques en horizontal, a su altura o uno más arriba. */
	private static boolean nearWater(List<BlockPos> water, BlockPos pos) {
		for (BlockPos w : water) {
			int dy = w.getY() - pos.getY();
			if (dy >= 0 && dy <= 1 && Math.abs(w.getX() - pos.getX()) <= 4 && Math.abs(w.getZ() - pos.getZ()) <= 4) {
				return true;
			}
		}
		return false;
	}

	private static boolean harvest(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) {
			return false;
		}
		List<ItemStack> drops = ctx.drops(pos, state);
		world.breakBlock(pos, false, ctx.worker);
		Item seed = crop.asItem();
		if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isOf(Blocks.FARMLAND)) {
			boolean replant = false;
			for (ItemStack drop : drops) {
				if (!drop.isEmpty() && drop.isOf(seed)) {
					drop.decrement(1);
					replant = true;
					break;
				}
			}
			if (!replant) {
				replant = ctx.site.consume(s -> s.isOf(seed), 1, true);
			}
			if (replant) {
				world.setBlockState(pos, crop.getDefaultState());
			}
		}
		ctx.storeAll(drops);
		return true;
	}

	private static boolean plant(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.down()).isOf(Blocks.FARMLAND)) {
			return false;
		}
		ItemStack seed = ctx.site.find(SEEDS, true);
		if (seed == null) {
			return false;
		}
		Block block = ((BlockItem) seed.getItem()).getBlock();
		BlockState crop = block.getDefaultState();
		if (!crop.canPlaceAt(world, pos)) {
			return false;
		}
		world.setBlockState(pos, crop);
		seed.decrement(1);
		ctx.site.markDirty();
		ctx.sound(pos, SoundEvents.ITEM_CROP_PLANT, 1.0F, 1.0F);
		return true;
	}

	private static boolean till(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		if (!isTillable(world.getBlockState(pos))) {
			return false;
		}
		BlockPos above = pos.up();
		if (!Jobs.isClear(world, above)) {
			return false;
		}
		if (!world.getBlockState(above).isAir()) {
			world.breakBlock(above, false, ctx.worker);
		}
		world.setBlockState(pos, Blocks.FARMLAND.getDefaultState());
		ctx.sound(pos, SoundEvents.ITEM_HOE_TILL, 1.0F, 1.0F);
		ctx.damageTool(1);
		return true;
	}
}
