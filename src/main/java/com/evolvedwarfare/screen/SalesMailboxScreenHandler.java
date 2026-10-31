package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.SalesMailboxBlockEntity;
import com.evolvedwarfare.item.GloryPouchItem;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** El Buzón de Ventas: 4x4 huecos para vender, el hueco de las monedas y tu inventario. */
public class SalesMailboxScreenHandler extends ScreenHandler {
	public static final int GRID_X = 17;
	public static final int GRID_Y = 18;
	public static final int COIN_X = 134;
	public static final int COIN_Y = 45;
	public static final int INVENTORY_Y = 104;

	private final Inventory mailbox;
	private final PlayerEntity player;
	private final PropertyDelegate properties;

	/** Cliente. */
	public SalesMailboxScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleInventory(SalesMailboxBlockEntity.SIZE) {
			@Override
			public boolean isValid(int slot, ItemStack stack) {
				return slot == SalesMailboxBlockEntity.COIN_SLOT ? GloryPouchItem.isPouch(stack)
						: !Market.isCurrency(stack) && !GloryPouchItem.isPouch(stack);
			}
		}, new ArrayPropertyDelegate(2));
	}

	/** Servidor. */
	public SalesMailboxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory mailbox, PropertyDelegate properties) {
		super(ModScreens.SALES_MAILBOX, syncId);
		checkSize(mailbox, SalesMailboxBlockEntity.SIZE);
		this.mailbox = mailbox;
		this.properties = properties;
		this.player = playerInventory.player;
		mailbox.onOpen(playerInventory.player);
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				addSlot(new Slot(mailbox, col + row * 4, GRID_X + col * 18, GRID_Y + row * 18) {
					@Override
					public boolean canInsert(ItemStack stack) {
						return mailbox.isValid(getIndex(), stack);
					}
				});
			}
		}
		addSlot(new Slot(mailbox, SalesMailboxBlockEntity.COIN_SLOT, COIN_X, COIN_Y) {
			/** Solo se saca (o se pone una bolsa vacía para que guarde ahí las monedas). */
			@Override
			public boolean canInsert(ItemStack stack) {
				return !hasStack() && GloryPouchItem.isPouch(stack);
			}

			@Override
			public int getMaxItemCount(ItemStack stack) {
				return GloryPouchItem.isPouch(stack) ? 1 : stack.getMaxCount();
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
		}
		addProperties(properties);
	}

	public boolean isSaleSlot(Slot slot) {
		return slot.inventory == mailbox && slot.getIndex() < SalesMailboxBlockEntity.SALE_SLOTS;
	}

	/** Lo que pagaría ahora el buzón por todo lo que tiene en venta (en monedas, con decimales). */
	public double pendingValue() {
		double total = 0.0;
		for (int i = 0; i < SalesMailboxBlockEntity.SALE_SLOTS; i++) {
			total += SalesMailboxBlockEntity.saleValue(slots.get(i).getStack(), player.getWorld());
		}
		return total;
	}

	public int lastSold() {
		return properties.get(0);
	}

	public int lastCoins() {
		return properties.get(1);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack before = stack.copy();
		int mailboxEnd = SalesMailboxBlockEntity.SIZE;
		if (index < mailboxEnd) {
			if (!insertItem(stack, mailboxEnd, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!mailbox.isValid(0, stack) || !insertItem(stack, 0, SalesMailboxBlockEntity.SALE_SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return before;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return mailbox.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		mailbox.onClose(player);
	}
}
