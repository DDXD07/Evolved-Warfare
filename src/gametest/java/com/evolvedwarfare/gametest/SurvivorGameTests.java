package com.evolvedwarfare.gametest;

import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import com.evolvedwarfare.skill.Survivor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El nivel de superviviente (fase 112): matar, minar y fabricar por primera vez dan experiencia, y los niveles dan puntos. */
public class SurvivorGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static float total(SkillData data) {
		float sum = data.levelXp;
		for (int l = 1; l < data.level; l++) {
			sum += SkillData.levelXpToNext(l);
		}
		return sum;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "survivor", tickLimit = 100)
	public void killingMiningAndCraftingGiveXp(TestContext ctx) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());

		float t0 = total(data);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, 3, 1, 3);
		float expectedKill = zombie.getMaxHealth() * 0.5F;
		zombie.damage(player.getDamageSources().playerAttack(player), 1000.0F);
		float kill = total(data) - t0;

		BlockPos natural = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		ctx.setBlockState(1, 1, 1, Blocks.DIAMOND_ORE);
		float t1 = total(data);
		player.interactionManager.tryBreakBlock(natural);
		float mined = total(data) - t1;

		BlockPos placed = ctx.getAbsolutePos(new BlockPos(2, 1, 1));
		ctx.setBlockState(2, 1, 1, Blocks.DIAMOND_ORE);
		Survivor.placed(ctx.getWorld(), placed);
		float t2 = total(data);
		player.interactionManager.tryBreakBlock(placed);
		float minedPlaced = total(data) - t2;

		float t3 = total(data);
		Survivor.crafted(player, new ItemStack(Items.IRON_PICKAXE));
		float first = total(data) - t3;
		Survivor.crafted(player, new ItemStack(Items.IRON_PICKAXE));
		float second = total(data) - t3 - first;

		float t4 = total(data);
		Survivor.crafted(player, new ItemStack(Items.OAK_STAIRS));
		float block = total(data) - t4;
		LOG.info("[EW-GAMETEST] superviviente: zombi {}, diamante {}, diamante puesto {}, pico 1ª {}, 2ª {}, escalera {}", kill, mined, minedPlaced, first,
				second, block);
		check(Math.abs(block - Survivor.FIRST_CRAFT_BLOCK_XP) < 0.01F, "Un bloque nuevo no da lo poco que debe: " + block);
		check(zombie.isDead(), "El zombi no ha muerto");
		check(Math.abs(kill - expectedKill) < 0.01F, "Matar un zombi no da " + expectedKill + ": " + kill);
		check(Math.abs(mined - 12.0F) < 0.01F, "Minar diamante no da 12: " + mined);
		check(minedPlaced == 0.0F, "Lo que pone un jugador da experiencia: " + minedPlaced);
		check(Math.abs(first - Survivor.FIRST_CRAFT_XP) < 0.01F, "Fabricar por primera vez no da experiencia: " + first);
		check(second == 0.0F, "Fabricar lo mismo otra vez da experiencia: " + second);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "survivor", tickLimit = 40)
	public void levelsGiveEngramAndAttributePoints(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		int startLevel = data.level, startEngram = data.engramPoints, startAttr = data.attributePoints;
		Skills.survivorXp(player, 500.0F);
		int level = data.level;
		check(level > startLevel, "No sube de nivel");
		int engram = data.engramPoints - startEngram, attr = data.attributePoints - startAttr;
		check(engram == SkillData.engramPointsUpTo(level) - SkillData.engramPointsUpTo(startLevel), "Puntos de engrama mal: " + engram);
		check(attr == level - startLevel, "Puntos de atributo mal: " + attr);
		Skills.survivorXp(player, 10_000_000.0F);
		check(data.level == SkillData.MAX_LEVEL && data.levelXp == 0.0F, "No se para en el 100: " + data.level + " " + data.levelXp);
		Skills.survivorXp(player, 1000.0F);
		check(data.level == SkillData.MAX_LEVEL, "Pasa del 100");

		// los que ya jugaban: su nivel de antes, con los puntos de esos niveles
		SkillData old = new SkillData();
		old.level = 30;
		old.levelXp = 5.0F;
		Skills.migrateSurvivor(old);
		Skills.migrateSurvivor(old);
		LOG.info("[EW-GAMETEST] superviviente: 500 XP -> nivel {} (+{} engrama, +{} atributo); al 100 {} engrama; nivel 30 de antes -> {} engrama, {} atributo",
				level, engram, attr, data.engramPoints, old.engramPoints, old.attributePoints);
		check(old.level == 30 && old.engramPoints == SkillData.engramPointsUpTo(30) && old.attributePoints == 29,
				"La migración no da los puntos de antes: " + old.engramPoints + " / " + old.attributePoints);
		ctx.complete();
	}
}
