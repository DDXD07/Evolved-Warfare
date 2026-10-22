package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.WarehouseBlockEntity;
import com.evolvedwarfare.logistics.StorageNetwork;
import com.evolvedwarfare.network.WarehouseActionPayload;
import com.evolvedwarfare.network.WarehouseContentsPayload;
import com.evolvedwarfare.registry.ModScreens;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal del almacén. Solo tiene las ranuras del inventario del jugador; la rejilla de la red la dibuja la pantalla
 * con lo que manda el servidor ({@link WarehouseContentsPayload}) y los clics van con {@link WarehouseActionPayload}.
 * Mayús + clic en el inventario del jugador mete el montón en la red.
 */
public class WarehouseScreenHandler extends ScreenHandler {
	public static final int INVENTORY_Y = 139;
	public static final int HOTBAR_Y = 197;
	private static final int SYNC_INTERVAL = 10;

	@Nullable
	private final WarehouseBlockEntity warehouse;
	private final PlayerEntity player;
	private final BlockPos pos;
	private int ticks;
	@Nullable
	private List<StorageNetwork.Entry> lastSent;
	private int lastContainers = -1;

	// lo que ve el cliente
	private List<StorageNetwork.Entry> clientEntries = List.of();
	private int clientContainers;
	private int clientVersion;

	/** Cliente. */
	public WarehouseScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, null, pos);
	}

	/** Servidor. */
	public WarehouseScreenHandler(int syncId, PlayerInventory playerInventory, WarehouseBlockEntity warehouse) {
		this(syncId, playerInventory, warehouse, warehouse.getPos());
	}

	private WarehouseScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable WarehouseBlockEntity warehouse, BlockPos pos) {
		super(ModScreens.WAREHOUSE, syncId);
		this.warehouse = warehouse;
		this.player = playerInventory.player;
		this.pos = pos.toImmutable();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
		}
	}

	// ------------------------------------------------------------------ servidor

	@Override
	public void sendContentUpdates() {
		super.sendContentUpdates();
		if (warehouse != null && ticks++ % SYNC_INTERVAL == 0) {
			sync(false);
		}
	}

	/** Manda al cliente lo que hay en la red (solo si ha cambiado, salvo que se fuerce). */
	private void sync(boolean force) {
		if (warehouse == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		StorageNetwork network = warehouse.network();
		List<StorageNetwork.Entry> entries = network.contents();
		if (!force && network.containerCount() == lastContainers && same(entries, lastSent)) {
			return;
		}
		lastSent = entries;
		lastContainers = network.containerCount();
		ServerPlayNetworking.send(serverPlayer, new WarehouseContentsPayload(syncId, network.containerCount(), entries));
	}

	private static boolean same(List<StorageNetwork.Entry> a, @Nullable List<StorageNetwork.Entry> b) {
		if (b == null || a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i).count() != b.get(i).count() || !ItemStack.areItemsAndComponentsEqual(a.get(i).prototype(), b.get(i).prototype())) {
				return false;
			}
		}
		return true;
	}

	/** Un clic en la rejilla (ver {@link WarehouseActionPayload}). */
	public void handleAction(ServerPlayerEntity serverPlayer, int action, ItemStack prototype) {
		if (warehouse == null || !canUse(serverPlayer)) {
			return;
		}
		StorageNetwork network = warehouse.network();
		ItemStack cursor = getCursorStack();
		switch (action) {
			case WarehouseActionPayload.TAKE_STACK, WarehouseActionPayload.TAKE_HALF -> {
				if (prototype.isEmpty()) {
					break;
				}
				if (!cursor.isEmpty() && !ItemStack.areItemsAndComponentsEqual(cursor, prototype)) {
					setCursorStack(network.insert(cursor));
					break;
				}
				int max = prototype.getMaxCount();
				int amount = max;
				if (action == WarehouseActionPayload.TAKE_HALF) {
					int available = network.count(stack -> ItemStack.areItemsAndComponentsEqual(stack, prototype));
					amount = Math.max(1, Math.min(available, max) / 2);
				}
				amount = Math.min(amount, max - cursor.getCount());
				if (amount > 0) {
					ItemStack taken = network.extractLike(prototype, amount);
					if (cursor.isEmpty()) {
						setCursorStack(taken);
					} else {
						cursor.increment(taken.getCount());
					}
				}
			}
			case WarehouseActionPayload.TAKE_TO_INVENTORY -> {
				if (prototype.isEmpty()) {
					break;
				}
				ItemStack taken = network.extractLike(prototype, prototype.getMaxCount());
				serverPlayer.getInventory().insertStack(taken);
				if (!taken.isEmpty()) {
					ItemStack rest = network.insert(taken);
					if (!rest.isEmpty()) {
						serverPlayer.dropItem(rest, false);
					}
				}
			}
			case WarehouseActionPayload.INSERT_CURSOR -> {
				if (!cursor.isEmpty()) {
					setCursorStack(network.insert(cursor));
				}
			}
			case WarehouseActionPayload.INSERT_ONE -> {
				if (!cursor.isEmpty() && network.insert(cursor.copyWithCount(1)).isEmpty()) {
					cursor.decrement(1);
				}
			}
			default -> {
			}
		}
		sendContentUpdates();
		sync(true);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (warehouse == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack rest = warehouse.network().insert(slot.getStack());
		slot.setStack(rest);
		slot.markDirty();
		sync(true);
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (warehouse == null) {
			return true;
		}
		return !warehouse.isRemoved() && warehouse.canAccess(player) && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0;
	}

	// ------------------------------------------------------------------ cliente

	public void setClientContents(int containers, List<StorageNetwork.Entry> entries) {
		clientContainers = containers;
		clientEntries = entries;
		clientVersion++;
	}

	public List<StorageNetwork.Entry> clientEntries() {
		return clientEntries;
	}

	public int clientContainers() {
		return clientContainers;
	}

	/** Cambia cada vez que llegan datos nuevos (para que la pantalla vuelva a ordenar). */
	public int clientVersion() {
		return clientVersion;
	}
}
