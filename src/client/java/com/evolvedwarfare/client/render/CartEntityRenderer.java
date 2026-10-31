package com.evolvedwarfare.client.render;

import com.evolvedwarfare.cart.CartEntity;
import com.evolvedwarfare.cart.CartModule;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * La carreta en 3D, con las texturas de madera de vanilla: la tabla de 3x3, dos vigas debajo, el eje, dos ruedas de
 * radios (giran según avanza) y las dos varas (en el suelo si nadie tira; a la altura de las manos, o de los costados
 * del caballo, si tiran de ella). Encima, lo que lleve: cofres, barriles, hornos (encendidos si funden), mesas y camas.
 * Las coordenadas son las de {@link CartEntity} (+Z hacia las varas).
 */
public class CartEntityRenderer extends EntityRenderer<CartEntity> {
	private static final Identifier DECK = Identifier.ofVanilla("block/oak_planks");
	private static final Identifier BEAM = Identifier.ofVanilla("block/stripped_dark_oak_log");
	private static final Identifier WHEEL = Identifier.ofVanilla("block/spruce_planks");
	private static final Identifier IRON = Identifier.ofVanilla("block/iron_block");
	private static final Identifier SHAFT = Identifier.ofVanilla("block/stripped_oak_log");
	private static final float R = (float) CartEntity.WHEEL_RADIUS;
	private static final float WHEEL_X = 1.62F;

	public CartEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 1.5F;
	}

	@Override
	public Identifier getTexture(CartEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public void render(CartEntity cart, float entityYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		matrices.push();
		float yaw = MathHelper.lerpAngleDegrees(tickDelta, cart.prevYaw, cart.getYaw());
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		int hurt = cart.hurtTime();
		if (hurt > 0) {
			float h = hurt - tickDelta;
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(h * 1.4F) * h * 0.25F));
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
		// la tabla, las vigas y el eje
		box(matrices, buffer, sprite(DECK), -1.5F, 0.55F, -1.5F, 1.5F, (float) CartEntity.DECK, 1.5F, light);
		box(matrices, buffer, sprite(BEAM), 0.95F, 0.40F, -1.65F, 1.2F, 0.55F, 1.6F, light);
		box(matrices, buffer, sprite(BEAM), -1.2F, 0.40F, -1.65F, -0.95F, 0.55F, 1.6F, light);
		box(matrices, buffer, sprite(BEAM), -WHEEL_X, R - 0.06F, -0.06F, WHEEL_X, R + 0.06F, 0.06F, light);
		// las ruedas
		float angle = MathHelper.lerp(tickDelta, cart.prevWheelAngle, cart.wheelAngle);
		wheel(matrices, buffer, WHEEL_X, angle, light);
		wheel(matrices, buffer, -WHEEL_X, angle, light);
		// las varas
		shafts(cart, matrices, buffer, light);
		// lo de encima
		for (int i = 0; i < CartEntity.CELLS; i++) {
			CartEntity.Look look = cart.look(i);
			if (look != null) {
				module(i, look, matrices, consumers, light);
			}
		}
		matrices.pop();
		super.render(cart, entityYaw, tickDelta, matrices, consumers, light);
	}

	// ------------------------------------------------------------------ piezas

	private static void wheel(MatrixStack matrices, VertexConsumer buffer, float x, float angle, int light) {
		Sprite wood = sprite(WHEEL);
		Sprite iron = sprite(IRON);
		matrices.push();
		matrices.translate(0.0F, R, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(angle));
		float segment = 2.0F * MathHelper.PI * R / 12.0F * 0.56F;
		for (int k = 0; k < 12; k++) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(k * 30.0F));
			box(matrices, buffer, wood, x - 0.06F, R - 0.1F, -segment, x + 0.06F, R, segment, light);
			box(matrices, buffer, iron, x - 0.065F, R - 0.025F, -segment, x + 0.065F, R + 0.005F, segment, light);
			matrices.pop();
		}
		for (int k = 0; k < 3; k++) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(k * 60.0F));
			box(matrices, buffer, wood, x - 0.035F, -R + 0.08F, -0.035F, x + 0.035F, R - 0.08F, 0.035F, light);
			matrices.pop();
		}
		box(matrices, buffer, iron, x - 0.09F, -0.09F, -0.09F, x + 0.09F, 0.09F, 0.09F, light);
		matrices.pop();
	}

	/** Las varas: de la parte de delante de la tabla hasta quien tira (o al suelo). */
	private static void shafts(CartEntity cart, MatrixStack matrices, VertexConsumer buffer, int light) {
		Entity puller = cart.puller();
		float tipX;
		float tipY;
		float tipZ;
		if (puller instanceof AbstractHorseEntity) {
			tipX = 0.78F;
			tipY = 1.05F;
			tipZ = (float) CartEntity.HORSE_HITCH + 0.45F;
		} else if (puller != null) {
			tipX = 0.42F;
			tipY = 0.68F;
			tipZ = (float) CartEntity.PLAYER_HITCH + 0.2F;
		} else {
			tipX = 0.55F;
			tipY = 0.06F;
			tipZ = 2.95F;
		}
		Sprite wood = sprite(SHAFT);
		for (int side : new int[] {1, -1}) {
			float bx = side * 0.7F;
			float by = 0.62F;
			float bz = 1.45F;
			float dx = side * tipX - bx;
			float dy = tipY - by;
			float dz = tipZ - bz;
			float horizontal = MathHelper.sqrt(dx * dx + dz * dz);
			float length = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
			matrices.push();
			matrices.translate(bx, by, bz);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) MathHelper.atan2(dx, dz)));
			matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -MathHelper.atan2(dy, horizontal)));
			box(matrices, buffer, wood, -0.05F, -0.05F, 0.0F, 0.05F, 0.05F, length, light);
			matrices.pop();
		}
		// travesaño entre las varas, pegado a la tabla
		box(matrices, buffer, wood, -0.75F, 0.57F, 1.48F, 0.75F, 0.67F, 1.6F, light);
	}

	/** Lo que va en un hueco, mirando hacia fuera (la cama, hacia donde tiene los pies). */
	private static void module(int cell, CartEntity.Look look, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ItemStack stack = new ItemStack(look.item());
		CartModule.Kind kind = CartModule.kindOf(stack);
		if (kind == null || !(look.item() instanceof BlockItem blockItem)) {
			return;
		}
		Vec3d c = CartEntity.cellCenter(cell);
		matrices.push();
		matrices.translate(c.x, CartEntity.DECK, c.z);
		Block block = blockItem.getBlock();
		BlockState state = block.getDefaultState();
		if (kind == CartModule.Kind.BED) {
			Vec3d p = look.partner() >= 0 ? CartEntity.cellCenter(look.partner()) : c.add(0, 0, -1);
			// el de vanilla tiene los pies hacia -Z de la cabecera
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) MathHelper.atan2(-(p.x - c.x), -(p.z - c.z))));
		} else {
			int row = cell / 3;
			int col = cell % 3;
			double ox = col == 0 ? 1 : col == 2 ? -1 : 0;
			double oz = ox != 0 ? 0 : row == 0 ? 1 : -1;
			// el frente (cofre, horno) mira a +Z: se gira hacia fuera
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) MathHelper.atan2(ox, oz)));
			if (state.contains(Properties.HORIZONTAL_FACING)) {
				state = state.with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
			}
			if (kind.furnace()) {
				state = state.with(AbstractFurnaceBlock.LIT, look.lit());
			}
		}
		matrices.translate(-0.5F, 0.0F, -0.5F);
		if (block == Blocks.BARREL) {
			state = Blocks.BARREL.getDefaultState();
		}
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}

	// ------------------------------------------------------------------ cajas con textura de bloque

	private static Sprite sprite(Identifier id) {
		return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(id);
	}

	/** Una caja (en bloques) con la textura repetida cada bloque, como se vería en el mundo. */
	static void box(MatrixStack matrices, VertexConsumer buffer, Sprite sprite, float x0, float y0, float z0, float x1, float y1, float z1,
			int light) {
		MatrixStack.Entry e = matrices.peek();
		// arriba y abajo (x, z)
		face(buffer, e, sprite, light, 0, 1, 0, x0, z0, x1, z1, (a, b) -> new float[] {a, y1, b});
		face(buffer, e, sprite, light, 0, -1, 0, x0, z0, x1, z1, (a, b) -> new float[] {a, y0, b});
		// norte y sur (x, y)
		face(buffer, e, sprite, light, 0, 0, -1, x0, y0, x1, y1, (a, b) -> new float[] {a, b, z0});
		face(buffer, e, sprite, light, 0, 0, 1, x0, y0, x1, y1, (a, b) -> new float[] {a, b, z1});
		// oeste y este (z, y)
		face(buffer, e, sprite, light, -1, 0, 0, z0, y0, z1, y1, (a, b) -> new float[] {x0, b, a});
		face(buffer, e, sprite, light, 1, 0, 0, z0, y0, z1, y1, (a, b) -> new float[] {x1, b, a});
	}

	private interface Corner {
		float[] at(float a, float b);
	}

	/** Una cara partida en trozos de un bloque como mucho (las texturas del atlas no se repiten solas). */
	private static void face(VertexConsumer buffer, MatrixStack.Entry e, Sprite sprite, int light, float nx, float ny, float nz, float a0, float b0,
			float a1, float b1, Corner corner) {
		for (float a = a0; a < a1 - 1.0E-4F; a = Math.min(a1, (float) Math.floor(a + 1.0F + 1.0E-4F))) {
			float an = Math.min(a1, (float) Math.floor(a + 1.0F + 1.0E-4F));
			for (float b = b0; b < b1 - 1.0E-4F; b = Math.min(b1, (float) Math.floor(b + 1.0F + 1.0E-4F))) {
				float bn = Math.min(b1, (float) Math.floor(b + 1.0F + 1.0E-4F));
				float u0 = u(sprite, a);
				float u1 = u(sprite, an == Math.floor(an) && an > a ? an - 1.0E-4F : an);
				float v0 = v(sprite, b);
				float v1 = v(sprite, bn == Math.floor(bn) && bn > b ? bn - 1.0E-4F : bn);
				vertex(buffer, e, corner.at(a, b), u0, v1, light, nx, ny, nz);
				vertex(buffer, e, corner.at(an, b), u1, v1, light, nx, ny, nz);
				vertex(buffer, e, corner.at(an, bn), u1, v0, light, nx, ny, nz);
				vertex(buffer, e, corner.at(a, bn), u0, v0, light, nx, ny, nz);
			}
		}
	}

	private static float u(Sprite sprite, float a) {
		float f = a - (float) Math.floor(a);
		return sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * f;
	}

	private static float v(Sprite sprite, float b) {
		float f = b - (float) Math.floor(b);
		return sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * (1.0F - f);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry e, float[] p, float u, float v, int light, float nx, float ny, float nz) {
		buffer.vertex(e, p[0], p[1], p[2]).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
				.normal(e, nx, ny, nz);
	}
}
