package com.evolvedwarfare.weapon;

import com.evolvedwarfare.config.EwConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Lo especial de cada arma (lo usan tanto las armas de espada como el hacha de batalla). */
public final class MedievalWeapons {
	private MedievalWeapons() {
	}

	@Nullable
	public static WeaponType typeOf(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof MedievalWeaponItem weapon) {
			return weapon.getType();
		}
		if (item instanceof BattleAxeItem) {
			return WeaponType.BATTLE_AXE;
		}
		return null;
	}

	/** Cuánto más llega un NPC con esta arma en la mano. */
	public static double npcReach(ItemStack stack) {
		WeaponType type = typeOf(stack);
		return type == null ? 0.0 : type.npcReach;
	}

	/** ¿Está 'attacker' detrás de 'target'? */
	public static boolean behind(Entity attacker, LivingEntity target) {
		Vec3d facing = Vec3d.fromPolar(0.0F, target.getBodyYaw());
		Vec3d to = attacker.getPos().subtract(target.getPos());
		double length = Math.sqrt(to.x * to.x + to.z * to.z);
		return length > 1.0E-4 && (facing.x * to.x + facing.z * to.z) / length < -0.5;
	}

	private static boolean mounted(Entity target) {
		return target.hasVehicle() || target instanceof AbstractHorseEntity;
	}

	/** Daño de más según el arma y el blanco. */
	public static float bonusDamage(WeaponType type, Entity target, float base, DamageSource source) {
		EwConfig config = EwConfig.get();
		Entity attacker = source.getAttacker();
		switch (type) {
			case DAGGER -> {
				if (attacker != null && target instanceof LivingEntity living && behind(attacker, living)) {
					if (target.getWorld() instanceof ServerWorld world) {
						world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
					}
					return (float) (base * (config.daggerBackstab - 1.0));
				}
			}
			case HALBERD -> {
				if (mounted(target)) {
					return (float) (base * (config.halberdVsMounted - 1.0));
				}
			}
			case WARHAMMER -> {
				if (target instanceof LivingEntity living) {
					return (float) (living.getArmor() * config.warhammerArmorPierce);
				}
			}
			default -> {
			}
		}
		return 0.0F;
	}

	/** Después del golpe: el mangual aturde y el martillo empuja. */
	public static void afterHit(WeaponType type, LivingEntity target, LivingEntity attacker) {
		if (target.getWorld().isClient) {
			return;
		}
		switch (type) {
			case FLAIL -> {
				if (attacker.getRandom().nextFloat() < EwConfig.get().flailStunChance) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), attacker);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0), attacker);
					target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_ANVIL_LAND,
							SoundCategory.PLAYERS, 0.5F, 1.6F);
				}
			}
			case WARHAMMER -> target.takeKnockback(0.6, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
			default -> {
			}
		}
	}
}
