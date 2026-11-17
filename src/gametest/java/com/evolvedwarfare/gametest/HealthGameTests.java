package com.evolvedwarfare.gametest;

import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.health.BodyPart;
import com.evolvedwarfare.health.Health;
import com.evolvedwarfare.health.HealthItems;
import com.evolvedwarfare.health.WoundData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La salud (fase 92): heridas por partes, huesos rotos por caídas, sangrar, vendas, entablillados y cristales de corazón. */
public class HealthGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static ServerPlayerEntity player(TestContext ctx) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		Health.heal(player);
		player.setAttached(Health.HEARTS, 0);
		Health.applyMaxHealth(player);
		player.setHealth(player.getMaxHealth());
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health")
	public void hardFallBreaksTheLegs(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		double speed = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		Health.onHurt(player, player.getDamageSources().fall(), null, null, 18.0F);
		WoundData d = Health.get(player);
		double limping = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		LOG.info("[EW-GAMETEST] caída: piernas {} / {}, rotas {}, velocidad {} -> {}, torso {}", d.get(BodyPart.RIGHT_LEG),
				d.get(BodyPart.LEFT_LEG), d.broken(), speed, limping, d.get(BodyPart.TORSO));
		check(d.isBroken(BodyPart.RIGHT_LEG) && d.isBroken(BodyPart.LEFT_LEG), "Una caída de 18 no rompe las piernas");
		check(d.get(BodyPart.TORSO) == BodyPart.TORSO.max, "La caída le da al torso");
		check(limping < speed * 0.6, "Con las piernas rotas no se va más lento: " + limping);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health")
	public void hitsLandOnTheirPart(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		float yaw = player.getBodyYaw() * net.minecraft.util.math.MathHelper.RADIANS_PER_DEGREE;
		Vec3d right = player.getPos().add(-Math.cos(yaw), 1.2, -Math.sin(yaw));
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.ARMS, right, 3.0F);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.HEAD, null, 2.0F);
		WoundData d = Health.get(player);
		LOG.info("[EW-GAMETEST] golpes: brazo der. {} izq. {} cabeza {} torso {}", d.get(BodyPart.RIGHT_ARM), d.get(BodyPart.LEFT_ARM),
				d.get(BodyPart.HEAD), d.get(BodyPart.TORSO));
		check(d.get(BodyPart.RIGHT_ARM) == BodyPart.RIGHT_ARM.max - 3.0F, "El golpe al brazo derecho no le da");
		check(d.get(BodyPart.LEFT_ARM) == BodyPart.LEFT_ARM.max, "El golpe al brazo derecho le da al izquierdo");
		check(d.get(BodyPart.HEAD) == BodyPart.HEAD.max - 2.0F, "El golpe a la cabeza no le da");
		// un brazo sin vida se rompe y pega menos
		double damage = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.ARMS, right, 10.0F);
		check(Health.get(player).isBroken(BodyPart.RIGHT_ARM), "El brazo sin vida no se rompe");
		check(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) < damage, "Con el brazo roto se pega igual");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health", tickLimit = 200)
	public void bleedingHurtsAndBandagesStopIt(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		// pasada la invulnerabilidad de entrar
		ctx.waitAndRun(80, () -> bleed(ctx, player));
	}

	private static void bleed(TestContext ctx, ServerPlayerEntity player) {
		Health.startBleeding(player, 20);
		check(!Health.canRegenerate(player), "Sangrando se regenera con la comida");
		float life = player.getHealth();
		for (int s = 1; s <= 8; s++) {
			Health.second(player, s);
		}
		float after = player.getHealth();
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.TORSO, null, 4.0F);
		float torso = Health.get(player).get(BodyPart.TORSO);
		ItemStack bandage = new ItemStack(HealthItems.BANDAGE, 2);
		player.setStackInHand(Hand.MAIN_HAND, bandage);
		bandage.finishUsing(ctx.getWorld(), player);
		WoundData d = Health.get(player);
		LOG.info("[EW-GAMETEST] sangrar: vida {} -> {}; venda: torso {} -> {}, sangra {}, quedan {} vendas", life, after, torso,
				d.get(BodyPart.TORSO), d.bleeding(), bandage.getCount());
		check(after < life, "Sangrar no quita vida");
		check(d.bleeding() == 0, "La venda no corta la sangre");
		check(d.get(BodyPart.TORSO) > torso, "La venda no cura el torso");
		check(bandage.getCount() == 1, "La venda no se gasta");
		check(Health.canRegenerate(player), "Vendado no se regenera");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health")
	public void splintMendsTheBone(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.LEGS, null, 30.0F);
		player.setHealth(player.getMaxHealth());
		WoundData d = Health.get(player);
		BodyPart leg = d.isBroken(BodyPart.RIGHT_LEG) ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
		check(d.isBroken(leg), "La pierna sin vida no se rompe");
		double broken = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		ItemStack splint = new ItemStack(HealthItems.SPLINT);
		splint.finishUsing(ctx.getWorld(), player);
		double splinted = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		check(Health.get(player).isSplinted(leg), "El entablillado no se pone");
		check(splinted > broken, "Entablillado se cojea igual");
		for (int s = 1; s <= Health.SPLINT_SECONDS; s++) {
			Health.second(player, s);
		}
		WoundData healed = Health.get(player);
		LOG.info("[EW-GAMETEST] entablillado: velocidad {} -> {}; a los {} s rota {}, vida {}", broken, splinted, Health.SPLINT_SECONDS,
				healed.isBroken(leg), healed.get(leg));
		check(!healed.isBroken(leg), "El hueso entablillado no suelda");
		check(healed.get(leg) >= leg.max * 0.5F, "El hueso soldado no recupera la mitad");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health")
	public void heartCrystalsAddHearts(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		float start = player.getMaxHealth();
		ItemStack crystal = new ItemStack(HealthItems.HEART_CRYSTAL, 2);
		player.setStackInHand(Hand.MAIN_HAND, crystal);
		crystal.use(ctx.getWorld(), player, Hand.MAIN_HAND);
		float one = player.getMaxHealth();
		LOG.info("[EW-GAMETEST] corazones: {} -> {} (quedan {} cristales)", start, one, crystal.getCount());
		check(start == com.evolvedwarfare.config.EwConfig.get().startHearts * 2.0F, "No se empieza con los corazones de la configuración: " + start);
		check(one == start + 2.0F, "El cristal no da un corazón: " + one);
		check(crystal.getCount() == 1, "El cristal no se gasta");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "health")
	public void respawnHealthy(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.HEAD, null, 20.0F);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.TORSO, null, 3.0F);
		check(Health.get(player).get(BodyPart.HEAD) == 0.0F, "La cabeza no se queda sin vida");
		player.setHealth(0.0F);
		ServerPlayerEntity reborn = ctx.getWorld().getServer().getPlayerManager().respawnPlayer(player, false,
				net.minecraft.entity.Entity.RemovalReason.KILLED);
		WoundData d = Health.get(reborn);
		LOG.info("[EW-GAMETEST] renacer: cabeza {}, torso {}, vida {} / {}", d.get(BodyPart.HEAD), d.get(BodyPart.TORSO), reborn.getHealth(),
				reborn.getMaxHealth());
		check(!d.isHurt(), "Se renace con las heridas de antes");
		check(reborn.getHealth() == reborn.getMaxHealth(), "Se renace sin la vida llena");
		ctx.complete();
	}
}
