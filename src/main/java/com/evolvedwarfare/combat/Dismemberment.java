package com.evolvedwarfare.combat;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.network.SeveredPayload;
import com.evolvedwarfare.registry.ModEffects;
import com.evolvedwarfare.weapon.JavelinEntity;
import com.evolvedwarfare.weapon.MedievalWeaponItem;
import com.evolvedwarfare.weapon.MusketBallEntity;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Desmembramiento de los NPCs del mod (reclutas, soldados, guardias, trabajadores, comerciantes, bandidos, vikingos y
 * piratas). Con el daño locacional se sabe dónde da cada golpe: en un brazo o una pierna, un arma de filo puede
 * <b>cortarlo</b> (más probable cuanto más fuerte el golpe, más cortante el arma y más herido esté); en la cabeza, con
 * mucha suerte, <b>decapitarlo</b> (más con un golpe crítico, cayendo). Lo cortado sale volando y queda en el suelo un
 * rato, sangra y el NPC ya no puede hacer lo que necesita esa parte:
 * <ul>
 * <li><b>Brazo derecho</b>: suelta el arma; pelea a puñetazos con el otro. <b>Brazo izquierdo</b>: suelta el escudo.</li>
 * <li><b>Cualquier brazo</b>: ya no puede usar el arco, la ballesta ni el mosquete (hacen falta las dos manos).</li>
 * <li><b>Los dos brazos</b>: no puede pelear, ni curar, ni trabajar; huye de quien le ataca.</li>
 * <li><b>Una pierna</b>: va a un 40 % de velocidad y no puede saltar; <b>las dos</b>: se arrastra (12 %).</li>
 * <li><b>La cabeza</b>: muere en el acto.</li>
 * </ul>
 * Es para siempre (no vuelve a crecer). Lo que le falta se guarda con él y se manda a quien lo ve.
 */
public final class Dismemberment {
	public static final int RIGHT_ARM = 1;
	public static final int LEFT_ARM = 2;
	public static final int RIGHT_LEG = 4;
	public static final int LEFT_LEG = 8;
	public static final int HEAD = 16;

	public static final AttachmentType<Integer> SEVERED = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.initializer(() -> 0)
			.buildAndRegister(EvolvedWarfare.id("severed"));
	public static final RegistryKey<DamageType> DECAPITATION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("decapitation"));
	private static final Identifier LEGS_MODIFIER = EvolvedWarfare.id("severed_legs");

	private Dismemberment() {
	}

	public static void register() {
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof LivingEntity living && severed(living) != 0) {
				send(viewer, new SeveredPayload(living.getId(), severed(living)));
			}
		});
	}

	public static boolean enabled() {
		return EwConfig.get().dismemberment;
	}

	/** ¿Es un NPC del mod (de los que se pueden desmembrar)? */
	public static boolean isNpc(Entity e) {
		return e instanceof AbstractNpcEntity || e instanceof FactionNpcEntity || e instanceof TraderEntity;
	}

	// ------------------------------------------------------------------ lo que le falta

	public static int severed(LivingEntity e) {
		Integer v = e.getAttached(SEVERED);
		return v == null ? 0 : v;
	}

	public static boolean isSevered(LivingEntity e, int part) {
		return (severed(e) & part) != 0;
	}

	public static boolean hasAnArm(LivingEntity e) {
		return !isSevered(e, RIGHT_ARM) || !isSevered(e, LEFT_ARM);
	}

	public static boolean hasBothArms(LivingEntity e) {
		return !isSevered(e, RIGHT_ARM) && !isSevered(e, LEFT_ARM);
	}

	public static int missingLegs(LivingEntity e) {
		return (isSevered(e, RIGHT_LEG) ? 1 : 0) + (isSevered(e, LEFT_LEG) ? 1 : 0);
	}

	/** ¿No tiene la mano de ese hueco? (lo que se le dé se cae al suelo) */
	public static boolean noHandFor(LivingEntity e, EquipmentSlot slot) {
		return slot == EquipmentSlot.MAINHAND && isSevered(e, RIGHT_ARM) || slot == EquipmentSlot.OFFHAND && isSevered(e, LEFT_ARM);
	}

	/** Le corta esa parte: suelta lo de esa mano, las piernas lo frenan, y se lo cuenta a quien lo ve. */
	public static void sever(LivingEntity e, int part) {
		int mask = severed(e) | part;
		e.setAttached(SEVERED, mask);
		if (!(e.getWorld() instanceof ServerWorld)) {
			return;
		}
		if ((part & RIGHT_ARM) != 0 && !e.getMainHandStack().isEmpty()) {
			e.dropStack(e.getMainHandStack().copy());
			e.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		if ((part & LEFT_ARM) != 0 && !e.getOffHandStack().isEmpty()) {
			e.dropStack(e.getOffHandStack().copy());
			e.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		EntityAttributeInstance speed = e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(LEGS_MODIFIER);
			int legs = missingLegs(e);
			if (legs > 0) {
				speed.addPersistentModifier(new EntityAttributeModifier(LEGS_MODIFIER, legs == 1 ? -0.6 : -0.88,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		SeveredPayload payload = new SeveredPayload(e.getId(), mask);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(e)) {
			send(viewer, payload);
		}
	}

	private static void send(ServerPlayerEntity to, SeveredPayload payload) {
		if (ServerPlayNetworking.canSend(to, SeveredPayload.ID)) {
			ServerPlayNetworking.send(to, payload);
		}
	}

	// ------------------------------------------------------------------ el golpe

	/** Lo cortante que es lo que ha dado el golpe (0: no corta nada). */
	public static float sharpness(DamageSource source) {
		Entity direct = source.getSource();
		if (direct instanceof MusketBallEntity) {
			return 0.55F;
		}
		if (direct instanceof JavelinEntity) {
			return 0.4F;
		}
		if (direct instanceof PersistentProjectileEntity) {
			return 0.12F;
		}
		if (direct instanceof ProjectileEntity || !(direct instanceof LivingEntity attacker) || direct != source.getAttacker()) {
			return 0.0F;
		}
		ItemStack stack = attacker.getMainHandStack();
		if (stack.getItem() instanceof MedievalWeaponItem weapon) {
			return switch (weapon.getType()) {
				case DAGGER -> 0.6F;
				case SPEAR -> 0.35F;
				case LONGSWORD -> 1.3F;
				case HALBERD -> 1.45F;
				case BATTLE_AXE -> 1.6F;
				case WARHAMMER -> 0.25F;
				case FLAIL -> 0.3F;
			};
		}
		if (stack.getItem() instanceof com.evolvedwarfare.weapon.BattleAxeItem) {
			return 1.6F;
		}
		if (stack.getItem() instanceof AxeItem) {
			return 1.3F;
		}
		if (stack.getItem() instanceof SwordItem) {
			return 1.0F;
		}
		if (stack.getItem() instanceof TridentItem) {
			return 0.4F;
		}
		if (stack.getItem() instanceof MaceItem || stack.getItem() instanceof ShearsItem) {
			return 0.2F;
		}
		return 0.0F;
	}

	/** ¿Es un golpe crítico de un jugador (cayendo)? */
	private static boolean critical(DamageSource source) {
		return source.getAttacker() instanceof PlayerEntity p && source.getSource() == p && p.fallDistance > 0.0F && !p.isOnGround()
				&& !p.isClimbing() && !p.isTouchingWater();
	}

	/** La probabilidad de cortar un brazo o una pierna con este golpe. */
	public static float limbChance(float sharpness, float dealt, float healthLeft) {
		float wound = 1.0F + (1.0F - MathHelper.clamp(healthLeft, 0.0F, 1.0F));
		return Math.min(0.6F, sharpness * MathHelper.clamp(dealt / 10.0F, 0.1F, 1.0F) * 0.12F * wound * (float) EwConfig.get().dismembermentChance);
	}

	/** La probabilidad de decapitar con un golpe a la cabeza (los proyectiles no decapitan). */
	public static float headChance(float sharpness, boolean critical, float healthLeft) {
		float wound = 1.0F + (1.0F - MathHelper.clamp(healthLeft, 0.0F, 1.0F));
		return Math.min(0.5F, sharpness * 0.04F * (critical ? 2.5F : 1.0F) * wound * (float) EwConfig.get().dismembermentChance);
	}

	/**
	 * Después de un golpe que ha hecho daño en una parte del cuerpo. {@code point}: dónde ha dado (para saber el lado), o
	 * null si se reparte al azar.
	 */
	public static void afterHit(LivingEntity target, DamageSource source, @Nullable HitZone zone, float dealt, @Nullable Vec3d point) {
		if (zone == null || zone == HitZone.TORSO || !isNpc(target) || !enabled() || isSevered(target, HEAD)
				|| !(target.getWorld() instanceof ServerWorld)) {
			return;
		}
		float sharp = sharpness(source);
		if (sharp <= 0.0F) {
			return;
		}
		float health = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
		float roll = target.getRandom().nextFloat();
		if (zone == HitZone.HEAD) {
			if (source.getSource() instanceof ProjectileEntity) {
				return;
			}
			if (roll < headChance(sharp, critical(source), health)) {
				decapitate(target, source);
			}
			return;
		}
		boolean right = side(target, point);
		int part = zone == HitZone.ARMS ? (right ? RIGHT_ARM : LEFT_ARM) : (right ? RIGHT_LEG : LEFT_LEG);
		if (isSevered(target, part) || roll >= limbChance(sharp, dealt, health)) {
			return;
		}
		cut(target, source, part);
	}

	/** ¿Ha dado en su lado derecho? */
	private static boolean side(LivingEntity target, @Nullable Vec3d point) {
		if (point == null) {
			return target.getRandom().nextBoolean();
		}
		float yaw = target.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		double lateral = (point.x - target.getX()) * -MathHelper.cos(yaw) + (point.z - target.getZ()) * -MathHelper.sin(yaw);
		return lateral > 0.0;
	}

	/** Corta un brazo o una pierna: sale volando, sangra, y se lo dice a quien ha dado el golpe. */
	public static void cut(LivingEntity target, DamageSource source, int part) {
		sever(target, part);
		ServerWorld world = (ServerWorld) target.getWorld();
		double y = (part == RIGHT_LEG || part == LEFT_LEG) ? target.getY() + 0.4 : target.getY() + target.getHeight() * 0.62;
		Vec3d away = target.getPos().subtract(source.getAttacker() != null ? source.getAttacker().getPos() : target.getPos().add(1, 0, 0));
		throwLimb(target, part, new Vec3d(target.getX(), y, target.getZ()), away);
		blood(world, target.getX(), y, target.getZ(), 30);
		world.playSound(null, target.getX(), y, target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0F, 0.7F);
		world.playSound(null, target.getX(), y, target.getZ(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.HOSTILE, 1.0F, 0.6F);
		if (target.isAlive()) {
			target.addStatusEffect(new StatusEffectInstance(ModEffects.BLEEDING, 160, 0));
		}
		if (source.getAttacker() instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.dismember." + partName(part)).formatted(Formatting.DARK_RED), true);
		}
	}

	/** Decapita: la cabeza sale volando, un chorro de sangre, y muere en el acto. */
	public static void decapitate(LivingEntity target, DamageSource source) {
		sever(target, HEAD);
		ServerWorld world = (ServerWorld) target.getWorld();
		double y = target.getEyeY();
		throwLimb(target, HEAD, new Vec3d(target.getX(), y, target.getZ()), new Vec3d(0, 1, 0));
		blood(world, target.getX(), y, target.getZ(), 60);
		world.playSound(null, target.getX(), y, target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.2F, 0.5F);
		world.playSound(null, target.getX(), y, target.getZ(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.HOSTILE, 1.2F, 0.5F);
		if (source.getAttacker() instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.dismember.head").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		}
		DamageSource kill = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DECAPITATION), source.getSource(),
				source.getAttacker());
		target.damage(kill, Float.MAX_VALUE / 4.0F);
		if (target.isAlive()) {
			target.setHealth(0.0F);
			target.onDeath(kill);
		}
	}

	public static String partName(int part) {
		return switch (part) {
			case RIGHT_ARM -> "right_arm";
			case LEFT_ARM -> "left_arm";
			case RIGHT_LEG -> "right_leg";
			case LEFT_LEG -> "left_leg";
			default -> "head";
		};
	}

	private static void throwLimb(LivingEntity target, int part, Vec3d at, Vec3d away) {
		SeveredLimbEntity limb = SeveredLimbEntity.create(target, part, at);
		Vec3d dir = away.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 0) : away.normalize();
		var r = target.getRandom();
		limb.setVelocity(dir.x * 0.25 + (r.nextDouble() - 0.5) * 0.2, part == HEAD ? 0.55 : 0.3, dir.z * 0.25 + (r.nextDouble() - 0.5) * 0.2);
		target.getWorld().spawnEntity(limb);
	}

	private static void blood(ServerWorld world, double x, double y, double z, int count) {
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()), x, y, z, count, 0.2, 0.2,
				0.2, 0.15);
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, count / 6, 0.2, 0.2, 0.2, 0.1);
	}

	/** Para las pruebas y los goles: ¿puede este NPC pelear cuerpo a cuerpo? */
	public static boolean canMelee(MobEntity mob) {
		return hasAnArm(mob);
	}
}
