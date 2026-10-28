package com.evolvedwarfare.siege;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModItems;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;

/** Munición de las máquinas: piedras (cualquier piedra de la etiqueta), vasijas incendiarias y virotes de balista. */
public enum SiegeAmmo {
	STONE,
	FIRE_POT,
	BOLT;

	public static final TagKey<Item> STONES = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("siege_stones"));
	/** Lo que cabe de cada munición dentro de una máquina. */
	public static final int CAPACITY = 256;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public boolean matches(ItemStack stack) {
		return switch (this) {
			case STONE -> stack.isIn(STONES);
			case FIRE_POT -> stack.isOf(ModItems.FIRE_POT);
			case BOLT -> stack.isOf(ModItems.BALLISTA_BOLT);
		};
	}

	@Nullable
	public static SiegeAmmo of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (SiegeAmmo ammo : values()) {
			if (ammo.matches(stack)) {
				return ammo;
			}
		}
		return null;
	}

	public boolean usableBy(SiegeKind kind) {
		return switch (kind) {
			case CATAPULT, TREBUCHET -> this == STONE || this == FIRE_POT;
			case BALLISTA -> this == BOLT;
			case RAM -> false;
		};
	}

	/** Cuántas unidades gasta un disparo (el trebuchet lanza tres piedras de golpe). */
	public int cost(SiegeKind kind) {
		return kind == SiegeKind.TREBUCHET && this == STONE ? 3 : 1;
	}

	/** Objeto que se devuelve al desmontar la máquina con munición dentro. */
	public Item item() {
		return switch (this) {
			case STONE -> Items.COBBLESTONE;
			case FIRE_POT -> ModItems.FIRE_POT;
			case BOLT -> ModItems.BALLISTA_BOLT;
		};
	}

	public String translationKey() {
		return "ammo.evolvedwarfare." + id();
	}

	/** Qué proyectil sale de esta máquina con esta munición. */
	public SiegeShotEntity.Kind shotFor(SiegeKind kind) {
		return switch (this) {
			case STONE -> kind == SiegeKind.TREBUCHET ? SiegeShotEntity.Kind.BOULDER : SiegeShotEntity.Kind.STONE;
			case FIRE_POT -> SiegeShotEntity.Kind.FIRE_POT;
			case BOLT -> SiegeShotEntity.Kind.BOLT;
		};
	}
}
