package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.CrucibleBlockEntity;
import com.evolvedwarfare.smithing.CrucibleContents;
import com.evolvedwarfare.smithing.CrucibleItem;
import com.evolvedwarfare.smithing.ForgeMaterial;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Lo de dentro del crisol: el metal fundido (la lava del juego teñida del metal, brillando en la oscuridad) que sube con lo
 * que lleva, y los lingotes que aún no se han fundido, hundiéndose poco a poco. En el mundo y en la mano.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
	private static final Identifier LAVA = Identifier.ofVanilla("block/lava_still");
	private static final float IN0 = 4.0F / 16.0F;
	private static final float IN1 = 12.0F / 16.0F;
	private static final float FLOOR = 2.2F / 16.0F;
	private static final float TOP = 9.2F / 16.0F;

	public CrucibleRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(CrucibleBlockEntity crucible, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		contents(crucible.contents(), crucible.meltFraction(), crucible.heated(), matrices, consumers, light, overlay);
	}

	public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, consumers, light, overlay) -> {
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(ModBlocks.CRUCIBLE.getDefaultState(), matrices, consumers, light, overlay);
		contents(CrucibleItem.contents(stack), 0.0F, false, matrices, consumers, light, overlay);
	};

	/** El color del metal fundido. */
	static int moltenColor(@Nullable ForgeMaterial m) {
		return m == ForgeMaterial.GOLD ? 0xFFFFE070 : 0xFFFFB060;
	}

	static void contents(CrucibleContents c, float melt, boolean heated, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		if (c.isEmpty()) {
			return;
		}
		ForgeMaterial m = c.material();
		if (c.molten() > 0.01F) {
			Sprite lava = Quads.sprite(LAVA);
			VertexConsumer b = consumers.getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
			Quads.top(b, matrices.peek(), lava, IN0, IN0, IN1, IN1, FLOOR + (TOP - FLOOR) * Math.min(1.0F, c.molten() / CrucibleBlockEntity.CAPACITY),
					moltenColor(m), LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
		}
		// los lingotes sin fundir: el de arriba se va hundiendo
		var items = MinecraftClient.getInstance().getItemRenderer();
		ItemStack ingot = new ItemStack(heated || c.molten() > 0.01F ? (m == ForgeMaterial.GOLD ? ModItems.HOT_GOLD_INGOT : ModItems.HOT_IRON_INGOT)
				: (m == ForgeMaterial.GOLD ? Items.GOLD_INGOT : Items.IRON_INGOT));
		float base = FLOOR + (TOP - FLOOR) * Math.min(1.0F, c.molten() / CrucibleBlockEntity.CAPACITY);
		for (int i = 0; i < c.solid(); i++) {
			matrices.push();
			float sink = i == 0 ? melt * 0.12F : 0.0F;
			matrices.translate(0.5F + (i % 2 == 0 ? -0.08F : 0.08F), base + 0.03F + i * 0.06F - sink, 0.5F + (i / 2 == 0 ? -0.06F : 0.06F));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 67.0F + 20.0F));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
			matrices.scale(0.4F, 0.4F, 0.4F);
			items.renderItem(ingot, ModelTransformationMode.FIXED, heated ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, overlay, matrices,
					consumers, null, i);
			matrices.pop();
		}
	}
}
