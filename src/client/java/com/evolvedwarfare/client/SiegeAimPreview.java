package com.evolvedwarfare.client;

import com.evolvedwarfare.siege.SiegeBallistics;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Mientras manejas una catapulta, una balista o un trebuchet, marca con puntos el recorrido del tiro y con un círculo
 * dónde caerá (verde si llega a lo que miras; rojo si se queda corto).
 */
public final class SiegeAimPreview {
	private static final DustParticleEffect PATH_OK = new DustParticleEffect(new Vector3f(0.95F, 0.85F, 0.3F), 0.7F);
	private static final DustParticleEffect PATH_SHORT = new DustParticleEffect(new Vector3f(0.95F, 0.45F, 0.3F), 0.7F);
	private static final DustParticleEffect RING_OK = new DustParticleEffect(new Vector3f(0.3F, 0.95F, 0.35F), 2.0F);
	private static final DustParticleEffect RING_SHORT = new DustParticleEffect(new Vector3f(0.95F, 0.2F, 0.2F), 2.0F);

	private SiegeAimPreview() {
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null || client.isPaused() || player.age % 2 != 0) {
			return;
		}
		if (!(player.getVehicle() instanceof SiegeEngineEntity engine) || !engine.getKind().fires()) {
			return;
		}
		SiegeKind kind = engine.getKind();
		SiegeBallistics.Aim aim = SiegeBallistics.aimFor(kind, world, engine.muzzle(), engine.getYaw(), player);
		List<Vec3d> path = SiegeBallistics.path(world, aim.muzzle(), aim.velocity(), kind.shotGravity, player);
		boolean ok = aim.reachable();
		// el recorrido, a trozos que avanzan (parece que fluye)
		int phase = (player.age / 2) % 3;
		for (int i = 3 + phase; i < path.size() - 1; i += 3) {
			Vec3d at = path.get(i);
			world.addImportantParticle(ok ? PATH_OK : PATH_SHORT, true, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		}
		Vec3d end = path.get(path.size() - 1);
		double radius = kind == SiegeKind.BALLISTA ? 0.6 : Math.max(1.0, kind.blastRadius);
		int points = kind == SiegeKind.BALLISTA ? 6 : 14;
		for (int i = 0; i < points; i++) {
			double angle = (i + (player.age % 20) / 20.0) * MathHelper.TAU / points;
			world.addImportantParticle(ok ? RING_OK : RING_SHORT, true, end.x + Math.cos(angle) * radius, end.y + 0.15,
					end.z + Math.sin(angle) * radius, 0.0, 0.0, 0.0);
		}
	}
}
