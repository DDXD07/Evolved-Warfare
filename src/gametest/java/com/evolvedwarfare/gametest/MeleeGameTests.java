package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.MeleeSwing;
import com.evolvedwarfare.weapon.WeaponType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El golpe dura lo de cada arma (la daga menos que el puño, el martillo casi el doble), con cualquiera que la lleve. */
public class MeleeGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void warhammerSwingIsSlow(TestContext ctx) {
		swing(ctx, WeaponType.WARHAMMER);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void daggerSwingIsQuick(TestContext ctx) {
		swing(ctx, WeaponType.DAGGER);
	}

	private static void swing(TestContext ctx, WeaponType type) {
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ZombieEntity zombie = ctx.spawnMob(EntityType.ZOMBIE, 1, 1, 1);
		zombie.setAiDisabled(true);
		zombie.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.weapon(type, "iron")));
		int expected = MeleeSwing.ticks(type);
		ctx.waitAndRun(2, () -> {
			zombie.swingHand(Hand.MAIN_HAND);
			// un par de tics antes del final sigue golpeando; un par de tics después, ya ha acabado
			ctx.waitAndRun(expected - 2, () -> {
				boolean during = zombie.handSwinging;
				ctx.waitAndRun(4, () -> {
					boolean after = zombie.handSwinging;
					LOG.info("[EW-GAMETEST] golpe con {}: {} tics; a {} tics golpeando={}, a {} tics golpeando={}", type, expected,
							expected - 2, during, expected + 2, after);
					if (!during || after) {
						throw new GameTestException("El golpe con " + type + " no dura " + expected + " tics (durante=" + during
								+ ", después=" + after + ")");
					}
					ctx.complete();
				});
			});
		});
	}
}
