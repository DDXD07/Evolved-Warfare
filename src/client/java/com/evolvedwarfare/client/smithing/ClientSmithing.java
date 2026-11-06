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
		ClientPlayNetworking.registerGlobalReceiver(com.evolvedwarfare.smithing.MoldPayloads.Open.ID,
				(payload, context) -> context.client().setScreen(new MoldScreen(payload.pos())));
		net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE_ENTITY,
				CrucibleRenderer::new);
		net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(com.evolvedwarfare.registry.ModBlocks.CLAY_MOLD_ENTITY,
				MoldRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(com.evolvedwarfare.registry.ModBlocks.CRUCIBLE,
				CrucibleRenderer.ITEM);
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tick) -> PourGauge.render(ctx));
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(
				new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
					@Override
					public net.minecraft.util.Identifier getFabricId() {
						return EvolvedWarfare.id("part_masks");
					}

					@Override
					public void reload(net.minecraft.resource.ResourceManager manager) {
						PartMasks.clear();
					}
				});
		ModelPredicateProviderRegistry.register(ModItems.FORGED_PART, EvolvedWarfare.id("part"), (stack, world, entity, seed) -> {
			PartData data = stack.get(ModComponents.FORGED_PART);
			ForgedPart part = data == null ? null : ForgedPart.byId(data.part());
			return part == null ? 0.0F : part.ordinal() / 100.0F;
		});
		ColorProviderRegistry.ITEM.register((stack, tint) -> {
			PartData data = stack.get(ModComponents.FORGED_PART);
			ForgeMaterial m = data == null ? null : ForgeMaterial.byId(data.material());
			if (tint != 0 || m == null) {
				return -1;
			}
			// en bruto: más oscura (sin pulir)
			return data.quality() <= 0 ? Quads.shade(0xFF000000 | m.color, 0.62F) : 0xFF000000 | m.color;
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
