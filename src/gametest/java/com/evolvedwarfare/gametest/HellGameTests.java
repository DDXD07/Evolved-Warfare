package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.hell.DemonEntity;
import com.evolvedwarfare.hell.Hell;
import com.evolvedwarfare.hell.ImpEntity;
import com.evolvedwarfare.hell.LostSoulEntity;
import com.evolvedwarfare.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El Nether bíblico: sin techo, con montañas y lagos de fuego; sus biomas, sus criaturas y la lluvia de fuego. */
public class HellGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void netherHasNoCeiling(TestContext ctx) {
		var server = ctx.getWorld().getServer();
		DimensionType type = server.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE).get(DimensionTypes.THE_NETHER);
		var biomes = server.getRegistryManager().get(RegistryKeys.BIOME);
		boolean all = true;
		for (String b : new String[] {"lake_of_fire", "valley_of_bones", "sodom_ruins", "abyss", "sulfur_mountains"}) {
			all &= biomes.contains(RegistryKey.of(RegistryKeys.BIOME, EvolvedWarfare.id(b)));
		}
		LOG.info("[EW-GAMETEST] Nether: techo {}, altura lógica {}, biomas nuevos {}", type.hasCeiling(), type.logicalHeight(), all);
		check(!type.hasCeiling() && type.logicalHeight() == 256 && all, "El Nether sigue con techo o faltan biomas");
		ServerWorld nether = server.getWorld(World.NETHER);
		if (nether == null) {
			LOG.info("[EW-GAMETEST] Nether: el servidor de pruebas no tiene Nether (no se mira el terreno)");
			ctx.complete();
			return;
		}
		ChunkGenerator gen = nether.getChunkManager().getChunkGenerator();
		NoiseConfig noise = nether.getChunkManager().getNoiseConfig();
		int columns = 0;
		int roofs = 0;
		int highest = 0;
		int lowest = 999;
		int lava = 0;
		for (int i = 0; i < 14; i++) {
			for (int j = 0; j < 14; j++) {
				int x = i * 96 - 600;
				int z = j * 96 - 600;
				VerticalBlockSample column = gen.getColumnSample(x, z, nether, noise);
				int surface = -1;
				for (int y = 250; y > 0; y--) {
					BlockState s = column.getState(y);
					if (!s.isAir() && s.getFluidState().isEmpty()) {
						surface = y;
						break;
					}
				}
				columns++;
				if (!column.getState(252).isAir()) {
					roofs++;
				}
				if (column.getState(31).isOf(Blocks.LAVA) && column.getState(40).isAir()) {
					lava++;
				}
				highest = Math.max(highest, surface);
				lowest = Math.min(lowest, surface);
			}
		}
		// un mapa del terreno en el registro: ~ lava, . bajo, - llano, + colinas, ^ alto, A montaña
		StringBuilder map = new StringBuilder("\n");
		int lakes = 0;
		int cells = 0;
		for (int j = 0; j < 24; j++) {
			for (int i = 0; i < 64; i++) {
				VerticalBlockSample column = gen.getColumnSample(i * 40 - 1280, j * 80 - 960, nether, noise);
				int surface = 0;
				for (int y = 250; y > 0; y--) {
					if (!column.getState(y).isAir()) {
						surface = y;
						break;
					}
				}
				boolean molten = column.getState(surface).isOf(Blocks.LAVA);
				cells++;
				if (molten) {
					lakes++;
				}
				map.append(molten ? '~' : surface < 45 ? '.' : surface < 60 ? '-' : surface < 80 ? '+' : surface < 110 ? '^' : 'A');
			}
			map.append('\n');
		}
		LOG.info("[EW-GAMETEST] mapa del Nether (lava en {} de {}):{}", lakes, cells, map);
		// los biomas: cuántas veces sale cada uno (y dónde cae el Abismo, que debe ser hondo)
		java.util.Map<String, Integer> found = new java.util.TreeMap<>();
		StringBuilder bmap = new StringBuilder("\n");
		int abyssDeep = 0;
		StringBuilder deepLog = new StringBuilder();
		int abyssCells = 0;
		var sampler = noise.getMultiNoiseSampler();
		// el mundo de pruebas usa el Nether de serie: los biomas se miran con la fuente de biomas del datapack
		var biomeSource = gen.getBiomeSource();
		try (var reader = server.getResourceManager().getResource(net.minecraft.util.Identifier.ofVanilla("dimension/the_nether.json")).orElseThrow()
				.getReader()) {
			var json = com.google.gson.JsonParser.parseReader(reader);
			var ops = net.minecraft.registry.RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, server.getRegistryManager());
			biomeSource = net.minecraft.world.dimension.DimensionOptions.CODEC.parse(ops, json).getOrThrow().chunkGenerator().getBiomeSource();
		} catch (Exception e) {
			LOG.info("[EW-GAMETEST] no se pudo leer la dimensión del datapack: {}", e.toString());
		}
		for (int j = 0; j < 24; j++) {
			for (int i = 0; i < 64; i++) {
				int x = i * 40 - 1280;
				int z = j * 80 - 960;
				var entry = biomeSource.getBiome(x >> 2, 80 >> 2, z >> 2, sampler);
				String id = entry.getKey().map(k -> k.getValue().getPath()).orElse("?");
				found.merge(id, 1, Integer::sum);
				char c = switch (id) {
					case "lake_of_fire" -> 'L';
					case "valley_of_bones" -> 'V';
					case "sodom_ruins" -> 'S';
					case "abyss" -> 'A';
					case "sulfur_mountains" -> 'M';
					default -> '.';
				};
				bmap.append(c);
				if (c == 'A') {
					abyssCells++;
					VerticalBlockSample column = gen.getColumnSample(x, z, nether, noise);
					int surface = 0;
					for (int y = 250; y > 0; y--) {
						if (!column.getState(y).isAir()) {
							surface = y;
							break;
						}
					}
					if (surface < 40) {
						abyssDeep++;
					}
					if (abyssCells < 40) {
						deepLog.append(surface).append(column.getState(surface).getBlock().getTranslationKey().replace("block.minecraft.", ":")).append(' ');
					}
				}
			}
			bmap.append('\n');
		}
		LOG.info("[EW-GAMETEST] biomas del Nether {} (Abismo hondo en {} de {}; suelos {}):{}", found, abyssDeep, abyssCells, deepLog, bmap);
		for (String b : new String[] {"lake_of_fire", "valley_of_bones", "sodom_ruins", "abyss", "sulfur_mountains"}) {
			check(found.getOrDefault(b, 0) > 10, "Casi no sale el bioma " + b);
		}
		check(abyssCells == 0 || abyssDeep * 2 > abyssCells, "El Abismo no es hondo");
		LOG.info("[EW-GAMETEST] terreno del Nether: {} columnas, con techo {}, suelo de {} a {}, lagos de fuego al aire {}", columns, roofs, lowest,
				highest, lava);
		check(roofs == 0, "Hay techo en el Nether");
		check(highest > 110, "No hay montañas: lo más alto es " + highest);
		check(lava > 0, "No hay lagos de fuego al aire libre");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void creaturesOfHell(TestContext ctx) {
		DemonEntity demon = ctx.spawnEntity(ModEntities.DEMON, 1, 2, 1);
		ImpEntity imp = ctx.spawnEntity(ModEntities.IMP, 3, 2, 1);
		LostSoulEntity soul = ctx.spawnEntity(ModEntities.LOST_SOUL, 5, 3, 1);
		ArmorStandEntity dummy = ctx.spawnEntity(EntityType.ARMOR_STAND, 3, 2, 10);
		SmallFireballEntity ball = imp.shoot(dummy);
		boolean fire = demon.isFireImmune() && imp.isFireImmune() && soul.isFireImmune();
		LOG.info("[EW-GAMETEST] criaturas del Nether: demonio {} de vida, diablillo {}, alma {} (sin gravedad {}), inmunes al fuego {}, bola de fuego {}",
				demon.getMaxHealth(), imp.getMaxHealth(), soul.getMaxHealth(), soul.hasNoGravity(), fire, ball != null && ball.isAlive());
		check(demon.getMaxHealth() == 60.0F && imp.getMaxHealth() == 16.0F && soul.hasNoGravity() && fire, "Las criaturas del Nether no van bien");
		check(ball != null, "El diablillo no tira bolas de fuego");
		ball.discard();
		demon.discard();
		imp.discard();
		soul.discard();
		dummy.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void fireRainFalls(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int fell = 0;
		for (int i = 0; i < 30; i++) {
			SmallFireballEntity f = Hell.drop(world, ctx.getAbsolutePos(new BlockPos(4, 1, 4)), world.getRandom());
			if (f != null) {
				fell++;
				check(f.getVelocity().y < 0, "La bola de fuego no cae");
				f.discard();
			}
		}
		LOG.info("[EW-GAMETEST] lluvia de fuego: {} de 30 bolas caen del cielo", fell);
		check(fell > 10, "Casi no cae fuego: " + fell);
		ctx.complete();
	}

	/** El Valle de los Huesos: los esqueletos enterrados se levantan del suelo. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void bonesRise(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				world.setBlockState(base.add(x + 4, 0, z + 4), com.evolvedwarfare.registry.ModBlocks.BONE_DUST.getDefaultState());
				world.setBlockState(base.add(x + 4, 1, z + 4), Blocks.AIR.getDefaultState());
				world.setBlockState(base.add(x + 4, 2, z + 4), Blocks.AIR.getDefaultState());
			}
		}
		int raised = com.evolvedwarfare.hell.NetherClimate.raiseSkeletons(world, base.add(4, 1, 4), 3, world.getRandom());
		LOG.info("[EW-GAMETEST] valle de los huesos: se levantan {} esqueletos", raised);
		check(raised == 3, "No se levantan los esqueletos: " + raised);
		world.getEntitiesByType(EntityType.SKELETON, new net.minecraft.util.math.Box(base).expand(16), e -> true).forEach(e -> e.discard());
		ctx.complete();
	}

	/** El Abismo: las antorchas se apagan solas; la corrupción vuelve maldita la piedra. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void lightsDieAndStoneCorrupts(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos c = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				world.setBlockState(c.add(x, -1, z), Blocks.NETHERRACK.getDefaultState());
				world.setBlockState(c.add(x, 0, z), Blocks.TORCH.getDefaultState());
			}
		}
		int out = 0;
		for (int i = 0; i < 300; i++) {
			out += com.evolvedwarfare.hell.NetherClimate.extinguish(world, c, 60, 1.0F, 1.0F);
		}
		int left = 0;
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (world.getBlockState(c.add(x, 0, z)).isOf(Blocks.TORCH)) {
					left++;
				}
				world.setBlockState(c.add(x, 0, z), Blocks.AIR.getDefaultState());
			}
		}
		boolean cursed = false;
		for (int i = 0; i < 30 && !cursed; i++) {
			cursed = com.evolvedwarfare.hell.NetherClimate.corruptNear(world, c, world.getRandom());
		}
		LOG.info("[EW-GAMETEST] abismo: se apagan {} antorchas (quedan {}), la piedra se maldice {}", out, left, cursed);
		check(out > 10 && left < 10, "Las antorchas no se apagan");
		check(cursed, "La corrupción no maldice la piedra");
		ctx.complete();
	}

	/** Las maldiciones: duran sin fin y se quitan de golpe (al salir del Nether). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cursesLift(TestContext ctx) {
		ArmorStandEntity dummy = ctx.spawnEntity(EntityType.ARMOR_STAND, 2, 2, 2);
		boolean all = true;
		for (var c : com.evolvedwarfare.hell.Curses.all()) {
			all &= dummy.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(c, net.minecraft.entity.effect.StatusEffectInstance.INFINITE));
		}
		int had = dummy.getStatusEffects().size();
		for (var c : com.evolvedwarfare.hell.Curses.all()) {
			dummy.removeStatusEffect(c);
		}
		LOG.info("[EW-GAMETEST] maldiciones: puestas {} ({}), quedan al quitarlas {}", all, had, dummy.getStatusEffects().size());
		all &= had == 4 && dummy.getStatusEffects().isEmpty();
		check(all, "No se ponen las maldiciones");
		dummy.discard();
		ctx.complete();
	}
}
