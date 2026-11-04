package com.evolvedwarfare.night;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Infección (de un mordisco de zombi): da hambre y quita medio corazón cada 6 segundos (cada vez más a menudo según
 * empeora), con debilidad y, en el peor grado, lentitud y náuseas. No mata sola (se para con medio corazón). Se cura con
 * leche o una manzana de oro; si no, se pasa con el tiempo.
 */
public class InfectionEffect extends StatusEffect {
	public InfectionEffect() {
		super(StatusEffectCategory.HARMFUL, 0x4E7A3A);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % Math.max(40, 120 - 30 * amplifier) == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity instanceof PlayerEntity player) {
			player.addExhaustion(2.0F * (amplifier + 1));
		}
		if (entity.getWorld() instanceof ServerWorld world) {
			if (entity.getHealth() > 1.0F) {
				entity.damage(NightThreat.infectionSource(world), 1.0F);
			}
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, Math.min(1, amplifier), true, false, true));
			if (amplifier >= 2) {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 0, true, false, true));
				if (world.random.nextInt(4) == 0) {
					entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140, 0, true, false, true));
				}
			}
		}
		return true;
	}
}
