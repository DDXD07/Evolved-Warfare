package com.evolvedwarfare.armor;

import java.util.Locale;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Los materiales de las armaduras de clase, de menos a más: cuero, cobre, hierro, diamante y netherita. Protegen como
 * las de vanilla (el cobre, entre el cuero y el hierro) y cada uno sube un escalón ({@link #level}) las ventajas de la
 * clase: con cuero, +5 %; con cobre, +10 %; ... con netherita, +25 % (en el arquero, por ejemplo).
 */
public enum ArmorTier {
	LEATHER(1, new int[] {1, 3, 2, 1}, 5, 15, 0.0F, 0.0F, Items.LEATHER),
	COPPER(2, new int[] {2, 4, 3, 1}, 11, 13, 0.0F, 0.0F, Items.COPPER_INGOT),
	IRON(3, new int[] {2, 6, 5, 2}, 15, 9, 0.0F, 0.0F, Items.IRON_INGOT),
	DIAMOND(4, new int[] {3, 8, 6, 3}, 33, 10, 2.0F, 0.0F, Items.DIAMOND),
	NETHERITE(5, new int[] {3, 8, 6, 3}, 37, 15, 3.0F, 0.1F, Items.NETHERITE_INGOT);

	/** Escalón (1 cuero ... 5 netherita): multiplica las ventajas de la clase. */
	public final int level;
	/** Protección: casco, peto, grebas, botas. */
	private final int[] protection;
	public final int durability;
	public final int enchantability;
	public final float toughness;
	public final float knockbackResistance;
	/** Con qué se fabrica y se repara (la de netherita se mejora desde la de diamante en la mesa de herrería). */
	public final Item material;

	ArmorTier(int level, int[] protection, int durability, int enchantability, float toughness, float knockbackResistance, Item material) {
		this.level = level;
		this.protection = protection;
		this.durability = durability;
		this.enchantability = enchantability;
		this.toughness = toughness;
		this.knockbackResistance = knockbackResistance;
		this.material = material;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int protection(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> protection[0];
			case CHESTPLATE, BODY -> protection[1];
			case LEGGINGS -> protection[2];
			case BOOTS -> protection[3];
		};
	}

	/** Cuero y cobre cuentan como armadura ligera para el árbol de habilidades; el resto, pesada. */
	public boolean light() {
		return this == LEATHER || this == COPPER;
	}

	public RegistryEntry<SoundEvent> equipSound() {
		return switch (this) {
			case LEATHER -> SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
			case COPPER -> SoundEvents.ITEM_ARMOR_EQUIP_CHAIN;
			case IRON -> SoundEvents.ITEM_ARMOR_EQUIP_IRON;
			case DIAMOND -> SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND;
			case NETHERITE -> SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
		};
	}
}
