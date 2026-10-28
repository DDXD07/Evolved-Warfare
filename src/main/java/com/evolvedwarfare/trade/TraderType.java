package com.evolvedwarfare.trade;

import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import com.evolvedwarfare.world.CityLevels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Los oficios de los comerciantes de las ciudades y su catálogo completo. Todo se paga en Golden Coins (o esmeraldas si
 * no está el mod de la moneda).
 *
 * <p>Cada línea tiene una rareza ({@link Tier}); las ciudades de más nivel (más lejos del punto de aparición)
 * desbloquean las más raras, y además venden más caro: cada nivel por encima del 1 sube los precios de venta un 10 %
 * (configurable). Lo que te compran lo pagan al precio del mercado ({@link Market#priceOf}) en todas partes.
 * Cada comerciante tiene sus propios precios (±10 %), así que conviene comparar.</p>
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

	/** Una línea antes de poner precio: {@code value} monedas por unidad (a nivel 1). */
	private record Entry(boolean sell, Item item, double value, Tier tier) {
	}

	private static final class Builder {
		final List<Entry> entries = new ArrayList<>();

		/** Vende {@code count} por {@code coins}. */
		Builder sell(Tier tier, ItemConvertible item, int count, int coins) {
			entries.add(new Entry(true, item.asItem(), coins / (double) count, tier));
			return this;
		}

		/** Compra al precio del mercado. */
		Builder buy(Tier tier, ItemConvertible... items) {
			for (ItemConvertible item : items) {
				Market.Price price = Market.priceOf(new ItemStack(item));
				if (price != null) {
					entries.add(new Entry(false, item.asItem(), price.coins() / (double) price.items(), tier));
				}
			}
			return this;
		}

		/** Compra {@code count} por {@code coins} (lo que no está en el mercado). */
		Builder buy(Tier tier, ItemConvertible item, int count, int coins) {
			entries.add(new Entry(false, item.asItem(), coins / (double) count, tier));
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
					.sell(u, Items.COOKED_SALMON, 4, 1).sell(u, Items.MUSHROOM_STEW, 1, 1)
					.sell(r, Items.GOLDEN_CARROT, 3, 2).sell(r, Items.RABBIT_STEW, 1, 2).sell(r, Items.GOLDEN_APPLE, 1, 8)
					.sell(e, Items.HONEY_BLOCK, 1, 4).sell(e, Items.GLISTERING_MELON_SLICE, 2, 3)
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
				b.sell(r, Items.DIAMOND_SWORD, 1, 18).sell(r, Items.DIAMOND_HELMET, 1, 20).sell(r, Items.DIAMOND_BOOTS, 1, 18)
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
				b.buy(c, Items.COAL, Items.IRON_INGOT, Items.FLINT, Items.LEATHER, Items.STRING, Items.FEATHER)
						.buy(u, Items.RAW_IRON, Items.GOLD_INGOT, Items.CHARCOAL)
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
				if (Market.currency() != Items.EMERALD) {
					b.sell(u, Items.EMERALD, 1, 2);
				}
				b.sell(r, Items.DIAMOND, 1, 6).sell(r, Items.NAME_TAG, 1, 8).sell(r, Items.GOLDEN_APPLE, 1, 9)
						.sell(r, Items.SPYGLASS, 1, 4).sell(r, Items.ENDER_EYE, 1, 5)
						.sell(e, Items.DIAMOND_BLOCK, 1, 55).sell(e, Items.TOTEM_OF_UNDYING, 1, 25)
						.sell(e, Items.HEART_OF_THE_SEA, 1, 32).sell(e, Items.NETHERITE_SCRAP, 1, 20)
						.sell(e, Items.ECHO_SHARD, 1, 10)
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
	 * El catálogo de un comerciante concreto (siempre el mismo para la misma semilla y nivel): todo lo que vende primero
	 * y luego todo lo que compra, cada cosa con su lote, su precio y cuánto mueve al día.
	 */
	public List<Deal> catalog(int cityLevel, long seed) {
		java.util.Random random = new java.util.Random(seed * 31L + ordinal());
		double markup = CityLevels.priceMultiplier(cityLevel);
		List<Deal> sells = new ArrayList<>();
		List<Deal> buys = new ArrayList<>();
		for (Entry entry : entries()) {
			double variance = 1.0 + (random.nextDouble() * 2.0 - 1.0) * VARIANCE;
			int maxCount = Math.min(64, entry.item().getMaxCount());
			int minLevel = entry.tier().minLevel();
			if (entry.sell()) {
				int[] fit = fit(entry.value() * variance * markup, maxCount, true);
				sells.add(new Deal(true, entry.item(), fit[0], fit[1], entry.tier().sellStock, entry.tier(), minLevel));
			} else {
				int[] fit = fit(entry.value() * variance, maxCount, false);
				buys.add(new Deal(false, entry.item(), fit[0], fit[1], entry.tier().buyStock, entry.tier(), minLevel));
			}
		}
		sells.addAll(buys);
		return sells;
	}

	/**
	 * Lote y precio que mejor se ajustan a {@code coinsPerItem}, con números sencillos ("4 por 1", no "7 por 2").
	 * Al vender redondea a favor del comerciante; al comprar, al más cercano.
	 *
	 * @return {lote, monedas}
	 */
	private static int[] fit(double coinsPerItem, int maxCount, boolean sell) {
		int bestCount = 1;
		int bestCoins = Math.max(1, (int) Math.round(coinsPerItem));
		double bestScore = Double.MAX_VALUE;
		for (int count = 1; count <= maxCount; count++) {
			double exact = count * coinsPerItem;
			int coins = sell ? (int) Math.ceil(exact - 1.0E-9) : (int) Math.round(exact);
			if (coins < 1) {
				continue;
			}
			double error = Math.abs(coins / (double) count - coinsPerItem) / coinsPerItem;
			// premia los precios exactos, pero sin agrupar cosas caras en lotes raros
			double score = error + 0.04 * Math.min(coins - 1, 20) + 0.002 * count;
			if (score < bestScore) {
				bestScore = score;
				bestCount = count;
				bestCoins = coins;
			}
		}
		return new int[] {bestCount, bestCoins};
	}
}
