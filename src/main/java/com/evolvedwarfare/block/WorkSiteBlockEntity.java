package com.evolvedwarfare.block;

import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.WorkerEntity;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.screen.WorkSiteScreenHandler;
import com.evolvedwarfare.work.CookingJob;
import com.evolvedwarfare.work.ExcavationJob;
import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkArea;
import com.evolvedwarfare.work.WorkStatus;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Puesto de trabajo: guarda el almacén del oficio y quién trabaja en él.
 *
 * <p>Almacén: ranuras 0-8 <b>suministros</b> (lo que el trabajador gasta: semillas, brotes, combustible, materiales,
 * herramientas de repuesto...) y 9-26 <b>producción</b> (lo que saca). Las tolvas meten en los suministros por arriba
 * y los lados, y sacan la producción por abajo.</p>
 */
public class WorkSiteBlockEntity extends BlockEntity implements SidedInventory, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SUPPLY_SLOTS = 9;
	public static final int SIZE = 27;
	public static final int UNSET = Integer.MIN_VALUE;
	/** Distancia a la que busca reclutas tuyos al pulsar "Asignar". */
	public static final int ASSIGN_RANGE = 24;
	private static final int[] SUPPLY = slots(0, SUPPLY_SLOTS);
	private static final int[] OUTPUT = slots(SUPPLY_SLOTS, SIZE);

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
	@Nullable
	private UUID owner;
	@Nullable
	private UUID workerUuid;
	private int mineY = UNSET;
	private int fuel;

	// solo en memoria: lo que cuenta el trabajador cada segundo
	private long lastHeartbeat = Long.MIN_VALUE / 2;
	private int workerEntityId = -1;
	private WorkStatus status = WorkStatus.WAITING;
	private int jobsDone;

	/** Datos que ve el menú (se mandan al cliente como números de 16 bits). */
	public final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case WorkSiteScreenHandler.P_STATUS -> displayStatus().ordinal();
				case WorkSiteScreenHandler.P_HAS_WORKER -> workerUuid != null ? 1 : 0;
				case WorkSiteScreenHandler.P_WORKER_LO -> workerEntityId & 0xFFFF;
				case WorkSiteScreenHandler.P_WORKER_HI -> (workerEntityId >>> 16) & 0xFFFF;
				case WorkSiteScreenHandler.P_FUEL -> Math.min(fuel / CookingJob.FUEL_PER_ITEM, 9999);
				case WorkSiteScreenHandler.P_DEPTH -> mineY == UNSET ? WorkSiteScreenHandler.NO_DEPTH : mineY;
				case WorkSiteScreenHandler.P_JOBS -> Math.min(jobsDone, 30000);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return WorkSiteScreenHandler.PROPERTY_COUNT;
		}
	};

	public WorkSiteBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.WORK_SITE_ENTITY, pos, state);
	}

	private static int[] slots(int from, int to) {
		int[] result = new int[to - from];
		for (int i = 0; i < result.length; i++) {
			result[i] = from + i;
		}
		return result;
	}

	// ------------------------------------------------------------------ oficio

	public Profession getProfession() {
		return getCachedState().getBlock() instanceof WorkSiteBlock block ? block.getProfession() : Profession.FARMER;
	}

	public Direction getFacing() {
		BlockState state = getCachedState();
		return state.contains(WorkSiteBlock.FACING) ? state.get(WorkSiteBlock.FACING) : Direction.NORTH;
	}

	public int getMineY() {
		return mineY;
	}

	public void setMineY(int y) {
		mineY = y;
		markDirty();
	}

	public int getFuel() {
		return fuel;
	}

	public void addFuel(int amount) {
		fuel = Math.max(0, fuel + amount);
		markDirty();
	}

	public int getSupplySlots() {
		return SUPPLY_SLOTS;
	}

	// ------------------------------------------------------------------ dueño y trabajador

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		markDirty();
	}

	/** Puesto sin dueño (puesto con comandos o de una estructura) o tuyo. */
	public boolean canAccess(PlayerEntity player) {
		return owner == null || owner.equals(player.getUuid()) || player.isCreative();
	}

	@Nullable
	public UUID getWorkerUuid() {
		return workerUuid;
	}

	/** El trabajador avisa cada segundo de lo que está haciendo. */
	public void heartbeat(long time, int entityId, WorkStatus status, int jobsDone) {
		this.lastHeartbeat = time;
		this.workerEntityId = entityId;
		this.status = status;
		this.jobsDone = jobsDone;
	}

	public WorkStatus displayStatus() {
		if (workerUuid == null) {
			return WorkStatus.NO_WORKER;
		}
		if (world == null || world.getTime() - lastHeartbeat > 100) {
			return WorkStatus.ABSENT;
		}
		return status;
	}

	/** Pone a trabajar aquí al recluta libre del jugador más cercano. */
	public void assign(ServerPlayerEntity player) {
		if (!(world instanceof ServerWorld serverWorld) || workerUuid != null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(pos);
		List<RecruitEntity> recruits = serverWorld.getEntitiesByClass(RecruitEntity.class, new Box(pos).expand(ASSIGN_RANGE),
				recruit -> recruit.isAlive() && recruit.isOwner(player));
		if (recruits.isEmpty()) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.site.no_recruit", ASSIGN_RANGE), false);
			return;
		}
		recruits.sort(Comparator.comparingDouble(recruit -> recruit.squaredDistanceTo(center)));
		WorkerEntity worker = recruits.get(0).becomeWorker(getProfession(), pos);
		if (worker == null) {
			return;
		}
		if (owner == null) {
			owner = player.getUuid();
		}
		workerUuid = worker.getUuid();
		workerEntityId = worker.getId();
		lastHeartbeat = serverWorld.getTime();
		status = WorkStatus.WAITING;
		jobsDone = 0;
		markDirty();
		player.sendMessage(Text.translatable("message.evolvedwarfare.site.assigned", worker.getName(),
				Text.translatable(getProfession().translationKey())), false);
	}

	/** Libera al trabajador: vuelve a ser recluta y sigue al jugador. */
	public void dismiss(ServerPlayerEntity player) {
		if (!(world instanceof ServerWorld serverWorld) || workerUuid == null) {
			return;
		}
		Entity entity = serverWorld.getEntity(workerUuid);
		clearWorker();
		if (entity instanceof WorkerEntity worker) {
			RecruitEntity recruit = worker.quitJob(false);
			if (recruit != null) {
				recruit.setOrder(NpcOrder.FOLLOW);
				player.sendMessage(Text.translatable("message.evolvedwarfare.site.dismissed", recruit.getName()), false);
			}
		}
	}

	/** Al romper el puesto, su trabajador vuelve a ser recluta. */
	public void onBroken() {
		if (world instanceof ServerWorld serverWorld && workerUuid != null) {
			Entity entity = serverWorld.getEntity(workerUuid);
			clearWorker();
			if (entity instanceof WorkerEntity worker) {
				worker.quitJob(true);
			}
		}
	}

	private void clearWorker() {
		workerUuid = null;
		workerEntityId = -1;
		markDirty();
	}

	/** Marca con partículas (solo para ese jugador) el borde de la zona de trabajo. */
	public void showArea(ServerPlayerEntity player) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		WorkArea area = getProfession().area();
		if (!area.exists()) {
			return;
		}
		BlockBox box = area.box(pos, getFacing());
		double y = pos.getY() + 0.15;
		for (int x = box.getMinX(); x <= box.getMaxX() + 1; x++) {
			particle(serverWorld, player, x, y, box.getMinZ());
			particle(serverWorld, player, x, y, box.getMaxZ() + 1);
		}
		for (int z = box.getMinZ() + 1; z <= box.getMaxZ(); z++) {
			particle(serverWorld, player, box.getMinX(), y, z);
			particle(serverWorld, player, box.getMaxX() + 1, y, z);
		}
		if (area.underground()) {
			// la entrada de la mina: una marca por tramo, en la superficie
			Direction back = getFacing().getOpposite();
			for (int step = 1; step <= ExcavationJob.STEPS + 1; step++) {
				BlockPos feet = ExcavationJob.stepFeet(pos, back, step);
				serverWorld.spawnParticles(player, ParticleTypes.END_ROD, true, feet.getX() + 0.5, y + 0.3, feet.getZ() + 0.5,
						2, 0.1, 0.2, 0.1, 0.0);
			}
		}
	}

	private static void particle(ServerWorld world, ServerPlayerEntity player, double x, double y, double z) {
		world.spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, true, x, y, z, 2, 0.05, 0.1, 0.05, 0.0);
	}

	// ------------------------------------------------------------------ almacén (lo usan los oficios)

	/** Cuántos hay que cumplan el filtro (en los suministros y, si se pide, también en la producción). */
	public int count(Predicate<ItemStack> filter, boolean includeOutput) {
		int end = includeOutput ? SIZE : SUPPLY_SLOTS;
		int total = 0;
		for (int i = 0; i < end; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** El primer montón que cumpla el filtro (el de verdad: si lo gastas, llama a markDirty). */
	@Nullable
	public ItemStack find(Predicate<ItemStack> filter, boolean includeOutput) {
		int end = includeOutput ? SIZE : SUPPLY_SLOTS;
		for (int i = 0; i < end; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				return stack;
			}
		}
		return null;
	}

	/** Gasta {@code amount} (primero de los suministros). Si no hay bastantes, no gasta nada. */
	public boolean consume(Predicate<ItemStack> filter, int amount, boolean includeOutput) {
		if (count(filter, includeOutput) < amount) {
			return false;
		}
		int end = includeOutput ? SIZE : SUPPLY_SLOTS;
		for (int i = 0; i < end && amount > 0; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				int take = Math.min(amount, stack.getCount());
				stack.decrement(take);
				amount -= take;
			}
		}
		markDirty();
		return true;
	}

	/** Lleno = sin ningún hueco libre en la producción. */
	public boolean isOutputFull() {
		for (int i = SUPPLY_SLOTS; i < SIZE; i++) {
			if (items.get(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Mete en la producción; devuelve lo que no cabe. */
	public ItemStack addOutput(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int i = SUPPLY_SLOTS; i < SIZE && !rest.isEmpty(); i++) {
			ItemStack slot = items.get(i);
			if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, rest)) {
				int move = Math.min(rest.getCount(), slot.getMaxCount() - slot.getCount());
				if (move > 0) {
					slot.increment(move);
					rest.decrement(move);
				}
			}
		}
		for (int i = SUPPLY_SLOTS; i < SIZE && !rest.isEmpty(); i++) {
			if (items.get(i).isEmpty()) {
				int move = Math.min(rest.getCount(), rest.getMaxCount());
				items.set(i, rest.copyWithCount(move));
				rest.decrement(move);
			}
		}
		markDirty();
		return rest;
	}

	// ------------------------------------------------------------------ Inventory

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(items, slot, amount);
		if (!result.isEmpty()) {
			markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack result = Inventories.removeStack(items, slot);
		markDirty();
		return result;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player) && canAccess(player);
	}

	@Override
	public void clear() {
		items.clear();
		markDirty();
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? OUTPUT : SUPPLY;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return slot < SUPPLY_SLOTS;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return dir == Direction.DOWN && slot >= SUPPLY_SLOTS;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return slot < SUPPLY_SLOTS;
	}

	// ------------------------------------------------------------------ menú

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new WorkSiteScreenHandler(syncId, playerInventory, this);
	}

	// ------------------------------------------------------------------ guardado

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		if (workerUuid != null) {
			nbt.putUuid("Worker", workerUuid);
		}
		nbt.putInt("MineY", mineY);
		nbt.putInt("Fuel", fuel);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		items.clear();
		Inventories.readNbt(nbt, items, registries);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		workerUuid = nbt.containsUuid("Worker") ? nbt.getUuid("Worker") : null;
		mineY = nbt.contains("MineY") ? nbt.getInt("MineY") : UNSET;
		fuel = nbt.getInt("Fuel");
	}
}
