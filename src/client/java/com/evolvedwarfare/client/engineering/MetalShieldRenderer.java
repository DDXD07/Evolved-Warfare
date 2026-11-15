package com.evolvedwarfare.client.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.engineering.MetalShieldItem;
import com.evolvedwarfare.engineering.Metalwork;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ShieldEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Los escudos reforzados y el pavés: el modelo del escudo de vanilla con su textura (el pavés, más grande). */
public final class MetalShieldRenderer {
	private static ShieldEntityModel model;

	private MetalShieldRenderer() {
	}

	public static void register() {
		for (Item item : new Item[] {Metalwork.REINFORCED_SHIELD, Metalwork.PAVISE}) {
			Identifier tex = EvolvedWarfare.id("textures/entity/shield/" + (item == Metalwork.PAVISE ? "pavise" : "reinforced_shield") + ".png");
			boolean pavise = item == Metalwork.PAVISE;
			BuiltinItemRendererRegistry.INSTANCE.register(item, (ItemStack stack, ModelTransformationMode mode, MatrixStack m,
					VertexConsumerProvider consumers, int light, int overlay) -> {
				if (model == null) {
					model = new ShieldEntityModel(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.SHIELD));
				}
				m.push();
				m.scale(1.0F, -1.0F, -1.0F);
				if (pavise) {
					m.scale(1.25F, 1.35F, 1.0F);
				}
				VertexConsumer vc = ItemRenderer.getDirectItemGlintConsumer(consumers, model.getLayer(tex), true, stack.hasGlint());
				model.getHandle().render(m, vc, light, overlay);
				model.getPlate().render(m, vc, light, overlay);
				m.pop();
			});
			ModelPredicateProviderRegistry.register(item, Identifier.ofVanilla("blocking"),
					(stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);
		}
	}

	@SuppressWarnings("unused")
	private static boolean isShield(ItemStack stack) {
		return stack.getItem() instanceof MetalShieldItem;
	}
}
