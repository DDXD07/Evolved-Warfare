package com.evolvedwarfare.combat;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.siege.SiegeShotEntity;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Daño locacional: a jugadores y NPCs se les divide el cuerpo en cabeza, torso, brazos y piernas, y cada parte recibe
 * más o menos daño (cabeza 2,5x, torso 1x, brazos 0,5x, piernas 0,7x; se cambia en la configuración).
 *
 * <p>Dónde da el golpe:</p>
 * <ul>
 *   <li><b>Proyectiles</b> (flechas, balas de mosquete, jabalinas...): donde su trayectoria corta la caja del objetivo.</li>
 *   <li><b>Cuerpo a cuerpo de un jugador</b>: donde apunta (su mirada corta la caja del objetivo).</li>
 *   <li><b>Cuerpo a cuerpo de una criatura o NPC</b>: no apuntan (siempre miran a los ojos), así que es al azar según el
 *   tamaño de cada parte: cabeza 15 %, torso 40 %, brazos 20 %, piernas 25 %.</li>
 * </ul>
 * El resto del daño (caídas, fuego, explosiones, magia, piedras de las máquinas de asedio...) no cambia.
 *
 * <p>Las partes, con las medidas del cuerpo de jugador: piernas el 37,5 % de abajo, cabeza el 25 % de arriba, y en
 * medio el torso; lo que queda a los lados del torso (más allá del 70 % del medio ancho) son los brazos.</p>
 */
public final class LocationalDamage {
	/** A quién se aplica siempre: jugadores y los NPCs del mod. */
	public static final TagKey<EntityType<?>> TARGETS = TagKey.of(RegistryKeys.ENTITY_TYPE, EvolvedWarfare.id("locational_damage"));
	/** Criaturas con forma de persona de Minecraft (zombis, esqueletos, aldeanos, illagers, piglins...), si se activa. */
	public static final TagKey<EntityType<?>> VANILLA_TARGETS = TagKey.of(RegistryKeys.ENTITY_TYPE,
			EvolvedWarfare.id("locational_damage_vanilla"));

	private static final double LEGS_TOP = 0.375;
	private static final double HEAD_BOTTOM = 0.75;
	private static final double ARMS_FROM = 0.7;

	private LocationalDamage() {
	}

	public static boolean applies(LivingEntity target) {
		EwConfig config = EwConfig.get();
		if (!config.locationalDamage) {
			return false;
		}
		return target.getType().isIn(TARGETS) || (config.locationalVanillaMobs && target.getType().isIn(VANILLA_TARGETS));
	}

	/** La parte del cuerpo en la que da este daño, o null si no es un golpe ni un disparo (o el objetivo no cuenta). */
	@Nullable
	public static HitZone zoneOf(LivingEntity target, DamageSource source) {
		if (target.getWorld().isClient || !applies(target)) {
			return null;
		}
		if (source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.BYPASSES_ARMOR) && !source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			return null;
		}
		Entity direct = source.getSource();
		if (direct instanceof SiegeShotEntity) {
			return null;
		}
		if (direct instanceof ProjectileEntity projectile) {
			Vec3d point = projectileHit(target, projectile);
			return point == null ? HitZone.TORSO : zoneAt(target, point);
		}
		boolean melee = source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO);
		if (!melee || !(direct instanceof LivingEntity attacker) || direct != source.getAttacker()) {
			return null;
		}
		if (attacker instanceof PlayerEntity player) {
			Vec3d point = aimHit(target, player);
			return point == null ? HitZone.TORSO : zoneAt(target, point);
		}
		float roll = target.getRandom().nextFloat();
		return roll < 0.15F ? HitZone.HEAD : roll < 0.55F ? HitZone.TORSO : roll < 0.75F ? HitZone.ARMS : HitZone.LEGS;
	}

	/** Qué parte del cuerpo hay en este punto (de la caja del objetivo). */
	public static HitZone zoneAt(LivingEntity target, Vec3d point) {
		EntityPose pose = target.getPose();
		if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) {
			return HitZone.TORSO; // nadando, durmiendo, planeando...: el cuerpo no está de pie
		}
		Box box = target.getBoundingBox();
		double height = (point.y - box.minY) / Math.max(0.01, box.getLengthY());
		if (height >= HEAD_BOTTOM) {
			return HitZone.HEAD;
		}
		if (height < LEGS_TOP) {
			return HitZone.LEGS;
		}
		// a los lados del torso, los brazos (según hacia dónde mira el cuerpo)
		float yaw = target.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		double rightX = -MathHelper.cos(yaw);
		double rightZ = -MathHelper.sin(yaw);
		double lateral = (point.x - target.getX()) * rightX + (point.z - target.getZ()) * rightZ;
		return Math.abs(lateral) > box.getLengthX() / 2.0 * ARMS_FROM ? HitZone.ARMS : HitZone.TORSO;
	}

	/** Donde la trayectoria del proyectil corta la caja del objetivo. */
	@Nullable
	private static Vec3d projectileHit(LivingEntity target, ProjectileEntity projectile) {
		Vec3d velocity = projectile.getVelocity();
		if (velocity.lengthSquared() < 1.0E-6) {
			return null;
		}
		Vec3d from = projectile.getPos().subtract(velocity);
		Vec3d to = projectile.getPos().add(velocity.multiply(2.0));
		return crossing(target, from, to, 0.3);
	}

	/** Donde mira el jugador sobre la caja del objetivo. */
	@Nullable
	private static Vec3d aimHit(LivingEntity target, PlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d to = eye.add(player.getRotationVec(1.0F).multiply(player.getEntityInteractionRange() + 2.0));
		return crossing(target, eye, to, 0.15);
	}

	@Nullable
	private static Vec3d crossing(LivingEntity target, Vec3d from, Vec3d to, double margin) {
		Box box = target.getBoundingBox();
		Optional<Vec3d> hit = box.expand(margin).raycast(from, to);
		if (hit.isEmpty()) {
			hit = box.contains(from) ? Optional.of(from) : Optional.empty();
		}
		return hit.map(p -> new Vec3d(MathHelper.clamp(p.x, box.minX, box.maxX), MathHelper.clamp(p.y, box.minY, box.maxY),
				MathHelper.clamp(p.z, box.minZ, box.maxZ))).orElse(null);
	}

	/** Un tiro o golpe a la cabeza que ha hecho daño: chispas en la cabeza y, si lo ha dado un jugador, el "ding". */
	public static void headshot(LivingEntity target, DamageSource source) {
		if (target.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 8, 0.2, 0.15, 0.2, 0.25);
		}
		if (source.getAttacker() instanceof ServerPlayerEntity player && player != target) {
			player.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.6F, 1.4F);
		}
	}
}
