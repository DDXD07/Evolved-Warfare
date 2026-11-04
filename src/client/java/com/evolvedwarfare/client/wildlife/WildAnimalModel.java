package com.evolvedwarfare.client.wildlife;

import com.evolvedwarfare.wildlife.WildAnimalEntity;
import com.evolvedwarfare.wildlife.WildSpecies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * El modelo de un animal salvaje, animado según el papel de cada parte:
 * <ul>
 * <li><b>patas</b>: al andar, por parejas en diagonal (las aves, alternas); la liebre y la ardilla van a saltos;</li>
 * <li><b>cabeza</b> (y cuello): mira a donde mira el animal; al pastar baja hasta el suelo; al morder, embiste;</li>
 * <li><b>cola</b>: se mece sola y más al correr (y se levanta);</li>
 * <li><b>orejas</b>: de vez en cuando se sacuden;</li>
 * <li><b>alas</b>: plegadas en el suelo, batiendo en el aire;</li>
 * <li><b>cuerpo</b>: respira, y en el agua las patas reman.</li>
 * </ul>
 */
public class WildAnimalModel extends SinglePartEntityModel<WildAnimalEntity> {
	private record Leg(ModelPart part, int phase, float gait, boolean front) {
	}

	private final ModelPart root;
	private final WildSpecies species;
	@Nullable
	private ModelPart body;
	@Nullable
	private ModelPart head;
	@Nullable
	private ModelPart neck;
	@Nullable
	private ModelPart tail;
	private final List<Leg> legs = new ArrayList<>();
	private final List<ModelPart> ears = new ArrayList<>();
	private final List<Integer> earSides = new ArrayList<>();
	private final List<ModelPart> wings = new ArrayList<>();
	private final List<Integer> wingSides = new ArrayList<>();

	public WildAnimalModel(ModelPart root, WildSpecies species) {
		this.root = root;
		this.species = species;
		for (WildModels.PartDef def : WildModels.def(species).parts()) {
			ModelPart part = root;
			for (String step : def.path()) {
				part = part.getChild(step);
			}
			switch (def.role()) {
				case "body" -> body = part;
				case "head" -> head = part;
				case "neck" -> neck = part;
				case "tail" -> tail = part;
				case "leg" -> legs.add(new Leg(part, def.phase(), def.gait(), def.name().startsWith("leg_f")));
				case "ear" -> {
					ears.add(part);
					earSides.add(def.side());
				}
				case "wing" -> {
					wings.add(part);
					wingSides.add(def.side());
				}
				default -> {
				}
			}
		}
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(WildAnimalEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		getPart().traverse().forEach(ModelPart::resetTransform);
		float speed = Math.min(1.0F, limbDistance);
		float seed = (entity.getId() * 37) % 100;
		float tickDelta = MathHelper.clamp(animationProgress - entity.age, 0.0F, 1.0F);
		boolean water = entity.isTouchingWater();
		// patas
		if (species.hopper && speed > 0.05F) {
			float hop = Math.abs(MathHelper.sin(limbAngle * 0.6662F * 0.9F));
			for (Leg leg : legs) {
				leg.part().pitch += (leg.front() ? -1.0F : 1.0F) * hop * 0.9F * speed;
			}
			if (body != null) {
				body.pivotY -= hop * 2.0F * speed;
			}
		} else {
			for (Leg leg : legs) {
				float swing = MathHelper.cos(limbAngle * 0.6662F * leg.gait() + (leg.phase() == 1 ? MathHelper.PI : 0.0F));
				leg.part().pitch += swing * 1.2F * speed;
			}
		}
		if (water) {
			for (Leg leg : legs) {
				leg.part().pitch += MathHelper.cos(animationProgress * 0.5F + leg.phase() * MathHelper.PI) * 0.6F;
			}
		}
		// cuerpo: respira
		if (body != null) {
			body.pitch += MathHelper.sin(animationProgress * 0.06F + seed) * 0.015F;
		}
		// cabeza: mira, pasta y embiste
		if (head != null) {
			head.yaw += headYaw * MathHelper.RADIANS_PER_DEGREE * 0.7F;
			head.pitch += headPitch * MathHelper.RADIANS_PER_DEGREE * 0.6F;
			float graze = entity.grazeAmount(tickDelta);
			if (graze > 0.0F) {
				if (neck != null) {
					neck.pitch += graze * 1.05F;
					head.pitch += graze * 0.45F + MathHelper.sin(animationProgress * 0.9F) * 0.06F * graze;
				} else {
					head.pitch += graze * 0.9F;
				}
			}
			if (handSwingProgress > 0.0F) {
				float lunge = MathHelper.sin(handSwingProgress * MathHelper.PI);
				head.pitch += lunge * 0.55F;
				if (body != null) {
					body.pivotZ -= lunge * 1.5F;
				}
			}
		}
		// cola
		if (tail != null) {
			tail.yaw += MathHelper.sin(animationProgress * 0.12F + seed) * 0.12F + MathHelper.sin(limbAngle * 0.6662F) * 0.3F * speed;
			if (!species.bird) {
				tail.pitch += speed * 0.3F;
			}
		}
		// orejas: se sacuden de vez en cuando
		float cycle = (animationProgress + seed * 3.7F) % 70.0F;
		if (cycle < 5.0F) {
			float twitch = MathHelper.sin(cycle / 5.0F * MathHelper.PI) * 0.35F;
			for (int i = 0; i < ears.size(); i++) {
				ears.get(i).roll += twitch * earSides.get(i);
			}
		}
		// alas: plegadas o batiendo
		boolean flying = !entity.isOnGround() && !water;
		for (int i = 0; i < wings.size(); i++) {
			float flap = flying ? 0.35F + 0.95F * Math.abs(MathHelper.sin(animationProgress * 1.7F)) : (entity.running() ? 0.12F : 0.0F);
			wings.get(i).roll += flap * wingSides.get(i);
		}
	}
}
