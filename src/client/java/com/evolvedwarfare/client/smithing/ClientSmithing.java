package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgeQuality;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.PartData;
import com.evolvedwarfare.smithing.SmithingPayloads;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.text.Text;

/** La forja en el cliente: la pantalla del yunque, el dibujo de cada pieza (y su color de metal) y la calidad en las herramientas. */
public final class ClientSmithing {
	private ClientSmithing() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SmithingPayloads.Open.ID, (payload, context) -> context.client().setScreen(new ForgingScreen(payload)));
		ModelPredicateProviderRegistry.register(ModItems.FORGED_PART, EvolvedWarfare.id("part"), (stack, world, entity, seed) -> {
			PartData data = stack.get(ModComponents.FORGED_PART);
			ForgedPart part = data == null ? null : ForgedPart.byId(data.part());
			return part == null ? 0.0F : part.ordinal() / 100.0F;
		});
		ColorProviderRegistry.ITEM.register((stack, tint) -> {
			PartData data = stack.get(ModComponents.FORGED_PART);
			ForgeMaterial m = data == null ? null : ForgeMaterial.byId(data.material());
			return tint == 0 && m != null ? 0xFF000000 | m.color : -1;
		}, ModItems.FORGED_PART);
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			Integer q = stack.get(ModComponents.FORGE_QUALITY);
			if (q != null && !stack.isOf(ModItems.FORGED_PART)) {
				lines.add(1, Text.translatable("tooltip.evolvedwarfare.forged.quality", Text.translatable("forge_quality.evolvedwarfare." + ForgeQuality.key(q)))
						.formatted(ForgeQuality.color(q)));
			}
		});
	}
}
