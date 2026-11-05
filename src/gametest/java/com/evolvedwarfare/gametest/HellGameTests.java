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
		for (String b : new String[] {"ash_plains", "brimstone_fields", "lake_of_fire"}) {
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
}
