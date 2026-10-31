package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.HeavyAttack;
import com.evolvedwarfare.weapon.MeleeSwing;
import com.evolvedwarfare.weapon.WeaponType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El golpe fuerte: cargado del todo hace el doble que el golpe normal; soltado antes de tiempo no hace nada; con un escudo
 * en la otra mano, el clic derecho sigue siendo del escudo. (Los dos golpes se dan con la barra de ataque igual, para
 * comparar solo lo que añade el golpe fuerte.)
 */
public class HeavyAttackGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void longswordHeavyDoublesDamage(TestContext ctx) {
		heavy(ctx, WeaponType.LONGSWORD);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void battleAxeHeavyDoublesDamage(TestContext ctx) {
		heavy(ctx, WeaponType.BATTLE_AXE);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void spearHeavyDoublesDamage(TestContext ctx) {
		heavy(ctx, WeaponType.SPEAR);
	}

	/** Cargar dura al menos lo que tarda en llenarse la barra de ataque de cada arma (si no, el golpe fuerte saldría flojo). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void chargeCoversAttackCooldown(TestContext ctx) {
		for (WeaponType type : WeaponType.values()) {
			float cooldown = 20.0F / (4.0F + type.speed);
			LOG.info("[EW-GAMETEST] carga de {}: {} tics (barra de ataque {} tics)", type, MeleeSwing.chargeTicks(type), cooldown);
			if (MeleeSwing.chargeTicks(type) + 0.01F < cooldown) {
				throw new GameTestException("La carga de " + type + " (" + MeleeSwing.chargeTicks(type) + " tics) es más corta que su barra de ataque ("
						+ cooldown + ")");
			}
		}
		ctx.complete();
	}

	/** Con un escudo (o comida, bloques...) en la otra mano, el clic derecho no carga: es para eso. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void shieldKeepsRightClick(TestContext ctx) {
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ItemStack weapon = new ItemStack(ModItems.weapon(WeaponType.LONGSWORD, "iron"));
		player.setStackInHand(Hand.MAIN_HAND, weapon);
		player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
		TypedActionResult<ItemStack> withShield = weapon.use(ctx.getWorld(), player, Hand.MAIN_HAND);
		boolean chargingWithShield = player.isUsingItem();
		player.clearActiveItem();
		player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
		TypedActionResult<ItemStack> free = weapon.use(ctx.getWorld(), player, Hand.MAIN_HAND);
		boolean chargingFree = player.isUsingItem();
		player.clearActiveItem();
		LOG.info("[EW-GAMETEST] golpe fuerte con escudo: {} (cargando={}); sin escudo: {} (cargando={})", withShield.getResult(),
				chargingWithShield, free.getResult(), chargingFree);
		if (withShield.getResult() != ActionResult.PASS || chargingWithShield) {
			throw new GameTestException("Con escudo en la otra mano, el clic derecho carga el golpe en vez de usar el escudo");
		}
		if (!chargingFree) {
			throw new GameTestException("Sin nada en la otra mano, el clic derecho no carga el golpe fuerte");
		}
		ctx.complete();
	}

	private static void heavy(TestContext ctx, WeaponType type) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d pos = ctx.getAbsolute(new Vec3d(2.5, 1.0, 1.5));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 10.0F);
		ItemStack weapon = new ItemStack(ModItems.weapon(type, "iron"));
		player.setStackInHand(Hand.MAIN_HAND, weapon);
		// un zombi quieto delante (a 2 bloques, mirando hacia él), con mucha vida para que aguante los golpes
		ZombieEntity zombie = ctx.spawnMob(EntityType.ZOMBIE, 2, 1, 3);
		zombie.setAiDisabled(true);
		zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500.0);
		zombie.setHealth(500.0F);
		ctx.waitAndRun(2, () -> {
			ServerWorld world = ctx.getWorld();
			player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 10.0F);
			float start = zombie.getHealth();

			// 1) un golpe normal
			player.resetLastAttackedTicks();
			player.attack(zombie);
			float normal = start - zombie.getHealth();
			reset(zombie, start);

			// 2) empezar a cargar y soltar enseguida: nada
			TypedActionResult<ItemStack> use = weapon.use(world, player, Hand.MAIN_HAND);
			boolean charging = player.isUsingItem();
			player.resetLastAttackedTicks();
			weapon.onStoppedUsing(world, player, HeavyAttack.maxUseTime() - 3);
			float early = start - zombie.getHealth();
			player.clearActiveItem();
			reset(zombie, start);

			// 3) cargar del todo y soltar: el golpe fuerte, a lo que tiene delante
			weapon.use(world, player, Hand.MAIN_HAND);
			player.resetLastAttackedTicks();
			weapon.onStoppedUsing(world, player, HeavyAttack.maxUseTime() - MeleeSwing.chargeTicks(type));
			float heavy = start - zombie.getHealth();
			player.clearActiveItem();

			LOG.info("[EW-GAMETEST] golpe fuerte con {}: normal {}, soltado antes {}, cargado {} (x{}); clic derecho {} (cargando={})", type,
					normal, early, heavy, normal > 0.0F ? heavy / normal : 0.0F, use.getResult(), charging);
			if (!charging || !use.getResult().isAccepted()) {
				throw new GameTestException("Clic derecho con " + type + " no empieza a cargar (" + use.getResult() + ")");
			}
			if (normal <= 0.0F) {
				throw new GameTestException("El golpe normal con " + type + " no hace daño");
			}
			if (early > 0.0F) {
				throw new GameTestException("Soltar antes de cargar del todo golpea igual (" + early + " de daño)");
			}
			if (heavy < normal * 1.8F) {
				throw new GameTestException("El golpe fuerte con " + type + " no hace el doble: " + normal + " -> " + heavy);
			}
			zombie.discard();
			ctx.complete();
		});
	}

	private static void reset(ZombieEntity zombie, float health) {
		zombie.setHealth(health);
		zombie.timeUntilRegen = 0;
		zombie.hurtTime = 0;
	}
}
