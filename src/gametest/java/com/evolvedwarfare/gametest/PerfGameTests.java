package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.fluid.FiniteFluids;
import com.evolvedwarfare.wildlife.WildSpecies;
import com.evolvedwarfare.wildlife.Wildlife;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Banco de pruebas de rendimiento (no falla nunca): una zona con fauna, soldados, bandidos, trabajadores y demonios, agua
 * que corre por un hueco y cubos sacando de un estanque, y un muestreador que cuenta en qué se va el tiempo del servidor.
 */
public class PerfGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
	public void perfBench(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos o = ctx.getAbsolutePos(new BlockPos(0, 0, 0)).add(400, 0, 0);
		int y = o.getY() + 1;
		java.util.List<ChunkPos> forced = new java.util.ArrayList<>();
		for (int cx = -2; cx <= 4; cx++) {
			for (int cz = -2; cz <= 4; cz++) {
				ChunkPos c = new ChunkPos((o.getX() >> 4) + cx, (o.getZ() >> 4) + cz);
				forced.add(c);
				world.setChunkForced(c.x, c.z, true);
			}
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -4; x < 52; x++) {
			for (int z = -4; z < 52; z++) {
				world.setBlockState(m.set(o.getX() + x, y - 1, o.getZ() + z), Blocks.STONE.getDefaultState(), 2);
				for (int k = 0; k < 6; k++) {
					world.setBlockState(m.set(o.getX() + x, y + k, o.getZ() + z), Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
		// un estanque de 10x10x3 con un muro, y al lado un foso de 10x10x2 vacío
		for (int x = 30; x < 52; x++) {
			for (int z = 30; z < 42; z++) {
				for (int k = -3; k < 0; k++) {
					boolean wall = x == 30 || x == 51 || z == 30 || z == 41 || x == 41;
					world.setBlockState(m.set(o.getX() + x, y + k, o.getZ() + z), wall ? Blocks.STONE.getDefaultState()
							: x < 41 ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
				}
				world.setBlockState(m.set(o.getX() + x, y - 4, o.getZ() + z), Blocks.STONE.getDefaultState(), 2);
			}
		}
		int spawned = 0;
		for (WildSpecies s : WildSpecies.values()) {
			for (int i = 0; i < 2; i++) {
				spawned += spawn(world, Wildlife.type(s), o.add(4 + (spawned % 20) * 2, 1, 4 + (spawned / 20) * 3)) ? 1 : 0;
			}
		}
		for (String id : new String[] {"soldier", "soldier", "soldier", "soldier", "bandit", "bandit", "bandit", "bandit", "worker", "worker", "worker",
				"recruit", "recruit", "demon", "imp", "imp", "lost_soul", "trader"}) {
			EntityType<?> type = Registries.ENTITY_TYPE.get(EvolvedWarfare.id(id));
			spawned += spawn(world, type, o.add(6 + (spawned % 20) * 2, 1, 16 + (spawned / 20) * 3)) ? 1 : 0;
		}
		for (EntityType<?> t : new EntityType<?>[] {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.ZOMBIE,
				EntityType.SKELETON, EntityType.VILLAGER}) {
			for (int i = 0; i < 3; i++) {
				spawned += spawn(world, t, o.add(4 + (spawned % 20) * 2, 1, 26 + (spawned / 20) * 2)) ? 1 : 0;
			}
		}
		var player = ctx.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(o.getX() + 24, y, o.getZ() + 24, 0, 0);
		LOG.info("[EW-GAMETEST] RENDIMIENTO: {} criaturas", spawned);
		Map<String, Integer> self = new ConcurrentHashMap<>(), ours = new ConcurrentHashMap<>();
		AtomicBoolean on = new AtomicBoolean(false);
		Thread server = Thread.currentThread();
		int[] samples = {0};
		Thread sampler = new Thread(() -> {
			while (true) {
				if (on.get() && server.getState() == Thread.State.RUNNABLE) {
					StackTraceElement[] st = server.getStackTrace();
					if (st.length > 0) {
						samples[0]++;
						self.merge(name(st[0]), 1, Integer::sum);
						java.util.Set<String> seen = new java.util.HashSet<>();
						for (StackTraceElement el : st) {
							if (el.getClassName().startsWith("com.evolvedwarfare") && !el.getClassName().contains("gametest")) {
								String n = name(el);
								if (seen.add(n)) {
									ours.merge(n, 1, Integer::sum);
								}
							}
						}
					}
				}
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "ew-perf-sampler");
		sampler.setDaemon(true);
		long[] t = new long[3];
		ctx.runAtTick(40, () -> {
			// se abre el muro: el agua corre al foso
			for (int z = 31; z < 41; z++) {
				for (int k = -3; k < 0; k++) {
					world.setBlockState(new BlockPos(o.getX() + 41, y + k, o.getZ() + z), Blocks.AIR.getDefaultState(), 3);
				}
			}
			t[0] = System.nanoTime();
			on.set(true);
			sampler.start();
		});
		for (int tick = 50; tick < 640; tick += 20) {
			ctx.runAtTick(tick, () -> {
				long s = System.nanoTime();
				boolean ok = FiniteFluids.drain(world, new BlockPos(o.getX() + 35, y - 2, o.getZ() + 35), Fluids.WATER, FiniteFluids.BUCKET);
				t[2] += System.nanoTime() - s;
			});
		}
		ctx.runAtTick(640, () -> {
			on.set(false);
			sampler.interrupt();
			t[1] = System.nanoTime();
			double ms = (t[1] - t[0]) / 1e6 / 600.0;
			StringBuilder sb = new StringBuilder(String.format("[EW-GAMETEST] RENDIMIENTO: %.2f ms por tic (600 tics), %d muestras; sacar 30 cubos %.1f ms%n",
					ms, samples[0], t[2] / 1e6));
			sb.append(" lo más caliente (marco de arriba):\n");
			self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
					.forEach(e -> sb.append(String.format("  %5d  %s%n", e.getValue(), e.getKey())));
			sb.append(" del mod (incluido lo que llama):\n");
			ours.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(45)
					.forEach(e -> sb.append(String.format("  %5d  %s%n", e.getValue(), e.getKey())));
			LOG.info(sb.toString());
			for (Entity e : world.getOtherEntities(null, new net.minecraft.util.math.Box(o).expand(80))) {
				if (!(e instanceof net.minecraft.entity.player.PlayerEntity)) {
					e.discard();
				}
			}
			forced.forEach(c -> world.setChunkForced(c.x, c.z, false));
			ctx.complete();
		});
	}

	private static String name(StackTraceElement el) {
		String c = el.getClassName();
		return c.substring(c.lastIndexOf('.') + 1) + "." + el.getMethodName();
	}

	private static boolean spawn(ServerWorld world, EntityType<?> type, BlockPos pos) {
		if (type == null) {
			return false;
		}
		Entity e = type.spawn(world, pos, SpawnReason.COMMAND);
		return e != null;
	}

	@SuppressWarnings("unused")
	private static Map<String, Integer> unused() {
		return new HashMap<>();
	}
}
