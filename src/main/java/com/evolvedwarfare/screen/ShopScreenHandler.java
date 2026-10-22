package com.evolvedwarfare.screen;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Tienda del comerciante. Enseña los 9 huecos de suministros del puesto con su precio y su lote. Los demás jugadores
 * compran con monedas ({@link Market#currency()}), que van a la producción del puesto; el dueño pone los precios.
 * Solo abre si el comerciante está cerca de su puesto.
 */
public class ShopScreenHandler extends ScreenHandler {
	public static final int OFFERS = WorkSiteBlockEntity.SUPPLY_SLOTS;
	public static final int P_PRICE = 0;
	public static final int P_BUNDLE = OFFERS;
	public static final int P_PRESENT = OFFERS * 2;
	public static final int SITE_PROPERTIES = P_PRESENT + 1;
	public static final int P_OWNER = SITE_PROPERTIES;
	public static final int PROPERTY_COUNT = P_OWNER + 1;

	/** Botones: tipo * 9 + hueco. */
	public static final int BUY = 0;
	public static final int PRICE_DOWN = 1;
	public static final int PRICE_UP = 2;
	public static final int BUNDLE_NEXT = 3;
	public static final int PRICE_DOWN_10 = 4;
	public static final int PRICE_UP_10 = 5;
	public static final int BUNDLE_PREVIOUS = 6;

	// disposición (la comparte la pantalla)
	public static final int WIDTH = 186;
	public static final int CARD_W = 56;
	public static final int CARD_H = 38;
	public static final int CARD_GAP = 2;
	public static final int GRID_X = 7;
	public static final int GRID_Y = 18;
	public static final int INVENTORY_X = 13;
	public static final int INVENTORY_Y = 162;
	public static final int HOTBAR_Y = 220;

	@Nullable
	private final WorkSiteBlockEntity site;
	private final Inventory stock;
	private final PropertyDelegate properties;
	private final BlockPos pos;

	/** Cliente. */
	public ShopScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, null, new SimpleInventory(WorkSiteBlockEntity.SIZE), new ArrayPropertyDelegate(PROPERTY_COUNT), pos);
	}

	/** Servidor. */
	public ShopScreenHandler(int syncId, PlayerInventory playerInventory, WorkSiteBlockEntity site) {
		this(syncId, playerInventory, site, site, serverProperties(site, playerInventory.player), site.getPos());
	}

	private ShopScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable WorkSiteBlockEntity site, Inventory stock,
			PropertyDelegate properties, BlockPos pos) {
		super(ModScreens.SHOP, syncId);
		this.site = site;
		this.stock = stock;
		this.properties = properties;
		this.pos = pos.toImmutable();
		for (int i = 0; i < OFFERS; i++) {
			addSlot(new Slot(stock, i, cardX(i) + 3, cardY(i) + 3) {
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
		addProperties(properties);
	}

	private static PropertyDelegate serverProperties(WorkSiteBlockEntity site, PlayerEntity player) {
		boolean owner = site.canAccess(player);
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return index == P_OWNER ? (owner ? 1 : 0) : site.shopProperties.get(index);
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return PROPERTY_COUNT;
			}
		};
	}

	public static int cardX(int offer) {
		return GRID_X + (offer % 3) * (CARD_W + CARD_GAP);
	}

	public static int cardY(int offer) {
		return GRID_Y + (offer / 3) * (CARD_H + CARD_GAP);
	}

	// ------------------------------------------------------------------ lo que ve el cliente

	public ItemStack offer(int slot) {
		return stock.getStack(slot);
	}

	public int price(int slot) {
		return properties.get(P_PRICE + slot);
	}

	public int bundle(int slot) {
		return Math.max(1, properties.get(P_BUNDLE + slot));
	}

	public boolean merchantPresent() {
		return properties.get(P_PRESENT) != 0;
	}

	public boolean isOwner() {
		return properties.get(P_OWNER) != 0;
	}

	// ------------------------------------------------------------------ botones

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (site == null || !(player instanceof ServerPlayerEntity serverPlayer) || id < 0) {
			return false;
		}
		int kind = id / OFFERS;
		int slot = id % OFFERS;
		if (kind == BUY) {
			buy(serverPlayer, slot);
			return true;
		}
		if (!site.canAccess(player)) {
			return false;
		}
		switch (kind) {
			case PRICE_DOWN -> site.addShopPrice(slot, -1);
			case PRICE_UP -> site.addShopPrice(slot, 1);
			case PRICE_DOWN_10 -> site.addShopPrice(slot, -10);
			case PRICE_UP_10 -> site.addShopPrice(slot, 10);
			case BUNDLE_NEXT -> site.cycleShopBundle(slot, false);
			case BUNDLE_PREVIOUS -> site.cycleShopBundle(slot, true);
			default -> {
				return false;
			}
		}
		return true;
	}

	private void buy(ServerPlayerEntity player, int slot) {
		if (site == null) {
			return;
		}
		if (!site.isMerchantPresent()) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.shop.closed"), true);
			return;
		}
		ItemStack offer = site.getStack(slot);
		int bundle = site.getShopBundle(slot);
		int price = site.getShopPrice(slot);
		if (offer.isEmpty() || offer.getCount() < bundle) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.shop.out_of_stock"), true);
			return;
		}
		Item coin = Market.currency();
		PlayerInventory inventory = player.getInventory();
		if (!player.isCreative()) {
			if (countCoins(inventory, coin) < price) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.shop.no_money", price, coin.getName()), true);
				return;
			}
			takeCoins(inventory, coin, price);
		}
		ItemStack bought = offer.split(bundle);
		site.markDirty();
		inventory.offerOrDrop(bought);
		// el dinero va a la producción del puesto (lo que no quepa, al suelo junto al puesto)
		for (ItemStack coins : Market.coins(price)) {
			ItemStack rest = site.addOutput(coins);
			if (!rest.isEmpty()) {
				Block.dropStack(player.getWorld(), pos.up(), rest);
			}
		}
		player.getWorld().playSound(null, pos, SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 0.6F, 1.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.shop.bought", bundle, bought.getName(), price, coin.getName()), true);
	}

	private static int countCoins(PlayerInventory inventory, Item coin) {
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (isPlainCoin(stack, coin)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void takeCoins(PlayerInventory inventory, Item coin, int amount) {
		for (int i = 0; i < inventory.size() && amount > 0; i++) {
			ItemStack stack = inventory.getStack(i);
			if (isPlainCoin(stack, coin)) {
				int take = Math.min(amount, stack.getCount());
				stack.decrement(take);
				amount -= take;
			}
		}
		inventory.markDirty();
	}

	private static boolean isPlainCoin(ItemStack stack, Item coin) {
		return !stack.isEmpty() && stack.isOf(coin) && stack.getComponentChanges().isEmpty();
	}

	// ------------------------------------------------------------------ ranuras

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != stock && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (site == null) {
			return true;
		}
		return !site.isRemoved() && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0;
	}
}
