package com.evolvedwarfare.client.smithing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Cajitas y caras sueltas con una textura del atlas de bloques (para el crisol y el molde). */
final class Quads {
	private Quads() {
	}

	static Sprite sprite(Identifier id) {
		return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(id);
	}

	/** La cara de arriba (y) de x0..x1, z0..z1 (en bloques), con la parte de la textura que le toca. */
	static void top(VertexConsumer b, MatrixStack.Entry e, Sprite s, float x0, float z0, float x1, float z1, float y, int argb, int light, int overlay) {
		float u0 = s.getFrameU(x0), u1 = s.getFrameU(x1), v0 = s.getFrameV(z0), v1 = s.getFrameV(z1);
		v(b, e, x0, y, z0, u0, v0, argb, light, overlay, 0, 1, 0);
		v(b, e, x0, y, z1, u0, v1, argb, light, overlay, 0, 1, 0);
		v(b, e, x1, y, z1, u1, v1, argb, light, overlay, 0, 1, 0);
		v(b, e, x1, y, z0, u1, v0, argb, light, overlay, 0, 1, 0);
	}

	/** Una cara vertical mirando a dx/dz (±1), de y0 a y1. */
	static void side(VertexConsumer b, MatrixStack.Entry e, Sprite s, float x0, float z0, float x1, float z1, float y0, float y1, int nx, int nz,
			int argb, int light, int overlay) {
		float u0 = s.getFrameU(nx != 0 ? z0 : x0), u1 = s.getFrameU(nx != 0 ? z1 : x1);
		float va = s.getFrameV(1.0F - y1), vb = s.getFrameV(1.0F - y0);
		v(b, e, x0, y1, z0, u0, va, argb, light, overlay, nx, 0, nz);
		v(b, e, x0, y0, z0, u0, vb, argb, light, overlay, nx, 0, nz);
		v(b, e, x1, y0, z1, u1, vb, argb, light, overlay, nx, 0, nz);
		v(b, e, x1, y1, z1, u1, va, argb, light, overlay, nx, 0, nz);
	}

	static void v(VertexConsumer b, MatrixStack.Entry e, float x, float y, float z, float u, float v, int argb, int light, int overlay, int nx, int ny,
			int nz) {
		b.vertex(e, x, y, z).color(argb).texture(u, v).overlay(overlay).light(light).normal(e, nx, ny, nz);
	}

	static int lerpColor(int a, int b, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	static int shade(int argb, float k) {
		int r = Math.min(255, (int) (((argb >> 16) & 255) * k));
		int g = Math.min(255, (int) (((argb >> 8) & 255) * k));
		int bl = Math.min(255, (int) ((argb & 255) * k));
		return (argb & 0xFF000000) | r << 16 | g << 8 | bl;
	}
}
