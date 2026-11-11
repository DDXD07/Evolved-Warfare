package com.evolvedwarfare.client.train;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

/**
 * Dibujar cajas con texturas de bloques (las de vanilla: tablones, piedra, hierro...) a cualquier tamaño: cada cara se
 * parte en cuadros de un bloque como mucho, para que la textura se repita como en un bloque de verdad.
 */
final class Cubes {
	private static final Map<String, Identifier> IDS = new HashMap<>();

	private Cubes() {
	}

	/** La textura (se busca cada vez en el atlas: al recargar los recursos, cambia). */
	static Sprite sprite(String path) {
		return MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(IDS.computeIfAbsent(path, Identifier::ofVanilla));
	}

	static void box(MatrixStack.Entry e, VertexConsumer vc, Box b, Sprite s, int argb, int light) {
		box(e, vc, (float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY, (float) b.maxZ, s, argb, light);
	}

	static void box(MatrixStack.Entry e, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, Sprite s, int argb, int light) {
		// abajo y arriba
		face(e, vc, s, argb, light, 0, -1, 0, x0, x1, z0, z1, (a, c) -> new float[] {a, y0, c});
		face(e, vc, s, argb, light, 0, 1, 0, x0, x1, z0, z1, (a, c) -> new float[] {a, y1, c});
		// los lados
		face(e, vc, s, argb, light, 0, 0, -1, x0, x1, y0, y1, (a, c) -> new float[] {a, c, z0});
		face(e, vc, s, argb, light, 0, 0, 1, x0, x1, y0, y1, (a, c) -> new float[] {a, c, z1});
		face(e, vc, s, argb, light, -1, 0, 0, z0, z1, y0, y1, (a, c) -> new float[] {x0, c, a});
		face(e, vc, s, argb, light, 1, 0, 0, z0, z1, y0, y1, (a, c) -> new float[] {x1, c, a});
	}

	private interface Map2 {
		float[] at(float a, float c);
	}

	/** Una cara (de a0 a a1 por c0 a c1), en cuadros de un bloque. */
	private static void face(MatrixStack.Entry e, VertexConsumer vc, Sprite s, int argb, int light, float nx, float ny, float nz, float a0, float a1, float c0,
			float c1, Map2 m) {
		if (a1 - a0 < 1e-4F || c1 - c0 < 1e-4F) {
			return;
		}
		for (float a = a0; a < a1 - 1e-4F; a += 1.0F) {
			float an = Math.min(a1, a + 1.0F);
			for (float c = c0; c < c1 - 1e-4F; c += 1.0F) {
				float cn = Math.min(c1, c + 1.0F);
				float u0 = s.getFrameU(0), u1 = s.getFrameU(an - a), v0 = s.getFrameV(1.0F - (cn - c)), v1 = s.getFrameV(1.0F);
				vertex(e, vc, m.at(a, c), u0, v1, argb, light, nx, ny, nz);
				vertex(e, vc, m.at(an, c), u1, v1, argb, light, nx, ny, nz);
				vertex(e, vc, m.at(an, cn), u1, v0, argb, light, nx, ny, nz);
				vertex(e, vc, m.at(a, cn), u0, v0, argb, light, nx, ny, nz);
			}
		}
	}

	private static void vertex(MatrixStack.Entry e, VertexConsumer vc, float[] p, float u, float v, int argb, int light, float nx, float ny, float nz) {
		vc.vertex(e, p[0], p[1], p[2]).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
	}
}
