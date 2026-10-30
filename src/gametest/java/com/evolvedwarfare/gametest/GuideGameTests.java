package com.evolvedwarfare.gametest;

import com.evolvedwarfare.item.GuideBookItem;
import com.evolvedwarfare.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La guía se da una sola vez a cada jugador (la primera vez que entra). */
public class GuideGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void guideIsGivenOnce(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		// puede que ya se la haya dado al entrar (si el evento salta con el jugador de prueba): se empieza de cero
		player.removeCommandTag(GuideBookItem.GIVEN_TAG);
		player.getInventory().clear();
		boolean first = GuideBookItem.giveOnFirstJoin(player);
		boolean second = GuideBookItem.giveOnFirstJoin(player);
		int books = player.getInventory().count(ModItems.GUIDE_BOOK);
		LOG.info("[EW-GAMETEST] guía: primera vez={}, segunda={}, libros={}", first, second, books);
		if (!first || second || books != 1) {
			throw new GameTestException("La guía no se da una sola vez: " + first + ", " + second + ", " + books);
		}
		ctx.complete();
	}
}
