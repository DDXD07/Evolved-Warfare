package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.ores.MineralPebblesBlock;
import com.evolvedwarfare.ores.ProspectorPickItem;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Las menas realistas: cada yacimiento sale con su forma y su mineral, el pico de prospector lo encuentra, y el orden de features no tiene ciclos. */
public class OreGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final String[] OVERWORLD = {"coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald"};

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void depositsGenerate(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(-600, 0, 0);
		var registry = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE);
		StringBuilder log = new StringBuilder("[EW-GAMETEST] yacimientos:");
		String problem = null;
		int i = 0;
		for (String name : OVERWORLD) {
			// un bloque de piedra (arriba) y pizarra (abajo) de 32x64x32 por yacimiento, lejos
			BlockPos c = base.add(i * 48, 0, 0);
			i++;
			int bottom = world.getBottomY();
			java.util.List<ChunkPos> forced = new java.util.ArrayList<>();
			for (int cx = (c.getX() - 16) >> 4; cx <= (c.getX() + 16) >> 4; cx++) {
				for (int cz = (c.getZ() - 16) >> 4; cz <= (c.getZ() + 16) >> 4; cz++) {
					forced.add(new ChunkPos(cx, cz));
					world.setChunkForced(cx, cz, true);
				}
			}
			BlockPos.Mutable m = new BlockPos.Mutable();
			int top = bottom + 64;
			for (int x = -15; x <= 15; x++) {
				for (int z = -15; z <= 15; z++) {
					for (int y = bottom; y < top; y++) {
						world.setBlockState(m.set(c.getX() + x, y, c.getZ() + z), y < bottom + 20 ? Blocks.DEEPSLATE.getDefaultState()
								: Blocks.STONE.getDefaultState(), 2);
					}
					for (int y = top; y < top + 4; y++) {
						world.setBlockState(m.set(c.getX() + x, y, c.getZ() + z), Blocks.AIR.getDefaultState(), 2);
					}
				}
			}
			var feature = registry.get(RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EvolvedWarfare.id("deposit_" + name)));
			BlockPos origin = new BlockPos(c.getX(), name.equals("diamond") ? bottom + 45 : bottom + 36, c.getZ());
			boolean ok = feature != null && feature.generate(world, world.getChunkManager().getChunkGenerator(), world.getRandom(), origin);
			Map<MineralPebblesBlock.Mineral, Integer> counts = new EnumMap<>(MineralPebblesBlock.Mineral.class);
			int tuff = 0, pebbles = 0;
			for (int x = -15; x <= 15; x++) {
				for (int z = -15; z <= 15; z++) {
					for (int y = bottom; y < top + 2; y++) {
						var s = world.getBlockState(m.set(c.getX() + x, y, c.getZ() + z));
						var k = ProspectorPickItem.mineral(s);
						if (k != null) {
							counts.merge(k, 1, Integer::sum);
						} else if (s.isOf(Blocks.TUFF)) {
							tuff++;
						} else if (s.isOf(com.evolvedwarfare.ores.Ores.PEBBLES)) {
							pebbles++;
						}
					}
				}
			}
			var found = ProspectorPickItem.prospect(world, new BlockPos(c.getX() + 12, bottom + 30, c.getZ()));
			MineralPebblesBlock.Mineral want = MineralPebblesBlock.Mineral.byName(name);
			int n = counts.getOrDefault(want, 0);
			log.append(String.format(" %s=%d (generado %s, toba %d, guijarros %d, pico: %s)", name, n, ok, tuff, pebbles,
					found == null ? "nada" : found.mineral() + "x" + found.count()));
			if (!ok || n == 0 || found == null || found.mineral() != want) {
				problem = name + " no sale bien";
			}
			// limpiar
			for (int x = -15; x <= 15; x++) {
				for (int z = -15; z <= 15; z++) {
					for (int y = bottom; y < top + 4; y++) {
						world.setBlockState(m.set(c.getX() + x, y, c.getZ() + z), Blocks.AIR.getDefaultState(), 2);
					}
				}
			}
			forced.forEach(p -> world.setChunkForced(p.x, p.z, false));
		}
		LOG.info(log.toString());
		if (problem != null) {
			throw new GameTestException("Yacimientos: " + problem);
		}
		ctx.complete();
	}

	/** El orden de las features del mundo normal no tiene ciclos (con los yacimientos y el oro de aluvión). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void overworldFeatureOrderIsConsistent(TestContext ctx) {
		var all = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		var biomes = new java.util.ArrayList<net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome>>();
		all.streamEntries().filter(e -> e.isIn(net.minecraft.registry.tag.BiomeTags.IS_OVERWORLD)).forEach(biomes::add);
		try {
			net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.collectIndexedFeatures(biomes, b -> b.value().getGenerationSettings().getFeatures(), true);
		} catch (IllegalStateException e) {
			throw new GameTestException("Orden de features con un ciclo: " + e.getMessage());
		}
		var rivers = all.streamEntries().filter(e -> e.isIn(net.minecraft.registry.tag.BiomeTags.IS_RIVER)).toList();
		boolean alluvial = !rivers.isEmpty() && rivers.stream().allMatch(r -> r.value().getGenerationSettings().getFeatures().stream()
				.anyMatch(list -> list.stream().anyMatch(f -> f.getKey().map(k -> k.getValue().getPath().equals("alluvial_gold")).orElse(false))));
		LOG.info("[EW-GAMETEST] orden de features del mundo: bien ({} biomas); oro de aluvión en los ríos: {}", biomes.size(), alluvial);
		if (!alluvial) {
			throw new GameTestException("No hay oro de aluvión en los ríos");
		}
		ctx.complete();
	}

	@SuppressWarnings("unused")
	private static World unused() {
		return null;
	}
}
