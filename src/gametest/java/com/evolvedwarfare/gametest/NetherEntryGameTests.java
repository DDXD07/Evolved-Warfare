package com.evolvedwarfare.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entrar al Nether: que sus chunks se generen sin colgarse y que se pueda viajar allí (jugador y criaturas). */
public class NetherEntryGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	/** Si algo tarda demasiado, vuelca qué hacen los hilos (para ver dónde se cuelga). */
	private static Thread watchdog(String what) {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(90_000);
			} catch (InterruptedException e) {
				return;
			}
			StringBuilder sb = new StringBuilder("[EW-GAMETEST] COLGADO en " + what + ":\n");
			for (var e : Thread.getAllStackTraces().entrySet()) {
				String n = e.getKey().getName();
				if (!n.contains("Server thread") && !n.contains("Worker") && !n.contains("IO")) {
					continue;
				}
				sb.append("--- ").append(n).append('\n');
				for (StackTraceElement el : e.getValue()) {
					sb.append("    at ").append(el).append('\n');
				}
			}
			LOG.error(sb.toString());
		}, "ew-watchdog");
		t.setDaemon(true);
		t.start();
		return t;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void netherCanBeEntered(TestContext ctx) {
		ServerWorld nether = ctx.getWorld().getServer().getWorld(World.NETHER);
		check(nether != null, "No hay Nether");
		Thread dog = watchdog("generar el Nether");
		long t0 = System.currentTimeMillis();
		int[][] spots = {{0, 0}, {1, 0}, {0, 1}, {-1, -1}, {40, 12}, {-75, 300}, {500, -500}};
		for (int[] c : spots) {
			long t = System.currentTimeMillis();
			try {
				nether.getChunk(c[0], c[1], ChunkStatus.FULL, true);
			} catch (Throwable e) {
				LOG.error("[EW-GAMETEST] el chunk del Nether {} {} falla", c[0], c[1], e);
				throw new GameTestException("El Nether no se genera: " + e);
			}
			LOG.info("[EW-GAMETEST] chunk del Nether {} {} en {} ms", c[0], c[1], System.currentTimeMillis() - t);
		}
		// una zona grande, como la que carga un jugador al llegar (y mientras, dónde se va el tiempo)
		java.util.Map<String, Integer> hot = new java.util.concurrent.ConcurrentHashMap<>(), ours = new java.util.concurrent.ConcurrentHashMap<>();
		java.util.concurrent.atomic.AtomicBoolean sampling = new java.util.concurrent.atomic.AtomicBoolean(true);
		Thread sampler = new Thread(() -> {
			while (sampling.get()) {
				for (var e : Thread.getAllStackTraces().entrySet()) {
					String n = e.getKey().getName();
					if (!n.contains("Worker") && !n.contains("Server thread")) {
						continue;
					}
					StackTraceElement[] st = e.getValue();
					if (st.length == 0 || e.getKey().getState() != Thread.State.RUNNABLE) {
						continue;
					}
					StringBuilder k = new StringBuilder();
					for (int i = 0; i < Math.min(3, st.length); i++) {
						k.append(st[i].getClassName().replaceAll(".*\\.", "")).append('.').append(st[i].getMethodName()).append(" < ");
					}
					hot.merge(k.toString(), 1, Integer::sum);
					for (StackTraceElement el : st) {
						if (el.getClassName().contains("evolvedwarfare") || el.getClassName().contains("Feature") || el.getClassName().contains("Structure")
								|| el.getClassName().contains("DensityFunction") && false) {
							ours.merge(el.getClassName().replaceAll(".*\\.", "") + "." + el.getMethodName(), 1, Integer::sum);
							break;
						}
					}
				}
				try {
					Thread.sleep(5);
				} catch (InterruptedException ex) {
					return;
				}
			}
		}, "ew-sampler");
		sampler.setDaemon(true);
		sampler.start();
		for (int cx = 60; cx < 76; cx++) {
			for (int cz = -8; cz < 8; cz++) {
				long t = System.currentTimeMillis();
				nether.getChunk(cx, cz, ChunkStatus.FULL, true);
				long d = System.currentTimeMillis() - t;
				if (d > 1500) {
					LOG.info("[EW-GAMETEST] chunk lento del Nether {} {}: {} ms", cx, cz, d);
				}
			}
		}
		sampling.set(false);
		StringBuilder prof = new StringBuilder("[EW-GAMETEST] PERFIL del Nether (lo más caliente):\n");
		hot.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(30)
				.forEach(e -> prof.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n'));
		prof.append(" por quién (primer marco de features, estructuras o del mod):\n");
		ours.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(30)
				.forEach(e -> prof.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n'));
		LOG.info(prof.toString());
		dog.interrupt();
		LOG.info("[EW-GAMETEST] Nether generado en {} ms", System.currentTimeMillis() - t0);
		// viajar: una criatura y un jugador
		var pig = EntityType.PIG.create(ctx.getWorld());
		BlockPos here = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		pig.refreshPositionAndAngles(here, 0, 0);
		ctx.getWorld().spawnEntity(pig);
		Vec3d dest = new Vec3d(8.5, 100, 8.5);
		Thread dog2 = watchdog("viajar al Nether");
		var moved = pig.teleportTo(new TeleportTarget(nether, dest, Vec3d.ZERO, 0, 0, TeleportTarget.NO_OP));
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		Throwable playerError = null;
		try {
			player.teleportTo(new TeleportTarget(nether, dest, Vec3d.ZERO, 0, 0, TeleportTarget.NO_OP));
		} catch (Throwable e) {
			playerError = e;
			LOG.error("[EW-GAMETEST] el jugador no puede viajar al Nether", e);
		}
		dog2.interrupt();
		LOG.info("[EW-GAMETEST] viaje al Nether: cerdo {} ({}), jugador en {} ({}), error {}", moved != null ? moved.getWorld().getRegistryKey().getValue() : null,
				moved != null ? moved.getPos() : null, player.getWorld().getRegistryKey().getValue(), player.getPos(), playerError);
		boolean ok = moved != null && moved.getWorld() == nether && player.getWorld() == nether && playerError == null;
		if (moved != null) {
			moved.discard();
		}
		check(ok, "No se puede viajar al Nether");
		// por un portal de verdad (lejos de lo ya generado, para que tenga que crear el portal del otro lado)
		ServerWorld world = ctx.getWorld();
		BlockPos far = new BlockPos(here.getX() + 4000, 70, here.getZ() + 4000);
		world.getChunk(far);
		world.setBlockState(far, net.minecraft.block.Blocks.NETHER_PORTAL.getDefaultState(), 18);
		var pig2 = EntityType.PIG.create(world);
		pig2.refreshPositionAndAngles(far, 0, 0);
		Thread dog3 = watchdog("buscar o crear el portal del Nether");
		long tp = System.currentTimeMillis();
		TeleportTarget target = null;
		Throwable portalError = null;
		try {
			target = ((net.minecraft.block.NetherPortalBlock) net.minecraft.block.Blocks.NETHER_PORTAL).createTeleportTarget(world, pig2, far);
		} catch (Throwable e) {
			portalError = e;
			LOG.error("[EW-GAMETEST] el portal del Nether falla", e);
		}
		dog3.interrupt();
		LOG.info("[EW-GAMETEST] portal al Nether: destino {} en {} ({} ms), error {}", target != null ? target.pos() : null,
				target != null ? target.world().getRegistryKey().getValue() : null, System.currentTimeMillis() - tp, portalError);
		world.setBlockState(far, net.minecraft.block.Blocks.AIR.getDefaultState(), 18);
		check(target != null && target.world() == nether && portalError == null, "El portal no lleva al Nether");
		ctx.complete();
	}
}
