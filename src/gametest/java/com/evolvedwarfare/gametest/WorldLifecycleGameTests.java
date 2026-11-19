package com.evolvedwarfare.gametest;

import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Con el Nether del mod el mundo no sale como "experimental" (fase 118): sin los avisos al crearlo y al entrar. */
public class WorldLifecycleGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void worldIsNotExperimental(TestContext ctx) {
		Registry<DimensionOptions> dims = ctx.getWorld().getServer().getRegistryManager().get(RegistryKeys.DIMENSION);
		Lifecycle lifecycle = new DimensionOptionsRegistryHolder(dims).toConfig(dims).getLifecycle();
		LOG.info("[EW-GAMETEST] mundo: {} dimensiones, {}", dims.size(), lifecycle);
		if (!Lifecycle.stable().equals(lifecycle)) {
			throw new GameTestException("El mundo sale como experimental: " + lifecycle);
		}
		ctx.complete();
	}
}
