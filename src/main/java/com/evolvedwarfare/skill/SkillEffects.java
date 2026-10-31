package com.evolvedwarfare.skill;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.mixin.LivingEntityAccessor;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.siege.SiegeShotEntity;
import com.evolvedwarfare.weapon.JavelinItem;
import com.evolvedwarfare.weapon.MedievalWeapons;
import com.evolvedwarfare.weapon.MusketBallEntity;
import com.evolvedwarfare.weapon.MusketItem;
import com.evolvedwarfare.weapon.WeaponType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que hacen las ventajas y de dónde sale la experiencia de cada habilidad (solo en el servidor). Lo llaman los
 * mixins del daño ({@code LivingEntityDamageMixin}: antes y después de cada golpe; {@code LivingEntitySkillMixin}:
 * esquivar y devolver golpes), el tic del servidor (atributos y auras de mando) y el escudo
 * ({@code PlayerEntitySkillMixin}).
 */
public final class SkillEffects {
	private static final Identifier ATTACK_SPEED = EvolvedWarfare.id("skill_attack_speed");
	private static final Identifier SWEEP = EvolvedWarfare.id("skill_sweep");
	private static final Identifier REACH = EvolvedWarfare.id("skill_reach");
	private static final Identifier BREAK_SPEED = EvolvedWarfare.id("skill_break_speed");
	private static final Identifier ARMOR = EvolvedWarfare.id("skill_armor");
	private static final Identifier KNOCKBACK = EvolvedWarfare.id("skill_knockback");
	private static final Identifier SPEED = EvolvedWarfare.id("skill_speed");
	/** Segundo aliento: no más de una vez por minuto. */
	private static final Map<UUID, Long> SECOND_WIND = new HashMap<>();
	private static final int SECOND_WIND_COOLDOWN = 1200;
	/** Para que el golpe en área de "Aplastar" no se dispare a sí mismo. */
	private static boolean splashing;

	private SkillEffects() {
	}

	// ------------------------------------------------------------------ armas y armaduras

	/** La habilidad con la que se usa esto (o null si no cuenta). */
	@Nullable
	public static Skill weaponSkill(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		WeaponType type = MedievalWeapons.typeOf(stack);
		if (type != null) {
			return switch (type) {
				case DAGGER, LONGSWORD -> Skill.BLADES;
				case BATTLE_AXE -> Skill.AXES;
				case WARHAMMER, FLAIL -> Skill.MACES;
				case SPEAR, HALBERD -> Skill.POLEARMS;
			};
		}
		if (stack.isIn(ItemTags.SWORDS)) {
			return Skill.BLADES;
		}
		if (stack.isIn(ItemTags.AXES)) {
			return Skill.AXES;
		}
		if (stack.isOf(Items.MACE)) {
			return Skill.MACES;
		}
		if (stack.isOf(Items.TRIDENT)) {
			return Skill.POLEARMS;
		}
		if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof JavelinItem) {
			return Skill.ARCHERY;
		}
		if (stack.getItem() instanceof MusketItem) {
			return Skill.FIREARMS;
		}
		return null;
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** ¿Es ligera? Cuero, cota de malla, oro y caparazón de tortuga; el resto (hierro, diamante, netherita...) es pesada. */
	/** ¿Es ligera? Las de las facciones, según su rango (1 y 2 ligeras; 3 y 4 pesadas); las demás, por su material. */
	public static boolean light(ArmorItem armor) {
		if (armor instanceof com.evolvedwarfare.armor.FactionArmorItem faction) {
			return faction.set().light();
		}
		return light(armor.getMaterial());
	}

	private static boolean light(RegistryEntry<ArmorMaterial> material) {
		return material == ArmorMaterials.LEATHER || material == ArmorMaterials.CHAIN || material == ArmorMaterials.GOLD
				|| material == ArmorMaterials.TURTLE || material.equals(ArmorMaterials.LEATHER) || material.equals(ArmorMaterials.CHAIN)
				|| material.equals(ArmorMaterials.GOLD) || material.equals(ArmorMaterials.TURTLE);
	}

	public static int heavyPieces(LivingEntity entity) {
		int n = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (entity.getEquippedStack(slot).getItem() instanceof ArmorItem armor && !light(armor)) {
				n++;
			}
		}
		return n;
	}

	public static int lightPieces(LivingEntity entity) {
		int n = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (entity.getEquippedStack(slot).getItem() instanceof ArmorItem armor && light(armor)) {
				n++;
			}
		}
		return n;
	}

	private static float pct(ServerPlayerEntity player, Perk perk) {
		return Skills.value(player, perk) / 100.0F;
	}

	private static boolean roll(LivingEntity entity, float chance) {
		return chance > 0.0F && entity.getRandom().nextFloat() < chance;
	}

	/** Golpe crítico de vanilla (cayendo, sin estar en el suelo, en el agua, trepando ni montado). */
	private static boolean vanillaCrit(ServerPlayerEntity player) {
		return player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasVehicle();
	}

	private static boolean behind(Entity attacker, LivingEntity target) {
		return MedievalWeapons.behind(attacker, target);
	}

	private static boolean inFront(LivingEntity of, Entity other) {
		Vec3d look = Vec3d.fromPolar(0.0F, of.getHeadYaw());
		Vec3d to = other.getPos().subtract(of.getPos());
		to = new Vec3d(to.x, 0.0, to.z);
		return to.lengthSquared() > 1.0E-4 && to.normalize().dotProduct(look) > 0.0;
	}

	private static void critParticles(LivingEntity target) {
		if (target.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		}
	}

	// ------------------------------------------------------------------ antes del golpe

	/**
	 * Cambia el daño antes de la armadura: los bonus del que golpea (por su arma) y del que recibe (caídas). Si el golpe
	 * lo va a parar un escudo, da la experiencia de Escudo y sus ventajas (contraataque, golpe de escudo).
	 */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount, @Nullable HitZone zone) {
		if (target.getWorld().isClient || amount <= 0.0F || source.isOf(DamageTypes.THORNS)) {
			return amount;
		}
		float out = amount;
		Entity attacker = source.getAttacker();
		Entity direct = source.getSource();
		if (attacker instanceof ServerPlayerEntity player && attacker != target) {
			if (direct == player) {
				out = melee(player, target, out);
			} else if (direct instanceof MusketBallEntity) {
				out *= 1.0F + pct(player, Perk.FINE_POWDER);
				if (MusketItem.isAiming(player)) {
					out *= 1.0F + pct(player, Perk.STEADY_AIM);
				}
				if (zone == HitZone.HEAD) {
					out *= 1.0F + pct(player, Perk.SHARPSHOOTER);
				}
			} else if (direct instanceof PersistentProjectileEntity) {
				out *= 1.0F + pct(player, Perk.OVERDRAW);
				if (target.getType().getSpawnGroup() == SpawnGroup.CREATURE || target.getType().getSpawnGroup() == SpawnGroup.WATER_CREATURE) {
					out *= 1.0F + pct(player, Perk.HUNTER);
				}
				if (roll(player, pct(player, Perk.CRIT_SHOT))) {
					out *= 1.5F;
					critParticles(target);
				}
				if (zone == HitZone.HEAD) {
					out *= 1.0F + pct(player, Perk.BULLSEYE);
				}
			}
		}
		if (target instanceof ServerPlayerEntity player) {
			if (source.isIn(DamageTypeTags.IS_FALL)) {
				int heavy = heavyPieces(player);
				if (heavy > 0) {
					out *= 1.0F - pct(player, Perk.CUSHIONED);
				} else if (lightPieces(player) > 0) {
					out *= 1.0F - pct(player, Perk.CAT_FALL);
				}
			}
			if (attacker != null && attacker != player && ((LivingEntityAccessor) player).evolvedwarfare$blockedByShield(source)) {
				blocked(player, source, out);
			}
		}
		if (target instanceof AbstractNpcEntity npc && npc.getOwnerUuid() != null && target.getServer() != null) {
			ServerPlayerEntity owner = target.getServer().getPlayerManager().getPlayer(npc.getOwnerUuid());
			if (owner != null && owner.getWorld() == target.getWorld() && owner.isBlocking() && owner.squaredDistanceTo(target) < 36.0) {
				out *= 1.0F - pct(owner, Perk.SHIELD_WALL);
			}
		}
		return out;
	}

	private static float melee(ServerPlayerEntity player, LivingEntity target, float amount) {
		float out = amount;
		Skill skill = weaponSkill(player.getMainHandStack());
		if (skill != null) {
			switch (skill) {
				case BLADES -> {
					out *= 1.0F + pct(player, Perk.BLADE_MASTERY);
					if (behind(player, target)) {
						out *= 1.0F + pct(player, Perk.BACKSTAB);
					}
					if (roll(player, pct(player, Perk.BLADE_CRIT))) {
						out *= 1.5F;
						critParticles(target);
					}
				}
				case AXES -> {
					out *= 1.0F + pct(player, Perk.AXE_MASTERY);
					if (vanillaCrit(player)) {
						out *= 1.0F + pct(player, Perk.HEAVY_CHOP);
					}
					if (target.hasStatusEffect(ModEffects.BLEEDING)) {
						out *= 1.0F + pct(player, Perk.BUTCHER);
					}
				}
				case MACES -> {
					out *= 1.0F + pct(player, Perk.MACE_MASTERY);
					out += target.getArmor() * pct(player, Perk.BONEBREAKER);
					if (target.getHealth() < target.getMaxHealth() * 0.3F) {
						out *= 1.0F + pct(player, Perk.EXECUTIONER);
					}
				}
				case POLEARMS -> {
					out *= 1.0F + pct(player, Perk.POLE_MASTERY);
					if (player.isSprinting()) {
						out *= 1.0F + pct(player, Perk.LUNGE);
					}
					if (target.hasVehicle() || target instanceof AbstractHorseEntity) {
						out *= 1.0F + pct(player, Perk.ANTI_CAVALRY);
					}
				}
				default -> {
				}
			}
		}
		if (heavyPieces(player) == 4) {
			out *= 1.0F + pct(player, Perk.CONQUEROR);
		}
		return out;
	}

	/** Un golpe que va a parar el escudo: experiencia, contraataque y golpe de escudo. */
	private static void blocked(ServerPlayerEntity player, DamageSource source, float amount) {
		Skills.gain(player, Skill.BLOCK, amount * 1.5F);
		Entity attacker = source.getAttacker();
		if (source.getSource() == attacker && attacker instanceof LivingEntity living) {
			float counter = pct(player, Perk.COUNTER);
			if (counter > 0.0F) {
				living.damage(player.getDamageSources().thorns(player), amount * counter);
			}
			if (Skills.has(player, Perk.SHIELD_BASH)) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, (int) (Skills.value(player, Perk.SHIELD_BASH) * 20), 1),
						player);
			}
		}
	}

	/** Esquivar (armadura ligera) o devolver el golpe (armadura pesada): el golpe no llega. */
	public static boolean avoids(LivingEntity target, DamageSource source, float amount) {
		if (target.getWorld().isClient || !(target instanceof ServerPlayerEntity player) || amount <= 0.0F) {
			return false;
		}
		Entity attacker = source.getAttacker();
		if (attacker == null || attacker == player || source.isOf(DamageTypes.THORNS) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| player.isInvulnerableTo(source) || ((LivingEntityAccessor) player).evolvedwarfare$blockedByShield(source)) {
			return false;
		}
		int heavy = heavyPieces(player);
		if (heavy == 0 && lightPieces(player) >= 2 && roll(player, pct(player, Perk.EVASION))) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
					SoundCategory.PLAYERS, 1.0F, 1.3F);
			if (player.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.5), player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			}
			return true;
		}
		if (heavy >= 2 && source.getSource() == attacker && attacker instanceof LivingEntity living && roll(player, pct(player, Perk.REFLECT_BLOWS))) {
			living.damage(player.getDamageSources().thorns(player), amount);
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS,
					1.0F, 0.8F);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ después del golpe

	/** Ha entrado el golpe: experiencia y lo que causa (sangrado, aturdir, empujar...). */
	public static void afterDamage(LivingEntity target, DamageSource source, float amount, @Nullable HitZone zone) {
		if (target.getWorld().isClient || amount <= 0.0F || source.isOf(DamageTypes.THORNS) || target.getServer() == null) {
			return;
		}
		Entity attacker = source.getAttacker();
		Entity direct = source.getSource();
		if (attacker instanceof ServerPlayerEntity player && attacker != target) {
			if (direct == player) {
				Skill skill = weaponSkill(player.getMainHandStack());
				if (skill != null && skill.group == Skill.Group.MELEE) {
					Skills.gain(player, skill, amount);
					onMeleeHit(player, target, skill, amount);
				}
			} else if (direct instanceof MusketBallEntity) {
				Skills.gain(player, Skill.FIREARMS, amount * 0.8F);
				if (Skills.has(player, Perk.THUNDER)) {
					Vec3d push = target.getPos().subtract(player.getPos());
					target.takeKnockback(1.2, -push.x, -push.z);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), player);
				}
			} else if (direct instanceof SiegeShotEntity) {
				Skills.gain(player, Skill.SIEGE, amount * 0.5F);
			} else if (direct instanceof PersistentProjectileEntity projectile) {
				Skills.gain(player, Skill.ARCHERY, amount * 1.2F);
				if (projectile instanceof ArrowEntity && projectile.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED
						&& !player.getAbilities().creativeMode && roll(player, pct(player, Perk.RECOVERY))) {
					player.getInventory().offerOrDrop(new ItemStack(Items.ARROW));
				}
			}
		} else if (attacker instanceof AbstractNpcEntity npc && npc.getOwnerUuid() != null) {
			ServerPlayerEntity owner = target.getServer().getPlayerManager().getPlayer(npc.getOwnerUuid());
			if (owner != null) {
				Skills.gain(owner, Skill.LEADERSHIP, amount * 0.3F);
			}
		}
		if (target instanceof ServerPlayerEntity player && attacker != null && attacker != player) {
			int heavy = heavyPieces(player);
			int light = lightPieces(player);
			if (heavy > 0) {
				Skills.gain(player, Skill.HEAVY_ARMOR, amount * heavy / 4.0F);
			}
			if (light > 0) {
				Skills.gain(player, Skill.LIGHT_ARMOR, amount * light / 4.0F);
			}
			if (direct == attacker && attacker instanceof LivingEntity living && Skills.has(player, Perk.PIKE_WALL)
					&& weaponSkill(player.getMainHandStack()) == Skill.POLEARMS && inFront(player, living)) {
				living.takeKnockback(0.9, player.getX() - living.getX(), player.getZ() - living.getZ());
			}
			if (Skills.has(player, Perk.SECOND_WIND) && player.isAlive() && player.getHealth() < player.getMaxHealth() * 0.3F) {
				long now = player.getServerWorld().getTime();
				Long last = SECOND_WIND.get(player.getUuid());
				if (last == null || now - last > SECOND_WIND_COOLDOWN) {
					SECOND_WIND.put(player.getUuid(), now);
					int ticks = (int) (Skills.value(player, Perk.SECOND_WIND) * 20);
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 1));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, ticks, 0));
				}
			}
		}
	}

	private static void onMeleeHit(ServerPlayerEntity player, LivingEntity target, Skill skill, float amount) {
		switch (skill) {
			case AXES -> {
				int bleed = Skills.rank(player, Perk.BLEED);
				if (bleed > 0 && target.isAlive()) {
					target.addStatusEffect(new StatusEffectInstance(ModEffects.BLEEDING, 60, bleed - 1), player);
				}
				if (!target.isAlive() && Skills.has(player, Perk.FURY)) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, (int) (Skills.value(player, Perk.FURY) * 20), 0));
				}
			}
			case MACES -> {
				if (target.isAlive() && roll(player, pct(player, Perk.STUN))) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), player);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0), player);
				}
				boolean crit = vanillaCrit(player);
				if (crit && target.isAlive() && Skills.has(player, Perk.CONCUSSION)) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0), player);
					target.takeKnockback(1.0, player.getX() - target.getX(), player.getZ() - target.getZ());
				}
				float crush = pct(player, Perk.CRUSH);
				if (crit && crush > 0.0F && !splashing) {
					splashing = true;
					try {
						List<LivingEntity> around = target.getWorld().getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(2.0),
								e -> e != player && e != target && e.isAlive() && !NpcSides.allies(player, e));
						for (LivingEntity other : around) {
							other.damage(player.getDamageSources().playerAttack(player), amount * crush);
						}
					} finally {
						splashing = false;
					}
				}
			}
			case POLEARMS -> {
				if (player.isSprinting() && target.isAlive() && Skills.has(player, Perk.IMPALE)) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, (int) (Skills.value(player, Perk.IMPALE) * 20), 3),
							player);
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ cada poco

	public static void tick(MinecraftServer server) {
		int t = server.getTicks();
		if (t % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			refresh(player);
			if (t % 60 == 0) {
				auras(player);
			}
		}
	}

	/** Los atributos que dan las ventajas según lo que lleva puesto y en la mano. */
	public static void refresh(ServerPlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		Skill held = weaponSkill(main);
		int heavy = heavyPieces(player);
		int light = lightPieces(player);
		double armor = 0.0;
		if (heavy > 0) {
			armor += pct(player, Perk.JUGGERNAUT) * heavy / 4.0 + (heavy == 4 ? pct(player, Perk.WELL_FITTED) : 0.0);
		}
		if (light > 0) {
			armor += pct(player, Perk.AGILE_DEFENSE) * light / 4.0 + (light == 4 ? pct(player, Perk.CUSTOM_FIT) : 0.0);
		}
		double speed = (light > 0 && heavy == 0 ? pct(player, Perk.LIGHT_STEP) : 0.0)
				+ (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem ? pct(player, Perk.RANGER) : 0.0);
		set(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED, held == Skill.BLADES ? pct(player, Perk.BLADE_DANCE) : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, EntityAttributes.PLAYER_SWEEPING_DAMAGE_RATIO, SWEEP, held == Skill.BLADES ? pct(player, Perk.WHIRLWIND) : 0.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, REACH, held == Skill.POLEARMS ? Skills.value(player, Perk.KEEP_AT_BAY) : 0.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, BREAK_SPEED, held == Skill.AXES ? pct(player, Perk.LUMBERJACK) : 0.0,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, EntityAttributes.GENERIC_ARMOR, ARMOR, armor, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK, heavy >= 2 ? pct(player, Perk.STEADFAST) : 0.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		set(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED, speed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void set(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (value == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current != null && Math.abs(current.value() - value) < 1.0E-6 && current.operation() == operation) {
			return;
		}
		instance.removeModifier(id);
		instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
	}

	/** Inspirar y Estandarte: fuerza y resistencia a tus soldados cerca de ti. */
	private static void auras(ServerPlayerEntity player) {
		boolean inspire = Skills.has(player, Perk.INSPIRE);
		boolean banner = Skills.has(player, Perk.BANNER);
		if (!inspire && !banner) {
			return;
		}
		double radius = Math.max(12.0, Skills.value(player, Perk.INSPIRE));
		UUID owner = player.getUuid();
		for (AbstractNpcEntity npc : player.getServerWorld().getEntitiesByClass(AbstractNpcEntity.class, player.getBoundingBox().expand(radius),
				e -> owner.equals(e.getOwnerUuid()) && e.isAlive())) {
			if (inspire) {
				npc.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, false));
			}
			if (banner) {
				npc.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0, true, false));
			}
		}
	}
}
