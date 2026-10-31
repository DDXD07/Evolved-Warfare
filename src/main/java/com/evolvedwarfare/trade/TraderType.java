package com.evolvedwarfare.trade;

import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import com.evolvedwarfare.world.CityLevels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Los oficios de los comerciantes de las ciudades y su catálogo completo. Todo se paga en Glory Coins, la moneda del mod.
 *
 * <p>Cada línea tiene una rareza ({@link Tier}) y su precio cae dentro del rango de esa rareza (común 1–500, poco común
 * 501–2.000, rara 2.001–5.000, épica 5.001–10.000, legendaria 10.001–500.000) en una ciudad de nivel 1. Todo se puede
 * comprar y vender en cualquier ciudad; las de más nivel (más lejos del punto de aparición) cobran un recargo encima:
 * cada nivel por encima del 1 sube los precios de venta un 10 % (configurable). De cada cosa mueven al menos 20 unidades
 * al día, y reponen cada mañana. Lo que te compran lo pagan a la mitad del precio nuevo,
 * igual en todas partes. Cada comerciante tiene sus propios precios (±10 %), así que conviene comparar. Los cultivos solo
 * los compran en {@link CropCrateItem cajas} llenas.</p>
 *
 * <p>Los precios salen del valor de cada cosa: dentro de una rareza, el doble de valor cuesta el doble (así fabricar o
 * deshacer cosas no da dinero gratis); solo se recorta lo que se saldría del rango.</p>
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

	/** Variación de precios entre comerciantes. */
	private static final double VARIANCE = 0.10;
	/** Lo que pagan de más por los cultivos en caja (la caja también cuesta). */
	private static final double CRATE_BONUS = 1.10;
	/** Cajas que compran al día como mínimo. */
	private static final int MIN_CRATES = 2;

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

	// ------------------------------------------------------------------ catálogo

	/** Una línea antes de poner precio: {@code count} por lote y {@code value} monedas viejas por unidad (su valor). */
	private record Entry(boolean sell, Item item, int count, double value, Tier tier) {
	}

	private static final class Builder {
		final List<Entry> entries = new ArrayList<>();

		/** Vende {@code count} por {@code coins} (valor). */
		Builder sell(Tier tier, ItemConvertible item, int count, int coins) {
			entries.add(new Entry(true, item.asItem(), count, coins / (double) count, tier));
			return this;
		}

		/** Compra según el valor del mercado. */
		Builder buy(Tier tier, ItemConvertible... items) {
			for (ItemConvertible item : items) {
				Market.Price price = Market.priceOf(new ItemStack(item));
				if (price != null) {
					entries.add(new Entry(false, item.asItem(), price.items(), price.coins() / (double) price.items(), tier));
				}
			}
			return this;
		}

		/** Compra {@code count} que valen {@code coins} (lo que no está en el mercado). */
		Builder buy(Tier tier, ItemConvertible item, int count, int coins) {
			entries.add(new Entry(false, item.asItem(), count, coins / (double) count, tier));
			return this;
		}
	}

	private List<Entry> entries() {
		Builder b = new Builder();
		Tier c = Tier.COMMON;
		Tier u = Tier.UNCOMMON;
		Tier r = Tier.RARE;
		Tier e = Tier.EPIC;
		Tier l = Tier.LEGENDARY;
		switch (this) {
			case GROCER -> b
					.sell(c, Items.BREAD, 4, 1).sell(c, Items.BAKED_POTATO, 8, 1).sell(c, Items.COOKED_BEEF, 3, 1)
					.sell(c, Items.COOKED_PORKCHOP, 3, 1).sell(c, Items.COOKED_CHICKEN, 4, 1).sell(c, Items.APPLE, 8, 1)
					.sell(c, Items.COOKIE, 12, 1).sell(c, Items.CARROT, 16, 1)
					.sell(u, Items.PUMPKIN_PIE, 3, 1).sell(u, Items.CAKE, 1, 3).sell(u, Items.HONEY_BOTTLE, 2, 1)
					.sell(c, Items.COOKED_SALMON, 4, 1).sell(u, Items.MUSHROOM_STEW, 1, 1)
					.sell(r, Items.GOLDEN_CARROT, 3, 2).sell(u, Items.RABBIT_STEW, 1, 2).sell(r, Items.GOLDEN_APPLE, 1, 8)
					.sell(r, Items.GLISTERING_MELON_SLICE, 2, 3).sell(e, Items.HONEY_BLOCK, 1, 4)
					.sell(l, Items.ENCHANTED_GOLDEN_APPLE, 1, 45)
					.buy(c, Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE, Items.PUMPKIN,
							Items.SWEET_BERRIES, Items.EGG)
					.buy(u, Items.BEEF, Items.CHICKEN, Items.PORKCHOP, Items.MUTTON, Items.HONEY_BOTTLE, Items.COCOA_BEANS)
					.buy(r, Items.GLOW_BERRIES, Items.RABBIT);
			case ARMORER -> {
				b.sell(c, Items.ARROW, 16, 1).sell(c, Items.SHIELD, 1, 3).sell(c, Items.IRON_HELMET, 1, 5)
						.sell(c, Items.IRON_BOOTS, 1, 4).sell(c, Items.CHAINMAIL_CHESTPLATE, 1, 5)
						.sell(c, ModItems.weapon(WeaponType.DAGGER, "iron"), 1, 4)
						.sell(c, ModItems.weapon(WeaponType.SPEAR, "iron"), 1, 5)
						.sell(c, ModItems.JAVELIN, 4, 2);
				b.sell(u, Items.IRON_CHESTPLATE, 1, 9).sell(u, Items.IRON_LEGGINGS, 1, 7).sell(u, Items.CROSSBOW, 1, 3)
						.sell(u, ModItems.LONGBOW, 1, 6).sell(u, ModItems.BALLISTA_BOLT, 4, 2);
				for (WeaponType weapon : new WeaponType[] {WeaponType.LONGSWORD, WeaponType.HALBERD, WeaponType.WARHAMMER,
						WeaponType.FLAIL, WeaponType.BATTLE_AXE}) {
					b.sell(u, ModItems.weapon(weapon, "iron"), 1, 3 + weapon.damage);
				}
				b.sell(r, ModItems.MUSKET, 1, 20).sell(r, Items.DIAMOND_SWORD, 1, 18).sell(r, Items.DIAMOND_HELMET, 1, 20).sell(r, Items.DIAMOND_BOOTS, 1, 18)
						.sell(r, Items.IRON_HORSE_ARMOR, 1, 6).sell(r, ModItems.FIRE_POT, 2, 3)
						.sell(r, ModItems.weapon(WeaponType.DAGGER, "diamond"), 1, 16)
						.sell(r, ModItems.weapon(WeaponType.SPEAR, "diamond"), 1, 18)
						.sell(r, ModItems.weapon(WeaponType.LONGSWORD, "diamond"), 1, 22);
				b.sell(e, Items.DIAMOND_CHESTPLATE, 1, 30).sell(e, Items.DIAMOND_LEGGINGS, 1, 26)
						.sell(e, Items.DIAMOND_HORSE_ARMOR, 1, 20)
						.sell(e, ModItems.weapon(WeaponType.HALBERD, "diamond"), 1, 24)
						.sell(e, ModItems.weapon(WeaponType.WARHAMMER, "diamond"), 1, 24)
						.sell(e, ModItems.weapon(WeaponType.FLAIL, "diamond"), 1, 23)
						.sell(e, ModItems.weapon(WeaponType.BATTLE_AXE, "diamond"), 1, 26);
				b.sell(l, Items.NETHERITE_SWORD, 1, 70).sell(l, Items.NETHERITE_CHESTPLATE, 1, 90)
						.sell(l, ModItems.weapon(WeaponType.LONGSWORD, "netherite"), 1, 75)
						.sell(l, ModItems.weapon(WeaponType.HALBERD, "netherite"), 1, 80)
						.sell(l, Items.TRIDENT, 1, 40);
				// armaduras de las facciones: cuanto más rango, más rara (y más nivel de ciudad hace falta)
				for (com.evolvedwarfare.armor.FactionArmor armor : com.evolvedwarfare.armor.FactionArmor.values()) {
					Tier tier = switch (armor.tier) {
						case 1 -> c;
						case 2 -> u;
						case 3 -> r;
						default -> e;
					};
					int[] prices = switch (armor.tier) {
						case 1 -> new int[] {3, 5, 4, 3};
						case 2 -> new int[] {5, 8, 7, 5};
						case 3 -> new int[] {8, 13, 11, 8};
						default -> new int[] {16, 26, 22, 16};
					};
					List<Item> pieces = ModItems.FACTION_ARMOR.get(armor);
					for (int i = 0; i < pieces.size(); i++) {
						b.sell(tier, pieces.get(i), 1, prices[i]);
					}
				}
				b.buy(c, Items.COAL, Items.IRON_INGOT, Items.FLINT, Items.LEATHER, Items.STRING, Items.FEATHER)
						.buy(c, Items.RAW_IRON).buy(u, Items.GOLD_INGOT, Items.CHARCOAL)
						.buy(r, Items.DIAMOND)
						.buy(e, Items.NETHERITE_SCRAP, 1, 10);
			}
			case MASON -> b
					.sell(c, Items.STONE_BRICKS, 16, 1).sell(c, Items.POLISHED_ANDESITE, 16, 1).sell(c, Items.BRICKS, 10, 1)
					.sell(c, Items.SMOOTH_STONE, 12, 1).sell(c, Items.GLASS, 8, 1).sell(c, Items.TORCH, 32, 1)
					.sell(u, Items.CHISELED_STONE_BRICKS, 8, 1).sell(u, Items.TERRACOTTA, 8, 1)
					.sell(u, Items.DEEPSLATE_TILES, 12, 1).sell(u, Items.LANTERN, 2, 1).sell(u, Items.QUARTZ_BLOCK, 1, 1)
					.sell(u, Items.MUD_BRICKS, 16, 1)
					.sell(r, Items.OBSIDIAN, 4, 3).sell(r, Items.POLISHED_BLACKSTONE_BRICKS, 12, 2)
					.sell(r, Items.PRISMARINE_BRICKS, 4, 2).sell(r, Items.SEA_LANTERN, 1, 2).sell(r, Items.CALCITE, 8, 2)
					.sell(e, Items.CRYING_OBSIDIAN, 2, 3).sell(e, Items.END_STONE_BRICKS, 8, 4).sell(e, Items.PURPUR_BLOCK, 8, 5)
					.sell(e, Items.GILDED_BLACKSTONE, 1, 4)
					.sell(l, Items.LODESTONE, 1, 50).sell(l, Items.BEACON, 1, 64)
					.buy(c, Items.COBBLESTONE, Items.STONE, Items.SAND, Items.GRAVEL, Items.CLAY_BALL, Items.COBBLED_DEEPSLATE)
					.buy(u, Items.SANDSTONE, Items.STONE_BRICKS, Items.TUFF_BRICKS, Items.POLISHED_GRANITE)
					.buy(r, Items.OBSIDIAN, 4, 1);
			case JEWELER -> {
				b.sell(c, Items.GOLD_NUGGET, 18, 1).sell(c, Items.GOLD_INGOT, 2, 1).sell(c, Items.LAPIS_LAZULI, 6, 1)
						.sell(c, Items.AMETHYST_SHARD, 4, 1).sell(c, Items.COPPER_INGOT, 8, 1)
						.sell(u, Items.COMPASS, 1, 3).sell(u, Items.CLOCK, 1, 4).sell(u, Items.ENDER_PEARL, 1, 4)
						.sell(u, Items.EXPERIENCE_BOTTLE, 3, 2).sell(u, Items.GOLD_BLOCK, 1, 5);
				b.sell(u, Items.EMERALD, 1, 2);
				b.sell(r, Items.DIAMOND, 1, 6).sell(r, Items.NAME_TAG, 1, 8).sell(r, Items.GOLDEN_APPLE, 1, 9)
						.sell(r, Items.SPYGLASS, 1, 4).sell(r, Items.ENDER_EYE, 1, 5)
						.sell(e, Items.DIAMOND_BLOCK, 1, 55).sell(e, Items.ECHO_SHARD, 1, 10)
						.sell(l, Items.NETHERITE_SCRAP, 1, 20).sell(l, Items.TOTEM_OF_UNDYING, 1, 25)
						.sell(l, Items.HEART_OF_THE_SEA, 1, 32)
						.sell(l, Items.NETHERITE_INGOT, 1, 84).sell(l, Items.NETHER_STAR, 1, 60)
						.sell(l, Items.ELYTRA, 1, 80).sell(l, Items.ENCHANTED_GOLDEN_APPLE, 1, 40)
						.buy(c, Items.GOLD_INGOT, Items.RAW_GOLD, Items.QUARTZ, Items.LAPIS_LAZULI)
						.buy(u, Items.AMETHYST_SHARD, Items.REDSTONE, Items.RAW_COPPER)
						.buy(r, Items.DIAMOND).buy(r, Items.ENDER_PEARL, 2, 1)
						.buy(e, Items.ECHO_SHARD, 1, 4).buy(e, Items.NAUTILUS_SHELL, 1, 3)
						.buy(l, Items.NETHER_STAR, 1, 30).buy(l, Items.NETHERITE_INGOT, 1, 42);
			}
			case TAILOR -> {
				for (Item wool : new Item[] {Items.WHITE_WOOL, Items.RED_WOOL, Items.BLUE_WOOL, Items.YELLOW_WOOL,
						Items.GREEN_WOOL, Items.BLACK_WOOL}) {
					b.sell(c, wool, 4, 1);
				}
				b.sell(c, Items.WHITE_CARPET, 8, 1).sell(c, Items.RED_BED, 1, 2).sell(c, Items.SHEARS, 1, 2)
						.sell(c, Items.ITEM_FRAME, 2, 1)
						.sell(u, Items.WHITE_BANNER, 1, 2).sell(u, Items.RED_BANNER, 1, 2).sell(u, Items.PAINTING, 1, 2)
						.sell(u, Items.LEATHER_CHESTPLATE, 1, 3).sell(u, Items.LEAD, 1, 2)
						.sell(r, Items.SADDLE, 1, 6).sell(r, Items.NAME_TAG, 1, 8).sell(r, Items.LEATHER_HORSE_ARMOR, 1, 4)
						.sell(r, Items.GLOW_ITEM_FRAME, 2, 3)
						.sell(e, Items.GOLDEN_HORSE_ARMOR, 1, 12).sell(e, Items.FLOWER_BANNER_PATTERN, 1, 8)
						.sell(e, Items.GLOBE_BANNER_PATTERN, 1, 10)
						.sell(l, Items.PIGLIN_BANNER_PATTERN, 1, 30).sell(l, Items.MOJANG_BANNER_PATTERN, 1, 40)
						.buy(c, Items.STRING, Items.WHITE_WOOL, Items.FEATHER)
						.buy(u, Items.LEATHER, Items.RABBIT_HIDE)
						.buy(r, Items.INK_SAC, 8, 1).buy(r, Items.GLOW_INK_SAC, 4, 1)
						.buy(e, Items.PHANTOM_MEMBRANE, 2, 1);
			}
			case SPICE_MERCHANT -> b
					.sell(c, Items.SUGAR, 12, 1).sell(c, Items.COCOA_BEANS, 10, 1).sell(c, Items.RED_DYE, 6, 1)
					.sell(c, Items.ORANGE_DYE, 6, 1).sell(c, Items.YELLOW_DYE, 6, 1).sell(c, Items.DRIED_KELP, 16, 1)
					.sell(c, Items.BROWN_MUSHROOM, 6, 1)
					.sell(u, Items.GLOW_BERRIES, 10, 1).sell(u, Items.RABBIT_STEW, 1, 1).sell(u, Items.PURPLE_DYE, 4, 1)
					.sell(u, Items.FERMENTED_SPIDER_EYE, 2, 1).sell(u, Items.HONEY_BOTTLE, 2, 1)
					.sell(r, Items.BLAZE_POWDER, 2, 3).sell(r, Items.GLISTERING_MELON_SLICE, 2, 3)
					.sell(r, Items.MAGMA_CREAM, 2, 3).sell(r, Items.GHAST_TEAR, 1, 4).sell(r, Items.NETHER_WART, 8, 2)
					.sell(e, Items.DRAGON_BREATH, 1, 6).sell(e, Items.SHULKER_SHELL, 1, 12).sell(e, Items.SNIFFER_EGG, 1, 16)
					.sell(l, Items.SHULKER_BOX, 1, 30).sell(l, Items.HEART_OF_THE_SEA, 1, 40)
					.buy(c, Items.CACTUS, Items.SUGAR_CANE, Items.SWEET_BERRIES)
					.buy(u, Items.COCOA_BEANS, Items.RABBIT_HIDE, Items.NETHER_WART)
					.buy(r, Items.RABBIT_FOOT).buy(r, Items.BLAZE_ROD, 2, 1)
					.buy(e, Items.GHAST_TEAR, 1, 2);
			case FURRIER -> b
					.sell(c, Items.LEATHER, 3, 1).sell(c, Items.RABBIT_HIDE, 4, 1).sell(c, Items.WHITE_WOOL, 4, 1)
					.sell(c, Items.CAMPFIRE, 1, 1).sell(c, Items.COOKED_SALMON, 6, 1).sell(c, Items.COOKED_COD, 6, 1)
					.sell(u, Items.LEATHER_HELMET, 1, 2).sell(u, Items.LEATHER_CHESTPLATE, 1, 3)
					.sell(u, Items.LEATHER_LEGGINGS, 1, 3).sell(u, Items.LEATHER_BOOTS, 1, 2).sell(u, Items.PACKED_ICE, 4, 1)
					.sell(u, Items.LEAD, 1, 2)
					.sell(r, Items.BLUE_ICE, 1, 2).sell(r, Items.SADDLE, 1, 6).sell(r, Items.POWDER_SNOW_BUCKET, 1, 3)
					.sell(r, Items.RABBIT_FOOT, 1, 3)
					.sell(e, Items.WOLF_ARMOR, 1, 14).sell(e, Items.TURTLE_HELMET, 1, 18)
					.sell(l, Items.TOTEM_OF_UNDYING, 1, 36)
					.buy(c, Items.LEATHER, Items.COD, Items.SALMON, Items.WHITE_WOOL)
					.buy(u, Items.RABBIT_HIDE, Items.MUTTON, Items.FEATHER)
					.buy(r, Items.RABBIT_FOOT).buy(r, Items.ARMADILLO_SCUTE, 1, 1)
					.buy(e, Items.TURTLE_SCUTE, 1, 3);
		}
		return b.entries;
	}

	/**
	 * El catálogo de un comerciante concreto (siempre el mismo para la misma semilla, nivel y día): todo lo que vende
	 * primero y luego todo lo que compra, cada cosa con su lote, su precio, cuánto mueve al día y, lo que tiene
	 * durabilidad, con la {@link Quality calidad} de la ciudad (gastado, nuevo o encantado; los encantamientos cambian
	 * cada día).
	 */
	public List<Deal> catalog(int cityLevel, long seed, long day, @Nullable net.minecraft.registry.DynamicRegistryManager registries) {
		java.util.Random random = new java.util.Random(seed * 31L + ordinal());
		Quality quality = Quality.forLevel(cityLevel);
		int index = 0;
		double markup = CityLevels.priceMultiplier(cityLevel);
		Pricing pricing = pricing();
		List<Deal> sells = new ArrayList<>();
		List<Deal> buys = new ArrayList<>();
		for (Entry entry : entries()) {
			double variance = 1.0 + (random.nextDouble() * 2.0 - 1.0) * VARIANCE;
			int maxCount = maxCount(entry.item());
			Tier tier = entry.tier();
			double fair = pricing.factor()[tier.ordinal()] * entry.value();
			if (entry.sell()) {
				int[] lot = sellLot(fair * variance, entry.count(), maxCount, tier);
				int price = markup > 1.0 ? niceCeil(lot[1] * markup) : lot[1];
				ItemStack prototype = new ItemStack(entry.item());
				int grade = -1;
				if (Quality.affects(prototype)) {
					Random rolls = Random.create(seed * 961L + day * 7919L + index * 104729L + ordinal());
					prototype = quality.apply(prototype, rolls, registries);
					grade = quality.ordinal();
				}
				sells.add(new Deal(true, prototype, lot[0], price, dailyLots(tier.sellStock, lot[0]), tier, grade));
			} else {
				// la mitad del precio nuevo: lo que cuesta en la tienda más barata que lo vende (o, si nadie lo vende, lo
				// que costaría en su rareza)
				double reference = pricing.sold().getOrDefault(entry.item(), fair);
				int[] lot = buyLot(0.5 * reference * variance, entry.count(), maxCount);
				int daily = dailyLots(tier.buyStock, lot[0]);
				if (CropCrateItem.isCrop(entry.item())) {
					// los cultivos, solo en cajas llenas: una caja por lote, lo que vale lo de dentro (y un poco más por
					// venir en caja), y tantas cajas al día como cultivos sueltos movía
					int inside = CropCrateItem.capacity(entry.item());
					int price = Math.max(1, niceFloor(0.5 * reference * variance * inside * CRATE_BONUS));
					int crates = Math.max(MIN_CRATES, (int) Math.round(daily * lot[0] / (double) inside));
					buys.add(new Deal(false, CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, entry.item()), 1, price, crates, tier, -1));
				} else {
					buys.add(new Deal(false, new ItemStack(entry.item()), lot[0], lot[1], daily, tier, -1));
				}
			}
			index++;
		}
		sells.addAll(buys);
		return sells;
	}

	/** Lotes al día: los de su rareza, pero siempre los que hagan falta para mover al menos 20 unidades (configurable). */
	private static int dailyLots(int tierLots, int lotSize) {
		int minimum = Math.max(1, com.evolvedwarfare.config.EwConfig.get().traderMinDaily);
		return Math.max(tierLots, (minimum + lotSize - 1) / Math.max(1, lotSize));
	}

	// ------------------------------------------------------------------ precios

	/** Lotes "redondos" en los que se agrupa lo barato para llegar al precio mínimo de su rareza. */
	private static final int[] LOTS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64};

	/**
	 * Lo que se calcula una vez para todos: cuántas monedas nuevas vale cada moneda de valor en cada rareza, y lo más
	 * barato (por unidad, en una ciudad de nivel 1) que se vende cada cosa en cualquier tienda.
	 */
	private record Pricing(double[] factor, Map<Item, Double> sold) {
	}

	@Nullable
	private static volatile Pricing pricing;

	private static Pricing pricing() {
		Pricing p = pricing;
		if (p == null) {
			p = computePricing();
			pricing = p;
		}
		return p;
	}

	private static Pricing computePricing() {
		List<Entry> all = new ArrayList<>();
		for (TraderType type : values()) {
			all.addAll(type.entries());
		}
		double[] factor = new double[Tier.values().length];
		for (Tier tier : Tier.values()) {
			factor[tier.ordinal()] = factor(all, tier);
		}
		Map<Item, Double> sold = new HashMap<>();
		for (Entry entry : all) {
			if (entry.sell()) {
				int[] lot = sellLot(factor[entry.tier().ordinal()] * entry.value(), entry.count(), maxCount(entry.item()), entry.tier());
				sold.merge(entry.item(), lot[1] / (double) lot[0], Math::min);
			}
		}
		return new Pricing(factor, sold);
	}

	/**
	 * Monedas nuevas por moneda de valor en una rareza: la que menos tiene que recortar (para meterlas en el rango) a las
	 * cosas que se venden de una en una, como armas y armaduras; a igualdad, la más alta.
	 */
	private static double factor(List<Entry> all, Tier tier) {
		List<Double> values = new ArrayList<>();
		for (Entry entry : all) {
			if (entry.sell() && entry.tier() == tier && maxCount(entry.item()) == 1) {
				values.add(entry.value());
			}
		}
		if (values.isEmpty()) {
			for (Entry entry : all) {
				if (entry.sell() && entry.tier() == tier) {
					values.add(entry.value());
				}
			}
		}
		if (values.isEmpty()) {
			return 1.0;
		}
		double min = tier.minPrice;
		double max = tier.maxPrice;
		double best = max / values.get(0);
		double bestCost = Double.MAX_VALUE;
		List<Double> candidates = new ArrayList<>();
		for (double value : values) {
			candidates.add(min / value);
			candidates.add(max / value);
		}
		candidates.sort(null);
		for (double candidate : candidates) {
			double cost = 0.0;
			for (double value : values) {
				double price = candidate * value;
				cost += Math.abs(Math.log(Math.min(max, Math.max(min, price)) / price));
			}
			if (cost <= bestCost + 1.0E-9) {
				bestCost = cost;
				best = candidate;
			}
		}
		return best;
	}

	private static int maxCount(Item item) {
		return Math.max(1, Math.min(64, item.getMaxCount()));
	}

	/**
	 * Lote y precio de algo que vende: el lote de siempre, más grande si hace falta para llegar al mínimo de su rareza
	 * (o más pequeño si se pasa del máximo), y el precio redondeado hacia arriba y dentro del rango.
	 *
	 * @return {lote, monedas}
	 */
	private static int[] sellLot(double unit, int count, int maxCount, Tier tier) {
		int min = tier.minPrice;
		int max = tier.maxPrice;
		count = Math.min(count, maxCount);
		if (unit * count < min) {
			int bigger = count;
			for (int lot : LOTS) {
				if (lot > count && lot <= maxCount) {
					bigger = lot;
					if (unit * lot >= min) {
						break;
					}
				}
			}
			count = bigger;
		} else if (unit * count > max && count > 1) {
			int smaller = 1;
			for (int lot : LOTS) {
				if (lot < count && unit * lot <= max) {
					smaller = lot;
				}
			}
			count = smaller;
		}
		int price = Math.min(max, Math.max(min, niceCeil(Math.max(unit * count, min))));
		return new int[] {count, price};
	}

	/**
	 * Lote y precio de algo que compra: el lote de siempre (más grande si no llega a una moneda) y el precio redondeado
	 * hacia abajo.
	 *
	 * @return {lote, monedas}
	 */
	private static int[] buyLot(double unit, int count, int maxCount) {
		count = Math.min(count, maxCount);
		if (niceFloor(unit * count) < 1) {
			int bigger = count;
			for (int lot : LOTS) {
				if (lot > count && lot <= maxCount) {
					bigger = lot;
					if (niceFloor(unit * lot) >= 1) {
						break;
					}
				}
			}
			count = bigger;
		}
		return new int[] {count, Math.max(1, niceFloor(unit * count))};
	}

	/** De cuánto en cuánto se redondea un precio: 1 hasta 100, 5 hasta 1.000, 50 hasta 10.000, 500 hasta 100.000... */
	private static long step(double price) {
		if (price < 100) {
			return 1;
		}
		if (price < 1000) {
			return 5;
		}
		if (price < 10000) {
			return 50;
		}
		if (price < 100000) {
			return 500;
		}
		return 5000;
	}

	static int niceCeil(double price) {
		long step = step(price);
		return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(price / step - 1.0E-9) * step);
	}

	static int niceFloor(double price) {
		long step = step(price);
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) Math.floor(price / step + 1.0E-9) * step));
	}
}
