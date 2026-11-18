package com.evolvedwarfare.gametest;

import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import com.evolvedwarfare.skill.SurvivorAttribute;
import com.evolvedwarfare.skill.SurvivorAttributes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Los atributos del superviviente (fase 114): vida, daño, velocidad, fortaleza y metabolismo; sin puntos no se sube; y devolverlos. */
public class AttributeGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	private static void spend(ServerPlayerEntity player, SurvivorAttribute a, int times) {
		for (int i = 0; i < times; i++) {
			SurvivorAttributes.spend(player, a);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "attributes")
	public void pointsMakeYouStronger(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		SkillData data = Skills.get(ctx.getWorld().getServer(), player.getUuid());
		data.attributePoints = 60;
		float hp0 = player.getMaxHealth();
		double dmg0 = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		double spd0 = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		float starve0 = SurvivorAttributes.modifyIncoming(player, player.getDamageSources().starve(), 10.0F);
		spend(player, SurvivorAttribute.HEALTH, 10);
		spend(player, SurvivorAttribute.MELEE, 20);
		spend(player, SurvivorAttribute.SPEED, 10);
		spend(player, SurvivorAttribute.FORTITUDE, 10);
		spend(player, SurvivorAttribute.METABOLISM, 10);
		float hp1 = player.getMaxHealth();
		double dmg1 = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		double spd1 = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		float starve1 = SurvivorAttributes.modifyIncoming(player, player.getDamageSources().starve(), 10.0F);
		float generic1 = SurvivorAttributes.modifyIncoming(player, player.getDamageSources().generic(), 10.0F);
		float meta = SurvivorAttributes.metabolism(player);
		SurvivorAttributes.Spend none = SurvivorAttributes.spend(player, SurvivorAttribute.OXYGEN);
		LOG.info("[EW-GAMETEST] atributos: vida {} -> {}, daño {} -> {}, velocidad {} -> {}, hambre {} -> {} (otro {}), metabolismo {}, sin puntos {}",
				hp0, hp1, dmg0, dmg1, spd0, spd1, starve0, starve1, generic1, meta, none);
		check(Math.abs(hp1 - hp0 - 4.0F) < 0.01F, "10 puntos de vida no dan 2 corazones: " + hp0 + " -> " + hp1);
		check(Math.abs(dmg1 / dmg0 - 1.10) < 0.001, "20 puntos de daño no dan un 10 %: " + dmg0 + " -> " + dmg1);
		check(Math.abs(spd1 / spd0 - 1.02) < 0.001, "10 puntos de velocidad no dan un 2 %: " + spd0 + " -> " + spd1);
		check(Math.abs(starve0 - 10.0F) < 0.01F && Math.abs(starve1 - 9.5F) < 0.01F && generic1 == 10.0F, "La fortaleza no quita lo que debe");
		check(Math.abs(meta - 0.95F) < 0.001F, "El metabolismo no baja un 5 %: " + meta);
		check(none == SurvivorAttributes.Spend.POINTS && data.attributePoints == 0, "Se sube sin puntos");
		SurvivorAttributes.reset(player);
		check(data.attributePoints == 60 && Math.abs(player.getMaxHealth() - hp0) < 0.01F, "Al devolverlos no vuelve todo: " + data.attributePoints);
		ctx.complete();
	}
}
