package com.evolvedwarfare.logistics;

import com.evolvedwarfare.block.TownHallBlockEntity;
import com.evolvedwarfare.block.WarehouseBlockEntity;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

/** Búsquedas y movimientos de objetos que usan el mensajero y el comerciante. */
public final class Logistics {
	private Logistics() {
	}

	/** Bloques con contenido de cierto tipo a menos de {@code range} bloques (en horizontal), del más cercano al más lejano. */
	public static <T extends BlockEntity> List<T> nearby(World world, BlockPos center, int range, Class<T> type, Predicate<T> filter) {
		List<T> found = new ArrayList<>();
		int cx0 = (center.getX() - range) >> 4;
		int cx1 = (center.getX() + range) >> 4;
		int cz0 = (center.getZ() - range) >> 4;
		int cz1 = (center.getZ() + range) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				WorldChunk chunk = world.getChunk(cx, cz);
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					BlockPos p = e.getKey();
					if (Math.abs(p.getX() - center.getX()) > range || Math.abs(p.getZ() - center.getZ()) > range
							|| Math.abs(p.getY() - center.getY()) > 32) {
						continue;
					}
					BlockEntity be = e.getValue();
					if (!be.isRemoved() && type.isInstance(be)) {
						T t = type.cast(be);
						if (filter.test(t)) {
							found.add(t);
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(be -> be.getPos().getSquaredDistance(center)));
		return found;
	}

	/** ¿Es de ese dueño (o de nadie)? */
	public static boolean sameOwner(@Nullable UUID a, @Nullable UUID b) {
		return a == null || b == null || a.equals(b);
	}

	@Nullable
	public static WarehouseBlockEntity nearestWarehouse(World world, BlockPos center, int range, @Nullable UUID owner) {
		List<WarehouseBlockEntity> list = nearby(world, center, range, WarehouseBlockEntity.class, w -> sameOwner(w.getOwner(), owner));
		return list.isEmpty() ? null : list.get(0);
	}

	public static List<WorkSiteBlockEntity> sites(World world, BlockPos center, int range, @Nullable UUID owner) {
		return nearby(world, center, range, WorkSiteBlockEntity.class, s -> sameOwner(s.getOwner(), owner));
	}

	public static List<TownHallBlockEntity> townHalls(World world, BlockPos center, int range, @Nullable UUID owner) {
		return nearby(world, center, range, TownHallBlockEntity.class, h -> sameOwner(h.getOwner(), owner));
	}

	/** Mete en las ranuras [from, to) de un inventario. Devuelve lo que no cabe. */
	public static ItemStack insertInto(Inventory inventory, ItemStack stack, int from, int to) {
		ItemStack rest = stack.copy();
		boolean changed = false;
		for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
			for (int i = from; i < to && !rest.isEmpty(); i++) {
				ItemStack slot = inventory.getStack(i);
				if (pass == 0) {
					if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, rest)) {
						int move = Math.min(rest.getCount(), slot.getMaxCount() - slot.getCount());
						if (move > 0) {
							slot.increment(move);
							rest.decrement(move);
							changed = true;
						}
					}
				} else if (slot.isEmpty()) {
					int move = Math.min(rest.getCount(), rest.getMaxCount());
					inventory.setStack(i, rest.copyWithCount(move));
					rest.decrement(move);
					changed = true;
				}
			}
		}
		if (changed) {
			inventory.markDirty();
		}
		return rest;
	}

	/** Cuántos de ese objeto caben en las ranuras [from, to). */
	public static int roomFor(Inventory inventory, ItemStack stack, int from, int to) {
		int room = 0;
		int max = Math.min(stack.getMaxCount(), inventory.getMaxCountPerStack());
		for (int i = from; i < to; i++) {
			ItemStack slot = inventory.getStack(i);
			if (slot.isEmpty()) {
				room += max;
			} else if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
				room += Math.max(0, max - slot.getCount());
			}
		}
		return room;
	}

	/** Cuántos objetos hay en las ranuras [from, to). */
	public static int countItems(Inventory inventory, int from, int to, Predicate<ItemStack> filter) {
		int total = 0;
		for (int i = from; i < to; i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Saca de las ranuras [from, to) hasta {@code max} objetos del primer tipo que cumpla el filtro. */
	public static ItemStack extractFrom(Inventory inventory, int from, int to, Predicate<ItemStack> filter, int max) {
		ItemStack result = ItemStack.EMPTY;
		for (int i = from; i < to; i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			if (!result.isEmpty() && !ItemStack.areItemsAndComponentsEqual(result, stack)) {
				continue;
			}
			int want = Math.min(max, stack.getMaxCount()) - result.getCount();
			if (want <= 0) {
				break;
			}
			int take = Math.min(want, stack.getCount());
			if (result.isEmpty()) {
				result = stack.copyWithCount(take);
			} else {
				result.increment(take);
			}
			stack.decrement(take);
		}
		if (!result.isEmpty()) {
			inventory.markDirty();
		}
		return result;
	}
}
