package com.evolvedwarfare.health;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;

/**
 * Dónde salen los cristales de corazón (fase 92): siempre del jefe de cada mazmorra y del Wither y el guardián; a veces
 * de los cofres de las mazmorras y de los tesoros de vanilla más difíciles.
 */
public final class HealthLoot {
	private HealthLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			String id = key.getValue().toString();
			// remedios (fase 93): en los cofres de las mazmorras, las aldeas, las minas y las mazmorras de vanilla
			if ((id.startsWith("evolvedwarfare:chests/dungeon/") && !id.endsWith("/boss")) || id.startsWith("minecraft:chests/village/")
					|| id.equals("minecraft:chests/simple_dungeon") || id.equals("minecraft:chests/abandoned_mineshaft")
					|| id.equals("minecraft:chests/shipwreck_supply") || id.equals("minecraft:chests/igloo_chest")) {
				builder.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
						.conditionally(RandomChanceLootCondition.builder(0.35F))
						.with(ItemEntry.builder(HealthItems.BANDAGE).weight(6)
								.apply(net.minecraft.loot.function.SetCountLootFunction.builder(
										net.minecraft.loot.provider.number.UniformLootNumberProvider.create(1, 3))))
						.with(ItemEntry.builder(HealthItems.SPLINT).weight(3))
						.with(ItemEntry.builder(HealthItems.ALCOHOL).weight(3))
						.with(ItemEntry.builder(HealthItems.ANTIDOTE).weight(2))
						.with(ItemEntry.builder(HealthItems.MEDICINE).weight(2))
						.with(ItemEntry.builder(HealthItems.MEDKIT).weight(1)));
			}
			float chance;
			if (id.startsWith("evolvedwarfare:chests/dungeon/") && id.endsWith("/boss")) {
				chance = 1.0F;
			} else if (id.equals("minecraft:entities/wither") || id.equals("minecraft:entities/warden") || id.equals("minecraft:entities/ender_dragon")) {
				chance = 1.0F;
			} else if (id.equals("minecraft:entities/elder_guardian")) {
				chance = 0.5F;
			} else if (id.startsWith("evolvedwarfare:chests/dungeon/")) {
				chance = 0.06F;
			} else if (id.equals("minecraft:chests/ancient_city") || id.equals("minecraft:chests/end_city_treasure")
					|| id.equals("minecraft:chests/bastion_treasure") || id.equals("minecraft:chests/stronghold_corridor")) {
				chance = 0.08F;
			} else if (id.equals("minecraft:chests/woodland_mansion") || id.equals("minecraft:chests/desert_pyramid")
					|| id.equals("minecraft:chests/jungle_temple") || id.equals("minecraft:chests/buried_treasure")) {
				chance = 0.05F;
			} else {
				return;
			}
			LootPool.Builder pool = LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).with(ItemEntry.builder(HealthItems.HEART_CRYSTAL));
			if (chance < 1.0F) {
				pool.conditionally(RandomChanceLootCondition.builder(chance));
			}
			builder.pool(pool);
		});
	}
}
