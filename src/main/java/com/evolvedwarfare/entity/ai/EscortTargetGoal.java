package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.faction.Reputation;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Aliados de un jugador: atacan a quien le ataque o a quien él ataque. Lo hacen las escoltas que presta una facción
 * aliada y cualquier guerrero de esa facción que ande cerca (a menos de 16 bloques) de un jugador aliado.
 */
public class EscortTargetGoal extends TrackTargetGoal {
	private static final double ALLY_RANGE = 16.0;

	private final FactionNpcEntity npc;
	@Nullable
	private LivingEntity candidate;

	public EscortTargetGoal(FactionNpcEntity npc) {
		super(npc, false);
		this.npc = npc;
		setControls(EnumSet.of(Goal.Control.TARGET));
	}

	@Nullable
	private PlayerEntity friend() {
		PlayerEntity escorted = npc.getEscortedPlayer();
		if (escorted != null) {
			return escorted;
		}
		if (npc.age % 10 != 0) {
			return null;
		}
		List<PlayerEntity> players = npc.getWorld().getEntitiesByClass(PlayerEntity.class, npc.getBoundingBox().expand(ALLY_RANGE),
				p -> p.isAlive() && !p.isSpectator() && Reputation.allied(npc.getWorld(), p.getUuid(), npc.getFaction()));
		return players.isEmpty() ? null : players.get(0);
	}

	@Override
	public boolean canStart() {
		PlayerEntity player = friend();
		if (player == null) {
			return false;
		}
		LivingEntity target = player.getAttacker();
		if (!valid(target, player)) {
			target = player.getAttacking();
		}
		if (!valid(target, player)) {
			return false;
		}
		candidate = target;
		return canTrack(target, TargetPredicate.DEFAULT);
	}

	private boolean valid(@Nullable LivingEntity target, PlayerEntity player) {
		if (target == null || !target.isAlive() || target == npc || NpcSides.allies(target, player) || NpcSides.allies(target, npc)) {
			return false;
		}
		// a los de su facción no los ataca aunque el jugador se pelee con ellos
		if (target instanceof FactionNpcEntity other && other.getFaction() == npc.getFaction()) {
			return false;
		}
		return target instanceof Monster || target instanceof PlayerEntity || target instanceof FactionNpcEntity
				|| target instanceof net.minecraft.entity.mob.MobEntity hunter && hunter.getTarget() == player;
	}

	@Override
	public void start() {
		mob.setTarget(candidate);
		super.start();
	}
}
