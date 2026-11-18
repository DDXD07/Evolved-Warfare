package com.evolvedwarfare.skill;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.network.EngramPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * Los atributos del superviviente (fase 114): los puntos que da cada nivel se reparten en la pantalla K. Vida, daño,
 * velocidad y oxígeno van como modificadores de los atributos del juego; aguante, fortaleza, metabolismo y carga los
 * miran sus sistemas (el aguante, el daño que se recibe, el hambre y la sed, la mochila).
 */
public final class SurvivorAttributes {
	private static final Identifier HEALTH_ID = EvolvedWarfare.id("survivor_health");
	private static final Identifier MELEE_ID = EvolvedWarfare.id("survivor_melee");
	private static final Identifier SPEED_ID = EvolvedWarfare.id("survivor_speed");
	private static final Identifier OXYGEN_ID = EvolvedWarfare.id("survivor_oxygen");

	private SurvivorAttributes() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(EngramPayloads.SpendAttribute.ID, EngramPayloads.SpendAttribute.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EngramPayloads.SpendAttribute.ID, (payload, context) -> {
			SurvivorAttribute a = SurvivorAttribute.byId(payload.attribute());
			if (a != null) {
				spend(context.player(), a);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			apply(newPlayer);
			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});
	}

	/** Los puntos de un atributo (en el servidor; en el cliente, 0). */
	public static int points(PlayerEntity player, SurvivorAttribute a) {
		if (player.getWorld().isClient || player.getServer() == null) {
			return 0;
		}
		SkillData data = SkillState.get(player.getServer()).peek(player.getUuid());
		return data == null ? 0 : data.attribute(a);
	}

	public static double value(PlayerEntity player, SurvivorAttribute a) {
		return a.value(points(player, a));
	}

	public enum Spend {
		OK, POINTS
	}

	public static Spend spend(ServerPlayerEntity player, SurvivorAttribute a) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		if (data.attributePoints <= 0) {
			player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.6F);
			Skills.sync(player, false);
			return Spend.POINTS;
		}
		data.attributePoints--;
		data.attributes[a.ordinal()]++;
		state.markDirty();
		float before = player.getMaxHealth();
		apply(player);
		if (a == SurvivorAttribute.HEALTH) {
			// lo que sube la vida máxima, también la de ahora
			player.heal(player.getMaxHealth() - before);
		}
		player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 1.2F);
		Skills.sync(player, false);
		return Spend.OK;
	}

	/** Devuelve todos los puntos (comando). */
	public static void reset(ServerPlayerEntity player) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		for (int i = 0; i < data.attributes.length; i++) {
			data.attributePoints += data.attributes[i];
			data.attributes[i] = 0;
		}
		state.markDirty();
		apply(player);
		Skills.sync(player, false);
	}

	/** Pone los modificadores de vida, daño, velocidad y oxígeno según sus puntos. */
	public static void apply(ServerPlayerEntity player) {
		if (player.getServer() == null) {
			return;
		}
		SkillData data = SkillState.get(player.getServer()).of(player.getUuid());
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, SurvivorAttribute.HEALTH.value(data.attribute(SurvivorAttribute.HEALTH)) * 2.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MELEE_ID, SurvivorAttribute.MELEE.value(data.attribute(SurvivorAttribute.MELEE)),
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, SurvivorAttribute.SPEED.value(data.attribute(SurvivorAttribute.SPEED)),
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.GENERIC_OXYGEN_BONUS, OXYGEN_ID, SurvivorAttribute.OXYGEN.value(data.attribute(SurvivorAttribute.OXYGEN)),
				EntityAttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void modifier(PlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation op) {
		EntityAttributeInstance inst = player.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		EntityAttributeModifier have = inst.getModifier(id);
		if (value == 0.0) {
			if (have != null) {
				inst.removeModifier(id);
			}
			return;
		}
		if (have == null || Math.abs(have.value() - value) > 1.0E-6) {
			inst.removeModifier(id);
			inst.addPersistentModifier(new EntityAttributeModifier(id, value, op));
		}
	}

	// ------------------------------------------------------------------ lo que miran los demás sistemas

	/** Lo que cuesta el aguante (1 = normal). */
	public static float staminaCost(PlayerEntity player) {
		return 1.0F - (float) value(player, SurvivorAttribute.STAMINA);
	}

	/** Lo rápido que se recupera el aguante (1 = normal). */
	public static float staminaRegen(PlayerEntity player) {
		return 1.0F + (float) value(player, SurvivorAttribute.STAMINA);
	}

	/** Lo que bajan el hambre y la sed (1 = normal). */
	public static float metabolism(PlayerEntity player) {
		return 1.0F - (float) value(player, SurvivorAttribute.METABOLISM);
	}

	/** Lo que frena la carga pesada (1 = normal). */
	public static double weight(PlayerEntity player) {
		return 1.0 - value(player, SurvivorAttribute.WEIGHT);
	}

	/** Daño que recibe: la fortaleza quita parte del de sangrado, veneno, infección, frío, calor, sed y hambre. */
	public static float modifyIncoming(PlayerEntity player, DamageSource source, float amount) {
		if (amount <= 0.0F || !harsh(source)) {
			return amount;
		}
		return amount * (1.0F - (float) value(player, SurvivorAttribute.FORTITUDE));
	}

	private static boolean harsh(DamageSource source) {
		if (source.isOf(DamageTypes.FREEZE) || source.isOf(DamageTypes.STARVE) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.HOT_FLOOR)
				|| source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.MAGIC) && source.getAttacker() == null) {
			return true;
		}
		return source.getTypeRegistryEntry().getKey().map(k -> {
			Identifier id = k.getValue();
			if (!EvolvedWarfare.MOD_ID.equals(id.getNamespace())) {
				return false;
			}
			return switch (id.getPath()) {
				case "bleeding", "venom", "infection", "parasites", "dehydration", "hypothermia", "heatstroke" -> true;
				default -> false;
			};
		}).orElse(false);
	}
}
