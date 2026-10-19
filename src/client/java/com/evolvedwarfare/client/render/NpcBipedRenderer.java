package com.evolvedwarfare.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Base de los renders de personas del mod: cuerpo de jugador, armadura puesta y la postura de los brazos
 * según el arma (tensar el arco, cargar o apuntar la ballesta, sostener espada y escudo).
 * Todos los NPCs del mod son diestros.
 */
public abstract class NpcBipedRenderer<T extends MobEntity> extends BipedEntityRenderer<T, PlayerEntityModel<T>> {
	protected NpcBipedRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(ModModelLayers.NPC), false), 0.5F);
		addFeature(new ArmorFeatureRenderer<>(this,
				new ArmorEntityModel<>(context.getPart(ModModelLayers.NPC_INNER_ARMOR)),
				new ArmorEntityModel<>(context.getPart(ModModelLayers.NPC_OUTER_ARMOR)),
				context.getModelManager()));
	}

	@Override
	public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		PlayerEntityModel<T> model = getModel();
		ItemStack main = entity.getMainHandStack();
		ItemStack off = entity.getOffHandStack();
		BipedEntityModel.ArmPose right = main.isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		BipedEntityModel.ArmPose left = off.isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		boolean usingMain = entity.isUsingItem() && entity.getActiveHand() == Hand.MAIN_HAND;
		if (main.isOf(Items.BOW) && usingMain) {
			right = BipedEntityModel.ArmPose.BOW_AND_ARROW;
		} else if (main.isOf(Items.CROSSBOW)) {
			if (usingMain) {
				right = BipedEntityModel.ArmPose.CROSSBOW_CHARGE;
			} else if (entity.isAttacking()) {
				right = BipedEntityModel.ArmPose.CROSSBOW_HOLD;
			}
		}
		model.rightArmPose = right;
		model.leftArmPose = left;
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
