package com.evolvedwarfare.gametest;

import com.evolvedwarfare.difficulty.SmartMobs;
import com.evolvedwarfare.difficulty.ai.CreeperBreachGoal;
import com.evolvedwarfare.difficulty.ai.RetreatWhenHurtGoal;
import com.evolvedwarfare.difficulty.ai.SpiderWebGoal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Los monstruos más listos (fase 96). */
public class SmartMobsGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void floor(TestContext ctx, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "smartmobs")
	public void skeletonsLeadTheirShots(TestContext ctx) {
		Vec3d from = new Vec3d(0, 1.5, 0);
		Vec3d target = new Vec3d(0, 1.5, 20);
		Vec3d still = SmartMobs.lead(from, 1.6, target, Vec3d.ZERO);
		Vec3d running = SmartMobs.lead(from, 1.6, target, new Vec3d(0.25, 0, 0));
		LOG.info("[EW-GAMETEST] puntería: quieto x {}, corriendo x {}", still.x, running.x);
		check(Math.abs(still.x) < 1e-6, "Apunta de lado a una presa quieta");
		check(running.x > 2.0, "No se adelanta a la presa que corre: " + running.x);
		check(still.y > 0.0, "No compensa la caída de la flecha");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "smartmobs")
	public void monstersAlertEachOther(TestContext ctx) {
		floor(ctx, 8);
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ZombieEntity hit = ctx.spawnEntity(EntityType.ZOMBIE, 1, 1, 1);
		ZombieEntity friend = ctx.spawnEntity(EntityType.ZOMBIE, 5, 1, 5);
		SkeletonEntity archer = ctx.spawnEntity(EntityType.SKELETON, 6, 1, 1);
		for (var m : new net.minecraft.entity.mob.MobEntity[] {hit, friend, archer}) {
			m.setAiDisabled(true);
		}
		int alerted = SmartMobs.alert(hit, player.getDamageSources().playerAttack(player));
		LOG.info("[EW-GAMETEST] se avisan: {} avisados, zombi {}, esqueleto {}", alerted, friend.getTarget(), archer.getTarget());
		check(friend.getTarget() == player && archer.getTarget() == player, "Los de alrededor no van a por el jugador");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "smartmobs")
	public void creepersBlowThroughWalls(TestContext ctx) {
		floor(ctx, 8);
		// el aldeano, encerrado en una caja de piedra
		for (int x = 4; x <= 6; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 1; z <= 3; z++) {
					if (x != 5 || z != 2 || y == 3) {
						ctx.setBlockState(x, y, z, Blocks.STONE);
					}
				}
			}
		}
		VillagerEntity villager = ctx.spawnEntity(EntityType.VILLAGER, 5, 1, 2);
		CreeperEntity creeper = ctx.spawnEntity(EntityType.CREEPER, 1, 1, 2);
		creeper.setAiDisabled(true);
		creeper.setTarget(villager);
		CreeperBreachGoal goal = new CreeperBreachGoal(creeper);
		boolean starts = goal.canStart();
		if (starts) {
			goal.start();
		}
		LOG.info("[EW-GAMETEST] creeper: ve {}, empieza {}, encendido {}", creeper.canSee(villager), starts, creeper.isIgnited());
		check(starts && creeper.isIgnited(), "El creeper no revienta la pared");
		creeper.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "smartmobs")
	public void spidersSpitWebs(TestContext ctx) {
		floor(ctx, 10);
		SpiderEntity spider = ctx.spawnEntity(EntityType.SPIDER, 1, 1, 1);
		VillagerEntity villager = ctx.spawnEntity(EntityType.VILLAGER, 8, 1, 8);
		spider.setAiDisabled(true);
		villager.setAiDisabled(true);
		boolean fired = SpiderWebGoal.fire(spider, villager);
		BlockPos feet = villager.getBlockPos();
		LOG.info("[EW-GAMETEST] telaraña: {} en {}", fired, ctx.getWorld().getBlockState(feet));
		check(fired && ctx.getWorld().getBlockState(feet).isOf(Blocks.COBWEB), "La araña no escupe telaraña");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "smartmobs")
	public void badlyHurtMonstersRetreat(TestContext ctx) {
		floor(ctx, 8);
		SkeletonEntity skeleton = ctx.spawnEntity(EntityType.SKELETON, 1, 1, 1);
		VillagerEntity villager = ctx.spawnEntity(EntityType.VILLAGER, 4, 1, 4);
		skeleton.setTarget(villager);
		RetreatWhenHurtGoal goal = new RetreatWhenHurtGoal(skeleton);
		boolean healthy = goal.canStart();
		skeleton.setHealth(3.0F);
		boolean hurt = goal.canStart();
		LOG.info("[EW-GAMETEST] huir: sano {}, herido {}", healthy, hurt);
		check(!healthy && hurt, "El esqueleto muy herido no huye (o huye sano)");
		ctx.complete();
	}
}
