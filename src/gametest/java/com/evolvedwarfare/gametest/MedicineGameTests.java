package com.evolvedwarfare.gametest;

import com.evolvedwarfare.combat.HitZone;
import com.evolvedwarfare.health.BodyPart;
import com.evolvedwarfare.health.Health;
import com.evolvedwarfare.health.HealthItems;
import com.evolvedwarfare.health.Medicine;
import com.evolvedwarfare.health.WoundData;
import com.evolvedwarfare.registry.ModEffects;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La medicina (fase 93): la leche ya no lo cura todo, el aguardiente, la medicina, el antídoto, el botiquín y el veneno de araña. */
public class MedicineGameTests implements FabricGameTest {
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
		player.clearStatusEffects();
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "medicine")
	public void milkNoLongerCuresEverything(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, 2000, 1));
		player.addStatusEffect(new StatusEffectInstance(ModEffects.VENOM, 400, 0));
		player.addStatusEffect(new StatusEffectInstance(ModEffects.PARASITES, 2000, 0));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 400, 0));
		new ItemStack(Items.MILK_BUCKET).finishUsing(ctx.getWorld(), player);
		LOG.info("[EW-GAMETEST] leche: infección {} veneno {} parásitos {} lentitud {}", player.hasStatusEffect(ModEffects.INFECTION),
				player.hasStatusEffect(ModEffects.VENOM), player.hasStatusEffect(ModEffects.PARASITES), player.hasStatusEffect(StatusEffects.SLOWNESS));
		check(player.hasStatusEffect(ModEffects.INFECTION), "La leche cura la infección");
		check(player.getStatusEffect(ModEffects.INFECTION).getAmplifier() == 1, "La leche cambia el grado de la infección");
		check(player.hasStatusEffect(ModEffects.VENOM), "La leche cura el veneno fuerte");
		check(!player.hasStatusEffect(ModEffects.PARASITES), "La leche no cura los parásitos");
		check(!player.hasStatusEffect(StatusEffects.SLOWNESS), "La leche no quita lo demás");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "medicine")
	public void remediesCure(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, 2000, 1));
		ItemStack alcohol = new ItemStack(HealthItems.ALCOHOL);
		ItemStack left = alcohol.finishUsing(ctx.getWorld(), player);
		StatusEffectInstance eased = player.getStatusEffect(ModEffects.INFECTION);
		check(left.isOf(Items.GLASS_BOTTLE), "El aguardiente no deja el frasco: " + left);
		check(eased != null && eased.getAmplifier() == 0, "El aguardiente no baja un grado la infección");
		check(player.hasStatusEffect(ModEffects.DISINFECTED), "El aguardiente no desinfecta");
		new ItemStack(HealthItems.MEDICINE).finishUsing(ctx.getWorld(), player);
		check(!player.hasStatusEffect(ModEffects.INFECTION), "La medicina no cura la infección");
		player.addStatusEffect(new StatusEffectInstance(ModEffects.VENOM, 400, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 400, 0));
		new ItemStack(HealthItems.ANTIDOTE).finishUsing(ctx.getWorld(), player);
		LOG.info("[EW-GAMETEST] remedios: aguardiente -> infección grado {}, frasco {}; medicina y antídoto ok", eased.getAmplifier(), left);
		check(!player.hasStatusEffect(ModEffects.VENOM) && !player.hasStatusEffect(StatusEffects.POISON), "El antídoto no cura el veneno");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "medicine")
	public void medkitTreatsEverything(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		Health.onHurt(player, player.getDamageSources().fall(), null, null, 20.0F);
		Health.onHurt(player, player.getDamageSources().generic(), HitZone.HEAD, null, 4.0F);
		Health.startBleeding(player, 30);
		player.addStatusEffect(new StatusEffectInstance(ModEffects.INFECTION, 2000, 2));
		ItemStack kit = new ItemStack(HealthItems.MEDKIT, 2);
		kit.finishUsing(ctx.getWorld(), player);
		WoundData d = Health.get(player);
		LOG.info("[EW-GAMETEST] botiquín: cabeza {}, piernas entablilladas {}/{}, sangra {}, infección {}, quedan {}", d.get(BodyPart.HEAD),
				d.isSplinted(BodyPart.RIGHT_LEG), d.isSplinted(BodyPart.LEFT_LEG), d.bleeding(), player.hasStatusEffect(ModEffects.INFECTION),
				kit.getCount());
		check(d.get(BodyPart.HEAD) == BodyPart.HEAD.max, "El botiquín no cura la cabeza");
		check(d.isSplinted(BodyPart.RIGHT_LEG) && d.isSplinted(BodyPart.LEFT_LEG), "El botiquín no entablilla las piernas");
		check(d.bleeding() == 0, "El botiquín no corta la sangre");
		check(!player.hasStatusEffect(ModEffects.INFECTION), "El botiquín no cura la infección");
		check(kit.getCount() == 1, "El botiquín no se gasta");
		// sano del todo, no se gasta
		Health.heal(player);
		player.setHealth(player.getMaxHealth());
		kit.finishUsing(ctx.getWorld(), player);
		check(kit.getCount() == 1, "El botiquín se gasta sin nada que curar");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "medicine", tickLimit = 200)
	public void caveSpidersEnvenomAndVenomKills(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		CaveSpiderEntity spider = ctx.spawnEntity(EntityType.CAVE_SPIDER, 2, 1, 2);
		Medicine.onHurt(player, player.getDamageSources().mobAttack(spider), 2.0F);
		StatusEffectInstance venom = player.getStatusEffect(ModEffects.VENOM);
		check(venom != null && venom.getAmplifier() >= 1, "La araña de cueva no envenena (fuerte)");
		spider.discard();
		// pasada la invulnerabilidad de entrar: el veneno fuerte sí mata
		ctx.waitAndRun(80, () -> {
			player.setHealth(1.0F);
			ModEffects.VENOM.value().applyUpdateEffect(player, 0);
			LOG.info("[EW-GAMETEST] veneno: grado {}, vida tras el veneno {} (muerto {})", venom.getAmplifier(), player.getHealth(), player.isDead());
			check(player.isDead(), "El veneno fuerte no mata");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "medicine", tickLimit = 100)
	public void bleedingWoundsGetInfected(TestContext ctx) {
		ServerPlayerEntity player = player(ctx);
		var cfg = com.evolvedwarfare.config.EwConfig.get();
		int was = cfg.woundInfectionChance;
		cfg.woundInfectionChance = 6000;
		try {
			// desinfectado no se infecta
			player.addStatusEffect(new StatusEffectInstance(ModEffects.DISINFECTED, 400, 0));
			Health.startBleeding(player, 30);
			Health.second(player, 1);
			check(!player.hasStatusEffect(ModEffects.INFECTION), "Desinfectado se infecta");
			player.removeStatusEffect(ModEffects.DISINFECTED);
			Health.second(player, 2);
			check(player.hasStatusEffect(ModEffects.INFECTION), "La herida que sangra no se infecta");
			Health.second(player, Medicine.WORSEN_EVERY);
			int amp = player.getStatusEffect(ModEffects.INFECTION).getAmplifier();
			LOG.info("[EW-GAMETEST] infección de la herida: grado tras {} s sin tratar {}", Medicine.WORSEN_EVERY, amp);
			check(amp == 1, "La infección sin tratar no empeora");
		} finally {
			cfg.woundInfectionChance = was;
		}
		ctx.complete();
	}
}
