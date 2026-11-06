package com.evolvedwarfare.gametest;

import com.evolvedwarfare.hell.AshStagEntity;
import com.evolvedwarfare.hell.HellFlora;
import com.evolvedwarfare.hell.LavaToadEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El Nether vivo (fase 37): las plantas, los árboles de los ahorcados, las lianas del techo, la fauna y el suelo bajo la bóveda. */
public class HellFloraGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void plantsGrowOnHellGround(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
		BlockPos c = ctx.getAbsolutePos(new BlockPos(5, 1, 1));
		world.setBlockState(a, HellFlora.SCORCHED_SOIL.getDefaultState());
		world.setBlockState(b, Blocks.NETHERRACK.getDefaultState());
		world.setBlockState(c, Blocks.MAGMA_BLOCK.getDefaultState());
		boolean soil = HellFlora.HELLBLOOM.getDefaultState().canPlaceAt(world, a.up());
		boolean rack = HellFlora.EMBER_GRASS.getDefaultState().canPlaceAt(world, b.up());
		boolean magma = HellFlora.CORPSE_LILY.getDefaultState().canPlaceAt(world, c.up());
		boolean vineAir = HellFlora.EMBER_VINE.getDefaultState().canPlaceAt(world, a.up(3));
		LOG.info("[EW-GAMETEST] flora: en tierra chamuscada {}, en netherrack {}, en magma {}, liana sin techo {}", soil, rack, magma, vineAir);
		check(soil && rack && !magma && !vineAir, "Las plantas del infierno no crecen donde deben");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void thornsPrick(TestContext ctx) {
		var pig = ctx.spawnEntity(EntityType.PIG, 2, 2, 2);
		BlockPos pos = pig.getBlockPos();
		ctx.getWorld().setBlockState(pos.down(), Blocks.NETHERRACK.getDefaultState());
		ctx.getWorld().setBlockState(pos, HellFlora.THORN_BUSH.getDefaultState());
		float before = pig.getHealth();
		ctx.runAtTick(30, () -> {
			pig.setVelocity(0.3, 0, 0);
			pig.lastRenderX = pig.getX() - 0.5;
		});
		ctx.runAtTick(40, () -> {
			LOG.info("[EW-GAMETEST] zarza: vida {} -> {}", before, pig.getHealth());
			check(pig.getHealth() < before, "La zarza no pincha");
			pig.discard();
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void gallowsAndCeilingVines(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(1, 1, 4));
		world.setBlockState(base, Blocks.NETHERRACK.getDefaultState());
		for (int y = 1; y < 14; y++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					world.setBlockState(base.add(dx + 3, y, dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		boolean grew = HellFlora.growGallows(world, base.up());
		int logs = 0, chains = 0;
		for (BlockPos p : BlockPos.iterate(base.add(-4, 0, -4), base.add(4, 12, 4))) {
			logs += world.getBlockState(p).isOf(HellFlora.CHARRED_LOG) ? 1 : 0;
			chains += world.getBlockState(p).isOf(Blocks.CHAIN) ? 1 : 0;
		}
		// un techo de netherrack y debajo, aire: cuelgan lianas
		BlockPos roof = ctx.getAbsolutePos(new BlockPos(6, 12, 6));
		world.setBlockState(roof, Blocks.NETHERRACK.getDefaultState());
		int vines = HellFlora.hangVines(world, roof.down(8));
		LOG.info("[EW-GAMETEST] árbol de los ahorcados {} ({} troncos, {} cadenas); lianas del techo {}", grew, logs, chains, vines);
		check(grew && logs >= 5 && chains >= 1 && vines >= 3, "No crecen los árboles de los ahorcados o las lianas");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
	public void hellFaunaLives(TestContext ctx) {
		AshStagEntity stag = ctx.spawnEntity(HellFlora.ASH_STAG, 2, 2, 2);
		LavaToadEntity toad = ctx.spawnEntity(HellFlora.LAVA_TOAD, 5, 2, 5);
		var moth = ctx.spawnEntity(HellFlora.EMBER_MOTH, 4, 4, 2);
		boolean breeds = stag.isBreedingItem(new ItemStack(HellFlora.HELLBLOOM)) && toad.isBreedingItem(new ItemStack(HellFlora.BLOODCAP));
		boolean lava = toad.canWalkOnFluid(Fluids.LAVA.getDefaultState());
		ctx.runAtTick(40, () -> {
			LOG.info("[EW-GAMETEST] fauna: ciervo {}, sapo {} (anda en lava {}), polilla {}; se crían {}, ignífugos {}", stag.isAlive(), toad.isAlive(), lava,
					moth.isAlive(), breeds, stag.isFireImmune() && toad.isFireImmune() && moth.isFireImmune());
			check(stag.isAlive() && toad.isAlive() && moth.isAlive() && breeds && lava && stag.isFireImmune(), "La fauna del Nether no vive");
			var child = stag.createChild(ctx.getWorld(), stag);
			check(child instanceof AshStagEntity, "El ciervo no tiene crías");
			stag.discard();
			toad.discard();
			moth.discard();
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void structuresStandOnTheCaveFloor(TestContext ctx) {
		ServerWorld nether = ctx.getWorld().getServer().getWorld(World.NETHER);
		if (nether == null) {
			ctx.complete();
			return;
		}
		var gen = nether.getChunkManager().getChunkGenerator();
		var noise = nether.getChunkManager().getNoiseConfig();
		int under = 0, total = 0, highest = 0;
		for (int i = 0; i < 12; i++) {
			int h = gen.getHeightInGround(i * 173 - 900, i * 97 - 500, Heightmap.Type.WORLD_SURFACE_WG, nether, noise);
			total++;
			highest = Math.max(highest, h);
			under += h < 225 ? 1 : 0;
		}
		LOG.info("[EW-GAMETEST] suelo para las estructuras: {} de {} bajo la bóveda (lo más alto {})", under, total, highest);
		check(under == total, "Las estructuras se pondrían encima del techo");
		ctx.complete();
	}
}
