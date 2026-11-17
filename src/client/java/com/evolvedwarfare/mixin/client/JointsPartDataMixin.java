package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import java.util.Map;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cuando un modelo cambia un brazo o una pierna por otro suyo (la armadura del soporte, el aldeano zombi...), el juego le
 * pasa los hijos del de antes: el antebrazo o la espinilla que pusimos (fase 89) no deben pasar, o saldría un trozo de
 * más. Se quitan antes del cambio.
 */
@Mixin(ModelPartData.class)
public abstract class JointsPartDataMixin {
	@Shadow
	@Final
	private Map<String, ModelPartData> children;

	@Inject(method = "addChild(Ljava/lang/String;Lnet/minecraft/client/model/ModelPartBuilder;Lnet/minecraft/client/model/ModelTransform;)Lnet/minecraft/client/model/ModelPartData;",
			at = @At("HEAD"))
	private void evolvedwarfare$dropJoints(String name, ModelPartBuilder builder, ModelTransform transform, CallbackInfoReturnable<ModelPartData> cir) {
		ModelPartData old = children.get(name);
		if (old != null) {
			Map<String, ModelPartData> oldChildren = ((JointsPartDataMixin) (Object) old).children;
			oldChildren.remove(Joints.RIGHT_FOREARM);
			oldChildren.remove(Joints.LEFT_FOREARM);
			oldChildren.remove(Joints.RIGHT_SHIN);
			oldChildren.remove(Joints.LEFT_SHIN);
		}
	}
}
