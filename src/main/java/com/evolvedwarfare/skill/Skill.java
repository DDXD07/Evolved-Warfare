package com.evolvedwarfare.skill;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Las habilidades (como en Skyrim): suben de 0 a 100 usándolas, y cada subida da experiencia de personaje. Están en
 * cuatro grupos: cuerpo a cuerpo, a distancia, defensa y mando.
 */
public enum Skill {
	BLADES(Group.MELEE, 0xC8D2DC, () -> Items.IRON_SWORD),
	AXES(Group.MELEE, 0xD28C5A, () -> ModItems.weapon(WeaponType.BATTLE_AXE, "iron")),
	MACES(Group.MELEE, 0xA0A0B4, () -> ModItems.weapon(WeaponType.WARHAMMER, "iron")),
	POLEARMS(Group.MELEE, 0xB4C878, () -> ModItems.weapon(WeaponType.SPEAR, "iron")),
	ARCHERY(Group.RANGED, 0x8CC878, () -> Items.BOW),
	FIREARMS(Group.RANGED, 0xE6B45A, () -> ModItems.MUSKET),
	BLOCK(Group.DEFENSE, 0x78A0DC, () -> Items.SHIELD),
	HEAVY_ARMOR(Group.DEFENSE, 0x9696A0, () -> Items.IRON_CHESTPLATE),
	LIGHT_ARMOR(Group.DEFENSE, 0xB48C64, () -> Items.LEATHER_CHESTPLATE),
	LEADERSHIP(Group.COMMAND, 0xE6D278, () -> ModItems.COMMAND_STAFF),
	SIEGE(Group.COMMAND, 0xC87850, () -> ModItems.CATAPULT);

	public enum Group {
		MELEE, RANGED, DEFENSE, COMMAND;

		public String translationKey() {
			return "skill_group.evolvedwarfare." + name().toLowerCase(Locale.ROOT);
		}
	}

	public static final int MAX_LEVEL = 100;

	public final Group group;
	/** Color de la constelación. */
	public final int color;
	private final Supplier<Item> icon;

	Skill(Group group, int color, Supplier<Item> icon) {
		this.group = group;
		this.color = color;
		this.icon = icon;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "skill.evolvedwarfare." + id();
	}

	public Item icon() {
		return icon.get();
	}

	/** Lo que hace falta para pasar de este nivel al siguiente (en "usos": puntos de daño, más o menos). */
	public static float xpToNext(int level) {
		return 8.0F + level * 1.8F;
	}

	@Nullable
	public static Skill byId(String id) {
		for (Skill skill : values()) {
			if (skill.id().equals(id)) {
				return skill;
			}
		}
		return null;
	}
}
