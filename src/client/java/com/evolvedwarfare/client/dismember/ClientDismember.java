package com.evolvedwarfare.client.dismember;

import com.evolvedwarfare.combat.Dismemberment;
import com.evolvedwarfare.network.SeveredPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;

/** Lo que les falta a los NPCs que se ven (lo manda el servidor), para no dibujarlo y dibujar el muñón. */
public final class ClientDismember {
	private static final Map<Integer, Integer> MASKS = new HashMap<>();

	private ClientDismember() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SeveredPayload.ID, (payload, context) -> MASKS.put(payload.entityId(), payload.mask()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MASKS.clear());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MASKS.clear());
	}

	public static int mask(Entity e) {
		if (MASKS.isEmpty()) {
			return 0;
		}
		Integer m = MASKS.get(e.getId());
		return m == null ? 0 : m;
	}

	/** Esconde lo que le falta (o lo enseña todo si no le falta nada). */
	public static void apply(PlayerEntityModel<?> model, int mask) {
		boolean head = (mask & Dismemberment.HEAD) == 0;
		boolean ra = (mask & Dismemberment.RIGHT_ARM) == 0;
		boolean la = (mask & Dismemberment.LEFT_ARM) == 0;
		boolean rl = (mask & Dismemberment.RIGHT_LEG) == 0;
		boolean ll = (mask & Dismemberment.LEFT_LEG) == 0;
		model.head.visible = head;
		model.hat.visible = head;
		model.rightArm.visible = ra;
		model.rightSleeve.visible = ra;
		model.leftArm.visible = la;
		model.leftSleeve.visible = la;
		model.rightLeg.visible = rl;
		model.rightPants.visible = rl;
		model.leftLeg.visible = ll;
		model.leftPants.visible = ll;
	}

	/** La armadura de lo que falta tampoco se dibuja. */
	public static void hideArmor(BipedEntityModel<?> context, BipedEntityModel<?> armor) {
		copy(context.head, armor.head);
		copy(context.head, armor.hat);
		copy(context.rightArm, armor.rightArm);
		copy(context.leftArm, armor.leftArm);
		copy(context.rightLeg, armor.rightLeg);
		copy(context.leftLeg, armor.leftLeg);
	}

	private static void copy(ModelPart from, ModelPart to) {
		if (!from.visible) {
			to.visible = false;
		}
	}
}
