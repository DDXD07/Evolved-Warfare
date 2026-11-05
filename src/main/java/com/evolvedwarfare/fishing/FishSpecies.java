package com.evolvedwarfare.fishing;

import com.evolvedwarfare.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Los peces: dónde viven, lo difíciles que son de sacar (0-100), cómo se mueven en el minijuego, lo que pesan (kg) y lo
 * que se paga por uno de peso medio (monedas, en el buzón).
 */
public enum FishSpecies {
	COD(() -> Items.COD, Water.OCEAN, 25, Behavior.MIXED, 0.6, 4.0, 0.5, 30),
	SALMON(() -> Items.SALMON, Water.COLD, 40, Behavior.DART, 1.5, 8.0, 0.7, 25),
	TROPICAL_FISH(() -> Items.TROPICAL_FISH, Water.WARM, 35, Behavior.SMOOTH, 0.1, 0.8, 0.8, 20),
	PUFFERFISH(() -> Items.PUFFERFISH, Water.WARM, 55, Behavior.FLOATER, 0.3, 1.5, 1.0, 12),
	PERCH(() -> ModItems.PERCH, Water.FRESH, 20, Behavior.SMOOTH, 0.2, 1.8, 0.5, 30),
	CARP(() -> ModItems.CARP, Water.FRESH, 15, Behavior.SMOOTH, 1.0, 12.0, 0.5, 26),
	TROUT(() -> ModItems.TROUT, Water.FRESH_COLD, 45, Behavior.DART, 0.5, 5.0, 0.9, 18),
	PIKE(() -> ModItems.PIKE, Water.FRESH, 60, Behavior.DART, 1.5, 14.0, 1.3, 10),
	EEL(() -> ModItems.EEL, Water.SWAMP, 70, Behavior.SINKER, 0.5, 4.0, 1.6, 10),
	TUNA(() -> ModItems.TUNA, Water.DEEP_OCEAN, 75, Behavior.MIXED, 20.0, 180.0, 2.6, 8),
	SWORDFISH(() -> ModItems.SWORDFISH, Water.WARM, 90, Behavior.DART, 30.0, 250.0, 5.0, 2),
	STURGEON(() -> ModItems.STURGEON, Water.FRESH, 85, Behavior.SINKER, 10.0, 120.0, 4.0, 2);

	/** Cómo se mueve en la barra: a sacudidas, suave, a saltos, hacia el fondo o hacia arriba. */
	public enum Behavior {
		MIXED, SMOOTH, DART, SINKER, FLOATER
	}

	/** Dónde vive. */
	public enum Water {
		OCEAN, DEEP_OCEAN, COLD, WARM, FRESH, FRESH_COLD, SWAMP
	}

	private final Supplier<Item> item;
	public final Water water;
	public final int difficulty;
	public final Behavior behavior;
	public final double minKg;
	public final double maxKg;
	public final double price;
	/** Lo común que es (peso en el sorteo). */
	public final int weight;

	FishSpecies(Supplier<Item> item, Water water, int difficulty, Behavior behavior, double minKg, double maxKg, double price, int weight) {
		this.item = item;
		this.water = water;
		this.difficulty = difficulty;
		this.behavior = behavior;
		this.minKg = minKg;
		this.maxKg = maxKg;
		this.price = price;
		this.weight = weight;
	}

	public Item item() {
		return item.get();
	}

	public double averageKg() {
		return (minKg + maxKg) / 2.0;
	}

	@Nullable
	public static FishSpecies of(Item item) {
		for (FishSpecies s : values()) {
			if (s.item() == item) {
				return s;
			}
		}
		return null;
	}
}
