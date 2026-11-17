package com.evolvedwarfare.health;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;

/**
 * Veneno fuerte (fase 93): el de las arañas. A diferencia del veneno normal, <b>sí mata</b>: quita medio corazón cada dos
 * segundos (más a menudo cuanto más fuerte) y da hambre. Solo lo cura un antídoto o un botiquín; la leche no.
 */
public class VenomEffect extends StatusEffect {
	public VenomEffect() {
		super(StatusEffectCategory.HARMFUL, 0x5A8C1E);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % Math.max(10, 40 - 12 * amplifier) == 0;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.getWorld() instanceof ServerWorld world) {
			entity.damage(new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(Medicine.VENOM_DAMAGE)), 1.0F);
			if (entity instanceof PlayerEntity player) {
				player.addExhaustion(0.6F);
			}
		}
		return true;
	}
}
