package com.evolvedwarfare.orchard;

import com.evolvedwarfare.climate.Season;
import com.evolvedwarfare.registry.ModItems;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Los árboles frutales: qué fruta dan y en qué estaciones florecen y maduran. */
public enum FruitKind {
	APPLE("apple", () -> Items.APPLE, Kind.TEMPERATE),
	PEAR("pear", () -> ModItems.PEAR, Kind.TEMPERATE),
	PLUM("plum", () -> ModItems.PLUM, Kind.TEMPERATE),
	PEACH("peach", () -> ModItems.PEACH, Kind.TEMPERATE),
	LEMON("lemon", () -> ModItems.LEMON, Kind.CITRUS),
	ORANGE("orange", () -> ModItems.ORANGE, Kind.CITRUS);

	/** Los frutales de clima templado maduran de primavera a otoño; los cítricos, de otoño a primavera. */
	public enum Kind {
		TEMPERATE(EnumSet.of(Season.SPRING, Season.SUMMER, Season.AUTUMN)),
		CITRUS(EnumSet.of(Season.AUTUMN, Season.WINTER, Season.SPRING));

		public final Set<Season> seasons;

		Kind(Set<Season> seasons) {
			this.seasons = seasons;
		}
	}

	public final String id;
	private final Supplier<Item> fruit;
	public final Kind kind;

	FruitKind(String id, Supplier<Item> fruit, Kind kind) {
		this.id = id;
		this.fruit = fruit;
		this.kind = kind;
	}

	public Item fruit() {
		return fruit.get();
	}
}
