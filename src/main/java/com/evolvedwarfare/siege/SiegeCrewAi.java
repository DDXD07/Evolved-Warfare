package com.evolvedwarfare.siege;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.faction.Reputation;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La dotación de una máquina cuando no la maneja un jugador:
 * <ul>
 * <li>Un soldado tuyo (puesto con el bastón de mando) dispara solo a tus enemigos (facciones enemigas, monstruos) que
 * estén a tiro, sin tirar cerca de ti ni de los tuyos. Gasta la munición que tenga dentro la máquina.</li>
 * <li>La de una facción (asaltos, fortalezas) dispara a quien su facción ataque, y en un asalto bombardea el pueblo; el
 * ariete avanza hacia el centro y derriba lo que encuentra.</li>
 * </ul>
 */
final class SiegeCrewAi {
	private static final double CREW_SEARCH = 10.0;
	private static final double STAFF_RANGE = 24.0;

	private SiegeCrewAi() {
	}

	/** Un tic de la dotación; devuelve cuánto avanza la máquina (solo el ariete se mueve). */
	static double tick(SiegeEngineEntity engine, @Nullable LivingEntity crew) {
		if (crew == null) {
			if (engine.faction != null && engine.age % 40 == 0) {
				recruit(engine);
			}
			return 0.0;
		}
		if (engine.getKind() == SiegeKind.RAM) {
			return crew instanceof FactionNpcEntity ? drive(engine, crew) : 0.0;
		}
		if (engine.age % 10 != 0 || engine.getReload() > 0) {
			return 0.0;
		}
		Vec3d target = findTarget(engine, crew);
		if (target == null) {
			return 0.0;
		}
		SiegeKind kind = engine.getKind();
		// si el blanco se sale de su arco, gira la máquina hacia él (poco a poco)
		float wanted = SiegeBallistics.yawTo(engine.getPos(), target);
		float diff = MathHelper.wrapDegrees(wanted - engine.getYaw());
		if (Math.abs(diff) > kind.yawArc) {
			engine.setYaw(engine.getYaw() + MathHelper.clamp(diff, -15.0F, 15.0F));
			return 0.0;
		}
		SiegeAmmo ammo = engine.crewAmmo();
		if (ammo == null) {
			return 0.0;
		}
		SiegeBallistics.Aim aim = SiegeBallistics.aimAt(kind, engine.muzzle(), engine.getYaw(), target);
		if (!aim.reachable() && kind != SiegeKind.BALLISTA) {
			return 0.0;
		}
		crew.setYaw(aim.yaw());
		crew.setHeadYaw(aim.yaw());
		crew.setBodyYaw(aim.yaw());
		crew.setPitch(-aim.elevation() * 0.5F);
		if (engine.takeCrewAmmo(ammo)) {
			engine.fire(crew, aim, ammo, kind == SiegeKind.BALLISTA ? 1.0F : 2.5F);
		}
		return 0.0;
	}

	/** Una máquina de facción vacía llama al de los suyos que tenga más cerca. */
	private static void recruit(SiegeEngineEntity engine) {
		List<FactionNpcEntity> near = engine.getWorld().getEntitiesByClass(FactionNpcEntity.class,
				engine.getBoundingBox().expand(CREW_SEARCH), e -> e.isAlive() && !e.hasVehicle() && e.getFaction() == engine.faction);
		FactionNpcEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (FactionNpcEntity candidate : near) {
			double distance = candidate.squaredDistanceTo(engine);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		if (best != null) {
			best.startRiding(engine, true);
		}
	}

	// ------------------------------------------------------------------ blancos

	@Nullable
	private static Vec3d findTarget(SiegeEngineEntity engine, LivingEntity crew) {
		SiegeKind kind = engine.getKind();
		double max = kind.maxCrewRange();
		double min = kind.minCrewRange();
		World world = engine.getWorld();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, engine.getBoundingBox().expand(max, 24.0, max),
				e -> e.isAlive() && !e.isSpectator() && e != crew && !e.isConnectedThroughVehicle(engine))) {
			double distance = candidate.squaredDistanceTo(engine);
			if (distance < min * min || distance > max * max || distance >= bestDistance || !isEnemy(engine, crew, candidate)) {
				continue;
			}
			if (kind == SiegeKind.BALLISTA ? !crew.canSee(candidate) : nearFriends(engine, crew, candidate)) {
				continue;
			}
			best = candidate;
			bestDistance = distance;
		}
		if (best != null) {
			Vec3d at = best.getPos();
			if (kind == SiegeKind.BALLISTA) {
				// apunta adonde estará cuando llegue el virote
				double flight = Math.sqrt(bestDistance) / kind.shotSpeed;
				at = at.add(best.getVelocity().multiply(flight)).add(0.0, best.getHeight() * 0.5, 0.0);
			}
			return at;
		}
		// en un asalto, bombardea el pueblo
		BlockPos center = engine.siegeTarget;
		if (center != null && engine.faction != null) {
			double distance = Math.sqrt(MathHelper.squaredHypot(center.getX() + 0.5 - engine.getX(), center.getZ() + 0.5 - engine.getZ()));
			if (distance >= min && distance <= max) {
				BlockPos spot = center.add(engine.getRandom().nextInt(21) - 10, 0, engine.getRandom().nextInt(21) - 10);
				return Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot));
			}
		}
		return null;
	}

	private static boolean isEnemy(SiegeEngineEntity engine, LivingEntity crew, LivingEntity target) {
		if (crew instanceof FactionNpcEntity member) {
			return member.shouldAttack(target);
		}
		if (NpcSides.allies(crew, target) || target instanceof PlayerEntity) {
			return false;
		}
		UUID owner = engine.owner != null ? engine.owner : (crew instanceof AbstractNpcEntity npc ? npc.getOwnerUuid() : null);
		if (target instanceof FactionNpcEntity enemy) {
			LivingEntity enemyTarget = enemy.getTarget();
			return owner != null && (Reputation.hostile(engine.getWorld(), owner, enemy.getFaction())
					|| (enemyTarget != null && NpcSides.allies(crew, enemyTarget)));
		}
		return target instanceof Monster && !(target instanceof Angerable);
	}

	/** ¿Hay alguno de los suyos cerca del blanco? (para no reventarlos con la piedra). */
	private static boolean nearFriends(SiegeEngineEntity engine, LivingEntity crew, LivingEntity target) {
		if (crew instanceof FactionNpcEntity) {
			return false;
		}
		double radius = engine.getKind().blastRadius + 3.0;
		Box around = target.getBoundingBox().expand(radius);
		return !engine.getWorld().getEntitiesByClass(LivingEntity.class, around,
				e -> e != target && (e instanceof PlayerEntity || e instanceof AbstractNpcEntity) && NpcSides.allies(crew, e)).isEmpty();
	}

	// ------------------------------------------------------------------ el ariete de un asalto

	private static double drive(SiegeEngineEntity engine, LivingEntity crew) {
		BlockPos target = engine.siegeTarget;
		if (target == null) {
			return 0.0;
		}
		Vec3d goal = Vec3d.ofBottomCenter(target);
		double distance = Math.sqrt(MathHelper.squaredHypot(goal.x - engine.getX(), goal.z - engine.getZ()));
		if (distance < 6.0) {
			// ha llegado: la dotación se baja a pelear
			engine.siegeTarget = null;
			crew.stopRiding();
			return 0.0;
		}
		SiegeKind kind = engine.getKind();
		float wanted = SiegeBallistics.yawTo(engine.getPos(), goal);
		float diff = MathHelper.wrapDegrees(wanted - engine.getYaw());
		if (engine.stuckTicks == 0) {
			engine.setYaw(engine.getYaw() + MathHelper.clamp(diff, -kind.turnSpeed, kind.turnSpeed));
		}
		crew.setYaw(engine.getYaw());
		crew.setHeadYaw(engine.getYaw());
		if (engine.blockedAhead()) {
			if (engine.getReload() == 0 && engine.canBreakAhead()) {
				engine.strike(crew);
			}
			engine.stuckTicks++;
			if (engine.stuckTicks > 240 && !engine.canBreakAhead()) {
				// algo que no puede romper: rodearlo
				engine.setYaw(engine.getYaw() + (engine.getRandom().nextBoolean() ? 90.0F : -90.0F));
				engine.stuckTicks = 0;
				return 1.0;
			}
			return 0.0;
		}
		engine.stuckTicks = 0;
		return Math.abs(diff) > 45.0F ? 0.3 : 1.0;
	}

	// ------------------------------------------------------------------ con el bastón de mando

	/** Pone (o quita) a uno de tus soldados a manejarla. */
	static void assign(SiegeEngineEntity engine, PlayerEntity player) {
		Entity rider = engine.getFirstPassenger();
		Text name = Text.translatable("entity.evolvedwarfare." + engine.getKind().id());
		if (rider instanceof AbstractNpcEntity npc) {
			npc.stopRiding();
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.crew_off", npc.getName(), name), true);
			return;
		}
		if (rider != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.occupied", name), true);
			return;
		}
		if (!engine.getKind().fires()) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.ram_crew"), true);
			return;
		}
		SoldierEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SoldierEntity soldier : engine.getWorld().getEntitiesByClass(SoldierEntity.class, engine.getBoundingBox().expand(STAFF_RANGE),
				e -> e.isAlive() && !e.hasVehicle() && e.isOwner(player))) {
			double distance = soldier.squaredDistanceTo(engine);
			if (distance < bestDistance) {
				best = soldier;
				bestDistance = distance;
			}
		}
		if (best == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.no_soldier", (int) STAFF_RANGE), true);
			return;
		}
		best.startRiding(engine, true);
		best.setOrder(NpcOrder.STAY);
		player.sendMessage(Text.translatable("message.evolvedwarfare.siege.crew_on", best.getName(), name), true);
	}
}
