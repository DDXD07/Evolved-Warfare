package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.SalesMailboxBlockEntity;
import com.evolvedwarfare.fishing.FishData;
import com.evolvedwarfare.fishing.FishMinigame;
import com.evolvedwarfare.fishing.FishSpecies;
import com.evolvedwarfare.fishing.Fishing;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.trade.Deal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La pesca con minijuego: el minijuego, el peso y la calidad, y que los peces con calidad solo se venden en el buzón. */
public class FishingGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	/** Juega solo: mantiene pulsado si el pez está por encima del centro de la zona (y algo de inercia). */
	private static FishMinigame play(FishSpecies s, float zone, boolean skilled, long seed) {
		FishMinigame g = new FishMinigame(s.difficulty, s.behavior, zone, true, Random.create(seed));
		for (int i = 0; i < 2400 && !g.done(); i++) {
			boolean hold = skilled && (g.fish + g.fishVel * 3 > g.zone + g.zoneSize / 2 + g.zoneVel * 4);
			g.tick(hold);
		}
		return g;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void minigame(TestContext ctx) {
		int easyWins = 0;
		int hardWins = 0;
		int bigZoneHard = 0;
		int idle = 0;
		int perfect = 0;
		int ticks = 0;
		for (long seed = 0; seed < 40; seed++) {
			FishMinigame easy = play(FishSpecies.CARP, FishMinigame.zoneSize(0, 0, 0), true, seed);
			easyWins += easy.caught() ? 1 : 0;
			perfect += easy.caught() && easy.perfect ? 1 : 0;
			ticks += easy.ticks;
			hardWins += play(FishSpecies.SWORDFISH, FishMinigame.zoneSize(0, 0, 0), true, seed).caught() ? 1 : 0;
			bigZoneHard += play(FishSpecies.SWORDFISH, FishMinigame.zoneSize(10, 3, 2), true, seed).caught() ? 1 : 0;
			idle += play(FishSpecies.CARP, FishMinigame.zoneSize(0, 0, 0), false, seed).caught() ? 1 : 0;
		}
		LOG.info("[EW-GAMETEST] minijuego (40 partidas): carpa {} (perfectas {}, {} tics de media), pez espada nivel 0 {}, pez espada nivel 10 con cebo {}, sin tocar {}",
				easyWins, perfect, ticks / 40, hardWins, bigZoneHard, idle);
		check(easyWins >= 30, "Una carpa no se saca casi nunca: " + easyWins);
		check(hardWins < easyWins, "El pez espada no es más difícil que la carpa");
		check(bigZoneHard > hardWins, "Subir de nivel y el cebo no ayudan");
		check(idle == 0, "Se pescan peces sin hacer nada");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void weightQualityAndPrice(TestContext ctx) {
		int bronze = Fishing.quality(0.05, 0, 0, false, 0.0F);
		int perfect = Fishing.quality(0.05, 0, 0, true, 0.0F);
		int heavy = Fishing.quality(0.98, 0, 0, false, 0.5F);
		int heavyPerfect = Fishing.quality(0.98, 5, 0, true, 0.9F);
		ItemStack small = new ItemStack(Items.COD);
		small.set(ModComponents.FISH, new FishData(700, FishData.BRONZE));
		ItemStack big = new ItemStack(Items.COD);
		big.set(ModComponents.FISH, new FishData(3800, FishData.PLATINUM));
		double pSmall = SalesMailboxBlockEntity.saleValue(small, ctx.getWorld());
		double pBig = SalesMailboxBlockEntity.saleValue(big, ctx.getWorld());
		Deal trader = new Deal(false, new ItemStack(Items.COD), 1, 1, 10, com.evolvedwarfare.trade.Tier.COMMON, -1);
		boolean traderTakes = trader.accepts(big);
		ItemStack made = Fishing.makeFish(FishSpecies.PIKE, Random.create(3), 4, 1, true);
		FishData data = made.get(ModComponents.FISH);
		LOG.info("[EW-GAMETEST] calidad: ligero {}, ligero perfecto {}, pesado {}, pesado y perfecto {}; buzón: 0,7 kg bronce {} y 3,8 kg platino {}; "
				+ "el comerciante lo compra {}; un lucio: {} g, calidad {}", bronze, perfect, heavy, heavyPerfect, pSmall, pBig, traderTakes,
				data == null ? -1 : data.grams(), data == null ? -1 : data.quality());
		check(bronze == FishData.BRONZE && perfect == FishData.SILVER && heavy >= FishData.GOLD && heavyPerfect == FishData.PLATINUM, "Las calidades no van bien");
		check(pBig > pSmall * 5 && pSmall > 0, "El peso y la calidad no se pagan");
		check(!traderTakes, "Los comerciantes compran peces con calidad");
		check(data != null && data.grams() >= 1500 && data.grams() <= 14000 && data.quality() >= FishData.SILVER, "El pez no sale con su peso y calidad");
		ctx.complete();
	}
}
