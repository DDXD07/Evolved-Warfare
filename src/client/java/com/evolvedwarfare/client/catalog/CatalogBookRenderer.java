package com.evolvedwarfare.client.catalog;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.block.CatalogBlock;
import com.evolvedwarfare.block.CatalogBlockEntity;
import com.evolvedwarfare.trade.TraderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * El Catálogo del mercader en 3D, tumbado encima del mostrador: contratapa, hojas, lomo y la tapa con el título y el
 * emblema de su oficio, legible desde el lado de los clientes. Cuando el jugador lo abre, la tapa gira sobre el lomo y
 * se ven las guardas y la primera página ({@link CatalogSession#cover}).
 *
 * <p>Medidas en píxeles de bloque: 12 x 9, 2,2 de grueso. Coordenadas de textura en la escala de 128 (ver
 * tools/ew_catalog.py).</p>
 */
public class CatalogBookRenderer implements BlockEntityRenderer<CatalogBlockEntity> {
	private static final Identifier[] TEXTURES = new Identifier[TraderType.values().length];

	static {
		for (TraderType type : TraderType.values()) {
			TEXTURES[type.ordinal()] = EvolvedWarfare.id("textures/entity/catalog/cover_" + type.id() + ".png");
		}
	}

	private static final float X0 = -6.0F;
	private static final float X1 = 6.0F;
	private static final float Z0 = -4.5F;
	private static final float Z1 = 4.5F;
	private static final float BACK = 0.5F;
	private static final float PAGES = 1.7F;
	private static final float TOP = 2.2F;

	// regiones de la textura (escala 128)
	private static final float[] OUTER = {0, 0, 48, 36};
	private static final float[] INNER = {48, 0, 96, 36};
	private static final float[] PAGE = {0, 40, 48, 76};
	private static final float[] EDGE = {48, 40, 96, 48};
	private static final float[] LEATHER = {48, 48, 64, 64};
	private static final float[] BACK_COVER = {0, 80, 48, 116};

	@Override
	public void render(CatalogBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof CatalogBlock)) {
			return;
		}
		Identifier texture = TEXTURES[Math.floorMod(state.get(CatalogBlock.TRADE), TEXTURES.length)];
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		float open = CatalogSession.cover(entity.getPos());
		matrices.push();
		matrices.translate(0.5, 0.0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle(state.get(CatalogBlock.FACING))));
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		// contratapa
		box(vc, matrices, light, overlay, X0, 0, Z0, X1, BACK, Z1, LEATHER, BACK_COVER, LEATHER);
		// hojas (un poco metidas) y lomo
		box(vc, matrices, light, overlay, X0 + 0.3F, BACK, Z0 + 0.3F, X1 - 0.2F, PAGES, Z1 - 0.3F, PAGE, LEATHER, EDGE);
		box(vc, matrices, light, overlay, X0, BACK, Z0, X0 + 0.3F, PAGES, Z1, LEATHER, LEATHER, LEATHER);
		// tapa: gira sobre el lomo (de la derecha, por encima, a la izquierda)
		matrices.push();
		matrices.translate(X0, PAGES, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(open * 178.0F));
		matrices.translate(-X0, -PAGES, 0.0F);
		box(vc, matrices, light, overlay, X0, PAGES, Z0, X1, TOP, Z1, OUTER, INNER, LEATHER);
		matrices.pop();
		matrices.pop();
	}

	/** Giro para que el lado de los clientes (+z del libro) mire hacia FACING. */
	private static float angle(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0F;
			case NORTH -> 180.0F;
			case WEST -> 270.0F;
			default -> 0.0F;
		};
	}

	/** Un cubo: arriba, abajo y los cuatro lados, cada uno con su región de la textura. */
	private static void box(VertexConsumer vc, MatrixStack matrices, int light, int overlay, float x0, float y0, float z0, float x1, float y1,
			float z1, float[] top, float[] bottom, float[] sides) {
		MatrixStack.Entry e = matrices.peek();
		Matrix4f m = e.getPositionMatrix();
		// arriba: u a lo largo de x, v de lejos (z0) a cerca (z1) de los clientes
		quad(vc, e, m, light, overlay, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top, false);
		// abajo (se ve con la tapa abierta: las guardas)
		quad(vc, e, m, light, overlay, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, bottom, false);
		quad(vc, e, m, light, overlay, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, sides, true);
		quad(vc, e, m, light, overlay, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, sides, true);
		quad(vc, e, m, light, overlay, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, sides, true);
		quad(vc, e, m, light, overlay, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, sides, true);
	}

	private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int light, int overlay, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			float[] uv, boolean strip) {
		float u0 = uv[0] / 128.0F;
		float v0 = uv[1] / 128.0F;
		float u1 = uv[2] / 128.0F;
		float v1 = strip ? (uv[1] + Math.min(4.0F, uv[3] - uv[1])) / 128.0F : uv[3] / 128.0F;
		vertex(vc, e, m, ax, ay, az, u0, v0, light, overlay, nx, ny, nz);
		vertex(vc, e, m, bx, by, bz, u1, v0, light, overlay, nx, ny, nz);
		vertex(vc, e, m, cx, cy, cz, u1, v1, light, overlay, nx, ny, nz);
		vertex(vc, e, m, dx, dy, dz, u0, v1, light, overlay, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, float x, float y, float z, float u, float v, int light,
			int overlay, float nx, float ny, float nz) {
		vc.vertex(m, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light).normal(e, nx, ny, nz);
	}
}
