package com.evolvedwarfare.town;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Quién es un NPC en su pueblo: su oficio, su carácter, su cama (su casa), dónde trabaja, de qué pueblo y de qué casa es,
 * y lo que piensa de los demás (de -100, se odian, a 100, uña y carne; la familia empieza muy alto).
 */
public final class NpcProfile {
	public static final int FAMILY = 70;

	public NpcJob job = NpcJob.LABORER;
	public Personality personality = Personality.CHEERFUL;
	/** Ya tiene oficio y carácter (si no, se eligen al azar la primera vez). */
	public boolean rolled;
	/** La cabecera de su cama (null: no tiene). */
	@Nullable
	public BlockPos bed;
	/** Dónde trabaja (null: donde le toque). */
	@Nullable
	public BlockPos workplace;
	/** Su pueblo (la clave de la ciudad; 0: ninguno) y su casa (los que comparten casa son familia). */
	public long community;
	public long household;
	/** Ya ha buscado cama libre cerca / ya conoce a sus vecinos. */
	public boolean bedSearched;
	public boolean met;
	private final Map<UUID, Integer> relations = new HashMap<>();
	private final Set<UUID> family = new HashSet<>();

	public int affinity(UUID other) {
		return relations.getOrDefault(other, 0);
	}

	public boolean knows(UUID other) {
		return relations.containsKey(other);
	}

	public void like(UUID other, int delta) {
		relations.put(other, Math.max(-100, Math.min(100, affinity(other) + delta)));
	}

	public void setAffinity(UUID other, int value) {
		relations.put(other, Math.max(-100, Math.min(100, value)));
	}

	public void addFamily(UUID other) {
		family.add(other);
		relations.put(other, Math.max(affinity(other), FAMILY));
	}

	public boolean isFamily(UUID other) {
		return family.contains(other);
	}

	public Map<UUID, Integer> relations() {
		return relations;
	}

	public void forget(UUID other) {
		relations.remove(other);
		family.remove(other);
	}

	/** Oficio y carácter en un número (para que lo vea el cliente). */
	public int packed() {
		return job.ordinal() | personality.ordinal() << 8;
	}

	/** Igual, con si es mujer (para decir "herrera, curiosa"). */
	public int packed(boolean female) {
		return packed() | (female ? 1 << 16 : 0);
	}

	public static boolean femaleOf(int packed) {
		return (packed >> 16 & 1) != 0;
	}

	public static NpcJob jobOf(int packed) {
		return NpcJob.byOrdinal(packed & 0xFF);
	}

	public static Personality personalityOf(int packed) {
		return Personality.byOrdinal(packed >> 8 & 0xFF);
	}

	/** "herrero, alegre" (o "herrera, alegre"). */
	public static Text describe(int packed) {
		boolean f = femaleOf(packed);
		return Text.translatable("npc.evolvedwarfare.profile", jobOf(packed).label(f), personalityOf(packed).label(f));
	}

	public Text describe() {
		return describe(packed());
	}

	public void copyFrom(NpcProfile o) {
		job = o.job;
		personality = o.personality;
		rolled = o.rolled;
		bed = o.bed;
		workplace = o.workplace;
		community = o.community;
		household = o.household;
		bedSearched = o.bedSearched;
		met = o.met;
		relations.clear();
		relations.putAll(o.relations);
		family.clear();
		family.addAll(o.family);
	}

	public NbtCompound write() {
		NbtCompound n = new NbtCompound();
		n.putString("Job", job.id);
		n.putString("Personality", personality.id);
		n.putBoolean("Rolled", rolled);
		if (bed != null) {
			n.putLong("Bed", bed.asLong());
		}
		if (workplace != null) {
			n.putLong("Work", workplace.asLong());
		}
		n.putLong("Community", community);
		n.putLong("Household", household);
		n.putBoolean("BedSearched", bedSearched);
		n.putBoolean("Met", met);
		NbtList rel = new NbtList();
		for (Map.Entry<UUID, Integer> e : relations.entrySet()) {
			NbtCompound r = new NbtCompound();
			r.put("Who", NbtHelper.fromUuid(e.getKey()));
			r.putInt("V", e.getValue());
			if (family.contains(e.getKey())) {
				r.putBoolean("Family", true);
			}
			rel.add(r);
		}
		n.put("Relations", rel);
		return n;
	}

	public void read(NbtCompound n) {
		job = NpcJob.byId(n.getString("Job"));
		personality = Personality.CHEERFUL;
		for (Personality p : Personality.values()) {
			if (p.id.equals(n.getString("Personality"))) {
				personality = p;
			}
		}
		rolled = n.getBoolean("Rolled");
		bed = n.contains("Bed") ? BlockPos.fromLong(n.getLong("Bed")) : null;
		workplace = n.contains("Work") ? BlockPos.fromLong(n.getLong("Work")) : null;
		community = n.getLong("Community");
		household = n.getLong("Household");
		bedSearched = n.getBoolean("BedSearched");
		met = n.getBoolean("Met");
		relations.clear();
		family.clear();
		NbtList rel = n.getList("Relations", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < rel.size(); i++) {
			NbtCompound r = rel.getCompound(i);
			if (r.get("Who") == null) {
				continue;
			}
			UUID who = NbtHelper.toUuid(r.get("Who"));
			relations.put(who, r.getInt("V"));
			if (r.getBoolean("Family")) {
				family.add(who);
			}
		}
	}
}
