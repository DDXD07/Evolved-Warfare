package com.evolvedwarfare.health;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.WoundsPayload;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * La salud al estilo RLCraft (fase 92):
 * <ul>
 * <li><b>Heridas por partes</b>: cabeza, torso, cada brazo y cada pierna tienen su propia vida, que baja con lo que les
 * da (un golpe en la pierna, a la pierna; una caída, a las dos piernas; una explosión, a todo). Un brazo o una pierna sin
 * vida <b>se rompe</b>: con una pierna rota se cojea (lento, salta poco), con el brazo derecho roto se pega y se pica
 * mucho peor, con el izquierdo se golpea más despacio. La cabeza muy dañada da mareos y, sin vida, ve borroso; el torso
 * sin vida deja sin aliento (lento y sin curarse).</li>
 * <li><b>Sangrar</b>: un golpe fuerte (un tajo, un mordisco, una flecha) puede abrir una herida que sangra: se pierde vida
 * poco a poco hasta vendarla (o hasta que se cierra sola, al rato).</li>
 * <li><b>Curarse</b>: las partes que no están rotas se curan solas, despacio, si no se sangra y se ha comido; un hueso
 * roto solo se suelda <b>entablillado</b> (unos 4 minutos). Las vendas curan una parte y cortan la sangre. Dormir en la
 * cama cura mucho.</li>
 * <li><b>Caídas</b>: hacen más daño y una caída fuerte puede romper una pierna (o las dos).</li>
 * <li><b>Regeneración lenta</b>: la comida cura mucho más despacio que en el juego normal.</li>
 * <li><b>Menos corazones</b>: se empieza con 6 corazones; cada <b>cristal de corazón</b> (de los jefes y los tesoros más
 * difíciles) da uno más, hasta 15.</li>
 * </ul>
 */
public final class Health {
	public static final AttachmentType<WoundData> WOUNDS = AttachmentRegistry.<WoundData>builder()
			.persistent(WoundData.CODEC)
			.initializer(WoundData::healthy)
			.copyOnDeath()
			.buildAndRegister(EvolvedWarfare.id("wounds"));
	public static final AttachmentType<Integer> HEARTS = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.initializer(() -> 0)
			.copyOnDeath()
			.buildAndRegister(EvolvedWarfare.id("heart_crystals"));

	public static final RegistryKey<DamageType> BLEEDING = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("bleeding"));
	private static final Identifier HEARTS_ID = EvolvedWarfare.id("heart_crystals");
	private static final Identifier LEGS_ID = EvolvedWarfare.id("wound_legs");
	private static final Identifier JUMP_ID = EvolvedWarfare.id("wound_jump");
	private static final Identifier ARM_DAMAGE_ID = EvolvedWarfare.id("wound_arm_damage");
	private static final Identifier ARM_MINING_ID = EvolvedWarfare.id("wound_arm_mining");
	private static final Identifier ARM_SPEED_ID = EvolvedWarfare.id("wound_arm_speed");
	private static final Identifier TORSO_ID = EvolvedWarfare.id("wound_torso");

	/** Lo que tarda en soldar un hueso entablillado (segundos). */
	public static final int SPLINT_SECONDS = 240;
	/** Lo que sangra una herida si no se venda (segundos) y cada cuánto quita medio corazón. */
	public static final int BLEED_SECONDS = 40, BLEED_EVERY = 4;
	/** Cada cuánto se cura solo un punto de cada parte (segundos). */
	public static final int SELF_HEAL_EVERY = 25;

	private Health() {
	}

	public static boolean enabled() {
		return EwConfig.get().woundsEnabled;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Health::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			applyMaxHealth(handler.player);
			sync(handler.player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				// se renace sano
				newPlayer.setAttached(WOUNDS, WoundData.healthy());
			}
			applyMaxHealth(newPlayer);
			newPlayer.setHealth(newPlayer.getMaxHealth());
			sync(newPlayer);
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity player && player.getWorld().isDay()) {
				restAfterSleep(player);
			}
		});
	}

	public static WoundData get(PlayerEntity player) {
		return player.getAttachedOrCreate(WOUNDS);
	}

	static void set(PlayerEntity player, WoundData data) {
		player.setAttached(WOUNDS, data);
		if (player instanceof ServerPlayerEntity sp) {
			sync(sp);
		}
	}

	public static void sync(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, WoundsPayload.ID)) {
			return;
		}
		WoundData d = get(player);
		List<Float> fractions = new ArrayList<>();
		int splinted = 0;
		for (BodyPart part : BodyPart.values()) {
			fractions.add(d.fraction(part));
			if (d.isSplinted(part)) {
				splinted |= part.bit();
			}
		}
		ServerPlayNetworking.send(player, new WoundsPayload(fractions, d.broken(), splinted, d.bleeding() > 0, enabled()));
	}

	// ------------------------------------------------------------------ los golpes

	/** Lo que hace un golpe a las partes del cuerpo (lo llama LivingEntityDamageMixin con la vida que se ha perdido). */
	public static void onHurt(LivingEntity entity, DamageSource source, @Nullable HitZone zone, @Nullable Vec3d point, float lost) {
		if (!(entity instanceof ServerPlayerEntity player) || !enabled() || lost <= 0.0F || player.isCreative() || player.isSpectator()) {
			return;
		}
		WoundData.Mutable m = get(player).edit();
		boolean fall = source.isIn(DamageTypeTags.IS_FALL);
		List<BodyPart> parts = new ArrayList<>();
		if (fall) {
			parts.add(BodyPart.RIGHT_LEG);
			parts.add(BodyPart.LEFT_LEG);
		} else if (zone != null) {
			boolean right = side(player, point);
			switch (zone) {
				case HEAD -> parts.add(BodyPart.HEAD);
				case TORSO -> parts.add(BodyPart.TORSO);
				case ARMS -> parts.add(right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
				case LEGS -> parts.add(right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG);
			}
		} else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			for (BodyPart part : BodyPart.values()) {
				parts.add(part);
			}
		} else if (source.isIn(DamageTypeTags.IS_FIRE) || source.isOf(DamageTypes.LAVA)) {
			parts.add(BodyPart.TORSO);
			parts.add(BodyPart.RIGHT_LEG);
			parts.add(BodyPart.LEFT_LEG);
			parts.add(BodyPart.RIGHT_ARM);
			parts.add(BodyPart.LEFT_ARM);
		} else if (source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.PLAYER_ATTACK) || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			// un golpe sin sitio: al azar, sobre todo al torso
			float roll = player.getRandom().nextFloat();
			parts.add(roll < 0.12F ? BodyPart.HEAD : roll < 0.5F ? BodyPart.TORSO : roll < 0.62F ? BodyPart.RIGHT_ARM : roll < 0.74F ? BodyPart.LEFT_ARM
					: roll < 0.87F ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG);
		} else if (source.isOf(BLEEDING)) {
			parts.add(BodyPart.TORSO);
		} else {
			// sed, frío, hambre, ahogarse...: al torso, poco
			parts.add(BodyPart.TORSO);
			lost *= 0.5F;
		}
		float each = lost / parts.size() * (fall ? 1.5F : 1.0F);
		boolean newlyBroken = false;
		for (BodyPart part : parts) {
			int k = part.ordinal();
			m.points[k] = Math.max(0.0F, m.points[k] - each);
			if (part.canBreak() && m.points[k] <= 0.0F && (m.broken & part.bit()) == 0) {
				m.broken |= part.bit();
				m.splint[k] = 0;
				newlyBroken = true;
			}
		}
		// una caída fuerte puede romper una pierna aunque le quede vida
		if (fall && lost >= 5.0F) {
			float chance = Math.min(0.9F, 0.15F + (lost - 5.0F) * 0.08F) * (float) EwConfig.get().boneBreakChance;
			for (BodyPart leg : new BodyPart[] {BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG}) {
				if ((m.broken & leg.bit()) == 0 && player.getRandom().nextFloat() < chance * 0.6F) {
					m.broken |= leg.bit();
					m.points[leg.ordinal()] = 0.0F;
					newlyBroken = true;
				}
			}
		}
		// los tajos, mordiscos y flechas fuertes abren heridas que sangran
		boolean sharp = source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.PLAYER_ATTACK) || source.isIn(DamageTypeTags.IS_PROJECTILE)
				|| source.isOf(DamageTypes.THORNS);
		if (sharp && lost >= 3.0F && player.getRandom().nextFloat() < Math.min(0.85F, 0.25F + (lost - 3.0F) * 0.12F)) {
			if (m.bleeding <= 0) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.health.bleeding").formatted(Formatting.RED), true);
			}
			m.bleeding = Math.max(m.bleeding, BLEED_SECONDS);
		}
		m.sinceHit = 0;
		set(player, m.done());
		if (newlyBroken) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
					SoundCategory.PLAYERS, 0.7F, 1.6F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.health.broken").formatted(Formatting.DARK_RED), true);
		}
		applyEffects(player);
	}

	/** ¿Le han dado por la derecha? (según el punto del golpe, o a suertes) */
	private static boolean side(PlayerEntity player, @Nullable Vec3d point) {
		if (point == null) {
			return player.getRandom().nextBoolean();
		}
		float yaw = player.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		double rightX = -MathHelper.cos(yaw), rightZ = -MathHelper.sin(yaw);
		return (point.x - player.getX()) * rightX + (point.z - player.getZ()) * rightZ > 0;
	}

	// ------------------------------------------------------------------ cada segundo

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			applyMaxHealth(player);
			if (!enabled() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
				clearEffects(player);
				continue;
			}
			second(player, server.getTicks() / 20);
		}
	}

	/** Un segundo de heridas (sangrar, soldar, curarse); público para las pruebas. */
	public static void second(ServerPlayerEntity player, long second) {
		WoundData before = get(player);
		WoundData.Mutable m = before.edit();
		m.sinceHit = Math.min(1000, m.sinceHit + 1);
		// sangrar
		if (m.bleeding > 0) {
			m.bleeding--;
			if (m.bleeding % BLEED_EVERY == 0) {
				RegistryEntry<DamageType> type = player.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEEDING);
				player.damage(new DamageSource(type), 1.0F);
				if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
					sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
							net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState()), player.getX(), player.getY() + 0.8, player.getZ(), 6, 0.2, 0.3,
							0.2, 0.0);
				}
			}
		}
		// los huesos entablillados van soldando
		for (BodyPart part : BodyPart.values()) {
			int k = part.ordinal();
			if ((m.broken & part.bit()) != 0 && m.splint[k] > 0) {
				m.splint[k]--;
				if (m.splint[k] == 0) {
					m.broken &= ~part.bit();
					m.points[k] = part.max * 0.5F;
					player.sendMessage(Text.translatable("message.evolvedwarfare.health.mended", Text.translatable(part.translationKey()))
							.formatted(Formatting.GREEN), true);
				}
			}
		}
		// las partes sanas se curan solas, despacio (sin sangrar, con algo en el estómago y un rato sin golpes)
		if (second % SELF_HEAL_EVERY == 0 && m.bleeding <= 0 && player.getHungerManager().getFoodLevel() >= 6 && m.sinceHit >= 10) {
			for (BodyPart part : BodyPart.values()) {
				int k = part.ordinal();
				if ((m.broken & part.bit()) == 0) {
					m.points[k] = Math.min(part.max, m.points[k] + 1.0F);
				}
			}
		}
		WoundData after = m.done();
		if (!after.equals(before)) {
			player.setAttached(WOUNDS, after);
		}
		if (!after.equals(before) || second % 5 == 0) {
			sync(player);
		}
		applyEffects(player);
		// la cabeza
		float head = after.fraction(BodyPart.HEAD);
		if (head <= 0.0F) {
			if (second % 4 == 0) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 90, 0, false, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0, false, false, true));
			}
		} else if (head <= 0.25F && second % 30 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, false, false, true));
		}
	}

	// ------------------------------------------------------------------ lo que se nota

	private static void modifier(PlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation op) {
		EntityAttributeInstance inst = player.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		EntityAttributeModifier have = inst.getModifier(id);
		if (have != null && Math.abs(have.value() - value) < 1e-6 && have.operation() == op) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(value) > 1e-6) {
			inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
		}
	}

	public static void applyEffects(PlayerEntity player) {
		WoundData d = get(player);
		int legs = 0;
		double legSlow = 0.0;
		for (BodyPart leg : new BodyPart[] {BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG}) {
			if (d.isBroken(leg)) {
				legs++;
				legSlow += d.isSplinted(leg) ? 0.15 : 0.28;
			} else if (d.fraction(leg) < 0.4F) {
				legSlow += 0.06;
			}
		}
		boolean torsoOut = d.get(BodyPart.TORSO) <= 0.0F;
		modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, LEGS_ID, -Math.min(0.7, legSlow), EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, -0.12 * legs, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ARM_DAMAGE_ID, d.isBroken(BodyPart.RIGHT_ARM) ? -0.4 : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, ARM_MINING_ID, d.isBroken(BodyPart.RIGHT_ARM) ? -0.5 : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ARM_SPEED_ID, d.isBroken(BodyPart.LEFT_ARM) ? -0.25 : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, TORSO_ID, torsoOut ? -0.2 : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearEffects(PlayerEntity player) {
		for (Identifier id : new Identifier[] {LEGS_ID, TORSO_ID}) {
			modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, id, 0, EntityAttributeModifier.Operation.ADD_VALUE);
		}
		modifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, 0, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, ARM_DAMAGE_ID, 0, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, ARM_MINING_ID, 0, EntityAttributeModifier.Operation.ADD_VALUE);
		modifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ARM_SPEED_ID, 0, EntityAttributeModifier.Operation.ADD_VALUE);
	}

	/** ¿Se cura con la comida? (con el torso sin vida o sangrando, no) */
	public static boolean canRegenerate(PlayerEntity player) {
		if (!enabled()) {
			return true;
		}
		WoundData d = get(player);
		return d.bleeding() <= 0 && d.get(BodyPart.TORSO) > 0.0F;
	}

	/** Lo que cura la comida, multiplicado (regeneración lenta). */
	public static float regenRate() {
		return (float) MathHelper.clamp(EwConfig.get().regenRate, 0.0, 1.0);
	}

	// ------------------------------------------------------------------ los corazones

	public static int crystals(PlayerEntity player) {
		return player.getAttachedOrCreate(HEARTS);
	}

	public static int hearts(PlayerEntity player) {
		EwConfig c = EwConfig.get();
		return Math.min(c.maxHearts, c.startHearts + crystals(player));
	}

	public static void applyMaxHealth(PlayerEntity player) {
		double value = (hearts(player) - 10) * 2.0;
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEARTS_ID, value, EntityAttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Un cristal de corazón: un corazón más para siempre (si no se tiene ya el máximo). */
	public static boolean addHeart(PlayerEntity player) {
		if (hearts(player) >= EwConfig.get().maxHearts) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.health.max_hearts", EwConfig.get().maxHearts).formatted(Formatting.YELLOW), true);
			return false;
		}
		player.setAttached(HEARTS, crystals(player) + 1);
		applyMaxHealth(player);
		player.heal(2.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.health.heart", hearts(player)).formatted(Formatting.LIGHT_PURPLE), true);
		return true;
	}

	// ------------------------------------------------------------------ curar

	/** Una venda: corta la sangre y cura la parte más herida que no esté rota. */
	public static boolean bandage(PlayerEntity player) {
		WoundData.Mutable m = get(player).edit();
		BodyPart worst = null;
		float lowest = 2.0F;
		for (BodyPart part : BodyPart.values()) {
			float f = m.points[part.ordinal()] / part.max;
			if ((m.broken & part.bit()) == 0 && f < lowest && f < 0.999F) {
				lowest = f;
				worst = part;
			}
		}
		if (worst == null && m.bleeding <= 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.health.nothing_to_bandage").formatted(Formatting.GRAY), true);
			return false;
		}
		m.bleeding = 0;
		if (worst != null) {
			m.points[worst.ordinal()] = Math.min(worst.max, m.points[worst.ordinal()] + 5.0F);
		}
		set(player, m.done());
		player.heal(2.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.health.bandaged",
				Text.translatable((worst == null ? BodyPart.TORSO : worst).translationKey())).formatted(Formatting.GREEN), true);
		return true;
	}

	/** Un entablillado: al primer hueso roto sin entablillar (las piernas primero). */
	public static boolean splint(PlayerEntity player) {
		WoundData.Mutable m = get(player).edit();
		for (BodyPart part : new BodyPart[] {BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM}) {
			int k = part.ordinal();
			if ((m.broken & part.bit()) != 0 && m.splint[k] <= 0) {
				m.splint[k] = SPLINT_SECONDS;
				set(player, m.done());
				applyEffects(player);
				player.sendMessage(Text.translatable("message.evolvedwarfare.health.splinted", Text.translatable(part.translationKey()))
						.formatted(Formatting.GREEN), true);
				return true;
			}
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.health.nothing_broken").formatted(Formatting.GRAY), true);
		return false;
	}

	/** Dormir toda la noche: las partes sanas se curan mucho y los huesos entablillados avanzan. */
	public static void restAfterSleep(PlayerEntity player) {
		if (!enabled()) {
			return;
		}
		WoundData.Mutable m = get(player).edit();
		for (BodyPart part : BodyPart.values()) {
			int k = part.ordinal();
			if ((m.broken & part.bit()) == 0) {
				m.points[k] = Math.min(part.max, m.points[k] + part.max * 0.6F);
			} else if (m.splint[k] > 0) {
				m.splint[k] = Math.max(1, m.splint[k] - SPLINT_SECONDS / 2);
			}
		}
		m.bleeding = 0;
		set(player, m.done());
		player.heal(6.0F);
	}

	/** Abre una herida que sangra (para pruebas y para lo que corta). */
	public static void startBleeding(PlayerEntity player, int seconds) {
		WoundData.Mutable m = get(player).edit();
		m.bleeding = Math.max(m.bleeding, seconds);
		set(player, m.done());
	}

	/** Para pruebas: todo sano. */
	public static void heal(PlayerEntity player) {
		set(player, WoundData.healthy());
		applyEffects(player);
	}
}
