package com.evolvedwarfare.difficulty;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.difficulty.ai.CreeperBreachGoal;
import com.evolvedwarfare.difficulty.ai.DodgeAimGoal;
import com.evolvedwarfare.difficulty.ai.KeepDistanceGoal;
import com.evolvedwarfare.difficulty.ai.RetreatWhenHurtGoal;
import com.evolvedwarfare.difficulty.ai.SpiderWebGoal;
import com.evolvedwarfare.mixin.MobEntityGoalsAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.EscapeSunlightGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Monstruos más listos (fase 96):
 * <ul>
 * <li><b>Se avisan</b>: si le pegas a un monstruo, los de alrededor (14 bloques) que no tengan a quién atacar van a por ti.</li>
 * <li><b>Esqueletos</b>: <b>apuntan adelantándose</b> a donde vas (como un cazador), se apartan si te acercas y huyen
 * muy heridos.</li>
 * <li><b>Arañas</b>: te escupen <b>telaraña</b> a los pies desde lejos y huyen muy heridas.</li>
 * <li><b>Creepers</b>: si estás encerrado cerca y no pueden llegar, <b>revientan la pared</b>.</li>
 * <li><b>Zombis</b>: de día, si se queman, buscan la sombra.</li>
 * <li>Esqueletos y zombis <b>esquivan</b> a veces cuando les apuntas con el arco o la ballesta.</li>
 * </ul>
 */
public final class SmartMobs {
	private record Web(ServerWorld world, BlockPos pos, long until) {
	}

	private static final List<Web> WEBS = new ArrayList<>();
	/** Lo que dura una telaraña escupida (ticks). */
	public static final int WEB_TICKS = 20 * 15;

	private SmartMobs() {
	}

	public static boolean enabled() {
		return EwConfig.get().smarterMobs;
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!Difficulty.automatic() || !enabled()) {
				return;
			}
			if (entity instanceof PathAwareEntity mob && entity instanceof Monster && Difficulty.affected(mob)) {
				addGoals(mob);
			}
			if (entity instanceof PersistentProjectileEntity arrow && arrow.age == 0 && arrow.getOwner() instanceof AbstractSkeletonEntity skeleton
					&& skeleton.getTarget() != null) {
				leadShot(arrow, skeleton.getTarget());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0 || WEBS.isEmpty()) {
				return;
			}
			for (Iterator<Web> it = WEBS.iterator(); it.hasNext();) {
				Web web = it.next();
				if (web.world.getTime() >= web.until) {
					if (web.world.isChunkLoaded(web.pos) && web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
						web.world.removeBlock(web.pos, false);
					}
					it.remove();
				}
			}
		});
	}

	/** Les pone lo que les hace más listos (los objetivos no se guardan: se ponen cada vez que se cargan). */
	public static void addGoals(PathAwareEntity mob) {
		MobEntityGoalsAccessor ai = (MobEntityGoalsAccessor) mob;
		if (mob instanceof AbstractSkeletonEntity) {
			ai.evolvedwarfare$goals().add(2, new RetreatWhenHurtGoal(mob));
			ai.evolvedwarfare$goals().add(3, new KeepDistanceGoal(mob));
			ai.evolvedwarfare$goals().add(3, new DodgeAimGoal(mob, 0.12F));
		} else if (mob instanceof SpiderEntity spider) {
			ai.evolvedwarfare$goals().add(2, new RetreatWhenHurtGoal(mob));
			if (EwConfig.get().spiderWebs) {
				ai.evolvedwarfare$goals().add(3, new SpiderWebGoal(spider));
			}
		} else if (mob instanceof CreeperEntity creeper) {
			ai.evolvedwarfare$goals().add(1, new CreeperBreachGoal(creeper));
		} else if (mob instanceof ZombieEntity && !(mob instanceof ZombifiedPiglinEntity)) {
			if (!(mob instanceof HuskEntity)) {
				ai.evolvedwarfare$goals().add(2, new EscapeSunlightGoal(mob, 1.1));
			}
			ai.evolvedwarfare$goals().add(3, new DodgeAimGoal(mob, 0.06F));
		}
	}

	/** Le has pegado a un monstruo: los de alrededor sin presa te oyen y van a por ti. */
	public static void onHurt(LivingEntity victim, DamageSource source) {
		if (Difficulty.automatic() && enabled()) {
			alert(victim, source);
		}
	}

	public static int alert(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Monster) || !(source.getAttacker() instanceof PlayerEntity player) || player.getAbilities().creativeMode
				|| player.isSpectator()) {
			return 0;
		}
		int n = 0;
		Box box = victim.getBoundingBox().expand(14.0, 6.0, 14.0);
		for (MobEntity other : victim.getWorld().getEntitiesByClass(MobEntity.class, box, m -> m != victim && m.isAlive())) {
			if (!(other instanceof Monster) || other instanceof EndermanEntity || other instanceof ZombifiedPiglinEntity || !Difficulty.affected(other)) {
				continue;
			}
			LivingEntity current = other.getTarget();
			if (current == null || !current.isAlive()) {
				other.setTarget(player);
				if (++n >= 6) {
					break;
				}
			}
		}
		return n;
	}

	/** Re-apunta la flecha a donde va a estar la presa cuando llegue (adelantándose a cómo se mueve). */
	public static void leadShot(PersistentProjectileEntity arrow, LivingEntity target) {
		Vec3d velocity = arrow.getVelocity();
		double speed = velocity.length();
		if (speed < 0.1) {
			return;
		}
		Vec3d motion = new Vec3d(target.getX() - target.prevX, target.getY() - target.prevY, target.getZ() - target.prevZ);
		Vec3d aim = lead(arrow.getPos(), speed, new Vec3d(target.getX(), target.getBodyY(0.3333), target.getZ()), motion);
		arrow.setVelocity(aim.x, aim.y, aim.z, (float) speed, 2.0F);
	}

	/** Hacia dónde apuntar (con la caída de la flecha, como vanilla): a donde estará la presa al llegar. */
	public static Vec3d lead(Vec3d from, double speed, Vec3d target, Vec3d motionPerTick) {
		double time = target.subtract(from).length() / speed;
		Vec3d predicted = target.add(motionPerTick.x * time * 0.85, 0, motionPerTick.z * time * 0.85);
		double dx = predicted.x - from.x, dz = predicted.z - from.z;
		double dy = predicted.y - from.y + Math.sqrt(dx * dx + dz * dz) * 0.2;
		return new Vec3d(dx, dy, dz);
	}

	public static void temporaryWeb(ServerWorld world, BlockPos pos) {
		WEBS.add(new Web(world, pos.toImmutable(), world.getTime() + WEB_TICKS));
	}
}
