package com.evolvedwarfare.screen;

import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModScreens;
import com.evolvedwarfare.trade.Deal;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * La tienda de un comerciante de ciudad: todo su catálogo a la vez (lo que vende y lo que compra), con el precio de
 * cada lote y lo que le queda hoy. El catálogo va al cliente al abrir; las existencias se sincronizan en vivo.
 *
 * <p>Botones: {@code índice * 2} = un lote; {@code índice * 2 + 1} = muchos (un montón entero o todo lo que tengas).</p>
 */
public class TraderScreenHandler extends ScreenHandler {
	/** Lo que se manda al cliente al abrir. */
	public record Data(int entityId, int traderType, int cityLevel, int pricePercent, List<Deal> deals) {
		public static final PacketCodec<RegistryByteBuf, Data> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Data::entityId,
				PacketCodecs.VAR_INT, Data::traderType,
				PacketCodecs.VAR_INT, Data::cityLevel,
				PacketCodecs.VAR_INT, Data::pricePercent,
				Deal.PACKET_CODEC.collect(PacketCodecs.toList()), Data::deals,
				Data::new);
	}

	// disposición (la comparte la pantalla)
	public static final int WIDTH = 252;
	public static final int COLUMNS = 4;
	public static final int ROWS = 4;
	public static final int CARD_W = 56;
	public static final int CARD_H = 30;
	public static final int CARD_GAP = 2;
	public static final int GRID_X = 7;
	public static final int GRID_Y = 34;
	public static final int INVENTORY_X = (WIDTH - 162) / 2;
	public static final int INVENTORY_Y = GRID_Y + ROWS * (CARD_H + CARD_GAP) + 22;
	public static final int HOTBAR_Y = INVENTORY_Y + 58;
	public static final int HEIGHT = HOTBAR_Y + 24;

	@Nullable
	private final TraderEntity trader;
	private final Data data;
	private final PropertyDelegate stock;

	/** Cliente. */
	public TraderScreenHandler(int syncId, PlayerInventory playerInventory, Data data) {
		this(syncId, playerInventory, null, data, new ArrayPropertyDelegate(data.deals().size()));
	}

	/** Servidor. */
	public TraderScreenHandler(int syncId, PlayerInventory playerInventory, TraderEntity trader, Data data) {
		this(syncId, playerInventory, trader, data, new PropertyDelegate() {
			@Override
			public int get(int index) {
				return trader.stockLeft(index, data.deals().get(index));
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return data.deals().size();
			}
		});
	}

	private TraderScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable TraderEntity trader, Data data,
			PropertyDelegate stock) {
		super(ModScreens.TRADER, syncId);
		this.trader = trader;
		this.data = data;
		this.stock = stock;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
		addProperties(stock);
	}

	// ------------------------------------------------------------------ lo que ve el cliente

	public List<Deal> deals() {
		return data.deals();
	}

	public int cityLevel() {
		return data.cityLevel();
	}

	/** Cuánto más caro vende que en una ciudad de nivel 1 (%). */
	public int pricePercent() {
		return data.pricePercent();
	}

	/** El oficio del comerciante (cada uno tiene su propio diseño de tienda). */
	public com.evolvedwarfare.trade.TraderType traderType() {
		com.evolvedwarfare.trade.TraderType[] types = com.evolvedwarfare.trade.TraderType.values();
		return types[Math.floorMod(data.traderType(), types.length)];
	}

	public int entityId() {
		return data.entityId();
	}

	public int left(int index) {
		return stock.get(index);
	}

	// ------------------------------------------------------------------ comprar y vender

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (trader == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		int index = id / 2;
		if (id < 0 || index >= data.deals().size()) {
			return false;
		}
		Deal deal = data.deals().get(index);
		boolean many = id % 2 == 1;
		if (deal.sell()) {
			sellToPlayer(serverPlayer, index, deal, many);
		} else {
			buyFromPlayer(serverPlayer, index, deal, many);
		}
		return true;
	}

	/** El comerciante te vende: pagas monedas. */
	private void sellToPlayer(ServerPlayerEntity player, int index, Deal deal, boolean many) {
		int left = trader.stockLeft(index, deal);
		if (left <= 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.trader.sold_out"), true);
			return;
		}
		PlayerInventory inventory = player.getInventory();
		int lots = 1;
		if (many) {
			lots = Math.max(1, deal.item().getMaxCount() / Math.max(1, deal.count()));
		}
		lots = Math.min(lots, left);
		if (!player.isCreative()) {
			lots = Math.min(lots, Market.countCoins(inventory) / Math.max(1, deal.price()));
			if (lots <= 0) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.trader.no_money", deal.price(),
						Market.currency().getName()), true);
				return;
			}
			Market.takeCoins(inventory, deal.price() * lots);
		}
		int total = deal.count() * lots;
		int max = deal.item().getMaxCount();
		while (total > 0) {
			int n = Math.min(max, total);
			inventory.offerOrDrop(deal.prototype().copyWithCount(n));
			total -= n;
		}
		trader.consumeStock(index, lots);
		done(player, lots, deal);
	}

	/** El comerciante te compra: entregas el lote y te paga. */
	private void buyFromPlayer(ServerPlayerEntity player, int index, Deal deal, boolean many) {
		int left = trader.stockLeft(index, deal);
		if (left <= 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.trader.no_more"), true);
			return;
		}
		PlayerInventory inventory = player.getInventory();
		int have = count(inventory, deal);
		int lots = Math.min(have / deal.count(), many ? left : 1);
		if (lots <= 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.trader.missing", deal.count(), deal.prototype().getName()), true);
			return;
		}
		take(inventory, deal, deal.count() * lots);
		Market.giveCoins(inventory, (long) deal.price() * lots);
		trader.consumeStock(index, lots);
		done(player, lots, deal);
	}

	private void done(ServerPlayerEntity player, int lots, Deal deal) {
		player.addExperience(Math.max(1, lots * (1 + deal.tier().ordinal())));
		trader.onTrade(player);
		sendContentUpdates();
	}

	/** Cuántos de los que le valen a esa línea lleva el jugador (objetos "limpios"; los cultivos, en cajas llenas). */
	public static int count(PlayerInventory inventory, Deal deal) {
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (deal.accepts(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void take(PlayerInventory inventory, Deal deal, int amount) {
		for (int i = 0; i < inventory.size() && amount > 0; i++) {
			ItemStack stack = inventory.getStack(i);
			if (deal.accepts(stack)) {
				int n = Math.min(amount, stack.getCount());
				stack.decrement(n);
				amount -= n;
			}
		}
		inventory.markDirty();
	}

	// ------------------------------------------------------------------ ranuras

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (trader == null) {
			return true;
		}
		return trader.isAlive() && player.squaredDistanceTo(trader) <= 64.0;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (trader != null && trader.getCustomer() == player) {
			trader.setCustomer(null);
		}
	}
}
