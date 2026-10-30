package com.evolvedwarfare.client;

import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Mientras manejas una catapulta, una balista o un trebuchet: el tiro que saldría si disparas ahora, con las mismas
 * cuentas que el servidor. Se dibuja con líneas sobre el mundo (lo llama {@code SiegeEngineRenderer}): el recorrido,
 * el círculo de la zona del impacto (del tamaño de la explosión) y, si no llega, una cruz roja donde apuntas.
 *
 * <p>Con la cámara aérea ({@link SiegeAerialCamera}) se apunta a lo que marca la cámara; si no, a lo que miras.</p>
 */
public final class SiegeAimPreview {
	@Nullable
	private static SiegeEngineEntity engine;
	@Nullable
	private static SiegeBallistics.Aim aim;
	private static List<Vec3d> path = List.of();
	/** Desde dónde se dibuja (la máquina): las cuentas en double y solo lo relativo pasa a float. */
	private static double ox;
	private static double oy;
	private static double oz;

	private SiegeAimPreview() {
	}

	/** El tiro calculado en este tic (null si no manejas nada que dispare). */
	@Nullable
	public static SiegeBallistics.Aim aim() {
		return aim;
	}

	/** Dónde cae el tiro (el final del recorrido). */
	@Nullable
	public static Vec3d impact() {
		return path.isEmpty() ? null : path.get(path.size() - 1);
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null || !(player.getVehicle() instanceof SiegeEngineEntity riding) || !riding.getKind().fires()) {
			engine = null;
			aim = null;
			path = List.of();
			return;
		}
		if (client.isPaused()) {
			return;
		}
		SiegeKind kind = riding.getKind();
		Vec3d target = SiegeAerialCamera.target();
		SiegeBallistics.Aim next = target != null ? SiegeBallistics.aimAt(kind, riding.muzzle(), riding.getYaw(), target)
				: SiegeBallistics.aimFor(kind, world, riding.muzzle(), riding.getYaw(), player);
		engine = riding;
		aim = next;
		path = SiegeBallistics.path(world, next.muzzle(), next.velocity(), kind.shotGravity, player);
	}

	// ------------------------------------------------------------------ dibujo

	/** Las marcas del tiro; las matrices están en la posición (interpolada) de la máquina. */
	public static void render(SiegeEngineEntity rendered, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices) {
		SiegeBallistics.Aim current = aim;
		List<Vec3d> points = path;
		if (rendered != engine || current == null || points.size() < 2) {
			return;
		}
		SiegeKind kind = rendered.getKind();
		Vec3d origin = rendered.getLerpedPos(tickDelta);
		boolean ok = current.reachable();
		float time = (rendered.age + tickDelta) / 20.0F;
		ox = origin.x;
		oy = origin.y;
		oz = origin.z;
		MatrixStack.Entry entry = matrices.peek();
		Vec3d end = points.get(points.size() - 1);
		double radius = kind == SiegeKind.BALLISTA ? 0.6 : Math.max(1.0, kind.blastRadius);

		// la zona del impacto, rellena y transparente
		VertexConsumer fill = vertices.getBuffer(RenderLayer.getDebugQuads());
		int segments = 32;
		float pulse = 0.5F + 0.5F * MathHelper.sin(time * 6.0F);
		int alpha = (int) (45 + 35 * pulse);
		for (int i = 0; i < segments; i++) {
			double a0 = i * MathHelper.TAU / segments;
			double a1 = (i + 1) * MathHelper.TAU / segments;
			double y = end.y + 0.06;
			quadVertex(fill, entry, end.x, y, end.z, ok, alpha);
			quadVertex(fill, entry, end.x + Math.cos(a0) * radius, y, end.z + Math.sin(a0) * radius, ok, alpha);
			quadVertex(fill, entry, end.x + Math.cos(a1) * radius, y, end.z + Math.sin(a1) * radius, ok, alpha);
			quadVertex(fill, entry, end.x, y, end.z, ok, alpha);
		}

		VertexConsumer lines = vertices.getBuffer(RenderLayer.getLines());
		// el recorrido: a trozos que avanzan hacia el blanco
		int pathColor = ok ? 0xFFF2D24A : 0xFFF07A3C;
		int phase = (int) (time * 10.0F) % 4;
		for (int i = 0; i < points.size() - 1; i++) {
			if ((i + 4 - phase) % 4 == 3) {
				continue;
			}
			line(lines, entry, points.get(i), points.get(i + 1), pathColor);
		}
		// los bordes de la zona del impacto, una cruz en el centro y un poste para verlo de lejos
		int ringColor = ok ? 0xFF55F05A : 0xFFF04A3C;
		ring(lines, entry, end, radius, 0.08, ringColor, segments);
		ring(lines, entry, end, radius * 0.5, 0.08, ringColor, segments / 2);
		ring(lines, entry, end, radius * (0.5 + 0.5 * pulse), 0.1, (ringColor & 0x00FFFFFF) | 0x99000000, segments);
		double c = Math.min(0.6, radius * 0.4);
		line(lines, entry, end.add(-c, 0.1, 0.0), end.add(c, 0.1, 0.0), ringColor);
		line(lines, entry, end.add(0.0, 0.1, -c), end.add(0.0, 0.1, c), ringColor);
		line(lines, entry, end.add(0.0, 0.1, 0.0), end.add(0.0, SiegeAerialCamera.isActive() ? 2.5 : 6.0, 0.0), ringColor);
		// si no llega (o se sale del arco de la máquina), donde apuntas: una cruz roja
		Vec3d target = current.target();
		// (mirando al cielo no hay blanco: el punto queda a {@code LOOK_RANGE} en el aire y no se marca)
		boolean onSomething = SiegeAerialCamera.isActive()
				|| target.squaredDistanceTo(current.muzzle()) < (SiegeBallistics.LOOK_RANGE - 10.0) * (SiegeBallistics.LOOK_RANGE - 10.0);
		if (onSomething && (!ok || target.squaredDistanceTo(end) > radius * radius * 4.0)) {
			double s = 0.9;
			int red = 0xFFFF3030;
			Vec3d t = target.add(0.0, 0.1, 0.0);
			line(lines, entry, t.add(-s, 0.0, -s), t.add(s, 0.0, s), red);
			line(lines, entry, t.add(-s, 0.0, s), t.add(s, 0.0, -s), red);
			line(lines, entry, t, t.add(0.0, 3.0, 0.0), red);
		}
	}

	private static void quadVertex(VertexConsumer buffer, MatrixStack.Entry entry, double x, double y, double z, boolean ok, int alpha) {
		buffer.vertex(entry, (float) (x - ox), (float) (y - oy), (float) (z - oz)).color(ok ? 80 : 240, ok ? 240 : 70, ok ? 90 : 60, alpha);
	}

	private static void ring(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d center, double radius, double lift, int color, int segments) {
		for (int i = 0; i < segments; i++) {
			double a0 = i * MathHelper.TAU / segments;
			double a1 = (i + 1) * MathHelper.TAU / segments;
			line(buffer, entry, center.add(Math.cos(a0) * radius, lift, Math.sin(a0) * radius),
					center.add(Math.cos(a1) * radius, lift, Math.sin(a1) * radius), color);
		}
	}

	private static void line(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d a, Vec3d b, int color) {
		Vec3d d = b.subtract(a);
		double length = d.length();
		if (length < 1.0E-6) {
			return;
		}
		float nx = (float) (d.x / length);
		float ny = (float) (d.y / length);
		float nz = (float) (d.z / length);
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int bl = color & 0xFF;
		int al = (color >>> 24) & 0xFF;
		buffer.vertex(entry, (float) (a.x - ox), (float) (a.y - oy), (float) (a.z - oz)).color(r, g, bl, al).normal(entry, nx, ny, nz);
		buffer.vertex(entry, (float) (b.x - ox), (float) (b.y - oy), (float) (b.z - oz)).color(r, g, bl, al).normal(entry, nx, ny, nz);
	}
}
