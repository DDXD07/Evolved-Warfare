package com.evolvedwarfare.client.render.armor;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.armor.FactionArmor;
import com.evolvedwarfare.armor.FactionArmorItem;
import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Dibuja las armaduras de las facciones con sus modelos 3D ({@link FactionArmorModels}, de tools/ew_armor.py) en vez de
 * la capa plana de vanilla: cada parte copia la postura de su parte del cuerpo (cabeza, cuerpo, brazos, piernas; los
 * faldones y las botas, la de las piernas) y solo se dibujan las de la pieza que toca.
 */
public final class FactionArmorRenderer implements ArmorRenderer {
	private static final Map<FactionArmor, ModelPart> MODELS = new EnumMap<>(FactionArmor.class);
	private static final Map<FactionArmor, Identifier> TEXTURES = new EnumMap<>(FactionArmor.class);

	public static void register() {
		List<Item> items = new ArrayList<>();
		ModItems.FACTION_ARMOR.values().forEach(items::addAll);
		ArmorRenderer.register(new FactionArmorRenderer(), items.toArray(new ItemConvertible[0]));
	}

	private static ModelPart model(FactionArmor armor) {
		return MODELS.computeIfAbsent(armor, a -> FactionArmorModels.of(a).createModel());
	}

	private static Identifier texture(FactionArmor armor) {
		return TEXTURES.computeIfAbsent(armor, a -> EvolvedWarfare.id("textures/entity/faction_armor/" + a.id() + ".png"));
	}

	private static String[] parts(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> new String[] {"head"};
			case CHEST -> new String[] {"body", "right_arm", "left_arm", "right_skirt", "left_skirt"};
			case LEGS -> new String[] {"waist", "right_leg", "left_leg"};
			case FEET -> new String[] {"right_boot", "left_boot"};
			default -> new String[0];
		};
	}

	/** La parte del cuerpo cuya postura copia cada parte de la armadura. */
	private static ModelPart follow(BipedEntityModel<LivingEntity> body, String part) {
		return switch (part) {
			case "head" -> body.head;
			case "body", "waist" -> body.body;
			case "right_arm" -> body.rightArm;
			case "left_arm" -> body.leftArm;
			case "right_skirt", "right_leg", "right_boot" -> body.rightLeg;
			default -> body.leftLeg;
		};
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, LivingEntity entity,
			EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> contextModel) {
		if (!(stack.getItem() instanceof FactionArmorItem item)) {
			return;
		}
		FactionArmor armor = item.set();
		ModelPart root = model(armor);
		VertexConsumer vertices = ItemRenderer.getArmorGlintConsumer(vertexConsumers, RenderLayer.getArmorCutoutNoCull(texture(armor)),
				stack.hasGlint());
		for (String name : parts(slot)) {
			ModelPart part = root.getChild(name);
			part.copyTransform(follow(contextModel, name));
			part.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
		}
	}
}
