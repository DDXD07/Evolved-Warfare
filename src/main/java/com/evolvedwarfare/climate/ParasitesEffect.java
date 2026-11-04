package com.evolvedwarfare.climate;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Parásitos (de beber agua sin hervir): quitan medio corazón cada cinco segundos (sin matar: como el veneno, se paran
 * con medio corazón) y dan hambre y sed. Se van solos con el tiempo o al beber leche.
 */
public class ParasitesEffect extends StatusEffect {
	public ParasitesEffect() {
		super(StatusEffectCategory.HARMFUL, 0x6B7A2A);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 100 == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity instanceof PlayerEntity player) {
			player.addExhaustion(1.75F * (amplifier + 1));
		}
		if (entity.getWorld() instanceof ServerWorld world && entity.getHealth() > 1.0F) {
			entity.damage(Thirst.source(world, Thirst.PARASITES), 1.0F);
		}
		return true;
	}
}
