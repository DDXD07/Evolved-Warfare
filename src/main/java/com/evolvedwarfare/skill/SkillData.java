package com.evolvedwarfare.skill;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;

/** Lo de un jugador: el nivel y la experiencia de cada habilidad, su nivel de personaje, los puntos y las ventajas. */
public final class SkillData {
	public final int[] levels = new int[Skill.values().length];
	public final float[] xp = new float[Skill.values().length];
	/** Nivel de personaje (empieza en 1). */
	public int level = 1;
	public float levelXp;
	/** Puntos de ventaja sin gastar (se empieza con uno). */
	public int points = 1;
	public final Map<Perk, Integer> perks = new EnumMap<>(Perk.class);
	/** Ya se le pasó lo que tenía investigado (la investigación de antes). */
	public boolean migrated;

	public int level(Skill skill) {
		return levels[skill.ordinal()];
	}

	public float xp(Skill skill) {
		return xp[skill.ordinal()];
	}

	public int rank(Perk perk) {
		return perks.getOrDefault(perk, 0);
	}

	/** Experiencia de personaje para pasar de este nivel al siguiente (cada subida de habilidad da su nuevo nivel). */
	public static float levelXpToNext(int level) {
		return 15.0F * (level + 3);
	}

	/** Puntos que ha gastado en total (para devolverlos al reiniciar). */
	public int spentPoints() {
		int total = 0;
		for (int rank : perks.values()) {
			total += rank;
		}
		return total;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		NbtCompound skills = new NbtCompound();
		for (Skill skill : Skill.values()) {
			NbtCompound s = new NbtCompound();
			s.putInt("Level", levels[skill.ordinal()]);
			s.putFloat("Xp", xp[skill.ordinal()]);
			skills.put(skill.id(), s);
		}
		nbt.put("Skills", skills);
		nbt.putInt("Level", level);
		nbt.putFloat("LevelXp", levelXp);
		nbt.putInt("Points", points);
		NbtCompound perkTag = new NbtCompound();
		for (Map.Entry<Perk, Integer> e : perks.entrySet()) {
			if (e.getValue() > 0) {
				perkTag.putInt(e.getKey().id(), e.getValue());
			}
		}
		nbt.put("Perks", perkTag);
		nbt.putBoolean("Migrated", migrated);
		return nbt;
	}

	public static SkillData fromNbt(NbtCompound nbt) {
		SkillData data = new SkillData();
		NbtCompound skills = nbt.getCompound("Skills");
		for (Skill skill : Skill.values()) {
			NbtCompound s = skills.getCompound(skill.id());
			data.levels[skill.ordinal()] = Math.max(0, Math.min(Skill.MAX_LEVEL, s.getInt("Level")));
			data.xp[skill.ordinal()] = Math.max(0.0F, s.getFloat("Xp"));
		}
		data.level = Math.max(1, nbt.contains("Level") ? nbt.getInt("Level") : 1);
		data.levelXp = Math.max(0.0F, nbt.getFloat("LevelXp"));
		data.points = nbt.contains("Points") ? Math.max(0, nbt.getInt("Points")) : 1;
		NbtCompound perkTag = nbt.getCompound("Perks");
		for (String key : perkTag.getKeys()) {
			Perk perk = Perk.byId(key);
			if (perk != null) {
				data.perks.put(perk, Math.max(0, Math.min(perk.ranks(), perkTag.getInt(key))));
			}
		}
		data.migrated = nbt.getBoolean("Migrated");
		return data;
	}
}
