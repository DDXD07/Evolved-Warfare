package com.evolvedwarfare.research;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * El árbol de investigación. Cada técnica está en una columna (nivel 1, 2 o 3) y una fila del dibujo, pide las
 * anteriores, cuesta unos materiales (se gastan al empezar) y tarda unos minutos en la mesa de investigación. Desbloquea
 * la fabricación de algunos objetos o da una mejora a tus soldados.
 */
public enum Tech {
	MILITARY_SMITHING(0, 0, 3, null, Items.IRON_INGOT, 10, Items.BOOK, 1),
	HEAVY_WEAPONS(1, 0, 6, MILITARY_SMITHING, Items.IRON_INGOT, 16, Items.DIAMOND, 2, Items.BOOK, 2),
	POLEARMS(2, 0, 9, HEAVY_WEAPONS, Items.IRON_INGOT, 24, Items.DIAMOND, 4, Items.BOOK, 3),
	ARCHERY(0, 1, 3, null, Items.STRING, 16, Items.FEATHER, 12, Items.IRON_INGOT, 4),
	SIEGE_CARPENTRY(0, 2, 3, null, ItemTags.PLANKS, 32, ItemTags.LOGS, 16, Items.IRON_INGOT, 4),
	TORSION(1, 2, 6, SIEGE_CARPENTRY, Items.STRING, 24, Items.IRON_INGOT, 12, ItemTags.PLANKS, 16),
	COUNTERWEIGHT(2, 2, 9, TORSION, Items.IRON_BLOCK, 2, Items.COBBLESTONE, 64, Items.BOOK, 2),
	INCENDIARIES(2, 3, 6, TORSION, Items.FIRE_CHARGE, 6, Items.COAL, 16, Items.BRICK, 8),
	FORTIFICATION(0, 4, 3, null, Items.STONE_BRICKS, 32, Items.IRON_INGOT, 8, Items.CHAIN, 4),
	DRILL(0, 5, 4, null, Items.BOOK, 1, Items.BREAD, 16, Items.IRON_INGOT, 8),
	TACTICS(1, 5, 7, DRILL, Items.BOOK, 2, Items.DIAMOND, 1, Items.GOLD_INGOT, 8),
	VETERANS(2, 5, 10, TACTICS, Items.DIAMOND, 4, Items.BOOK, 3, Items.EMERALD, 16),
	FIELD_MEDICINE(1, 6, 5, DRILL, Items.GLISTERING_MELON_SLICE, 4, Items.PAPER, 16, Items.GOLDEN_APPLE, 1);

	/** Un material del coste: un objeto concreto o cualquiera de una etiqueta (tablones, troncos). */
	public record Cost(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
		public boolean matches(ItemStack stack) {
			return item != null ? stack.isOf(item) : tag != null && stack.isIn(tag);
		}

		/** Lo que se dibuja en la lista del coste. */
		public Item icon() {
			if (item != null) {
				return item;
			}
			return tag == ItemTags.LOGS ? Items.OAK_LOG : Items.OAK_PLANKS;
		}
	}

	public static final int COLUMNS = 3;
	public static final int ROWS = 7;

	public final int column;
	public final int row;
	public final int minutes;
	@Nullable
	public final Tech requires;
	private final List<Cost> cost = new ArrayList<>();

	Tech(int column, int row, int minutes, @Nullable Tech requires, Object... cost) {
		this.column = column;
		this.row = row;
		this.minutes = minutes;
		this.requires = requires;
		for (int i = 0; i + 1 < cost.length; i += 2) {
			Object what = cost[i];
			int count = (Integer) cost[i + 1];
			if (what instanceof Item item) {
				this.cost.add(new Cost(item, null, count));
			} else {
				@SuppressWarnings("unchecked")
				TagKey<Item> tag = (TagKey<Item>) what;
				this.cost.add(new Cost(null, tag, count));
			}
		}
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "research.evolvedwarfare." + id();
	}

	public List<Cost> cost() {
		return cost;
	}

	/** Tics a velocidad normal. */
	public int ticks() {
		return minutes * 1200;
	}

	/** ¿Da una mejora a los soldados en vez de desbloquear objetos? */
	public boolean isBonus() {
		return this == DRILL || this == TACTICS || this == VETERANS || this == FIELD_MEDICINE;
	}

	/** Los objetos que no se pueden fabricar hasta tenerla. */
	public List<Item> unlocks() {
		List<Item> out = new ArrayList<>();
		switch (this) {
			case MILITARY_SMITHING -> {
				out.addAll(ModItems.WEAPONS.get(WeaponType.DAGGER));
				out.addAll(ModItems.WEAPONS.get(WeaponType.SPEAR));
				out.add(ModItems.JAVELIN);
			}
			case HEAVY_WEAPONS -> {
				out.addAll(ModItems.WEAPONS.get(WeaponType.LONGSWORD));
				out.addAll(ModItems.WEAPONS.get(WeaponType.WARHAMMER));
				out.addAll(ModItems.WEAPONS.get(WeaponType.FLAIL));
				out.addAll(ModItems.WEAPONS.get(WeaponType.BATTLE_AXE));
			}
			case POLEARMS -> out.addAll(ModItems.WEAPONS.get(WeaponType.HALBERD));
			case ARCHERY -> out.add(ModItems.LONGBOW);
			case SIEGE_CARPENTRY -> out.add(ModItems.BATTERING_RAM);
			case TORSION -> {
				out.add(ModItems.CATAPULT);
				out.add(ModItems.BALLISTA);
				out.add(ModItems.BALLISTA_BOLT);
			}
			case COUNTERWEIGHT -> out.add(ModItems.TREBUCHET);
			case INCENDIARIES -> out.add(ModItems.FIRE_POT);
			case FORTIFICATION -> {
				out.add(ModBlocks.PORTCULLIS.asItem());
				out.add(ModBlocks.DRAWBRIDGE.asItem());
				out.add(ModBlocks.GATE_WINCH.asItem());
			}
			default -> {
			}
		}
		return out;
	}

	/** El dibujo del nodo. */
	public Item icon() {
		Supplier<Item> icon = switch (this) {
			case MILITARY_SMITHING -> () -> ModItems.weapon(WeaponType.SPEAR, "iron");
			case HEAVY_WEAPONS -> () -> ModItems.weapon(WeaponType.LONGSWORD, "iron");
			case POLEARMS -> () -> ModItems.weapon(WeaponType.HALBERD, "iron");
			case ARCHERY -> () -> ModItems.LONGBOW;
			case SIEGE_CARPENTRY -> () -> ModItems.BATTERING_RAM;
			case TORSION -> () -> ModItems.CATAPULT;
			case COUNTERWEIGHT -> () -> ModItems.TREBUCHET;
			case INCENDIARIES -> () -> ModItems.FIRE_POT;
			case FORTIFICATION -> () -> ModBlocks.PORTCULLIS.asItem();
			case DRILL -> () -> Items.SHIELD;
			case TACTICS -> () -> ModItems.COMMAND_STAFF;
			case VETERANS -> () -> Items.EXPERIENCE_BOTTLE;
			case FIELD_MEDICINE -> () -> ModItems.MEDIC_KIT;
		};
		return icon.get();
	}

	@Nullable
	public static Tech byId(String id) {
		for (Tech tech : values()) {
			if (tech.id().equals(id)) {
				return tech;
			}
		}
		return null;
	}
}
