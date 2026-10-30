package com.evolvedwarfare.skill;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Las ventajas de cada habilidad (las estrellas de su constelación, como en Skyrim). Cada una tiene su sitio en el
 * dibujo (columna 0-4, fila 0-5 de abajo arriba), el nivel de la habilidad que pide cada rango, las anteriores (basta
 * con una) y sus valores por rango, que usan tanto los efectos ({@link SkillEffects}) como las descripciones. Las de
 * "forja" desbloquean la fabricación de armas, máquinas y fortificaciones (lo que antes era la investigación).
 */
public enum Perk {
	// ------------------------------------------------------------------ hojas
	BLADE_MASTERY(Skill.BLADES, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	DAGGER_SMITH(Skill.BLADES, 0, 1, levels(0), amounts()),
	BACKSTAB(Skill.BLADES, 1, 2, levels(35), amounts(50), "DAGGER_SMITH", "BLADE_MASTERY"),
	BLADE_DANCE(Skill.BLADES, 3, 2, levels(30), amounts(15), "BLADE_MASTERY"),
	LONGSWORD_SMITH(Skill.BLADES, 0, 3, levels(25), amounts(), "DAGGER_SMITH"),
	BLADE_CRIT(Skill.BLADES, 3, 4, levels(50, 70, 90), amounts(10, 15, 20), "BLADE_DANCE"),
	WHIRLWIND(Skill.BLADES, 2, 5, levels(75), amounts(50), "BLADE_CRIT", "BACKSTAB"),
	// ------------------------------------------------------------------ hachas
	AXE_MASTERY(Skill.AXES, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	BATTLE_AXE_SMITH(Skill.AXES, 0, 1, levels(25), amounts()),
	LUMBERJACK(Skill.AXES, 4, 1, levels(10), amounts(40), "AXE_MASTERY"),
	BLEED(Skill.AXES, 2, 2, levels(30, 60, 90), amounts(1, 1.5F, 2), "AXE_MASTERY"),
	HEAVY_CHOP(Skill.AXES, 3, 3, levels(45), amounts(50), "BLEED"),
	BUTCHER(Skill.AXES, 1, 4, levels(65), amounts(25), "BLEED"),
	FURY(Skill.AXES, 2, 5, levels(85), amounts(5), "BUTCHER", "HEAVY_CHOP"),
	// ------------------------------------------------------------------ mazas
	MACE_MASTERY(Skill.MACES, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	MACE_SMITH(Skill.MACES, 0, 1, levels(0), amounts()),
	BONEBREAKER(Skill.MACES, 2, 2, levels(30, 60, 90), amounts(15, 30, 45), "MACE_MASTERY"),
	STUN(Skill.MACES, 4, 2, levels(40), amounts(15), "MACE_MASTERY"),
	EXECUTIONER(Skill.MACES, 1, 4, levels(70), amounts(30), "BONEBREAKER"),
	CONCUSSION(Skill.MACES, 4, 4, levels(60), amounts(), "STUN"),
	CRUSH(Skill.MACES, 2, 5, levels(90), amounts(25), "EXECUTIONER", "CONCUSSION"),
	// ------------------------------------------------------------------ astas
	POLE_MASTERY(Skill.POLEARMS, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	SPEAR_SMITH(Skill.POLEARMS, 0, 1, levels(0), amounts()),
	KEEP_AT_BAY(Skill.POLEARMS, 4, 1, levels(20), amounts(0.5F), "POLE_MASTERY"),
	LUNGE(Skill.POLEARMS, 2, 2, levels(30, 60, 90), amounts(20, 35, 50), "POLE_MASTERY"),
	HALBERD_SMITH(Skill.POLEARMS, 0, 3, levels(40), amounts(), "SPEAR_SMITH"),
	ANTI_CAVALRY(Skill.POLEARMS, 1, 3, levels(45), amounts(50), "LUNGE"),
	PIKE_WALL(Skill.POLEARMS, 4, 3, levels(65), amounts(), "KEEP_AT_BAY"),
	IMPALE(Skill.POLEARMS, 2, 5, levels(90), amounts(1.5F), "ANTI_CAVALRY", "PIKE_WALL"),
	// ------------------------------------------------------------------ arquería
	OVERDRAW(Skill.ARCHERY, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	JAVELIN_SMITH(Skill.ARCHERY, 0, 1, levels(0), amounts()),
	LONGBOW_SMITH(Skill.ARCHERY, 4, 1, levels(20), amounts()),
	HUNTER(Skill.ARCHERY, 1, 2, levels(20), amounts(30), "OVERDRAW"),
	RECOVERY(Skill.ARCHERY, 3, 2, levels(30), amounts(33), "OVERDRAW"),
	CRIT_SHOT(Skill.ARCHERY, 2, 3, levels(40, 60, 80), amounts(10, 15, 20), "OVERDRAW"),
	BULLSEYE(Skill.ARCHERY, 2, 4, levels(70), amounts(50), "CRIT_SHOT"),
	RANGER(Skill.ARCHERY, 1, 5, levels(90), amounts(10), "BULLSEYE", "HUNTER"),
	// ------------------------------------------------------------------ armas de fuego
	FINE_POWDER(Skill.FIREARMS, 2, 0, levels(0, 20, 40, 60, 80), amounts(10, 20, 30, 40, 50)),
	SPARE_BALLS(Skill.FIREARMS, 1, 2, levels(30), amounts(25), "FINE_POWDER"),
	QUICK_RELOAD(Skill.FIREARMS, 3, 2, levels(20, 50, 80), amounts(15, 30, 45), "FINE_POWDER"),
	STEADY_AIM(Skill.FIREARMS, 2, 3, levels(40), amounts(25), "SPARE_BALLS", "QUICK_RELOAD"),
	SHARPSHOOTER(Skill.FIREARMS, 1, 4, levels(75), amounts(50), "STEADY_AIM"),
	THUNDER(Skill.FIREARMS, 3, 4, levels(60), amounts(), "STEADY_AIM"),
	// ------------------------------------------------------------------ escudo
	STURDY_SHIELD(Skill.BLOCK, 2, 0, levels(0, 30, 60), amounts(25, 50, 75)),
	IMMOVABLE(Skill.BLOCK, 1, 1, levels(20), amounts(), "STURDY_SHIELD"),
	QUICK_GUARD(Skill.BLOCK, 3, 1, levels(30), amounts(1.5F), "STURDY_SHIELD"),
	COUNTER(Skill.BLOCK, 2, 2, levels(45), amounts(30), "IMMOVABLE", "QUICK_GUARD"),
	SHIELD_BASH(Skill.BLOCK, 3, 3, levels(60), amounts(1), "COUNTER"),
	SHIELD_WALL(Skill.BLOCK, 1, 4, levels(80), amounts(20), "COUNTER"),
	// ------------------------------------------------------------------ armadura pesada
	JUGGERNAUT(Skill.HEAVY_ARMOR, 2, 0, levels(0, 20, 40, 60, 80), amounts(8, 16, 24, 32, 40)),
	WELL_FITTED(Skill.HEAVY_ARMOR, 1, 1, levels(30), amounts(15), "JUGGERNAUT"),
	STEADFAST(Skill.HEAVY_ARMOR, 3, 1, levels(40), amounts(30), "JUGGERNAUT"),
	CONQUEROR(Skill.HEAVY_ARMOR, 1, 3, levels(60), amounts(10), "WELL_FITTED"),
	CUSHIONED(Skill.HEAVY_ARMOR, 3, 3, levels(50), amounts(50), "STEADFAST"),
	REFLECT_BLOWS(Skill.HEAVY_ARMOR, 2, 5, levels(90), amounts(10), "CONQUEROR", "CUSHIONED"),
	// ------------------------------------------------------------------ armadura ligera
	AGILE_DEFENSE(Skill.LIGHT_ARMOR, 2, 0, levels(0, 20, 40, 60, 80), amounts(8, 16, 24, 32, 40)),
	LIGHT_STEP(Skill.LIGHT_ARMOR, 1, 1, levels(20), amounts(10), "AGILE_DEFENSE"),
	CUSTOM_FIT(Skill.LIGHT_ARMOR, 3, 1, levels(30), amounts(20), "AGILE_DEFENSE"),
	EVASION(Skill.LIGHT_ARMOR, 2, 2, levels(40, 60, 80), amounts(5, 10, 15), "LIGHT_STEP", "CUSTOM_FIT"),
	SECOND_WIND(Skill.LIGHT_ARMOR, 1, 4, levels(70), amounts(4), "EVASION"),
	CAT_FALL(Skill.LIGHT_ARMOR, 3, 4, levels(50), amounts(50), "EVASION"),
	// ------------------------------------------------------------------ liderazgo
	DRILL(Skill.LEADERSHIP, 2, 0, levels(0), amounts(20)),
	TACTICS(Skill.LEADERSHIP, 1, 1, levels(20), amounts(15), "DRILL"),
	FIELD_MEDICINE(Skill.LEADERSHIP, 3, 1, levels(20), amounts(50), "DRILL"),
	QUARTERMASTER(Skill.LEADERSHIP, 2, 2, levels(30), amounts(25), "DRILL"),
	INSPIRE(Skill.LEADERSHIP, 1, 3, levels(40), amounts(12), "TACTICS"),
	VETERANS(Skill.LEADERSHIP, 3, 3, levels(50), amounts(100), "FIELD_MEDICINE"),
	BANNER(Skill.LEADERSHIP, 2, 5, levels(70), amounts(), "INSPIRE", "VETERANS"),
	// ------------------------------------------------------------------ asedio
	RAM_SMITH(Skill.SIEGE, 1, 0, levels(0), amounts()),
	FORTIFICATION(Skill.SIEGE, 3, 0, levels(0), amounts()),
	TORSION(Skill.SIEGE, 1, 2, levels(20), amounts(), "RAM_SMITH"),
	GUNNER(Skill.SIEGE, 3, 2, levels(20, 50, 80), amounts(15, 30, 45), "RAM_SMITH", "FORTIFICATION"),
	INCENDIARIES(Skill.SIEGE, 0, 3, levels(40), amounts(), "TORSION"),
	THRIFT(Skill.SIEGE, 4, 3, levels(30), amounts(25), "GUNNER"),
	COUNTERWEIGHT(Skill.SIEGE, 1, 4, levels(60), amounts(), "TORSION"),
	SAPPER(Skill.SIEGE, 3, 4, levels(40), amounts(30), "GUNNER");

	public static final int COLUMNS = 5;
	public static final int ROWS = 6;

	public final Skill skill;
	public final int column;
	public final int row;
	/** Nivel de la habilidad que pide cada rango. */
	private final int[] levels;
	/** Lo que da cada rango (porcentajes, segundos, bloques...). */
	private final float[] values;
	private final String[] requireNames;
	@Nullable
	private List<Perk> requires;

	Perk(Skill skill, int column, int row, int[] levels, float[] values, String... requires) {
		this.skill = skill;
		this.column = column;
		this.row = row;
		this.levels = levels;
		this.values = values;
		this.requireNames = requires;
	}

	private static int[] levels(int... levels) {
		return levels;
	}

	private static float[] amounts(float... values) {
		return values;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "perk.evolvedwarfare." + id();
	}

	public int ranks() {
		return levels.length;
	}

	/** Nivel de habilidad que pide el rango (1..ranks). */
	public int levelFor(int rank) {
		return levels[Math.max(0, Math.min(levels.length - 1, rank - 1))];
	}

	/** Lo que da con ese rango (0 sin rango). */
	public float value(int rank) {
		if (rank <= 0 || values.length == 0) {
			return 0.0F;
		}
		return values[Math.min(values.length, rank) - 1];
	}

	public boolean hasValues() {
		return values.length > 0;
	}

	/** Las anteriores: basta con tener una (ninguna = se puede coger desde el principio). */
	public List<Perk> requires() {
		if (requires == null) {
			List<Perk> list = new ArrayList<>();
			for (String name : requireNames) {
				list.add(valueOf(name));
			}
			requires = List.copyOf(list);
		}
		return requires;
	}

	/** Lo que no se puede fabricar sin ella. */
	public List<Item> unlocks() {
		List<Item> out = new ArrayList<>();
		switch (this) {
			case DAGGER_SMITH -> out.addAll(ModItems.WEAPONS.get(WeaponType.DAGGER));
			case LONGSWORD_SMITH -> out.addAll(ModItems.WEAPONS.get(WeaponType.LONGSWORD));
			case BATTLE_AXE_SMITH -> out.addAll(ModItems.WEAPONS.get(WeaponType.BATTLE_AXE));
			case MACE_SMITH -> {
				out.addAll(ModItems.WEAPONS.get(WeaponType.WARHAMMER));
				out.addAll(ModItems.WEAPONS.get(WeaponType.FLAIL));
			}
			case SPEAR_SMITH -> out.addAll(ModItems.WEAPONS.get(WeaponType.SPEAR));
			case HALBERD_SMITH -> out.addAll(ModItems.WEAPONS.get(WeaponType.HALBERD));
			case JAVELIN_SMITH -> out.add(ModItems.JAVELIN);
			case LONGBOW_SMITH -> out.add(ModItems.LONGBOW);
			case RAM_SMITH -> out.add(ModItems.BATTERING_RAM);
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

	public static List<Perk> of(Skill skill) {
		List<Perk> out = new ArrayList<>();
		for (Perk perk : values()) {
			if (perk.skill == skill) {
				out.add(perk);
			}
		}
		return out;
	}

	@Nullable
	public static Perk byId(String id) {
		for (Perk perk : values()) {
			if (perk.id().equals(id)) {
				return perk;
			}
		}
		return null;
	}
}
