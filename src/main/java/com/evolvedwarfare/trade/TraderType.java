package com.evolvedwarfare.trade;

import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

/**
 * Los oficios de los comerciantes de las ciudades. Cada uno vende unas cosas y compra otras, siempre por Golden
 * Coins (o esmeraldas si no está el mod de la moneda). Lo que compran lo pagan al mismo precio que el mercado
 * ({@link Market#priceOf}); lo que venden cuesta algo más.
 */
public enum TraderType {
	/** Tendero: comida. En todas las ciudades. */
	GROCER(0xC9A227, () -> new ItemStack(Items.BREAD)),
	/** Armero: armaduras, escudos y las armas medievales del mod. */
	ARMORER(0x4A4A52, () -> new ItemStack(Items.IRON_SWORD)),
	/** Cantero: piedra labrada, ladrillos y cristal. */
	MASON(0x8A8A8A, () -> new ItemStack(Items.STONE_BRICKS)),
	/** Joyero: gemas, oro y rarezas. */
	JEWELER(0x6A2C8C, () -> new ItemStack(Items.GOLD_INGOT)),
	/** Sastre (ciudades medievales): lana, tintes, camas, estandartes. */
	TAILOR(0x2E5FA8, () -> new ItemStack(Items.SHEARS)),
	/** Mercader de especias (ciudades del desierto). */
	SPICE_MERCHANT(0xC2572B, () -> new ItemStack(Items.SUGAR)),
	/** Peletero (ciudades de la nieve). */
	FURRIER(0xEDEDED, () -> new ItemStack(Items.RABBIT_HIDE));

	/** Cuántas cosas vende y cuántas compra cada comerciante (al azar de su lista). */
	private static final int SELLS = 5;
	private static final int BUYS = 3;
	private static final int SELL_USES = 12;
	private static final int BUY_USES = 16;

	/** Color del delantal (peto de cuero teñido). */
	public final int apron;
	private final Supplier<ItemStack> tool;

	TraderType(int apron, Supplier<ItemStack> tool) {
		this.apron = apron;
		this.tool = tool;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public ItemStack tool() {
		return tool.get();
	}

	@Nullable
	public static TraderType byId(String id) {
		for (TraderType type : values()) {
			if (type.id().equalsIgnoreCase(id)) {
				return type;
			}
		}
		return null;
	}

	public static TraderType random(Random random) {
		TraderType[] values = values();
		return values[random.nextInt(values.length)];
	}

	/** Lo que vende: {@code count} de esto por {@code coins} monedas. */
	private record Sell(Supplier<ItemStack> stack, int coins) {
	}

	private static Sell sell(ItemConvertible item, int count, int coins) {
		return new Sell(() -> new ItemStack(item, count), coins);
	}

	private List<Sell> sells() {
		List<Sell> list = new ArrayList<>();
		switch (this) {
			case GROCER -> {
				list.add(sell(Items.BREAD, 4, 1));
				list.add(sell(Items.COOKED_BEEF, 3, 1));
				list.add(sell(Items.COOKED_PORKCHOP, 3, 1));
				list.add(sell(Items.BAKED_POTATO, 8, 1));
				list.add(sell(Items.APPLE, 8, 1));
				list.add(sell(Items.PUMPKIN_PIE, 3, 1));
				list.add(sell(Items.COOKIE, 12, 1));
				list.add(sell(Items.CAKE, 1, 3));
				list.add(sell(Items.HONEY_BOTTLE, 2, 1));
				list.add(sell(Items.GOLDEN_CARROT, 3, 2));
			}
			case ARMORER -> {
				list.add(sell(Items.IRON_HELMET, 1, 5));
				list.add(sell(Items.IRON_CHESTPLATE, 1, 9));
				list.add(sell(Items.IRON_LEGGINGS, 1, 7));
				list.add(sell(Items.IRON_BOOTS, 1, 4));
				list.add(sell(Items.SHIELD, 1, 3));
				list.add(sell(Items.CROSSBOW, 1, 3));
				list.add(sell(Items.ARROW, 16, 1));
				list.add(sell(Items.DIAMOND_SWORD, 1, 18));
				list.add(sell(Items.DIAMOND_CHESTPLATE, 1, 30));
				list.add(sell(ModItems.LONGBOW, 1, 6));
				list.add(sell(ModItems.JAVELIN, 4, 2));
				list.add(sell(ModItems.BALLISTA_BOLT, 4, 2));
				for (WeaponType weapon : WeaponType.values()) {
					Item iron = ModItems.weapon(weapon, "iron");
					list.add(sell(iron, 1, 3 + weapon.damage));
				}
				list.add(sell(ModItems.weapon(WeaponType.LONGSWORD, "diamond"), 1, 22));
				list.add(sell(ModItems.weapon(WeaponType.HALBERD, "diamond"), 1, 24));
			}
			case MASON -> {
				list.add(sell(Items.STONE_BRICKS, 16, 1));
				list.add(sell(Items.CHISELED_STONE_BRICKS, 8, 1));
				list.add(sell(Items.POLISHED_ANDESITE, 16, 1));
				list.add(sell(Items.BRICKS, 10, 1));
				list.add(sell(Items.TERRACOTTA, 8, 1));
				list.add(sell(Items.GLASS, 8, 1));
				list.add(sell(Items.QUARTZ_BLOCK, 1, 1));
				list.add(sell(Items.LANTERN, 2, 1));
				list.add(sell(Items.SMOOTH_STONE, 12, 1));
				list.add(sell(Items.DEEPSLATE_TILES, 12, 1));
			}
			case JEWELER -> {
				list.add(sell(Items.DIAMOND, 1, 6));
				list.add(sell(Items.GOLD_INGOT, 2, 1));
				list.add(sell(Items.GOLD_BLOCK, 1, 5));
				list.add(sell(Items.LAPIS_LAZULI, 6, 1));
				list.add(sell(Items.AMETHYST_SHARD, 4, 1));
				list.add(sell(Items.NAME_TAG, 1, 8));
				list.add(sell(Items.CLOCK, 1, 4));
				list.add(sell(Items.COMPASS, 1, 3));
				list.add(sell(Items.GOLDEN_APPLE, 1, 10));
				list.add(sell(Items.ENDER_PEARL, 1, 4));
				list.add(sell(Items.EXPERIENCE_BOTTLE, 3, 2));
				if (Market.currency() != Items.EMERALD) {
					list.add(sell(Items.EMERALD, 1, 2));
				}
			}
			case TAILOR -> {
				for (Item wool : new Item[] {Items.WHITE_WOOL, Items.RED_WOOL, Items.BLUE_WOOL, Items.YELLOW_WOOL, Items.GREEN_WOOL}) {
					list.add(sell(wool, 4, 1));
				}
				list.add(sell(Items.WHITE_CARPET, 8, 1));
				list.add(sell(Items.RED_BED, 1, 2));
				list.add(sell(Items.WHITE_BANNER, 1, 2));
				list.add(sell(Items.PAINTING, 1, 2));
				list.add(sell(Items.ITEM_FRAME, 2, 1));
				list.add(sell(Items.LEATHER_CHESTPLATE, 1, 3));
				list.add(sell(Items.SADDLE, 1, 6));
				list.add(sell(Items.SHEARS, 1, 2));
			}
			case SPICE_MERCHANT -> {
				list.add(sell(Items.SUGAR, 12, 1));
				list.add(sell(Items.COCOA_BEANS, 10, 1));
				list.add(sell(Items.GLOW_BERRIES, 10, 1));
				list.add(sell(Items.RED_DYE, 6, 1));
				list.add(sell(Items.ORANGE_DYE, 6, 1));
				list.add(sell(Items.YELLOW_DYE, 6, 1));
				list.add(sell(Items.RABBIT_STEW, 1, 1));
				list.add(sell(Items.BROWN_MUSHROOM, 6, 1));
				list.add(sell(Items.BLAZE_POWDER, 2, 3));
				list.add(sell(Items.GLISTERING_MELON_SLICE, 2, 3));
				list.add(sell(Items.DRIED_KELP, 16, 1));
			}
			case FURRIER -> {
				list.add(sell(Items.LEATHER, 3, 1));
				list.add(sell(Items.RABBIT_HIDE, 4, 1));
				list.add(sell(Items.LEATHER_HELMET, 1, 2));
				list.add(sell(Items.LEATHER_CHESTPLATE, 1, 3));
				list.add(sell(Items.LEATHER_LEGGINGS, 1, 3));
				list.add(sell(Items.LEATHER_BOOTS, 1, 2));
				list.add(sell(Items.WHITE_WOOL, 4, 1));
				list.add(sell(Items.PACKED_ICE, 4, 1));
				list.add(sell(Items.BLUE_ICE, 1, 2));
				list.add(sell(Items.CAMPFIRE, 1, 1));
				list.add(sell(Items.COOKED_SALMON, 6, 1));
			}
		}
		return list;
	}

	/** Lo que compra (al precio del mercado). */
	private List<Item> buys() {
		return switch (this) {
			case GROCER -> List.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE, Items.PUMPKIN,
					Items.SWEET_BERRIES, Items.EGG, Items.BEEF, Items.CHICKEN, Items.PORKCHOP);
			case ARMORER -> List.of(Items.COAL, Items.IRON_INGOT, Items.RAW_IRON, Items.GOLD_INGOT, Items.FLINT, Items.LEATHER,
					Items.STRING);
			case MASON -> List.of(Items.COBBLESTONE, Items.STONE, Items.CLAY_BALL, Items.SAND, Items.GRAVEL, Items.COBBLED_DEEPSLATE);
			case JEWELER -> List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.RAW_GOLD, Items.QUARTZ, Items.AMETHYST_SHARD,
					Items.REDSTONE, Items.LAPIS_LAZULI);
			case TAILOR -> List.of(Items.STRING, Items.WHITE_WOOL, Items.LEATHER, Items.RABBIT_HIDE, Items.FEATHER);
			case SPICE_MERCHANT -> List.of(Items.CACTUS, Items.SUGAR_CANE, Items.COCOA_BEANS, Items.SWEET_BERRIES, Items.RABBIT_HIDE,
					Items.RABBIT_FOOT);
			case FURRIER -> List.of(Items.LEATHER, Items.RABBIT_HIDE, Items.COD, Items.SALMON, Items.MUTTON, Items.WHITE_WOOL);
		};
	}

	/** Las ofertas de un comerciante nuevo: unas cuantas ventas y compras al azar de su oficio. */
	public List<TradeOffer> rollOffers(Random random) {
		List<TradeOffer> offers = new ArrayList<>();
		List<Sell> sells = new ArrayList<>(sells());
		Collections.shuffle(sells, new java.util.Random(random.nextLong()));
		for (int i = 0; i < Math.min(SELLS, sells.size()); i++) {
			Sell sell = sells.get(i);
			offers.add(sellOffer(sell.stack().get(), sell.coins()));
		}
		List<Item> buys = new ArrayList<>(buys());
		Collections.shuffle(buys, new java.util.Random(random.nextLong()));
		int added = 0;
		for (Item item : buys) {
			if (added >= BUYS) {
				break;
			}
			TradeOffer offer = buyOffer(item);
			if (offer != null) {
				offers.add(offer);
				added++;
			}
		}
		return offers;
	}

	/** El comerciante vende {@code stack} por {@code coins} monedas (en dos huecos si no caben en uno). */
	private static TradeOffer sellOffer(ItemStack stack, int coins) {
		Item coin = Market.currency();
		int max = coin.getMaxCount();
		int first = Math.min(coins, max);
		int rest = Math.min(coins - first, max);
		Optional<TradedItem> second = rest > 0 ? Optional.of(new TradedItem(coin, rest)) : Optional.empty();
		return new TradeOffer(new TradedItem(coin, first), second, stack, SELL_USES, 1, 0.05F);
	}

	/** El comerciante compra este objeto al precio del mercado. */
	@Nullable
	private static TradeOffer buyOffer(Item item) {
		Market.Price price = Market.priceOf(new ItemStack(item));
		if (price == null) {
			return null;
		}
		int count = Math.min(price.items(), item.getMaxCount());
		return new TradeOffer(new TradedItem(item, count), Market.coins(price.coins()).get(0), BUY_USES, 1, 0.05F);
	}
}
