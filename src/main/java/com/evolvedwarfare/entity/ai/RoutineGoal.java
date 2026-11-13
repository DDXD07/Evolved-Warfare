package com.evolvedwarfare.entity.ai;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.town.Activity;
import com.evolvedwarfare.town.NpcJob;
import com.evolvedwarfare.town.NpcProfile;
import com.evolvedwarfare.town.Routine;
import com.evolvedwarfare.town.Work;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.jetbrains.annotations.Nullable;

/**
 * La rutina diaria de un NPC del pueblo (ver {@link Routine}): va a donde le toca y hace lo suyo. Duerme en su cama,
 * desayuna y cena en casa, trabaja en su puesto (con su herramienta, golpes, ruido y chispas o humo), pasa la tarde en la
 * plaza mirando a los demás, ronda (los guardias) y corre a casa si hay peligro. Si se atasca de camino a casa o a la cama
 * (las escalas de las casas de dos plantas), al rato aparece allí; si se atasca yendo a otro sitio, busca otro.
 */
public class RoutineGoal extends Goal {
	private static final double ARRIVED = 1.6;

	private final AbstractNpcEntity npc;
	private Activity activity = Activity.NONE;
	@Nullable
	private BlockPos target;
	/** Lo que mira y donde trabaja (el yunque, el agua...). */
	@Nullable
	private BlockPos focus;
	private int replan;
	private int repath;
	private int stuck;
	private double bestDistance = Double.MAX_VALUE;
	private int actTimer;
	private int eatTimer;
	private boolean ate;
	private int wait;
	private boolean centerSearched;

	public RoutineGoal(AbstractNpcEntity npc) {
		this.npc = npc;
		setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK, Goal.Control.JUMP));
	}

	private boolean allowed() {
		return npc.getWorld() instanceof ServerWorld && npc.isAlive() && npc.getTarget() == null && !npc.hasVehicle() && !npc.isLeashed();
	}

	@Override
	public boolean canStart() {
		if (!allowed() || (npc.age + npc.getId()) % 10 != 0) {
			return false;
		}
		return Routine.plan(npc, (ServerWorld) npc.getWorld()) != Activity.NONE;
	}

	@Override
	public boolean shouldContinue() {
		return allowed() && activity != Activity.NONE;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		activity = Activity.NONE;
		replan = 0;
	}

	@Override
	public void stop() {
		if (npc.isSleeping()) {
			npc.wakeUp();
		}
		npc.clearProp();
		npc.getNavigation().stop();
		activity = Activity.NONE;
		npc.setActivity(Activity.NONE);
	}

	public Activity activity() {
		return activity;
	}

	@Override
	public void tick() {
		ServerWorld world = (ServerWorld) npc.getWorld();
		if (--replan <= 0) {
			replan = 20;
			Activity next = Routine.plan(npc, world);
			if (next != activity) {
				begin(world, next);
				if (activity == Activity.NONE) {
					return;
				}
			}
		}
		// charlando: quieto, mirando al otro (lo mira la charla)
		if (com.evolvedwarfare.town.Chatter.talking(npc) && activity != Activity.SLEEP && activity != Activity.HIDE) {
			npc.getNavigation().stop();
			return;
		}
		if (target == null) {
			idle(world);
			return;
		}
		double dx = target.getX() + 0.5 - npc.getX(), dz = target.getZ() + 0.5 - npc.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double dy = Math.abs(target.getY() - npc.getY());
		boolean arrived = npc.isSleeping() || flat < ARRIVED && dy < 1.6;
		if (!arrived) {
			walk(world, flat, dy);
			return;
		}
		stuck = 0;
		bestDistance = Double.MAX_VALUE;
		switch (activity) {
			case SLEEP -> sleep(world);
			case BREAKFAST, DINNER -> eat(world);
			case WORK -> work(world);
			case PATROL -> {
				lookAround(world);
				if (--wait <= 0) {
					target = patrolPoint(world);
					wait = 60 + npc.getRandom().nextInt(120);
				}
			}
			default -> idle(world);
		}
	}

	// ------------------------------------------------------------------ elegir a dónde ir

	private void begin(ServerWorld world, Activity next) {
		if (npc.isSleeping() && next != Activity.SLEEP) {
			npc.wakeUp();
		}
		npc.clearProp();
		activity = next;
		npc.setActivity(next);
		focus = null;
		ate = false;
		eatTimer = 0;
		stuck = 0;
		repath = 0;
		wait = 0;
		bestDistance = Double.MAX_VALUE;
		NpcProfile p = npc.ewProfile();
		target = switch (next) {
			case NONE -> null;
			case SLEEP -> p.bed != null ? p.bed : home();
			case BREAKFAST, DINNER, HOME, HIDE -> home() == null ? null : Work.standNear(world, home(), npc.getId());
			case WORK -> workTarget(world);
			case LEISURE -> leisurePoint(world);
			case PATROL -> patrolPoint(world);
		};
	}

	@Nullable
	private BlockPos home() {
		BlockPos home = npc.getHome();
		if (home != null) {
			return home;
		}
		BlockPos bed = npc.ewProfile().bed;
		return bed != null ? bed.up() : null;
	}

	@Nullable
	private BlockPos workTarget(ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		if (p.job == NpcJob.SHEPHERD) {
			BlockPos sheep = Work.flock(npc, world);
			if (sheep != null) {
				focus = sheep;
				return sheep;
			}
		}
		BlockPos place = Work.workplace(npc, world);
		if (place == null) {
			// sin sitio: va y viene por el pueblo (los peones acarrean cosas)
			return wanderPoint(world, center(world), 12);
		}
		focus = place;
		return Work.standNear(world, place, npc.getId());
	}

	private BlockPos center(ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		if (p.center == null && !centerSearched) {
			centerSearched = true;
			BlockPos from = home() != null ? home() : npc.getBlockPos();
			// sin plaza conocida: la campana del pueblo (si hay)
			p.center = world.getPointOfInterestStorage().getNearestPosition(e -> e.matchesKey(PointOfInterestTypes.MEETING), from, 48,
					PointOfInterestStorage.OccupationStatus.ANY).orElse(null);
		}
		if (p.center != null) {
			return p.center;
		}
		BlockPos home = home();
		return home != null ? home : npc.getBlockPos();
	}

	/** Un sitio en la plaza, en corro: cada uno en su ángulo, a 2-6 bloques del centro. */
	private BlockPos leisurePoint(ServerWorld world) {
		BlockPos c = center(world);
		double a = (npc.getUuid().getMostSignificantBits() & 0xFFFF) / 65536.0 * Math.PI * 2.0 + world.getTime() / 6000L;
		double r = 2.5 + (npc.getId() % 4);
		int x = c.getX() + (int) Math.round(Math.cos(a) * r), z = c.getZ() + (int) Math.round(Math.sin(a) * r);
		return Work.standNear(world, surface(world, x, c.getY(), z), npc.getId());
	}

	private BlockPos patrolPoint(ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		int range = p.range > 0 ? p.range : 24;
		return wanderPoint(world, center(world), range);
	}

	private BlockPos wanderPoint(ServerWorld world, BlockPos around, int range) {
		int x = around.getX() + npc.getRandom().nextInt(2 * range + 1) - range;
		int z = around.getZ() + npc.getRandom().nextInt(2 * range + 1) - range;
		return surface(world, x, around.getY(), z);
	}

	/** El suelo en esa columna, cerca de la altura dada (dentro de casas o bajo techos, el de abajo). */
	private static BlockPos surface(ServerWorld world, int x, int y, int z) {
		BlockPos.Mutable m = new BlockPos.Mutable(x, y, z);
		for (int k = 0; k < 6; k++) {
			for (int s : new int[] {k, -k}) {
				m.set(x, y + s, z);
				if (Work.standable(world, m)) {
					return m.toImmutable();
				}
			}
		}
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	// ------------------------------------------------------------------ ir

	private void walk(ServerWorld world, double flat, double dy) {
		double distance = flat + dy;
		if (distance < bestDistance - 0.5) {
			bestDistance = distance;
			stuck = 0;
		} else {
			stuck++;
		}
		if (stuck > 300) {
			stuck = 0;
			bestDistance = Double.MAX_VALUE;
			boolean indoors = activity == Activity.SLEEP || activity == Activity.HOME || activity == Activity.BREAKFAST || activity == Activity.DINNER
					|| activity == Activity.HIDE;
			if (indoors && flat < 10.0 && target != null) {
				// las escalas de las casas: aparece en su sitio
				BlockPos to = activity == Activity.SLEEP ? target : Work.standNear(world, target, npc.getId());
				npc.getNavigation().stop();
				npc.refreshPositionAndAngles(to.getX() + 0.5, to.getY() + (activity == Activity.SLEEP ? 0.6 : 0.0), to.getZ() + 0.5, npc.getYaw(), 0.0F);
				return;
			}
			// no llega: a otro sitio
			if (activity == Activity.WORK) {
				npc.ewProfile().workplace = null;
			}
			begin(world, activity);
			return;
		}
		if (--repath <= 0) {
			repath = 40;
			double speed = activity == Activity.HIDE ? 0.8 : activity == Activity.PATROL ? 0.5 : 0.55;
			npc.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
		}
	}

	// ------------------------------------------------------------------ hacer

	private void sleep(ServerWorld world) {
		if (npc.isSleeping()) {
			return;
		}
		BlockPos bed = npc.ewProfile().bed;
		BlockState s = bed == null ? null : world.getBlockState(bed);
		if (s != null && s.getBlock() instanceof BedBlock && !s.get(BedBlock.OCCUPIED)) {
			npc.getNavigation().stop();
			npc.clearProp();
			npc.sleep(bed);
		} else {
			idle(world);
		}
	}

	private void eat(ServerWorld world) {
		if (ate) {
			idle(world);
			return;
		}
		if (eatTimer == 0) {
			npc.showProp(new ItemStack(Items.BREAD));
		}
		eatTimer++;
		if (eatTimer % 4 == 0) {
			Vec3d look = npc.getRotationVec(1.0F);
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BREAD)), npc.getX() + look.x * 0.4,
					npc.getEyeY() - 0.15, npc.getZ() + look.z * 0.4, 3, 0.05, 0.05, 0.05, 0.03);
			if (eatTimer % 8 == 0) {
				npc.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.4F, 0.9F + npc.getRandom().nextFloat() * 0.2F);
			}
		}
		if (eatTimer >= 48) {
			ate = true;
			npc.lastMeal = world.getTime();
			npc.clearProp();
		}
	}

	private void work(ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		if (focus == null) {
			// sin puesto: de un lado a otro del pueblo, con algo en las manos
			npc.showProp(Work.tool(p.job));
			if (--wait <= 0) {
				target = wanderPoint(world, center(world), 12);
				wait = 80 + npc.getRandom().nextInt(120);
			}
			return;
		}
		npc.getLookControl().lookAt(focus.getX() + 0.5, focus.getY() + 0.5, focus.getZ() + 0.5);
		npc.showProp(Work.tool(p.job));
		if (--actTimer <= 0) {
			actTimer = 50 + npc.getRandom().nextInt(50);
			Work.act(npc, world, p.job, focus);
		}
		if (p.job == NpcJob.SHEPHERD && npc.getRandom().nextInt(300) == 0) {
			target = workTarget(world);
		}
	}

	private void idle(ServerWorld world) {
		lookAround(world);
		if (activity == Activity.LEISURE && npc.getRandom().nextInt(600) == 0) {
			target = leisurePoint(world);
		}
	}

	/** Mira a quien tenga cerca (un jugador antes que nadie; si no, otro del pueblo). */
	private void lookAround(ServerWorld world) {
		if ((npc.age & 7) != 0) {
			return;
		}
		PlayerEntity player = world.getClosestPlayer(npc, 5.0);
		if (player != null && !player.isSpectator()) {
			npc.getLookControl().lookAt(player, 30.0F, 30.0F);
			return;
		}
		List<MobEntity> near = world.getEntitiesByClass(MobEntity.class, npc.getBoundingBox().expand(7.0),
				e -> e != npc && e instanceof com.evolvedwarfare.town.Townsfolk && e.isAlive());
		if (!near.isEmpty()) {
			MobEntity other = near.get((int) ((world.getTime() / 100L + npc.getId()) % near.size()));
			npc.getLookControl().lookAt(other, 30.0F, 30.0F);
		}
	}
}
