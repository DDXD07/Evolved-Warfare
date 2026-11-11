package com.evolvedwarfare.mixin;

import com.evolvedwarfare.train.Trains;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Jugadores y criaturas chocan con los trenes (su plataforma y lo construido encima), y andan sobre ellos. */
@Mixin(Entity.class)
public abstract class EntityTrainCollisionMixin {
	@ModifyExpressionValue(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntityCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
	private List<VoxelShape> evolvedwarfare$trainCollisions(List<VoxelShape> original, Vec3d movement) {
		Entity self = (Entity) (Object) this;
		return Trains.collisions(self, self.getBoundingBox().stretch(movement), original);
	}
}
