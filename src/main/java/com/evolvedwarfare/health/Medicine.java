package com.evolvedwarfare.health;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.registry.ModEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * La medicina (fase 93): las heridas que sangran sin desinfectar se pueden <b>infectar</b>, y la infección sin tratar
 * <b>empeora</b>; las arañas <b>envenenan</b> (un veneno que sí mata); y la <b>leche ya no lo cura todo</b> (ni la infección
 * ni el veneno fuerte). Para eso están el aguardiente (desinfecta y baja la infección), la medicina (la cura), el antídoto
 * (el veneno) y el botiquín (todo a la vez).
 */
public final class Medicine {
	public static final RegistryKey<DamageType> VENOM_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("venom"));
	/** Cada cuánto empeora una infección sin tratar (segundos). */
	public static final int WORSEN_EVERY = 300;

	private Medicine() {
	}

	public static boolean enabled() {
		return EwConfig.get().medicineEnabled;
	}

	// ------------------------------------------------------------------ venenos

	/** Lo que hace un golpe: las arañas envenenan (la de cueva, siempre y más fuerte). */
	public static void onHurt(LivingEntity entity, DamageSource source, float lost) {
		if (!(entity instanceof ServerPlayerEntity player) || !enabled() || lost <= 0.0F || player.getAbilities().creativeMode) {
			return;
		}
		Entity attacker = source.getSource();
		if (attacker instanceof CaveSpiderEntity) {
			envenom(player, 1, 20 * 12);
		} else if (attacker instanceof SpiderEntity && player.getRandom().nextInt(100) < EwConfig.get().venomChance) {
			envenom(player, 0, 20 * 15);
		}
	}

	public static void envenom(PlayerEntity player, int amplifier, int ticks) {
		StatusEffectInstance current = player.getStatusEffect(ModEffects.VENOM);
		int amp = current == null ? amplifier : Math.min(2, Math.max(amplifier, current.getAmplifier() + 1));
		player.addStatusEffect(new StatusEffectInstance(ModEffects.VENOM, Math.max(ticks, current == null ? 0 : current.getDuration()), amp));
		if (current == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.venom").formatted(Formatting.DARK_GREEN), true);
		}
	}

	// ------------------------------------------------------------------ la infección de las heridas

	/** Cada segundo (desde {@link Health}): una herida que sangra se puede infectar y una infección sin tratar empeora. */
	static void second(ServerPlayerEntity player, boolean bleeding, long second) {
		if (!enabled()) {
			return;
		}
		boolean clean = player.hasStatusEffect(ModEffects.DISINFECTED);
		StatusEffectInstance infection = player.getStatusEffect(ModEffects.INFECTION);
		if (bleeding && !clean && infection == null
				&& player.getRandom().nextFloat() < EwConfig.get().woundInfectionChance / 100.0F / 60.0F) {
			player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, 20 * 60 * 15, 0));
			player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.wound_infected").formatted(Formatting.DARK_GREEN), true);
		} else if (infection != null && !clean && second % WORSEN_EVERY == 0 && infection.getAmplifier() < 2) {
			player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, Math.max(infection.getDuration(), 20 * 60 * 10),
					infection.getAmplifier() + 1));
			player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.worse").formatted(Formatting.DARK_GREEN), true);
		}
	}

	// ------------------------------------------------------------------ la leche

	/** Lo que la leche ya no quita (se vuelve a poner después de beberla). */
	public static List<StatusEffectInstance> milkResistant(LivingEntity entity) {
		List<StatusEffectInstance> kept = new ArrayList<>();
		if (!enabled()) {
			return kept;
		}
		for (RegistryEntry<StatusEffect> effect : List.of(ModEffects.INFECTION, ModEffects.VENOM)) {
			StatusEffectInstance have = entity.getStatusEffect(effect);
			if (have != null) {
				kept.add(new StatusEffectInstance(have));
			}
		}
		return kept;
	}

	// ------------------------------------------------------------------ los remedios

	/** Aguardiente: marea un poco, desinfecta las heridas (3 minutos) y baja un grado la infección. */
	public static boolean alcohol(PlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20 * 10, 0));
		player.addStatusEffect(new StatusEffectInstance(ModEffects.DISINFECTED, 20 * 60 * 3, 0));
		StatusEffectInstance infection = player.getStatusEffect(ModEffects.INFECTION);
		if (infection != null) {
			player.removeStatusEffect(ModEffects.INFECTION);
			if (infection.getAmplifier() > 0) {
				player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, infection.getDuration(), infection.getAmplifier() - 1));
			}
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.alcohol").formatted(Formatting.GOLD), true);
		return true;
	}

	/** Medicina: cura la infección y los parásitos. */
	public static boolean medicine(PlayerEntity player) {
		player.removeStatusEffect(ModEffects.INFECTION);
		player.removeStatusEffect(ModEffects.PARASITES);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.DISINFECTED, 20 * 60, 0));
		player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.medicine").formatted(Formatting.GREEN), true);
		return true;
	}

	/** Antídoto: quita el veneno (el fuerte y el normal). */
	public static boolean antidote(PlayerEntity player) {
		player.removeStatusEffect(ModEffects.VENOM);
		player.removeStatusEffect(StatusEffects.POISON);
		player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.antidote").formatted(Formatting.GREEN), true);
		return true;
	}

	/** Botiquín: corta la sangre, cura todas las partes, entablilla los huesos rotos y quita infección y venenos. */
	public static boolean medkit(PlayerEntity player) {
		boolean need = Health.get(player).isHurt() || Health.get(player).broken() != 0 || Health.get(player).bleeding() > 0
				|| player.getHealth() < player.getMaxHealth() || player.hasStatusEffect(ModEffects.INFECTION) || player.hasStatusEffect(ModEffects.VENOM)
				|| player.hasStatusEffect(StatusEffects.POISON);
		if (!need) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.nothing").formatted(Formatting.GRAY), true);
			return false;
		}
		Health.treatAll(player, 8.0F);
		player.removeStatusEffect(ModEffects.INFECTION);
		player.removeStatusEffect(ModEffects.VENOM);
		player.removeStatusEffect(StatusEffects.POISON);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.DISINFECTED, 20 * 60 * 3, 0));
		player.heal(8.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.medicine.medkit").formatted(Formatting.GREEN), true);
		return true;
	}
}
