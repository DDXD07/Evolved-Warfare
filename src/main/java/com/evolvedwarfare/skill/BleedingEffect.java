package com.evolvedwarfare.skill;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** Sangrado (ventaja de Hachas): quita vida cada segundo, más cuanto más alto el nivel (no lo para la armadura). */
public class BleedingEffect extends StatusEffect {
	public BleedingEffect() {
		super(StatusEffectCategory.HARMFUL, 0x9B1010);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 20 == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		entity.damage(entity.getDamageSources().magic(), 1.0F + 0.5F * amplifier);
		return true;
	}
}
