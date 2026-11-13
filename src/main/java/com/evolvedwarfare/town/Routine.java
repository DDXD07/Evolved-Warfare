package com.evolvedwarfare.town;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * El horario de un día (la hora del mundo: 0 = las 6 de la mañana, 6000 = mediodía, 12000 = las 6 de la tarde).
 *
 * <ul>
 * <li><b>Vecinos</b>: se levantan a las 6 (los perezosos a las 7) y desayunan en casa; trabajan hasta las 3 de la tarde
 * (los perezosos paran a ratos); luego tiempo libre en la plaza hasta las 5 y media (si llueve, en casa; los miedosos
 * vuelven antes); cenan en casa y a las 6 y media a dormir.</li>
 * <li><b>Guardias con cama</b>: los del turno de día rondan por el pueblo de 6 de la mañana a 6 y media de la tarde y
 * duermen de noche; los del turno de noche duermen de 7 de la mañana a 4 de la tarde, comen, pasan un rato en la plaza y
 * rondan toda la noche.</li>
 * <li><b>Peligro</b> (un monstruo o un asaltante cerca, o les han atacado): los vecinos corren a casa (los miedosos lo
 * ven antes, los valientes más tarde) hasta que pasa un rato sin peligro. Los guardias luchan.</li>
 * <li><b>Contratados</b> a los que dejas en un sitio (no soldados): duermen de noche en una cama cercana, si la hay.</li>
 * </ul>
 */
public final class Routine {
	/** Mientras dura el peligro después de dejar de verlo. */
	public static final int DANGER_MEMORY = 200;

	public enum Mode {
		NONE, FULL, SLEEP_ONLY
	}

	private Routine() {
	}

	public static long timeOfDay(ServerWorld world) {
		return Math.floorMod(world.getTimeOfDay(), 24000L);
	}

	/** ¿Es el turno de noche? (la mitad de los guardias, según quiénes son). */
	public static boolean nightShift(AbstractNpcEntity npc) {
		return (npc.getUuid().getLeastSignificantBits() & 1L) != 0L;
	}

	/** Qué le toca hacer ahora. */
	public static Activity plan(AbstractNpcEntity npc, ServerWorld world) {
		Mode mode = npc.routineMode();
		NpcProfile p = npc.ewProfile();
		if (mode == Mode.NONE) {
			return Activity.NONE;
		}
		boolean fixedTime = npc.forcedTime == null && world.getDimension().hasFixedTime();
		long t = npc.forcedTime != null ? Math.floorMod(npc.forcedTime, 24000L) : timeOfDay(world);
		if (mode == Mode.SLEEP_ONLY) {
			return !fixedTime && p.bed != null && t >= 13000L && t < 23000L ? Activity.SLEEP : Activity.NONE;
		}
		if (p.job == NpcJob.GUARD) {
			if (fixedTime) {
				return Activity.PATROL;
			}
			if (!nightShift(npc)) {
				return t < 600L ? Activity.BREAKFAST : t < 12500L ? Activity.PATROL : t < 13300L ? Activity.DINNER : Activity.SLEEP;
			}
			return t >= 1000L && t < 10000L ? Activity.SLEEP : t >= 10000L && t < 10800L ? Activity.BREAKFAST
					: t >= 10800L && t < 12000L ? Activity.LEISURE : Activity.PATROL;
		}
		if (danger(npc, world)) {
			return Activity.HIDE;
		}
		if (fixedTime) {
			return Activity.WORK;
		}
		Personality who = p.personality;
		long wake = who == Personality.LAZY ? 1000L : 0L;
		long bedtime = who == Personality.FEARFUL ? 12300L : 12800L;
		if (t >= bedtime || t < wake) {
			return p.bed != null ? Activity.SLEEP : Activity.HOME;
		}
		if (t < wake + 900L) {
			return Activity.BREAKFAST;
		}
		long workEnd = who == Personality.LAZY ? 8000L : 9000L;
		if (t < workEnd) {
			// los perezosos se toman ratos libres
			if (who == Personality.LAZY && (t / 1500L) % 3L == 2L) {
				return Activity.LEISURE;
			}
			return Activity.WORK;
		}
		long leisureEnd = who == Personality.FEARFUL ? 11000L : 11500L;
		if (t < leisureEnd) {
			return world.isRaining() && npc.forcedTime == null ? Activity.HOME : Activity.LEISURE;
		}
		return t < leisureEnd + 600L ? Activity.DINNER : Activity.HOME;
	}

	/** ¿Hay peligro? (un monstruo o un asaltante a la vista, o le han atacado hace poco). Se recuerda un rato. */
	public static boolean danger(AbstractNpcEntity npc, ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		long now = world.getTime();
		if ((npc.age + npc.getId()) % 20 == 0) {
			double r = switch (p.personality) {
				case FEARFUL -> 18.0;
				case BRAVE -> 7.0;
				default -> 12.0;
			};
			boolean seen = !world.getEntitiesByClass(HostileEntity.class, npc.getBoundingBox().expand(r, 4.0, r),
					e -> e.isAlive() && !e.isInvisible() && npc.canSee(e)).isEmpty();
			boolean hurt = npc.getAttacker() != null && npc.age - npc.getLastAttackedTime() < 100;
			if (seen || hurt) {
				p.dangerUntil = now + DANGER_MEMORY;
			}
		}
		return now < p.dangerUntil;
	}
}
