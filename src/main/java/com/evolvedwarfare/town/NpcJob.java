package com.evolvedwarfare.town;

import com.evolvedwarfare.work.Profession;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * El oficio de la vida de un NPC (lo que hace en su pueblo, antes de que nadie lo contrate). Decide dónde trabaja en su
 * rutina y de qué habla; y, si lo contratas, en qué puesto de trabajo rinde como si lo conociera ({@link #profession}).
 */
public enum NpcJob {
	LABORER("laborer", null),
	FARMER("farmer", Profession.FARMER),
	BAKER("baker", Profession.COOK),
	BLACKSMITH("blacksmith", Profession.BLACKSMITH),
	CARPENTER("carpenter", Profession.BUILDER),
	LUMBERJACK("lumberjack", Profession.LUMBERJACK),
	MINER("miner", Profession.MINER),
	HUNTER("hunter", null),
	FISHER("fisher", Profession.FISHERMAN),
	SHEPHERD("shepherd", Profession.SHEPHERD),
	TAILOR("tailor", null),
	MASON("mason", Profession.STONEMASON),
	INNKEEPER("innkeeper", Profession.COOK),
	MERCHANT("merchant", Profession.MERCHANT),
	GUARD("guard", null),
	MERCENARY("mercenary", null);

	public final String id;
	/** El puesto de trabajo del mod que le corresponde (o null). */
	@Nullable
	public final Profession profession;

	NpcJob(String id, @Nullable Profession profession) {
		this.id = id;
		this.profession = profession;
	}

	public Text label() {
		return label(false);
	}

	/** En femenino si es mujer (en inglés da igual). */
	public Text label(boolean female) {
		return Text.translatable("npc.evolvedwarfare.job." + id + (female ? ".f" : ""));
	}

	public static NpcJob byOrdinal(int i) {
		NpcJob[] all = values();
		return i >= 0 && i < all.length ? all[i] : LABORER;
	}

	public static NpcJob byId(String id) {
		for (NpcJob j : values()) {
			if (j.id.equals(id)) {
				return j;
			}
		}
		return LABORER;
	}

	/** Los oficios de los vecinos de una ciudad (con más campesinos y peones que nada). */
	private static final NpcJob[] RESIDENT = {FARMER, FARMER, FARMER, LABORER, LABORER, BAKER, BLACKSMITH, CARPENTER, LUMBERJACK, MINER, HUNTER,
			FISHER, SHEPHERD, TAILOR, MASON, INNKEEPER};

	public static NpcJob randomResident(Random random) {
		return RESIDENT[random.nextInt(RESIDENT.length)];
	}

	/** El oficio de un aldeano de vanilla. */
	public static NpcJob of(VillagerEntity villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		if (p == VillagerProfession.FARMER) {
			return FARMER;
		}
		if (p == VillagerProfession.FISHERMAN) {
			return FISHER;
		}
		if (p == VillagerProfession.SHEPHERD) {
			return SHEPHERD;
		}
		if (p == VillagerProfession.BUTCHER) {
			return BAKER;
		}
		if (p == VillagerProfession.ARMORER || p == VillagerProfession.WEAPONSMITH || p == VillagerProfession.TOOLSMITH) {
			return BLACKSMITH;
		}
		if (p == VillagerProfession.MASON) {
			return MASON;
		}
		if (p == VillagerProfession.LEATHERWORKER) {
			return TAILOR;
		}
		if (p == VillagerProfession.FLETCHER) {
			return HUNTER;
		}
		if (p == VillagerProfession.LIBRARIAN || p == VillagerProfession.CARTOGRAPHER || p == VillagerProfession.CLERIC) {
			return MERCHANT;
		}
		return LABORER;
	}
}
