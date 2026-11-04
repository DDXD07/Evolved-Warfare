package com.evolvedwarfare.gametest;

import com.evolvedwarfare.orchard.FruitKind;
import com.evolvedwarfare.orchard.FruitLeavesBlock;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.wood.FallingTreeEntity;
import com.evolvedwarfare.wood.Woodcutting;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La tala (solo con hacha, el árbol que cae entero) y los frutales. */
public class WoodGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void logsNeedAnAxe(TestContext ctx) {
		BlockState log = Blocks.OAK_LOG.getDefaultState();
		boolean hand = Woodcutting.needsAxe(false, log, ItemStack.EMPTY);
		boolean pick = Woodcutting.needsAxe(false, log, new ItemStack(Items.IRON_PICKAXE));
		boolean axe = Woodcutting.needsAxe(false, log, new ItemStack(Items.STONE_AXE));
		boolean flint = Woodcutting.needsAxe(false, log, new ItemStack(ModItems.FLINT_HATCHET));
		boolean creative = Woodcutting.needsAxe(true, log, ItemStack.EMPTY);
		boolean dirt = Woodcutting.needsAxe(false, Blocks.DIRT.getDefaultState(), ItemStack.EMPTY);
		var recipes = ctx.getWorld().getRecipeManager();
		boolean slab = recipes.get(Identifier.of("evolvedwarfare", "oak_slab_from_sticks")).isPresent();
		boolean planks = recipes.get(Identifier.of("evolvedwarfare", "oak_planks_from_slabs")).isPresent();
		boolean hatchet = recipes.get(Identifier.of("evolvedwarfare", "flint_hatchet")).isPresent();
		LOG.info("[EW-GAMETEST] talar: a mano {}, con pico {}, con hacha {}, con hacha de sílex {}, en creativo {}, tierra {}; recetas losa {} tablones {} hacha {}",
				hand, pick, axe, flint, creative, dirt, slab, planks, hatchet);
		check(hand && pick && !axe && !flint && !creative && !dirt, "Lo de necesitar hacha no va bien");
		check(slab && planks && hatchet, "Faltan las recetas de palos, losas o el hacha de sílex");
		ctx.complete();
	}

	/** Un roble: tronco de 5 en (2, 1..5, 2) y copa natural alrededor de arriba. */
	private static void oak(TestContext ctx) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		BlockState leaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, false);
		for (int y = 4; y <= 6; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					ctx.setBlockState(x, y, z, leaves);
				}
			}
		}
		for (int y = 1; y <= 5; y++) {
			ctx.setBlockState(2, y, 2, Blocks.OAK_LOG);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void treesFallWhole(TestContext ctx) {
		oak(ctx);
		ServerWorld world = ctx.getWorld();
		// se tala el de abajo (el jugador lo ha roto)
		ctx.setBlockState(2, 1, 2, Blocks.AIR);
		FallingTreeEntity tree = Woodcutting.fell(world, ctx.getAbsolutePos(new BlockPos(2, 1, 2)), Direction.EAST);
		check(tree != null, "El árbol no cae");
		boolean gone = ctx.getBlockState(new BlockPos(2, 3, 2)).isAir() && ctx.getBlockState(new BlockPos(2, 6, 2)).isAir();
		int logs = tree.logCount();
		int blocks = tree.states().length;
		ctx.waitAndRun(tree.duration() + 10, () -> {
			Box area = new Box(ctx.getAbsolutePos(new BlockPos(-2, -2, -4))).union(new Box(ctx.getAbsolutePos(new BlockPos(12, 8, 8))));
			List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, area, e -> true);
			int wood = items.stream().filter(e -> e.getStack().isOf(Items.OAK_LOG)).mapToInt(e -> e.getStack().getCount()).sum();
			boolean landed = !tree.isAlive();
			double far = items.stream().filter(e -> e.getStack().isOf(Items.OAK_LOG)).mapToDouble(e -> e.getX() - ctx.getAbsolutePos(BlockPos.ORIGIN).getX())
					.max().orElse(0);
			LOG.info("[EW-GAMETEST] árbol talado: {} troncos y {} bloques caen; arriba vacío {}; al caer {} y sueltan {} troncos, el más lejos en x = {}",
					logs, blocks, gone, landed, wood, far);
			items.forEach(ItemEntity::discard);
			check(logs == 4 && gone && landed && wood == 4, "El árbol no cae entero o pierde madera");
			check(far > 4.0, "No ha caído hacia el este");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void houseLogsDoNotFall(TestContext ctx) {
		for (int y = 1; y <= 4; y++) {
			ctx.setBlockState(2, y, 2, Blocks.OAK_LOG);
		}
		ctx.setBlockState(2, 1, 2, Blocks.AIR);
		FallingTreeEntity tree = Woodcutting.fell(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(2, 1, 2)), Direction.EAST);
		LOG.info("[EW-GAMETEST] poste de troncos sin hojas: cae {}", tree != null);
		check(tree == null && ctx.getBlockState(new BlockPos(2, 3, 2)).isOf(Blocks.OAK_LOG), "Un poste sin copa se ha caído");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void fallGeometry(TestContext ctx) {
		BlockPos o = new BlockPos(10, 64, 10);
		// el tronco de 3 de alto (centro a 2.5) queda tumbado hacia el este a 2.5 del borde
		var top = FallingTreeEntity.rotated(o, Direction.EAST, FallingTreeEntity.pack(0, 2, 0), 90.0F);
		var still = FallingTreeEntity.rotated(o, Direction.EAST, FallingTreeEntity.pack(0, 2, 0), 0.0F);
		LOG.info("[EW-GAMETEST] caída: de pie {}, tumbado {}", still, top);
		check(Math.abs(still.y - 66.5) < 1e-6 && Math.abs(still.x - 10.5) < 1e-6, "De pie no está donde estaba");
		check(Math.abs(top.x - 13.5) < 1e-6 && Math.abs(top.y - 64.5) < 1e-6 && Math.abs(top.z - 10.5) < 1e-6, "Tumbado no queda hacia el este");
		check(FallingTreeEntity.angle(0, 20) == 0.0F && FallingTreeEntity.angle(20, 20) == 90.0F, "El ángulo no va de 0 a 90");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pickFruit(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockState ripe = ModBlocks.FRUIT_LEAVES.get(FruitKind.LEMON).getDefaultState().with(LeavesBlock.PERSISTENT, true)
				.with(FruitLeavesBlock.AGE, FruitLeavesBlock.RIPE);
		ctx.setBlockState(rel, ripe);
		BlockPos at = ctx.getAbsolutePos(rel);
		int n = FruitLeavesBlock.harvest(world, at, ctx.getBlockState(rel));
		int age = ctx.getBlockState(rel).get(FruitLeavesBlock.AGE);
		int again = FruitLeavesBlock.harvest(world, at, ctx.getBlockState(rel));
		List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(2), e -> e.getStack().isOf(ModItems.LEMON));
		int lemons = items.stream().mapToInt(e -> e.getStack().getCount()).sum();
		items.forEach(ItemEntity::discard);
		boolean leaves = ModBlocks.FRUIT_LEAVES.get(FruitKind.APPLE).getDefaultState().isIn(BlockTags.LEAVES);
		LOG.info("[EW-GAMETEST] fruta: cogidos {} limones (en el suelo {}), las hojas vuelven a edad {}, sin fruta coge {}; son hojas {}", n, lemons,
				age, again, leaves);
		check(n >= 1 && n == lemons && age == 0 && again == 0, "No se coge bien la fruta");
		check(leaves, "Las hojas de frutal no cuentan como hojas");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void saplingsGrowFruitTrees(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.GRASS_BLOCK);
				for (int y = 1; y <= 9; y++) {
					ctx.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}
		int grown = 0;
		StringBuilder what = new StringBuilder();
		FruitKind[] kinds = {FruitKind.ORANGE, FruitKind.APPLE};
		for (int k = 0; k < kinds.length; k++) {
			FruitKind kind = kinds[k];
			BlockPos rel = new BlockPos(2 + k * 4, 1, 4);
			SaplingBlock sapling = ModBlocks.FRUIT_SAPLINGS.get(kind);
			ctx.setBlockState(rel, sapling.getDefaultState().with(SaplingBlock.STAGE, 1));
			for (int tries = 0; tries < 10 && !ctx.getBlockState(rel).isOf(Blocks.OAK_LOG); tries++) {
				sapling.generate(world, ctx.getAbsolutePos(rel), ctx.getBlockState(rel), world.getRandom());
			}
			int leaves = 0;
			for (BlockPos p : BlockPos.iterate(ctx.getAbsolutePos(rel.add(-3, 0, -3)), ctx.getAbsolutePos(rel.add(3, 8, 3)))) {
				if (world.getBlockState(p).isOf(ModBlocks.FRUIT_LEAVES.get(kind))) {
					leaves++;
				}
			}
			boolean tree = ctx.getBlockState(rel).isOf(Blocks.OAK_LOG) && leaves > 10;
			what.append(kind.id).append(": tronco ").append(ctx.getBlockState(rel).getBlock()).append(", hojas ").append(leaves).append("; ");
			if (tree) {
				grown++;
			}
		}
		LOG.info("[EW-GAMETEST] brotes de frutal: {}", what);
		check(grown == kinds.length, "Un brote no da su árbol: " + what);
		ctx.complete();
	}
}
