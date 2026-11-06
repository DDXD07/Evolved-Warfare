package com.evolvedwarfare.client.backpack;

import com.evolvedwarfare.backpack.BackpackBlock;
import com.evolvedwarfare.backpack.BackpackBlockEntity;
import com.evolvedwarfare.backpack.Backpacks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/**
 * El inventario nuevo en el cliente (fase 38): la tecla B (abrir la mochila), su pantalla, el color (teñida), la mochila
 * a la espalda de los jugadores y en el suelo, y la barra rápida de 5.
 */
public final class BackpackClient {
	private static KeyBinding open;
	private static int lastSlot;

	private BackpackClient() {
	}

	public static void register() {
		open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.evolvedwarfare.backpack", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B,
				"key.categories.evolvedwarfare"));
		HandledScreens.register(Backpacks.SCREEN, BackpackScreen::new);
		ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 0 ? DyedColorComponent.getColor(stack, 0xFF8B5A2B) : -1, Backpacks.BACKPACKS);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			// la barra de 5: las teclas 6-9 no llevan a los huecos cerrados (se queda donde estaba)
			var inv = client.player.getInventory();
			if (Backpacks.limited(client.player)) {
				if (inv.selectedSlot >= Backpacks.HOTBAR) {
					inv.selectedSlot = Math.min(lastSlot, Backpacks.HOTBAR - 1);
				}
			}
			lastSlot = inv.selectedSlot;
			while (open.wasPressed()) {
				if (client.currentScreen == null) {
					ClientPlayNetworking.send(new Backpacks.OpenPayload());
				}
			}
		});
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) {
				helper.register(new OnBack(player));
			}
		});
		BlockEntityRendererFactories.register(Backpacks.BACKPACK_ENTITY, ctx -> new OnGround());
		// el botón de ordenar en las mochilas, los cofres, barriles y cajas de shulker (y en la mochila del inventario)
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hs) || client.player == null) {
				return;
			}
			var acc = (com.evolvedwarfare.mixin.client.HandledScreenAccessor) hs;
			int target;
			int bx, by;
			if (screen instanceof BackpackScreen || screen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen
					|| screen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen) {
				target = 0;
				bx = acc.evolvedwarfare$x() + acc.evolvedwarfare$width() - 19;
				by = acc.evolvedwarfare$y() + 4;
			} else if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen && Backpacks.limited(client.player)) {
				target = 1;
				bx = acc.evolvedwarfare$x() + acc.evolvedwarfare$width() - 19;
				by = acc.evolvedwarfare$y() + Backpacks.PACK_Y - 13;
			} else {
				return;
			}
			int syncId = hs.getScreenHandler().syncId;
			var button = net.minecraft.client.gui.widget.ButtonWidget.builder(net.minecraft.text.Text.literal("⇅"),
					b -> ClientPlayNetworking.send(new com.evolvedwarfare.backpack.Sorting.SortPayload(syncId, target)))
					.dimensions(bx, by, 12, 11).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.translatable(
							"container.evolvedwarfare.sort"))).build();
			net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(button);
		});
	}

	/** La mochila a la espalda. */
	static class OnBack extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
		OnBack(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
			super(context);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
				float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
			if (player.isInvisible()) {
				return;
			}
			ItemStack worn = Backpacks.worn(player);
			var shown = com.evolvedwarfare.backpack.Loadout.shown(player);
			ItemStack held = player.getMainHandStack();
			var renderer = MinecraftClient.getInstance().getItemRenderer();
			for (int i = 0; i < shown.size() && i < 3; i++) {
				ItemStack s = shown.get(i);
				if (s.isEmpty() || ItemStack.areItemsAndComponentsEqual(s, held)) {
					continue;
				}
				matrices.push();
				getContextModel().body.rotate(matrices);
				if (i == 0) {
					// a la espalda, en diagonal (por encima de la mochila si la lleva)
					matrices.translate(0.0F, 0.32F, worn.isEmpty() ? 0.17F : 0.45F);
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
					matrices.scale(0.85F, -0.85F, -0.85F);
				} else {
					// a la cadera (derecha o izquierda), colgando
					float side = i == 1 ? -1.0F : 1.0F;
					matrices.translate(side * 0.31F, 0.68F, 0.02F);
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F * side));
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
					matrices.scale(0.55F, -0.55F, -0.55F);
				}
				renderer.renderItem(s, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices, consumers, player.getWorld(), player.getId() + i);
				matrices.pop();
			}
			if (worn.isEmpty()) {
				return;
			}
			matrices.push();
			getContextModel().body.rotate(matrices);
			matrices.translate(0.0F, 0.45F, 0.28F);
			matrices.scale(0.62F, -0.62F, -0.62F);
			MinecraftClient.getInstance().getItemRenderer().renderItem(worn, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices,
					consumers, player.getWorld(), player.getId());
			matrices.pop();
		}
	}

	/** La mochila puesta en el suelo. */
	static class OnGround implements BlockEntityRenderer<BackpackBlockEntity> {
		@Override
		public void render(BackpackBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
			ItemStack stack = be.backpack();
			if (stack.isEmpty()) {
				return;
			}
			matrices.push();
			matrices.translate(0.5F, 0.5F, 0.5F);
			float rot = be.getCachedState().get(BackpackBlock.FACING).asRotation();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - rot));
			MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, light, overlay, matrices, consumers,
					be.getWorld(), 0);
			matrices.pop();
		}
	}
}
