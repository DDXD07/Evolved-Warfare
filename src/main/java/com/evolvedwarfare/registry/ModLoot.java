package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.loot.SettlementLevelCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Tipos de botín propios: la condición de nivel del asentamiento. */
public final class ModLoot {
	public static final LootConditionType SETTLEMENT_LEVEL = Registry.register(Registries.LOOT_CONDITION_TYPE,
			EvolvedWarfare.id("settlement_level"), new LootConditionType(SettlementLevelCondition.CODEC));

	private ModLoot() {
	}

	public static void init() {
	}
}
