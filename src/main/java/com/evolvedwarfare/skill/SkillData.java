package com.evolvedwarfare.skill;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

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

	// ------------------------------------------------------------------ superviviente (fase 112, como en ARK)
	/** Nivel máximo de superviviente. */
	public static final int MAX_LEVEL = 100;
	/** Puntos de engrama sin gastar (para desbloquear lo que se fabrica, fase 113). */
	public int engramPoints;
	/** Puntos de atributo sin gastar (vida, aguante, carga...). */
	public int attributePoints;
	/** Ya se le pasó su nivel de antes al de superviviente (con los puntos de los niveles que ya tenía). */
	public boolean survivor;
	/** Lo que ya ha fabricado alguna vez (la primera vez da experiencia). Solo en el servidor. */
	public final Set<String> crafted = new LinkedHashSet<>();
	/** Los biomas en los que ya ha estado. Solo en el servidor. */
	public final Set<String> biomes = new LinkedHashSet<>();
	/** El último amanecer que vio (día del mundo). */
	public long lastDawn = -1L;
	/** Los engramas aprendidos (fase 113). */
	public final Set<String> engrams = new LinkedHashSet<>();
	/** Ya se le dieron los puntos del principio y lo que sabía forjar pasó a engramas. */
	public boolean engramStart;
	/** Los puntos puestos en cada atributo (fase 114). */
	public final int[] attributes = new int[SurvivorAttribute.values().length];

	public int attribute(SurvivorAttribute a) {
		return attributes[a.ordinal()];
	}

	public int level(Skill skill) {
		return levels[skill.ordinal()];
	}

	public float xp(Skill skill) {
		return xp[skill.ordinal()];
	}

	/**
	 * El rango de una ventaja: desde la fase 113 las ventajas ya no se cogen; las de forja, mando y asedio las dan los
	 * engramas y las de combate ya no existen.
	 */
	public int rank(Perk perk) {
		return Engrams.perkRank(this, perk);
	}

	/** El rango que tenía de una ventaja en el árbol de antes (para pasarla a engramas). */
	public int legacyRank(Perk perk) {
		return perks.getOrDefault(perk, 0);
	}

	/**
	 * Experiencia de superviviente para pasar de este nivel al siguiente: muy poca al principio (los primeros niveles
	 * salen el primer día) y mucha al final (unos 71.000 en total hasta el 100).
	 */
	public static float levelXpToNext(int level) {
		return 20.0F + 6.0F * level + 0.12F * level * level;
	}

	/** Puntos de engrama que da llegar a este nivel (como en ARK, más cuanto más alto). */
	public static int engramPointsFor(int level) {
		return level <= 1 ? 0 : 6 + level / 10;
	}

	/** Puntos de engrama que se han ganado en total hasta este nivel. */
	public static int engramPointsUpTo(int level) {
		int total = 0;
		for (int l = 2; l <= level; l++) {
			total += engramPointsFor(l);
		}
		return total;
	}

	public boolean maxLevel() {
		return level >= MAX_LEVEL;
	}

	/** Puntos que ha gastado en total (para devolverlos al reiniciar). */
	public int spentPoints() {
		int total = 0;
		for (int rank : perks.values()) {
			total += rank;
		}
		return total;
	}

	/** Para guardar (con todo). */
	public NbtCompound toNbt() {
		NbtCompound nbt = toClientNbt();
		nbt.put("Crafted", strings(crafted));
		nbt.put("Biomes", strings(biomes));
		return nbt;
	}

	private static NbtList strings(Set<String> set) {
		NbtList list = new NbtList();
		for (String s : set) {
			list.add(NbtString.of(s));
		}
		return list;
	}

	/** Para el cliente (sin las listas de lo fabricado y los biomas, que solo hacen falta en el servidor). */
	public NbtCompound toClientNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("EngramPoints", engramPoints);
		nbt.putInt("AttributePoints", attributePoints);
		nbt.putBoolean("Survivor", survivor);
		nbt.putLong("LastDawn", lastDawn);
		nbt.put("Engrams", strings(engrams));
		nbt.putBoolean("EngramStart", engramStart);
		NbtCompound attrs = new NbtCompound();
		for (SurvivorAttribute a : SurvivorAttribute.values()) {
			if (attributes[a.ordinal()] > 0) {
				attrs.putInt(a.id(), attributes[a.ordinal()]);
			}
		}
		nbt.put("Attributes", attrs);
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
		data.engramPoints = Math.max(0, nbt.getInt("EngramPoints"));
		data.attributePoints = Math.max(0, nbt.getInt("AttributePoints"));
		data.survivor = nbt.getBoolean("Survivor");
		data.lastDawn = nbt.contains("LastDawn") ? nbt.getLong("LastDawn") : -1L;
		data.engramStart = nbt.getBoolean("EngramStart");
		NbtCompound attrs = nbt.getCompound("Attributes");
		for (SurvivorAttribute a : SurvivorAttribute.values()) {
			data.attributes[a.ordinal()] = Math.max(0, attrs.getInt(a.id()));
		}
		for (NbtElement e : nbt.getList("Engrams", NbtElement.STRING_TYPE)) {
			data.engrams.add(e.asString());
		}
		for (NbtElement e : nbt.getList("Crafted", NbtElement.STRING_TYPE)) {
			data.crafted.add(e.asString());
		}
		for (NbtElement e : nbt.getList("Biomes", NbtElement.STRING_TYPE)) {
			data.biomes.add(e.asString());
		}
		return data;
	}
}
