package com.evolvedwarfare.hell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** Abrasado (el veneno de la serpiente de fuego): pica un poco y, mientras ardes, el fuego hace más daño. */
public class ScorchedEffect extends StatusEffect {
	public ScorchedEffect() {
		super(StatusEffectCategory.HARMFUL, 0xFF7A1E);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 20 == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld world) {
			if (entity.isOnFire()) {
				entity.damage(world.getDamageSources().onFire(), 1.0F + amplifier);
			} else if (entity.age % 60 == 0 && entity.getHealth() > 1.0F) {
				entity.damage(world.getDamageSources().magic(), 1.0F);
			}
		}
		return true;
	}
}
