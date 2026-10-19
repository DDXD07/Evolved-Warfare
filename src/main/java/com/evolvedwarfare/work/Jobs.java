package com.evolvedwarfare.work;

import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

/** Utilidades compartidas por los oficios. */
final class Jobs {
	static final Predicate<ItemStack> BONE_MEAL = s -> s.isOf(Items.BONE_MEAL);

	private Jobs() {
	}

	/** Aire o una planta que se puede pisar/quitar (hierba, helecho, nieve fina...), pero no agua ni lava. */
	static boolean isClear(BlockState state) {
		return state.isAir() || (state.isReplaceable() && state.getFluidState().isEmpty());
	}

	static boolean isClear(World world, BlockPos pos) {
		return isClear(world.getBlockState(pos));
	}

	/** El objeto del suelo más cercano que le interesa a este oficio. */
	@Nullable
	static ItemEntity nearestItem(WorkContext ctx, Box box, Predicate<ItemStack> filter) {
		ItemEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ItemEntity item : ctx.world.getEntitiesByClass(ItemEntity.class, box,
				e -> e.isAlive() && !e.getStack().isEmpty() && filter.test(e.getStack()))) {
			if (ctx.ignored(item)) {
				continue;
			}
			double d = ctx.worker.squaredDistanceTo(item);
			if (d < bestDistance) {
				bestDistance = d;
				best = item;
			}
		}
		return best;
	}

	/** Echa harina de hueso (de los suministros) a una planta. */
	static boolean fertilize(WorkContext ctx, BlockPos pos) {
		ItemStack meal = ctx.site.find(BONE_MEAL, false);
		if (meal == null) {
			return false;
		}
		if (BoneMealItem.useOnFertilizable(meal, ctx.world, pos)) {
			ctx.site.markDirty();
			ctx.world.syncWorldEvent(WorldEvents.BONE_MEAL_USED, pos, 15);
			return true;
		}
		return false;
	}

	/** Ir a por un objeto del suelo y guardarlo en el puesto. */
	static WorkAction pickup(ItemEntity item) {
		return WorkAction.atEntity(item, 2, ctx -> {
			if (!item.isAlive()) {
				return false;
			}
			ItemStack stack = item.getStack().copy();
			ctx.worker.sendPickup(item, stack.getCount());
			item.discard();
			ctx.store(stack);
			return false;
		}).reach(1.6);
	}
}
