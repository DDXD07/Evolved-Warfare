package com.evolvedwarfare.client.train;

import com.evolvedwarfare.train.Track;
import com.evolvedwarfare.train.TrackBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

/**
 * Cómo se ven las vías grandes: un lecho de grava, traviesas de madera oscura cada medio bloque y los dos raíles de hierro,
 * siguiendo cada tramo (también en curva y en cuesta). Los desvíos llevan su palanca, verde por la recta y roja por la curva.
 * Se dibuja el trozo entero desde su bloque principal (y también como vista previa, transparente, al ir a ponerlo).
 */
final class TrackRenderer implements BlockEntityRenderer<TrackBlock.TrackBlockEntity> {
	/** Una caja girada: su centro (coordenadas del trozo en dibujo: x = -u, y = h, z = v), giro, inclinación, medidas y textura. */
	record Elem(float x, float y, float z, float yaw, float pitch, float hx, float hy, float hz, int tex, int color) {
	}

	static final String[] TEX = {"block/gravel", "block/dark_oak_planks", "block/iron_block", "block/lime_concrete", "block/red_concrete",
			"block/stripped_dark_oak_log"};
	private static final Map<String, List<Elem>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	/** Los trozos ya girados y colocados: sus caras (4 puntos, la normal y la textura), para no calcularlo en cada fotograma. */
	private static final Map<String, float[][]> BAKED = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public void render(TrackBlock.TrackBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		BlockState state = be.getCachedState();
		if (!(state.getBlock() instanceof TrackBlock)) {
			return;
		}
		Track.Piece p = Track.piece(be.getPos(), state);
		BlockPos o = be.getPos();
		matrices.push();
		matrices.translate(p.ox() - o.getX(), 0, p.oz() - o.getZ());
		draw(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)), p, light, -1);
		matrices.pop();
	}

	/** Dibuja un trozo con la matriz en su esquina de origen (a la altura de su suelo); color -1 = sus colores. */
	static void draw(MatrixStack matrices, VertexConsumer vc, Track.Piece p, int light, int tint) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.facing().asRotation()));
		MatrixStack.Entry entry = matrices.peek();
		net.minecraft.client.texture.Sprite[] sprites = new net.minecraft.client.texture.Sprite[TEX.length];
		for (int i = 0; i < TEX.length; i++) {
			sprites[i] = Cubes.sprite(TEX[i]);
		}
		for (float[] q : baked(p.shape(), p.left())) {
			int tex = (int) q[16];
			if (tex == 3 && p.thrown()) {
				tex = 4;
			}
			int color = tint != -1 ? tint : (int) q[15] << 16 | (int) q[17];
			net.minecraft.client.texture.Sprite s = sprites[tex];
			for (int v = 0; v < 4; v++) {
				vc.vertex(entry, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).color(color).texture(s.getFrameU(q[18 + v * 2]), s.getFrameV(q[19 + v * 2]))
						.overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(entry, q[12], q[13], q[14]);
			}
		}
		matrices.pop();
	}

	/** Las caras de un trozo, ya giradas (una vez por forma). Cada una: 4 x (x, y, z), la normal, (relleno), textura, color y 4 x (u, v). */
	static List<float[]> baked(Track.Shape shape, boolean left) {
		return java.util.Arrays.asList(BAKED.computeIfAbsent(shape + "/" + left, k -> bake(elems(shape, left))));
	}

	private static float[][] bake(List<Elem> elems) {
		List<float[]> out = new java.util.ArrayList<>();
		for (Elem e : elems) {
			org.joml.Matrix4f m = new org.joml.Matrix4f().translate(e.x(), e.y(), e.z()).rotateY(e.yaw()).rotateX(e.pitch());
			org.joml.Matrix3f n = new org.joml.Matrix3f().rotateY(e.yaw()).rotateX(e.pitch());
			float x0 = -e.hx(), y0 = -e.hy(), z0 = -e.hz(), x1 = e.hx(), y1 = e.hy(), z1 = e.hz();
			float[][][] faces = {
					{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {0, -1, 0}},
					{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
					{{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
					{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {0, 0, 1}},
					{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {-1, 0, 0}},
					{{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}}};
			for (float[][] f : faces) {
				float[] q = new float[26];
				for (int v = 0; v < 4; v++) {
					org.joml.Vector3f p = m.transformPosition(new org.joml.Vector3f(f[v][0], f[v][1], f[v][2]));
					q[v * 3] = p.x;
					q[v * 3 + 1] = p.y;
					q[v * 3 + 2] = p.z;
				}
				org.joml.Vector3f nn = n.transform(new org.joml.Vector3f(f[4][0], f[4][1], f[4][2]));
				q[12] = nn.x;
				q[13] = nn.y;
				q[14] = nn.z;
				q[16] = e.tex();
				q[15] = e.color() >>> 16;
				q[17] = e.color() & 0xFFFF;
				// la textura: cada cara toma el cuadro de la textura según su tamaño (como mucho uno entero)
				float du = Math.min(1.0F, (float) Math.sqrt(sq(q[3] - q[0]) + sq(q[4] - q[1]) + sq(q[5] - q[2])));
				float dv = Math.min(1.0F, (float) Math.sqrt(sq(q[9] - q[0]) + sq(q[10] - q[1]) + sq(q[11] - q[2])));
				float[] uv = {0, 1, du, 1, du, 1 - dv, 0, 1 - dv};
				System.arraycopy(uv, 0, q, 18, 8);
				out.add(q);
			}
		}
		return out.toArray(new float[0][]);
	}

	private static float sq(float v) {
		return v * v;
	}

	/** Para la precarga: deja hechas todas las formas. */
	static void warm() {
		for (Track.Shape shape : Track.Shape.values()) {
			baked(shape, false);
			baked(shape, true);
		}
	}

	static List<Elem> elems(Track.Shape shape, boolean left) {
		return CACHE.computeIfAbsent(shape + "/" + left, k -> build(shape, left));
	}

	private static List<Elem> build(Track.Shape shape, boolean left) {
		List<Elem> out = new ArrayList<>();
		int m = left ? -1 : 1;
		for (int r = 0; r < Track.routes(shape); r++) {
			double len = Track.length(shape, r);
			// lecho y traviesas, cada medio bloque (en un desvío, las del segundo tramo solo donde ya no pisan las del primero)
			int n = Math.max(1, (int) Math.round(len / 0.5));
			double step = len / n;
			for (int i = 0; i < n; i++) {
				double t = (i + 0.5) * step;
				double[] l = Track.local(shape, r, t), d = Track.localDir(shape, r, t);
				if (r == 1) {
					double[] s = Track.local(shape, 0, Math.min(Track.length(shape, 0), l[1]));
					if (Math.abs(s[0] - l[0]) < 1.6 && l[1] < Track.length(shape, 0) + 0.5) {
						continue;
					}
				}
				out.add(elem(m, l, d, 0, 0.03, 0, 1.12, 0.03, step * 0.55, 0, 0xFFFFFFFF));
				out.add(elem(m, l, d, 0, 0.10, 0, 1.05, 0.04, 0.11, 1, 0xFFFFFFFF));
			}
			// los raíles, en tramos cortos
			int rn = Math.max(1, (int) Math.round(len / 0.25));
			double rs = len / rn;
			for (int i = 0; i < rn; i++) {
				double t = (i + 0.5) * rs;
				double[] l = Track.local(shape, r, t), d = Track.localDir(shape, r, t);
				for (int side = -1; side <= 1; side += 2) {
					out.add(elem(m, l, d, side * Track.HALF_GAUGE, 0.164, 0, 0.05, 0.024, rs * 0.5 * 1.25, 2, 0xFF7C8088));
				}
			}
		}
		if (shape == Track.Shape.SWITCH) {
			// la palanca, al lado de la punta común
			double[] l = {-1.45, 0.7, 0}, d = {0, 1, 0};
			out.add(elem(m, l, d, 0, 0.12, 0, 0.18, 0.12, 0.18, 5, 0xFFFFFFFF));
			out.add(elem(m, l, d, 0, 0.45, 0, 0.05, 0.25, 0.05, 3, 0xFFFFFFFF));
		}
		return out;
	}

	/** Una caja en el tramo: en el punto l (con dirección d), desplazada "side" a la derecha y "up" hacia arriba. */
	private static Elem elem(int m, double[] l, double[] d, double side, double up, double fwd, double hx, double hy, double hz, int tex, int color) {
		double h = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
		double du = d[0] / h, dv = d[1] / h;
		// a la derecha del tramo (en u, v): (dv, -du)
		double u = l[0] + side * dv + fwd * du, v = l[1] - side * du + fwd * dv;
		float x = (float) (-m * u), z = (float) v;
		float dirX = (float) (-m * du), dirZ = (float) dv;
		float yaw = (float) Math.atan2(dirX, dirZ);
		float pitch = (float) -Math.atan2(d[2], h);
		return new Elem(x, (float) (l[2] + up), z, yaw, pitch, (float) hx, (float) hy, (float) hz, tex, color);
	}

	@Override
	public boolean rendersOutsideBoundingBox(TrackBlock.TrackBlockEntity be) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 128;
	}
}
