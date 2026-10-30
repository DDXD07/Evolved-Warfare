package com.evolvedwarfare.client.render;

import com.evolvedwarfare.block.CropCrateBlockEntity;
import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.registry.ModBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que se ve dentro de una Caja de cultivos: el cultivo amontonado, más alto cuanto más lleva (con una cama de paja
 * debajo cuando ya sube), en el mundo y en el icono del objeto. Las cosas planas (trigo, zanahorias…) van tumbadas y
 * revueltas; los bloques (calabazas, melones, cactus), en cubitos.
 */
public class CropCrateRenderer implements BlockEntityRenderer<CropCrateBlockEntity> {
	private static final Identifier STRAW = Identifier.ofVanilla("block/hay_block_top");
	private static final float INNER_MIN = 2.0F / 16.0F;
	private static final float INNER_MAX = 14.0F / 16.0F;
	private static final float FLOOR = 2.0F / 16.0F;

	public CropCrateRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(CropCrateBlockEntity crate, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		renderContents(crate.contents(), crate.getPos().asLong(), matrices, consumers, light, overlay, crate.getWorld());
	}

	/** El icono del objeto: la caja (su modelo de bloque) y, si lleva algo, lo de dentro. */
	public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, consumers, light, overlay) -> {
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(ModBlocks.CROP_CRATE.getDefaultState(), matrices,
				consumers, light, overlay);
		CropCrateItem.Contents contents = CropCrateItem.contents(stack);
		if (contents != null) {
			renderContents(contents.stack(), 0L, matrices, consumers, light, overlay, null);
		}
	};

	public static void renderContents(ItemStack inside, long seed, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			int overlay, @Nullable World world) {
		if (inside.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		ItemRenderer items = client.getItemRenderer();
		ItemStack one = inside.copyWithCount(1);
		boolean cube = items.getModel(one, world, null, 0).hasDepth();
		int count = inside.getCount();
		float fill = MathHelper.clamp(count / (float) CropCrateItem.capacity(inside.getItem()), 0.0F, 1.0F);
		// hasta dónde llega el montón: los cubitos (4 píxeles) asoman justo por el borde al estar llena
		float surface = FLOOR + (cube ? 9.0F : 10.5F) / 16.0F * fill;
		java.util.Random random = new java.util.Random(seed * 31L + inside.getItem().hashCode());
		if (surface - FLOOR > 1.0F / 16.0F) {
			straw(matrices, consumers, surface - (cube ? 0.0F : 0.6F / 16.0F), light, overlay);
		}
		int shown = Math.min(9, count);
		if (!cube && count >= 16) {
			// una capa de debajo, entre los huecos de la de arriba
			for (int i = 0; i < 4; i++) {
				float x = (6.0F + (i % 2) * 4.0F) / 16.0F;
				float z = (6.0F + (i / 2) * 4.0F) / 16.0F;
				flat(items, one, matrices, consumers, light, overlay, world, random, x, surface - 0.35F / 16.0F + i * 0.01F / 16.0F, z, i);
			}
		}
		for (int i = 0; i < shown; i++) {
			float x = (4.0F + (i % 3) * 4.0F) / 16.0F;
			float z = (4.0F + (i / 3) * 4.0F) / 16.0F;
			if (cube) {
				matrices.push();
				matrices.translate(x + jitter(random, 0.3F), surface + 2.0F / 16.0F, z + jitter(random, 0.3F));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(jitter(random, 8.0F * 16.0F)));
				matrices.scale(0.5F, 0.5F, 0.5F);
				items.renderItem(one, ModelTransformationMode.FIXED, light, overlay, matrices, consumers, world, i);
				matrices.pop();
			} else {
				flat(items, one, matrices, consumers, light, overlay, world, random, x, surface + i * 0.15F / 16.0F, z, 4 + i);
			}
		}
	}

	/** Uno tumbado, girado al azar y un poco movido de su sitio. */
	private static void flat(ItemRenderer items, ItemStack one, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay,
			@Nullable World world, java.util.Random random, float x, float y, float z, int seed) {
		matrices.push();
		matrices.translate(x + jitter(random, 0.6F), y + 0.5F / 16.0F, z + jitter(random, 0.6F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(random.nextFloat() * 360.0F));
		matrices.scale(0.42F, 0.42F, 0.42F);
		items.renderItem(one, ModelTransformationMode.FIXED, light, overlay, matrices, consumers, world, seed);
		matrices.pop();
	}

	/** ±amount píxeles (en bloques). */
	private static float jitter(java.util.Random random, float amount) {
		return (random.nextFloat() * 2.0F - 1.0F) * amount / 16.0F;
	}

	/** La cama de paja que sube con el montón. */
	private static void straw(MatrixStack matrices, VertexConsumerProvider consumers, float y, int light, int overlay) {
		Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(STRAW);
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
		MatrixStack.Entry entry = matrices.peek();
		float u0 = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * INNER_MIN;
		float u1 = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * INNER_MAX;
		float v0 = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * INNER_MIN;
		float v1 = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * INNER_MAX;
		vertex(buffer, entry, INNER_MIN, y, INNER_MIN, u0, v0, light, overlay);
		vertex(buffer, entry, INNER_MIN, y, INNER_MAX, u0, v1, light, overlay);
		vertex(buffer, entry, INNER_MAX, y, INNER_MAX, u1, v1, light, overlay);
		vertex(buffer, entry, INNER_MAX, y, INNER_MIN, u1, v0, light, overlay);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int light, int overlay) {
		buffer.vertex(entry, x, y, z).color(210, 200, 170, 255).texture(u, v).overlay(overlay).light(light).normal(entry, 0.0F, 1.0F, 0.0F);
	}
}
