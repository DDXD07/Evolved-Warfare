package com.evolvedwarfare.gametest;

import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.world.tower.TowerChecks;
import com.evolvedwarfare.world.tower.TowerDesigner;
import com.evolvedwarfare.world.tower.TowerPlan;
import com.evolvedwarfare.world.tower.WatchtowerPiece;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Las torres de vigilancia por semilla: cientos de semillas de cada estilo (se puede subir hasta arriba, todos los
 * bloques existen, los cofres son cofres, hay guardia y salen distintas) y una colocada de verdad en el mundo.
 */
public class TowerGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");
	private static final int SEEDS = 100;

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void towerDesigns(TestContext ctx) {
		int total = 0;
		StringBuilder errors = new StringBuilder();
		for (TowerDesigner.Style style : TowerDesigner.Style.values()) {
			Set<String> kinds = new HashSet<>();
			for (int snow = 0; snow <= 1; snow++) {
				for (long seed = 0; seed < SEEDS; seed++) {
					long s = seed * 0x9E3779B97F4A7C15L + style.ordinal();
					TowerPlan plan = TowerDesigner.design(s, style, snow == 1);
					total++;
					kinds.add(plan.description);
					String problem = check(plan);
					if (problem != null && errors.length() < 1500) {
						errors.append(style).append(" semilla ").append(seed).append(snow == 1 ? " (nieve)" : "").append(": ").append(problem)
								.append("; ");
					}
				}
			}
			LOG.info("[EW-GAMETEST] torres {}: {} distintas de {}", style, kinds.size(), 2 * SEEDS);
			if (kinds.size() < 40) {
				errors.append(style).append(": solo ").append(kinds.size()).append(" torres distintas; ");
			}
		}
		LOG.info("[EW-GAMETEST] torres comprobadas: {}", total);
		if (!errors.isEmpty()) {
			throw new GameTestException("Torres mal generadas: " + errors);
		}
		ctx.complete();
	}

	private static String check(TowerPlan plan) {
		int deck = TowerChecks.deck(plan);
		if (deck < 0) {
			return "sin trampilla arriba";
		}
		if (!TowerChecks.reachesTop(plan, deck)) {
			return "no se puede subir hasta arriba";
		}
		if (plan.guards().isEmpty()) {
			return "sin guardia";
		}
		for (TowerPlan.Block block : plan.blocks()) {
			try {
				BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), block.state(), false);
			} catch (CommandSyntaxException e) {
				return "bloque que no existe: " + block.state();
			}
		}
		for (TowerPlan.Loot loot : plan.lootSpots()) {
			String at = plan.get(loot.x(), loot.y(), loot.z());
			if (at == null || !(at.contains("chest") || at.contains("barrel"))) {
				return "botín sin cofre en " + loot.x() + "," + loot.y() + "," + loot.z() + " (" + at + ")";
			}
		}
		int[] b = plan.bounds();
		if (b[3] - b[0] > 30 || b[5] - b[2] > 30 || b[4] > 48) {
			return "demasiado grande (" + (b[3] - b[0] + 1) + "x" + (b[5] - b[2] + 1) + ", alto " + b[4] + ")";
		}
		return null;
	}

	/** Una torre puesta de verdad (girada), lejos de las demás pruebas: trampilla arriba, botín y reclutas. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "watchtower", tickLimit = 200)
	public void towerPlacesInWorld(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getAbsolutePos(new BlockPos(48, 0, 4));
		long seed = 0x5EEDL;
		WatchtowerPiece piece = new WatchtowerPiece(origin, seed, TowerDesigner.Style.TEMPERATE, false, BlockRotation.CLOCKWISE_90);
		piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), Random.create(seed),
				BlockBox.infinite(), new ChunkPos(origin), origin);
		TowerPlan plan = piece.plan();
		int deck = TowerChecks.deck(plan);
		BlockPos trapdoor = null;
		for (TowerPlan.Block block : plan.blocks()) {
			if (block.y() == deck && block.state().contains("trapdoor")) {
				trapdoor = WatchtowerPiece.world(origin, piece.rotation(), block.x(), block.y(), block.z());
			}
		}
		if (trapdoor == null) {
			throw new GameTestException("La torre no tiene trampilla");
		}
		BlockState top = world.getBlockState(trapdoor);
		int looted = 0;
		for (TowerPlan.Loot loot : plan.lootSpots()) {
			BlockPos pos = WatchtowerPiece.world(origin, piece.rotation(), loot.x(), loot.y(), loot.z());
			if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container && container.getLootTable() != null) {
				looted++;
			}
		}
		int[] b = plan.bounds();
		Box around = new Box(origin).expand(Math.max(b[3] - b[0], b[5] - b[2]) + 2, b[4] + 4, Math.max(b[3] - b[0], b[5] - b[2]) + 2);
		List<RecruitEntity> guards = world.getEntitiesByClass(RecruitEntity.class, around, e -> true);
		LOG.info("[EW-GAMETEST] torre en el mundo: {}; arriba {}, cofres con botín {}/{}, reclutas {}", plan.description, top,
				looted, plan.lootSpots().size(), guards.size());
		if (!top.toString().contains("trapdoor")) {
			throw new GameTestException("No está la trampilla de arriba: " + top);
		}
		if (looted < plan.lootSpots().size()) {
			throw new GameTestException("Cofres sin botín: " + looted + "/" + plan.lootSpots().size());
		}
		if (guards.size() < plan.guards().size()) {
			throw new GameTestException("Faltan reclutas: " + guards.size() + "/" + plan.guards().size());
		}
		guards.forEach(e -> e.discard());
		ctx.complete();
	}
}
