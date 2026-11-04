package com.evolvedwarfare.gametest;

import com.evolvedwarfare.climate.Climate;
import com.evolvedwarfare.climate.ClimateWearItem;
import com.evolvedwarfare.climate.Season;
import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El clima: las estaciones de 91 días se suceden en orden, el cuerpo se enfría con frío y se calienta con calor, la ropa
 * abriga o refresca lo que debe, las hogueras calientan y la hipotermia quita vida.
 */
public class ClimateGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void seasonsLast91Days(TestContext ctx) {
		int n = 91;
		check(Season.of(1, n) == Season.SPRING && Season.of(91, n) == Season.SPRING, "Los primeros 91 días no son primavera");
		check(Season.of(92, n) == Season.SUMMER && Season.dayOf(92, n) == 1, "El día 92 no es el primero del verano");
		check(Season.of(183, n) == Season.AUTUMN, "El día 183 no es otoño");
		check(Season.of(274, n) == Season.WINTER && Season.of(364, n) == Season.WINTER, "Del 274 al 364 no es invierno");
		check(Season.of(365, n) == Season.SPRING && Season.dayOf(365, n) == 1, "El año no vuelve a empezar en primavera el día 365");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bodyCoolsAndWarms(TestContext ctx) {
		float cold = Climate.NORMAL;
		float hot = Climate.NORMAL;
		float back = 34.0F;
		for (int s = 0; s < 120; s++) {
			cold = Climate.step(cold, -10.0F, 1.0F);
			hot = Climate.step(hot, 46.0F, 1.0F);
			back = Climate.step(back, 20.0F, 1.0F);
		}
		LOG.info("[EW-GAMETEST] clima: 2 min a -10 -> {}, a 46 -> {}, de 34 a 20 °C -> {}", cold, hot, back);
		check(cold < Climate.HYPOTHERMIA, "Dos minutos a -10 °C no dan hipotermia: " + cold);
		check(hot > Climate.HYPERTHERMIA, "Dos minutos a 46 °C no dan hipertermia: " + hot);
		check(Math.abs(back - Climate.NORMAL) < 0.01F, "A 20 °C el cuerpo no vuelve a 37: " + back);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void clothesWarmAndCool(TestContext ctx) {
		ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, 1, 1, 1);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		List<net.minecraft.item.Item> fur = ModItems.CLIMATE_WEAR.get(ClimateWearItem.Kind.FUR);
		List<net.minecraft.item.Item> linen = ModItems.CLIMATE_WEAR.get(ClimateWearItem.Kind.LINEN);
		for (int i = 0; i < 4; i++) {
			stand.equipStack(slots[i], new ItemStack(fur.get(i)));
		}
		Climate.Wear furWear = Climate.wear(stand);
		for (int i = 0; i < 4; i++) {
			stand.equipStack(slots[i], new ItemStack(linen.get(i)));
		}
		Climate.Wear linenWear = Climate.wear(stand);
		Climate.Wear none = new Climate.Wear(0, 0, 0);
		float coldBare = Climate.felt(-5.0F, none, false, false);
		float coldFur = Climate.felt(-5.0F, furWear, false, false);
		float hotBare = Climate.felt(42.0F, none, true, false);
		float hotLinen = Climate.felt(42.0F, linenWear, true, false);
		float hotFur = Climate.felt(42.0F, furWear, true, false);
		LOG.info("[EW-GAMETEST] ropa: piel {} / lino {}; a -5: {} -> {}; a 42: {} -> lino {}, piel {}", furWear, linenWear, coldBare, coldFur,
				hotBare, hotLinen, hotFur);
		check(furWear.warm() == 15.0F && linenWear.cool() == 14.0F, "La ropa no da lo que dice: " + furWear + " " + linenWear);
		check(coldFur >= 10.0F && coldBare < 10.0F, "La piel no abriga lo bastante a -5 °C");
		check(hotLinen <= 30.0F && hotBare > 30.0F, "El lino no refresca lo bastante a 42 °C");
		check(hotFur > hotBare, "La piel en verano no da calor de más");
		stand.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void campfiresWarm(TestContext ctx) {
		BlockPos at = new BlockPos(2, 1, 2);
		float before = Climate.nearby(ctx.getWorld(), ctx.getAbsolutePos(at));
		ctx.setBlockState(at.east(2), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		float after = Climate.nearby(ctx.getWorld(), ctx.getAbsolutePos(at));
		LOG.info("[EW-GAMETEST] hoguera: {} -> {}", before, after);
		check(after - before > 4.0F, "Una hoguera a 2 bloques no calienta: " + before + " -> " + after);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260)
	public void hypothermiaHurts(TestContext ctx) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		Climate.setBody(player, 32.0F);
		float start = player.getHealth();
		// (un jugador recién llegado es invulnerable 3 segundos)
		ctx.waitAndRun(140, () -> {
			float body = Climate.body(player);
			LOG.info("[EW-GAMETEST] hipotermia: vida {} -> {}, cuerpo {}", start, player.getHealth(), body);
			check(player.getHealth() < start, "Con el cuerpo a 32 °C no pierde vida");
			ctx.complete();
		});
	}
}
