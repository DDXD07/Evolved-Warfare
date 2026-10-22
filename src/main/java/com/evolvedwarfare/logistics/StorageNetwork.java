package com.evolvedwarfare.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * La red de un almacén: todos los cofres (también los de trampa) y barriles que hay a su alrededor (hasta
 * {@value #RANGE} bloques en horizontal y {@value #HEIGHT} en vertical), vistos como un solo inventario.
 *
 * <p>Se calcula cada vez que se usa (es barato: solo mira los bloques con contenido de los chunks cercanos), así que
 * poner o quitar un cofre se nota enseguida.</p>
 */
public final class StorageNetwork {
	public static final int RANGE = 16;
	public static final int HEIGHT = 8;

	/** Un tipo de objeto de la red y cuántos hay en total. */
	public record Entry(ItemStack prototype, int count) {
	}

	private final List<BlockPos> positions;
	private final List<Inventory> containers;

	private StorageNetwork(List<BlockPos> positions, List<Inventory> containers) {
		this.positions = positions;
		this.containers = containers;
	}

	public static StorageNetwork around(World world, BlockPos center) {
		List<BlockPos> positions = new ArrayList<>();
		List<BlockEntity> found = new ArrayList<>();
		int cx0 = (center.getX() - RANGE) >> 4;
		int cx1 = (center.getX() + RANGE) >> 4;
		int cz0 = (center.getZ() - RANGE) >> 4;
		int cz1 = (center.getZ() + RANGE) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				WorldChunk chunk = world.getChunk(cx, cz);
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					BlockPos p = e.getKey();
					BlockEntity be = e.getValue();
					if (be.isRemoved() || Math.abs(p.getX() - center.getX()) > RANGE || Math.abs(p.getZ() - center.getZ()) > RANGE
							|| Math.abs(p.getY() - center.getY()) > HEIGHT) {
						continue;
					}
					if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
						found.add(be);
					}
				}
			}
		}
		// los más cercanos primero: se llenan antes
		found.sort(Comparator.comparingDouble(be -> be.getPos().getSquaredDistance(center)));
		List<Inventory> containers = new ArrayList<>();
		for (BlockEntity be : found) {
			positions.add(be.getPos());
			containers.add((Inventory) be);
		}
		return new StorageNetwork(positions, containers);
	}

	public int containerCount() {
		return containers.size();
	}

	public List<BlockPos> positions() {
		return positions;
	}

	public boolean isEmpty() {
		return containers.isEmpty();
	}

	/** Cuántos hay que cumplan el filtro. */
	public int count(Predicate<ItemStack> filter) {
		long total = 0;
		for (Inventory inventory : containers) {
			for (int i = 0; i < inventory.size(); i++) {
				ItemStack stack = inventory.getStack(i);
				if (!stack.isEmpty() && filter.test(stack)) {
					total += stack.getCount();
				}
			}
		}
		return (int) Math.min(total, Integer.MAX_VALUE);
	}

	/**
	 * Saca hasta {@code max} objetos iguales entre sí (del primer tipo que cumpla el filtro), como mucho un montón.
	 * Devuelve lo sacado (vacío si no hay).
	 */
	public ItemStack extract(Predicate<ItemStack> filter, int max) {
		ItemStack result = ItemStack.EMPTY;
		for (Inventory inventory : containers) {
			boolean changed = false;
			for (int i = 0; i < inventory.size(); i++) {
				ItemStack stack = inventory.getStack(i);
				if (stack.isEmpty() || !filter.test(stack)) {
					continue;
				}
				if (!result.isEmpty() && !ItemStack.areItemsAndComponentsEqual(result, stack)) {
					continue;
				}
				int limit = Math.min(max, stack.getMaxCount());
				int want = limit - result.getCount();
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
				changed = true;
			}
			if (changed) {
				inventory.markDirty();
			}
			if (!result.isEmpty() && result.getCount() >= Math.min(max, result.getMaxCount())) {
				break;
			}
		}
		return result;
	}

	/** Saca exactamente ese tipo de objeto (mismo objeto y mismos datos). */
	public ItemStack extractLike(ItemStack prototype, int max) {
		return extract(stack -> ItemStack.areItemsAndComponentsEqual(stack, prototype), max);
	}

	/** Mete en la red (primero junto a los del mismo tipo, luego en huecos libres). Devuelve lo que no cabe. */
	public ItemStack insert(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
			for (Inventory inventory : containers) {
				boolean changed = false;
				for (int i = 0; i < inventory.size() && !rest.isEmpty(); i++) {
					ItemStack slot = inventory.getStack(i);
					if (pass == 0) {
						if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, rest)) {
							int move = Math.min(rest.getCount(), Math.min(slot.getMaxCount(), inventory.getMaxCountPerStack()) - slot.getCount());
							if (move > 0) {
								slot.increment(move);
								rest.decrement(move);
								changed = true;
							}
						}
					} else if (slot.isEmpty() && inventory.isValid(i, rest)) {
						int move = Math.min(rest.getCount(), Math.min(rest.getMaxCount(), inventory.getMaxCountPerStack()));
						inventory.setStack(i, rest.copyWithCount(move));
						rest.decrement(move);
						changed = true;
					}
				}
				if (changed) {
					inventory.markDirty();
				}
				if (rest.isEmpty()) {
					break;
				}
			}
		}
		return rest;
	}

	/** Todo lo que hay, agrupado por tipo. */
	public List<Entry> contents() {
		List<ItemStack> prototypes = new ArrayList<>();
		List<long[]> totals = new ArrayList<>();
		for (Inventory inventory : containers) {
			for (int i = 0; i < inventory.size(); i++) {
				ItemStack stack = inventory.getStack(i);
				if (stack.isEmpty()) {
					continue;
				}
				int index = -1;
				for (int k = 0; k < prototypes.size(); k++) {
					if (ItemStack.areItemsAndComponentsEqual(prototypes.get(k), stack)) {
						index = k;
						break;
					}
				}
				if (index < 0) {
					prototypes.add(stack.copyWithCount(1));
					totals.add(new long[] {stack.getCount()});
				} else {
					totals.get(index)[0] += stack.getCount();
				}
			}
		}
		List<Entry> entries = new ArrayList<>();
		for (int k = 0; k < prototypes.size(); k++) {
			entries.add(new Entry(prototypes.get(k), (int) Math.min(totals.get(k)[0], Integer.MAX_VALUE)));
		}
		return entries;
	}
}
