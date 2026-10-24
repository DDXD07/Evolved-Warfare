package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.WorkSiteBlock;
import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.registry.ModScreens;
import com.evolvedwarfare.work.Profession;
import com.evolvedwarfare.work.WorkStatus;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Menú de un puesto de trabajo: suministros (fila de arriba), producción (dos filas, solo se saca), estado del
 * trabajador y botones para asignar/despedir y ver la zona.
 *
 * <p>Ranuras: 0-8 suministros, 9-26 producción, 27-62 inventario del jugador.</p>
 */
public class WorkSiteScreenHandler extends ScreenHandler {
	public static final int BUTTON_ASSIGN = 0;
	public static final int BUTTON_DISMISS = 1;
	public static final int BUTTON_AREA = 2;
	public static final int BUTTON_BUILDING = 3;
	public static final int BUTTON_SHOP = 4;

	public static final int P_STATUS = 0;
	public static final int P_HAS_WORKER = 1;
	public static final int P_WORKER_LO = 2;
	public static final int P_WORKER_HI = 3;
	public static final int P_FUEL = 4;
	public static final int P_DEPTH = 5;
	public static final int P_JOBS = 6;
	public static final int P_NOTE = 7;
	/** Porcentaje de la obra en marcha (-1: ninguna). */
	public static final int P_PROGRESS = 8;
	public static final int PROPERTY_COUNT = 9;
	public static final int NO_DEPTH = Short.MAX_VALUE;

	// posiciones (compartidas con la pantalla)
	public static final int SUPPLY_Y = 18;
	public static final int OUTPUT_Y = 48;
	public static final int INVENTORY_Y = 137;
	public static final int HOTBAR_Y = 195;

	private static final int SIZE = WorkSiteBlockEntity.SIZE;
	private static final int SUPPLY_SLOTS = WorkSiteBlockEntity.SUPPLY_SLOTS;

	@Nullable
	private final WorkSiteBlockEntity site;
	private final Inventory inventory;
	private final PropertyDelegate properties;
	private final Profession profession;

	/** Cliente: recibe la posición del puesto. */
	public WorkSiteScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, null, new SimpleInventory(SIZE), new ArrayPropertyDelegate(PROPERTY_COUNT),
				professionAt(playerInventory.player.getWorld(), pos));
	}

	/** Servidor. */
	public WorkSiteScreenHandler(int syncId, PlayerInventory playerInventory, WorkSiteBlockEntity site) {
		this(syncId, playerInventory, site, site, site.properties, site.getProfession());
	}

	private WorkSiteScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable WorkSiteBlockEntity site,
			Inventory inventory, PropertyDelegate properties, Profession profession) {
		super(ModScreens.WORK_SITE, syncId);
		checkSize(inventory, SIZE);
		checkDataCount(properties, PROPERTY_COUNT);
		this.site = site;
		this.inventory = inventory;
		this.properties = properties;
		this.profession = profession;
		inventory.onOpen(playerInventory.player);

		for (int col = 0; col < SUPPLY_SLOTS; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, SUPPLY_Y));
		}
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, SUPPLY_SLOTS + row * 9 + col, 8 + col * 18, OUTPUT_Y + row * 18) {
					@Override
					public boolean canInsert(ItemStack stack) {
						return false;
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
		}
		addProperties(properties);
	}

	private static Profession professionAt(World world, BlockPos pos) {
		return world.getBlockState(pos).getBlock() instanceof WorkSiteBlock block ? block.getProfession() : Profession.FARMER;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (site == null || !(player instanceof ServerPlayerEntity serverPlayer) || !site.canAccess(player)) {
			return false;
		}
		switch (id) {
			case BUTTON_ASSIGN -> site.assign(serverPlayer);
			case BUTTON_DISMISS -> site.dismiss(serverPlayer);
			case BUTTON_AREA -> site.showArea(serverPlayer);
			case BUTTON_BUILDING -> site.openBlueprint(serverPlayer);
			case BUTTON_SHOP -> {
				if (site.getProfession() != Profession.MERCHANT) {
					return false;
				}
				site.openShop(serverPlayer);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return inventory.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		inventory.onClose(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < SIZE) {
			// del puesto al jugador
			if (!insertItem(stack, SIZE, SIZE + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertItem(stack, 0, SUPPLY_SLOTS, false)) {
			// del jugador a los suministros
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTakeItem(player, stack);
		return original;
	}

	// ------------------------------------------------------------------ datos para la pantalla

	public Profession getProfession() {
		return profession;
	}

	public WorkStatus status() {
		return WorkStatus.byOrdinal(properties.get(P_STATUS));
	}

	public boolean hasWorker() {
		return properties.get(P_HAS_WORKER) != 0;
	}

	/** Id de red del trabajador (llega partido en dos números de 16 bits). */
	public int workerEntityId() {
		return ((properties.get(P_WORKER_HI) & 0xFFFF) << 16) | (properties.get(P_WORKER_LO) & 0xFFFF);
	}

	/** Combustible guardado, en piezas que puede cocinar. */
	public int fuel() {
		return properties.get(P_FUEL);
	}

	/** Capa por la que va la mina, o {@link #NO_DEPTH} si aún no ha empezado. */
	public int depth() {
		return properties.get(P_DEPTH);
	}

	public int jobsDone() {
		return properties.get(P_JOBS);
	}

	/** Aviso (falta un material: ver {@link WorkSiteBlockEntity#missingFromNote}). */
	public int note() {
		return properties.get(P_NOTE);
	}

	public int progress() {
		return properties.get(P_PROGRESS);
	}
}
