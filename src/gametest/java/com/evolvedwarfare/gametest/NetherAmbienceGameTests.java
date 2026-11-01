package com.evolvedwarfare.gametest;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModSounds;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

/** Los ruidos del Nether: el sonido está registrado y sus tres .ogg van dentro del mod. */
public class NetherAmbienceGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void netherSoundsAreThere(TestContext ctx) {
		if (Registries.SOUND_EVENT.get(EvolvedWarfare.id("ambient.nether")) != ModSounds.NETHER_AMBIENCE) {
			throw new GameTestException("El sonido ambient.nether no está registrado");
		}
		for (int i = 1; i <= 3; i++) {
			String path = "/assets/evolvedwarfare/sounds/ambient/nether_" + i + ".ogg";
			if (EvolvedWarfare.class.getResource(path) == null) {
				throw new GameTestException("Falta " + path);
			}
		}
		ctx.complete();
	}
}
