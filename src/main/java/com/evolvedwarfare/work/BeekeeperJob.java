package com.evolvedwarfare.work;

import java.util.function.Predicate;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Apicultor (zona 17x17): cuando una colmena o nido está lleno de miel lo vacía, con un frasco de los suministros
 * (frasco de miel) o, si no hay frascos, con sus tijeras (3 panales). Las abejas no se enfadan. Si le das flores,
 * planta hasta {@value #MAX_FLOWERS} en la hierba de la zona.
 */
public class BeekeeperJob implements Job {
	private static final int MAX_FLOWERS = 12;
	private static final Predicate<ItemStack> BOTTLE = s -> s.isOf(Items.GLASS_BOTTLE);
	private static final Predicate<ItemStack> FLOWER = s -> s.isIn(ItemTags.SMALL_FLOWERS) && !s.isOf(Items.WITHER_ROSE)
			&& s.getItem() instanceof BlockItem;

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ServerWorld world = ctx.world;
		BlockBox area = ctx.area();
		boolean canHarvest = ctx.site.count(BOTTLE, true) > 0 || ctx.hasTool();
		boolean hasFlowers = ctx.site.count(FLOWER, true) > 0;
		Nearest full = new Nearest();
		Nearest spot = new Nearest();
		boolean hives = false;
		boolean fullHive = false;
		int flowers = 0;
		for (BlockPos pos : BlockPos.iterate(area.getMinX(), area.getMinY(), area.getMinZ(), area.getMaxX(), area.getMaxY(), area.getMaxZ())) {
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof BeehiveBlock) {
				hives = true;
				if (state.get(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.FULL_HONEY_LEVEL && !ctx.ignored(pos)) {
					fullHive = true;
					full.offer(ctx, pos);
				}
			} else if (state.isIn(BlockTags.SMALL_FLOWERS)) {
				flowers++;
			} else if (hasFlowers && state.isOf(Blocks.GRASS_BLOCK) && Math.floorMod(pos.getX() * 7 + pos.getZ() * 13, 5) == 0) {
				BlockPos above = pos.up();
				if (world.getBlockState(above).isAir() && !ctx.ignored(above)) {
					spot.offer(ctx, above);
				}
			}
		}
		if (full.found() && canHarvest) {
			BlockPos target = full.pos;
			return WorkAction.atBlock(target, ctx.ticks(25), c -> harvest(c, target));
		}
		if (hasFlowers && flowers < MAX_FLOWERS && spot.found()) {
			BlockPos target = spot.pos;
			return WorkAction.atBlock(target, ctx.ticks(15), c -> plantFlower(c, target));
		}
		if (!hives) {
			return ctx.idle(WorkStatus.NOTHING_HERE);
		}
		return ctx.idle(fullHive ? WorkStatus.NO_SUPPLIES : WorkStatus.WAITING);
	}

	private static boolean harvest(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof BeehiveBlock) || state.get(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.FULL_HONEY_LEVEL) {
			return false;
		}
		if (ctx.site.consume(BOTTLE, 1, true)) {
			ctx.store(new ItemStack(Items.HONEY_BOTTLE));
			ctx.sound(pos, SoundEvents.ITEM_BOTTLE_FILL, 1.0F, 1.0F);
		} else if (ctx.hasTool()) {
			ctx.store(new ItemStack(Items.HONEYCOMB, 3));
			ctx.sound(pos, SoundEvents.BLOCK_BEEHIVE_SHEAR, 1.0F, 1.0F);
			ctx.damageTool(1);
		} else {
			return false;
		}
		world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, 0), Block.NOTIFY_ALL);
		return true;
	}

	private static boolean plantFlower(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		if (!world.getBlockState(pos).isAir()) {
			return false;
		}
		for (int i = 0; i < ctx.site.size(); i++) {
			ItemStack stack = ctx.site.getStack(i);
			if (!FLOWER.test(stack)) {
				continue;
			}
			BlockState flower = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
			if (!flower.canPlaceAt(world, pos)) {
				continue;
			}
			world.setBlockState(pos, flower);
			stack.decrement(1);
			ctx.site.markDirty();
			ctx.sound(pos, SoundEvents.BLOCK_GRASS_PLACE, 1.0F, 1.0F);
			return true;
		}
		return false;
	}
}
