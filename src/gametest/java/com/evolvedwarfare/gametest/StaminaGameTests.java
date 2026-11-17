package com.evolvedwarfare.gametest;

import com.evolvedwarfare.health.Stamina;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El aguante (fase 97): correr cansa, agotado no se corre y se pega flojo, y se recupera parado. */
public class StaminaGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "stamina", tickLimit = 200)
	public void sprintingTiresYouOut(TestContext ctx) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Stamina.set(player, Stamina.MAX);
		Stamina.onJump(player);
		float afterJump = Stamina.get(player);
		check(afterJump < Stamina.MAX, "Saltar no cansa");
		int ticks = 0;
		player.setSprinting(true);
		while (!Stamina.exhausted(player) && ticks < 1000) {
			player.setSprinting(true);
			Stamina.tick(player);
			ticks++;
		}
		Stamina.tick(player);
		check(Stamina.exhausted(player), "Corriendo no se agota");
		check(!player.isSprinting(), "Agotado sigue corriendo");
		Stamina.onAttack(player);
		float hit = Stamina.modifyMelee(player.getDamageSources().playerAttack(player), 10.0F);
		LOG.info("[EW-GAMETEST] aguante: tras saltar {}, agotado corriendo en {} ticks, golpe agotado {} de 10, lento {}", afterJump, ticks, hit,
				player.hasStatusEffect(StatusEffects.SLOWNESS));
		check(ticks > 150 && ticks < 500, "Se agota demasiado pronto o tarde: " + ticks + " ticks");
		check(hit == 5.0F, "Agotado se pega igual: " + hit);
		ctx.waitAndRun(60, () -> {
			float back = Stamina.get(player);
			LOG.info("[EW-GAMETEST] aguante: a los 3 s parado {}", back);
			check(back > 5.0F, "Parado no se recupera el aguante: " + back);
			ctx.complete();
		});
	}
}
