package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.CombatNpc;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.entity.SoldierEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/** Médico: busca al aliado más herido (NPCs y su dueño) a menos de 12 bloques y lo cura 2 corazones cada 2 s. */
public class HealAlliesGoal extends Goal {
	private static final double SEARCH = 12.0;
	private static final float HEAL = 4.0F;

	private final PathAwareEntity medic;
	private final CombatNpc npc;
	@Nullable
	private LivingEntity patient;
	private int scanTimer;
	private int cooldown;

	public <T extends PathAwareEntity & CombatNpc> HealAlliesGoal(T medic) {
		this.medic = medic;
		this.npc = medic;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	private static boolean hurt(LivingEntity entity) {
		return entity.isAlive() && entity.getHealth() < entity.getMaxHealth() - 1.0F;
	}

	@Nullable
	private LivingEntity findPatient() {
		List<LivingEntity> candidates = medic.getWorld().getEntitiesByClass(LivingEntity.class,
				medic.getBoundingBox().expand(SEARCH),
				e -> e != medic && hurt(e) && NpcSides.allies(medic, e) && !(e instanceof PlayerEntity p && (p.isSpectator() || p.isCreative())));
		LivingEntity best = null;
		float bestRatio = 2.0F;
		for (LivingEntity e : candidates) {
			float ratio = e.getHealth() / e.getMaxHealth();
			if (ratio < bestRatio) {
				bestRatio = ratio;
				best = e;
			}
		}
		return best;
	}

	@Override
	public boolean canStart() {
		if (npc.getRole().kind() != NpcRole.Kind.HEALER || --scanTimer > 0) {
			return false;
		}
		scanTimer = 20;
		patient = findPatient();
		return patient != null;
	}

	@Override
	public boolean shouldContinue() {
		return patient != null && hurt(patient) && medic.squaredDistanceTo(patient) < 18.0 * 18.0;
	}

	@Override
	public void stop() {
		patient = null;
		medic.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (patient == null) {
			return;
		}
		medic.getLookControl().lookAt(patient, 30.0F, 30.0F);
		double distance = medic.squaredDistanceTo(patient);
		if (distance > 2.5 * 2.5) {
			medic.getNavigation().startMovingTo(patient, 1.1);
		} else {
			medic.getNavigation().stop();
		}
		if (distance <= 3.5 * 3.5 && --cooldown <= 0) {
			cooldown = 40;
			// medicina de campaña (investigación del dueño): cura la mitad más
			boolean trained = medic instanceof com.evolvedwarfare.entity.AbstractNpcEntity npc
					&& com.evolvedwarfare.skill.Skills.has(medic.getWorld(), npc.getOwnerUuid(),
							com.evolvedwarfare.skill.Perk.FIELD_MEDICINE);
			patient.heal(trained ? HEAL * 1.5F : HEAL);
			medic.swingHand(Hand.MAIN_HAND);
			medic.playSound(SoundEvents.ENTITY_GENERIC_DRINK, 0.6F, 1.2F);
			if (medic.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.HEART, patient.getX(), patient.getBodyY(1.0), patient.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			}
			if (medic instanceof SoldierEntity soldier) {
				soldier.gainExperience(1);
			}
		}
	}
}
