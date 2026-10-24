package com.evolvedwarfare.loot;

import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.registry.ModLoot;
import com.evolvedwarfare.world.SettlementLevels;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Condición de botín {@code evolvedwarfare:settlement_level}: se cumple si el nivel del asentamiento es al menos
 * {@code min}. Para un cofre, el del asentamiento donde está; para un guerrero de una facción, el del que defendía.
 * <pre>{"condition": "evolvedwarfare:settlement_level", "min": 5}</pre>
 */
public record SettlementLevelCondition(int min) implements LootCondition {
	public static final MapCodec<SettlementLevelCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("min").forGetter(SettlementLevelCondition::min)
	).apply(instance, SettlementLevelCondition::new));

	@Override
	public LootConditionType getType() {
		return ModLoot.SETTLEMENT_LEVEL;
	}

	@Override
	public boolean test(LootContext context) {
		return level(context) >= min;
	}

	private static int level(LootContext context) {
		Entity entity = context.get(LootContextParameters.THIS_ENTITY);
		if (entity instanceof FactionNpcEntity npc) {
			return npc.getSettlementLevel();
		}
		Vec3d origin = context.get(LootContextParameters.ORIGIN);
		if (origin == null) {
			return 0;
		}
		return SettlementLevels.levelAt(context.getWorld(), BlockPos.ofFloored(origin));
	}
}
