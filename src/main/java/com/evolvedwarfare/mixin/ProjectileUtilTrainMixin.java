package com.evolvedwarfare.mixin;

import com.evolvedwarfare.train.TrainCarEntity;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apuntar con precisión a los trenes: el tren es una entidad enorme, y con su caja entera cualquier clic dentro de ella
 * daba al tren. Aquí (como en vanilla) se busca la entidad más cercana en la línea de la mira, pero en los trenes solo
 * cuenta donde hay tren de verdad (la plataforma y los bloques puestos), así que se puede picar y usar lo de alrededor.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilTrainMixin {
	@Inject(method = "raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;",
			at = @At("HEAD"), cancellable = true)
	private static void evolvedwarfare$trainRaycast(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate, double maxDistance,
			CallbackInfoReturnable<EntityHitResult> cir) {
		var world = entity.getWorld();
		var found = world.getOtherEntities(entity, box, predicate);
		boolean trains = false;
		for (Entity e : found) {
			if (e instanceof TrainCarEntity) {
				trains = true;
				break;
			}
		}
		if (!trains) {
			return;
		}
		double d = maxDistance;
		Entity target = null;
		Vec3d at = null;
		for (Entity e : found) {
			Vec3d hit;
			boolean inside;
			if (e instanceof TrainCarEntity car) {
				hit = car.raycast(min, max);
				if (hit == null) {
					continue;
				}
				inside = hit == min;
			} else {
				Box b = e.getBoundingBox().expand(e.getTargetingMargin());
				Optional<Vec3d> o = b.raycast(min, max);
				inside = b.contains(min);
				if (!inside && o.isEmpty()) {
					continue;
				}
				hit = o.orElse(min);
			}
			if (inside) {
				if (d >= 0.0) {
					target = e;
					at = hit;
					d = 0.0;
				}
				continue;
			}
			double sq = min.squaredDistanceTo(hit);
			if (sq < d || d == 0.0) {
				if (e.getRootVehicle() == entity.getRootVehicle()) {
					if (d == 0.0) {
						target = e;
						at = hit;
					}
				} else {
					target = e;
					at = hit;
					d = sq;
				}
			}
		}
		cir.setReturnValue(target == null ? null : new EntityHitResult(target, at));
	}
}
