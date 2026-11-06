package com.evolvedwarfare.hell;

import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Caído: un ángel de alas rotas. No ataca de frente: te sigue a distancia y te susurra; si lo miras demasiado rato te
 * ciega, y si le das la espalda de cerca, te hiere. Suelta plumas oscuras y fragmentos de armadura.
 */
public class FallenEntity extends DamnedBase {
	public static final int WHISPERS = 8;
	private int stare;
	private int nextWhisper = 200;
	private int strikeCooldown;

	public FallenEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 12;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ARMOR, 6.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new StalkGoal(this));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.7));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** ¿Le está mirando? (y lo ve) */
	public static boolean looksAt(PlayerEntity player, HostileEntity mob, double cos) {
		Vec3d to = mob.getEyePos().subtract(player.getEyePos()).normalize();
		return player.getRotationVec(1.0F).dotProduct(to) > cos && player.canSee(mob);
	}

	/** Seguir de lejos: ni muy cerca ni muy lejos, mirando. */
	static class StalkGoal extends Goal {
		private final FallenEntity fallen;

		StalkGoal(FallenEntity fallen) {
			this.fallen = fallen;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return fallen.getTarget() instanceof PlayerEntity p && p.isAlive();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (!(fallen.getTarget() instanceof PlayerEntity p)) {
				return;
			}
			fallen.getLookControl().lookAt(p, 30, 30);
			double d = fallen.distanceTo(p);
			boolean watched = looksAt(p, fallen, 0.3);
			if (!watched && d < 3.0) {
				fallen.getNavigation().startMovingTo(p, 1.2);
			} else if (d < 9 && fallen.age % 10 == 0) {
				Vec3d away = fallen.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize().multiply(6).add(fallen.getPos());
				Path path = fallen.getNavigation().findPathTo(away.x, away.y, away.z, 1);
				fallen.getNavigation().startMovingAlong(path, 1.1);
			} else if (d > 18 && fallen.age % 10 == 0) {
				fallen.getNavigation().startMovingTo(p, 1.0);
			} else if (d >= 9 && d <= 18) {
				fallen.getNavigation().stop();
			}
		}
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getTarget() instanceof PlayerEntity p) || !p.isAlive()) {
			stare = 0;
			return;
		}
		// mirarlo demasiado ciega
		if (looksAt(p, this, 0.97) && distanceTo(p) < 32) {
			if (++stare > 60) {
				stare = 0;
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0), this);
				getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.6F, 1.4F);
			}
		} else {
			stare = Math.max(0, stare - 2);
		}
		// por la espalda, de cerca
		if (strikeCooldown > 0) {
			strikeCooldown--;
		} else if (distanceTo(p) < 2.6 && !looksAt(p, this, 0.3)) {
			tryAttack(p);
			strikeCooldown = 40;
		}
		// los susurros
		if (--nextWhisper <= 0) {
			nextWhisper = 300 + random.nextInt(400);
			if (p instanceof ServerPlayerEntity sp && distanceTo(p) < 32) {
				sp.sendMessage(Text.literal("").append(Text.translatable("message.evolvedwarfare.fallen.name").formatted(Formatting.DARK_GRAY))
						.append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
						.append(Text.translatable("message.evolvedwarfare.fallen.whisper." + random.nextInt(WHISPERS)).formatted(Formatting.GRAY,
								Formatting.ITALIC)), false);
				sp.playSoundToPlayer(SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.HOSTILE, 0.6F, 0.4F);
			}
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}
}
