package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Las piezas que se forjan en el yunque: lo que cuestan (lingotes al rojo), los golpes que llevan, el mango que piden al
 * montarlas en la mesa (palos, y una empuñadura de cuero o cuerda, o una cadena) y en qué se convierten.
 */
public enum ForgedPart {
	SWORD_BLADE("sword_blade", 2, 6, 1, Extra.GRIP, "sword", null),
	PICKAXE_HEAD("pickaxe_head", 3, 7, 2, Extra.NONE, "pickaxe", null),
	AXE_HEAD("axe_head", 3, 7, 2, Extra.NONE, "axe", null),
	SHOVEL_HEAD("shovel_head", 1, 4, 2, Extra.NONE, "shovel", null),
	HOE_HEAD("hoe_head", 2, 5, 2, Extra.NONE, "hoe", null),
	DAGGER_BLADE("dagger_blade", 1, 5, 1, Extra.GRIP, null, WeaponType.DAGGER),
	SPEARHEAD("spearhead", 1, 5, 2, Extra.NONE, null, WeaponType.SPEAR),
	LONGSWORD_BLADE("longsword_blade", 3, 8, 1, Extra.GRIP, null, WeaponType.LONGSWORD),
	HALBERD_HEAD("halberd_head", 3, 8, 2, Extra.NONE, null, WeaponType.HALBERD),
	WARHAMMER_HEAD("warhammer_head", 4, 8, 2, Extra.NONE, null, WeaponType.WARHAMMER),
	FLAIL_HEAD("flail_head", 3, 7, 1, Extra.CHAIN, null, WeaponType.FLAIL),
	BATTLE_AXE_HEAD("battle_axe_head", 4, 9, 2, Extra.NONE, null, WeaponType.BATTLE_AXE);

	/** Lo que pide el mango además de los palos. */
	public enum Extra {
		NONE, GRIP, CHAIN
	}

	public final String id;
	public final int cost;
	public final int strikes;
	public final int sticks;
	public final Extra extra;
	@Nullable
	private final String vanillaTool;
	@Nullable
	private final WeaponType weapon;

	ForgedPart(String id, int cost, int strikes, int sticks, Extra extra, @Nullable String vanillaTool, @Nullable WeaponType weapon) {
		this.id = id;
		this.cost = cost;
		this.strikes = strikes;
		this.sticks = sticks;
		this.extra = extra;
		this.vanillaTool = vanillaTool;
		this.weapon = weapon;
	}

	/** La herramienta o el arma que sale con este metal (null si no existe: no hay armas medievales de oro). */
	@Nullable
	public Item result(ForgeMaterial material) {
		if (vanillaTool != null) {
			String prefix = material == ForgeMaterial.GOLD ? "golden" : material.id;
			return switch (prefix + "_" + vanillaTool) {
				case "iron_sword" -> Items.IRON_SWORD;
				case "iron_pickaxe" -> Items.IRON_PICKAXE;
				case "iron_axe" -> Items.IRON_AXE;
				case "iron_shovel" -> Items.IRON_SHOVEL;
				case "iron_hoe" -> Items.IRON_HOE;
				case "golden_sword" -> Items.GOLDEN_SWORD;
				case "golden_pickaxe" -> Items.GOLDEN_PICKAXE;
				case "golden_axe" -> Items.GOLDEN_AXE;
				case "golden_shovel" -> Items.GOLDEN_SHOVEL;
				case "golden_hoe" -> Items.GOLDEN_HOE;
				case "diamond_sword" -> Items.DIAMOND_SWORD;
				case "diamond_pickaxe" -> Items.DIAMOND_PICKAXE;
				case "diamond_axe" -> Items.DIAMOND_AXE;
				case "diamond_shovel" -> Items.DIAMOND_SHOVEL;
				case "diamond_hoe" -> Items.DIAMOND_HOE;
				default -> null;
			};
		}
		if (weapon == null || material == ForgeMaterial.GOLD) {
			return null;
		}
		return ModItems.weapon(weapon, material.id);
	}

	@Nullable
	public static ForgedPart byId(String id) {
		for (ForgedPart p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}
}
