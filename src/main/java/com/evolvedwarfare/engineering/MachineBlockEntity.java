package com.evolvedwarfare.engineering;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Las máquinas (fase 73): además de girar, guardan lo que trabajan (la muela y el aserradero: una entrada y cuatro
 * huecos de salida; las tolvas meten por arriba y los lados y sacan por abajo) y llevan la cuenta del trabajo.
 */
public class MachineBlockEntity extends KineticBlockEntity implements SidedInventory {
	public static final int INPUT = 0, SIZE = 5;
	private static final int[] IN_SLOTS = {0}, OUT_SLOTS = {1, 2, 3, 4}, NONE = {};
	final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
	/** El trabajo hecho en lo que hay a la entrada. */
	float progress;
	/** El martinete: el giro (en grados) para saber cuándo cae el mazo; lo que golpea y cuántas veces. */
	float phase;
	int target = -1, hits;
	/** El fuelle: los tics de más que le debe a lo que sopla. */
	float boost;

	public MachineBlockEntity(BlockPos pos, BlockState state) {
		super(Engineering.MACHINE_ENTITY, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, MachineBlockEntity be) {
		KineticBlockEntity.tick(world, pos, state, be);
		if (world instanceof ServerWorld sw && state.getBlock() instanceof MachineBlock m) {
			m.work(sw, pos, state, be, Math.abs(be.speed()));
		}
	}

	private MachineBlock machine() {
		return getCachedState().getBlock() instanceof MachineBlock m ? m : null;
	}

	@Nullable
	private MachineRecipes.Recipe recipe(ItemStack stack) {
		MachineBlock m = machine();
		return m == null || stack.isEmpty() ? null : m.recipe(stack);
	}

	public float progress() {
		return progress;
	}

	public float phase() {
		return phase;
	}

	public int hits() {
		return hits;
	}

	/** Lo que se ve a la entrada (para dibujarlo encima). */
	public ItemStack input() {
		return items.get(INPUT);
	}

	/** Avanza lo de la entrada; devuelve true si ha terminado una pieza. */
	boolean process(float rpm, net.minecraft.util.math.random.Random random) {
		ItemStack in = items.get(INPUT);
		MachineRecipes.Recipe r = recipe(in);
		if (r == null) {
			progress = 0;
			return false;
		}
		if (rpm < 0.01F) {
			return false;
		}
		progress = Math.min(progress + rpm, r.work());
		if (progress < r.work()) {
			return false;
		}
		List<ItemStack> out = r.output().apply(random);
		if (!fits(out)) {
			return false;
		}
		progress = 0;
		in.decrement(1);
		for (ItemStack o : out) {
			addOutput(o.copy());
		}
		changed();
		return true;
	}

	private boolean fits(List<ItemStack> out) {
		DefaultedList<ItemStack> copy = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
		for (int i = 0; i < SIZE; i++) {
			copy.set(i, items.get(i).copy());
		}
		for (ItemStack o : out) {
			if (!addTo(copy, o.copy()).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void addOutput(ItemStack stack) {
		addTo(items, stack);
	}

	private static ItemStack addTo(DefaultedList<ItemStack> list, ItemStack stack) {
		for (int i = 1; i < SIZE && !stack.isEmpty(); i++) {
			ItemStack have = list.get(i);
			if (!have.isEmpty() && ItemStack.areItemsAndComponentsEqual(have, stack)) {
				int n = Math.min(stack.getCount(), have.getMaxCount() - have.getCount());
				have.increment(n);
				stack.decrement(n);
			}
		}
		for (int i = 1; i < SIZE && !stack.isEmpty(); i++) {
			if (list.get(i).isEmpty()) {
				list.set(i, stack.copy());
				stack.setCount(0);
			}
		}
		return stack;
	}

	/** Mete lo que se pueda de esa pila a la entrada; devuelve cuántos ha cogido. */
	public int insert(ItemStack stack) {
		if (recipe(stack) == null) {
			return 0;
		}
		ItemStack in = items.get(INPUT);
		int n;
		if (in.isEmpty()) {
			n = stack.getCount();
			items.set(INPUT, stack.copyWithCount(n));
		} else if (ItemStack.areItemsAndComponentsEqual(in, stack)) {
			n = Math.min(stack.getCount(), in.getMaxCount() - in.getCount());
			in.increment(n);
		} else {
			return 0;
		}
		stack.decrement(n);
		if (n > 0) {
			changed();
		}
		return n;
	}

	/** Coge lo que caiga encima (en esa caja). */
	void suck(ServerWorld world, Box box) {
		for (ItemEntity e : world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive)) {
			ItemStack s = e.getStack();
			insert(s);
			if (s.isEmpty()) {
				e.discard();
			} else {
				e.setStack(s);
			}
		}
	}

	/** Saca las salidas (para el jugador y para soltarlas). */
	public ItemStack takeOutput() {
		for (int i = 1; i < SIZE; i++) {
			if (!items.get(i).isEmpty()) {
				ItemStack s = items.get(i);
				items.set(i, ItemStack.EMPTY);
				changed();
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	void changed() {
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	// ------------------------------------------------------------------ el inventario (para las tolvas)

	private boolean hasInventory() {
		MachineBlock m = machine();
		return m != null && m.hasInventory();
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return !hasInventory() ? NONE : side == Direction.DOWN ? OUT_SLOTS : IN_SLOTS;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		if (slot != INPUT || !hasInventory() || recipe(stack) == null) {
			return false;
		}
		ItemStack in = items.get(INPUT);
		return in.isEmpty() || ItemStack.areItemsAndComponentsEqual(in, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot != INPUT;
	}

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack s = Inventories.splitStack(items, slot, amount);
		if (!s.isEmpty()) {
			changed();
		}
		return s;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack s = Inventories.removeStack(items, slot);
		changed();
		return s;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		changed();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return true;
	}

	@Override
	public void clear() {
		items.clear();
		changed();
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		progress = nbt.getFloat("Progress");
		phase = nbt.getFloat("Phase");
		hits = nbt.getInt("Hits");
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, true, registries);
		nbt.putFloat("Progress", progress);
		nbt.putFloat("Phase", phase);
		nbt.putInt("Hits", hits);
	}
}
