package com.evolvedwarfare.screen;

import com.evolvedwarfare.item.RouteSlipItem;
import com.evolvedwarfare.logistics.RouteData;
import com.evolvedwarfare.logistics.RouteStop;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModScreens;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/**
 * Editor de una hoja de ruta (la que lleva el jugador en la mano). Por cada parada: botón recoger/entregar, quitar, y
 * tres huecos "fantasma" de filtro: clic con un objeto lo pone como filtro (sin gastarlo), clic con la mano vacía lo quita.
 *
 * <p>Ranuras: 0-17 filtros (parada * 3 + n), 18-53 inventario del jugador.</p>
 */
public class RouteSlipScreenHandler extends ScreenHandler {
	public static final int WIDTH = 230;
	public static final int ROW_Y = 26;
	public static final int ROW_HEIGHT = 20;
	public static final int FILTER_X = 146;
	public static final int INVENTORY_X = 35;
	public static final int INVENTORY_Y = 162;
	public static final int HOTBAR_Y = 220;
	private static final int GHOSTS = RouteData.MAX_STOPS * RouteData.MAX_FILTERS;

	private final PlayerEntity player;
	private final Hand hand;
	private final SimpleInventory ghosts = new SimpleInventory(GHOSTS);

	/** Cliente: recibe qué mano. */
	public RouteSlipScreenHandler(int syncId, PlayerInventory playerInventory, Integer handOrdinal) {
		this(syncId, playerInventory, handOrdinal != null && handOrdinal == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND);
	}

	public RouteSlipScreenHandler(int syncId, PlayerInventory playerInventory, Hand hand) {
		super(ModScreens.ROUTE_SLIP, syncId);
		this.player = playerInventory.player;
		this.hand = hand;
		for (int i = 0; i < GHOSTS; i++) {
			int stop = i / RouteData.MAX_FILTERS;
			int n = i % RouteData.MAX_FILTERS;
			addSlot(new Slot(ghosts, i, FILTER_X + n * 18, ROW_Y + stop * ROW_HEIGHT + 1) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return false;
				}

				@Override
				public boolean canTakeItems(PlayerEntity playerEntity) {
					return false;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
		loadGhosts();
	}

	public ItemStack stack() {
		return player.getStackInHand(hand);
	}

	public RouteData data() {
		return RouteSlipItem.data(stack());
	}

	private void loadGhosts() {
		RouteData data = data();
		for (int i = 0; i < GHOSTS; i++) {
			int stop = i / RouteData.MAX_FILTERS;
			int n = i % RouteData.MAX_FILTERS;
			ItemStack ghost = ItemStack.EMPTY;
			if (stop < data.stops().size() && n < data.stops().get(stop).filter().size()) {
				ghost = data.stops().get(stop).filter().get(n).copyWithCount(1);
			}
			ghosts.setStack(i, ghost);
		}
	}

	private void write(RouteData data) {
		stack().set(ModComponents.ROUTE, data);
		loadGhosts();
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
		if (slotIndex >= 0 && slotIndex < GHOSTS) {
			if (actionType == SlotActionType.PICKUP && !clicker.getWorld().isClient && stack().getItem() instanceof RouteSlipItem) {
				setFilter(slotIndex, getCursorStack());
			}
			return;
		}
		super.onSlotClick(slotIndex, button, actionType, clicker);
	}

	private void setFilter(int slotIndex, ItemStack cursor) {
		RouteData data = data();
		int stopIndex = slotIndex / RouteData.MAX_FILTERS;
		int n = slotIndex % RouteData.MAX_FILTERS;
		if (stopIndex >= data.stops().size()) {
			return;
		}
		RouteStop stop = data.stops().get(stopIndex);
		List<ItemStack> filter = new ArrayList<>(stop.filter());
		if (cursor.isEmpty()) {
			if (n < filter.size()) {
				filter.remove(n);
			}
		} else if (n < filter.size()) {
			filter.set(n, cursor.copyWithCount(1));
		} else {
			filter.add(cursor.copyWithCount(1));
		}
		write(data.replace(stopIndex, new RouteStop(stop.pos(), stop.pickup(), List.copyOf(filter))));
	}

	/** Botones: parada * 2 → cambiar recoger/entregar; parada * 2 + 1 → quitar la parada. */
	@Override
	public boolean onButtonClick(PlayerEntity clicker, int id) {
		RouteData data = data();
		int stopIndex = id / 2;
		if (!(stack().getItem() instanceof RouteSlipItem) || stopIndex < 0 || stopIndex >= data.stops().size()) {
			return false;
		}
		RouteStop stop = data.stops().get(stopIndex);
		if (id % 2 == 0) {
			write(data.replace(stopIndex, new RouteStop(stop.pos(), !stop.pickup(), stop.filter())));
		} else {
			write(data.without(stopIndex));
		}
		return true;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != ghosts && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public ItemStack quickMove(PlayerEntity clicker, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity clicker) {
		return stack().getItem() instanceof RouteSlipItem;
	}
}
