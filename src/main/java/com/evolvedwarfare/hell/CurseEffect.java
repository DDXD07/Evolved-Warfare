package com.evolvedwarfare.hell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Las maldiciones del Nether (duran hasta salir de él): fiebre (pega más despacio), sed de sal (da hambre), ojos del ídolo
 * (te ciegan a ratos) y calor del juicio (te quema de vez en cuando).
 */
public class CurseEffect extends StatusEffect {
	public enum Kind {
		FEVER, SALT_THIRST, IDOL_EYES, JUDGEMENT_HEAT
	}

	private final Kind kind;

	public CurseEffect(Kind kind, int color) {
		super(StatusEffectCategory.HARMFUL, color);
		this.kind = kind;
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.getWorld().isClient) {
			return true;
		}
		switch (kind) {
			case SALT_THIRST -> {
				if (entity instanceof PlayerEntity player) {
					player.addExhaustion(0.012F * (amplifier + 1));
				}
			}
			case IDOL_EYES -> {
				if (entity.age % 400 == 0) {
					entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
				}
			}
			case JUDGEMENT_HEAT -> {
				if (entity.age % 240 == 0 && entity.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
					entity.damage(Curses.judgement(sw), 1.0F + amplifier);
					entity.setOnFireFor(1.0F);
				}
			}
			default -> {
			}
		}
		return true;
	}
}
