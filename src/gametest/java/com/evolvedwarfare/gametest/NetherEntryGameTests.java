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
				Thread.sleep(40_000);
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
		ctx.complete();
	}
}
