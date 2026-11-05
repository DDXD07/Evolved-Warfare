package com.evolvedwarfare.night;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Un zombi que sabe dónde estás sin verte (la horda, o cualquiera de noche si estás cerca): busca al jugador más cercano
 * a su alcance, aunque estés dentro de una casa, y no lo suelta mientras siga a su alcance (no se le pierde de vista).
 */
public class ScentTargetGoal extends Goal {
	private final MobEntity mob;
	private final double range;
	private final Predicate<MobEntity> when;
	@Nullable
	private PlayerEntity prey;
	private int cooldown;

	public ScentTargetGoal(MobEntity mob, double range, Predicate<MobEntity> when) {
		this.mob = mob;
		this.range = range;
		this.when = when;
		setControls(EnumSet.of(Control.TARGET));
	}

	private boolean valid(PlayerEntity p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative() && p.getWorld() == mob.getWorld() && mob.squaredDistanceTo(p) < range * range;
	}

	@Nullable
	private PlayerEntity nearest() {
		PlayerEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (PlayerEntity p : mob.getWorld().getPlayers()) {
			if (valid(p)) {
				double d = mob.squaredDistanceTo(p);
				if (d < bestD) {
					bestD = d;
					best = p;
				}
			}
		}
		return best;
	}

	@Override
	public boolean canStart() {
		if (cooldown-- > 0 || !when.test(mob)) {
			return false;
		}
		cooldown = 10;
		prey = nearest();
		return prey != null;
	}

	@Override
	public void start() {
		mob.setTarget(prey);
	}

	@Override
	public boolean shouldContinue() {
		return prey != null && valid(prey) && when.test(mob);
	}

	@Override
	public void tick() {
		if (mob.getTarget() != prey) {
			mob.setTarget(prey);
		}
	}

	@Override
	public void stop() {
		if (mob.getTarget() == prey) {
			mob.setTarget(null);
		}
		prey = null;
	}
}
