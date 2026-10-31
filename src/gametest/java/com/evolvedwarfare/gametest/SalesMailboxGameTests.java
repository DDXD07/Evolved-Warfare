package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.SalesMailboxBlock;
import com.evolvedwarfare.block.SalesMailboxBlockEntity;
import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.item.GloryPouchItem;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.trade.TraderType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El Buzón de Ventas: al empezar un día nuevo vende lo que tiene precio a la cuarta parte del mercado, deja lo que no
 * lo tiene, mete las monedas en su hueco (en una bolsa si pasan de un montón), guarda lo que sobra de una moneda y
 * levanta la banderita.
 */
public class SalesMailboxGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static SalesMailboxBlockEntity place(TestContext ctx) {
		BlockPos rel = new BlockPos(1, 1, 1);
		ctx.setBlockState(rel, ModBlocks.SALES_MAILBOX.getDefaultState());
		if (!(ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(rel)) instanceof SalesMailboxBlockEntity mailbox)) {
			throw new GameTestException("No hay Buzón de Ventas");
		}
		return mailbox;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void sellsAtDawnForAQuarter(TestContext ctx) {
		SalesMailboxBlockEntity mailbox = place(ctx);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamage(sword.getMaxDamage() / 2);
		mailbox.setStack(0, new ItemStack(Items.WHEAT, 64));
		mailbox.setStack(1, new ItemStack(Items.DIAMOND, 3));
		mailbox.setStack(2, sword);
		mailbox.setStack(3, new ItemStack(Items.DIRT, 10));
		mailbox.setStack(4, CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.CARROT));

		double wheat = TraderType.marketPrice(Items.WHEAT);
		double diamond = TraderType.marketPrice(Items.DIAMOND);
		double swordPrice = TraderType.marketPrice(Items.IRON_SWORD);
		double carrot = TraderType.marketPrice(Items.CARROT);
		double crate = TraderType.marketPrice(ModBlocks.CROP_CRATE_ITEM);
		double expected = 0.25 * (64 * wheat + 3 * diamond
				+ swordPrice * (sword.getMaxDamage() - sword.getDamage()) / (double) sword.getMaxDamage()
				+ crate + CropCrateItem.capacity(Items.CARROT) * carrot);
		LOG.info("[EW-GAMETEST] buzón: trigo {} diamante {} espada {} zanahoria {} -> esperado {}", wheat, diamond, swordPrice, carrot, expected);
		if (wheat <= 0 || diamond <= 0 || swordPrice <= 0 || carrot <= 0) {
			throw new GameTestException("Algo de la prueba no tiene precio de mercado");
		}
		if (TraderType.marketPrice(Items.DIRT) != 0.0) {
			throw new GameTestException("La tierra no debería tener precio");
		}

		// el primer día solo se apunta; al siguiente, vende
		boolean first = mailbox.onDay(10);
		boolean sameDay = mailbox.onDay(10);
		boolean dawn = mailbox.onDay(11);
		ItemStack coins = mailbox.getStack(SalesMailboxBlockEntity.COIN_SLOT);
		long got = GloryPouchItem.isPouch(coins) ? GloryPouchItem.balance(coins) : Market.isPlainCoin(coins) ? coins.getCount() : -1;
		LOG.info("[EW-GAMETEST] buzón: vendió={} (antes {} {}), monedas {} ({}), tierra {}", dawn, first, sameDay, got, coins,
				mailbox.getStack(3));
		if (first || sameDay || !dawn) {
			throw new GameTestException("Tiene que vender solo al empezar un día nuevo (" + first + ", " + sameDay + ", " + dawn + ")");
		}
		if (got != (long) Math.floor(expected + 1.0E-9)) {
			throw new GameTestException("Monedas: " + got + ", esperaba " + (long) Math.floor(expected));
		}
		if (!GloryPouchItem.isPouch(coins) && got > 64) {
			throw new GameTestException("Más de un montón de monedas y no van en una bolsa");
		}
		for (int i = 0; i < 5; i++) {
			if (i != 3 && !mailbox.getStack(i).isEmpty()) {
				throw new GameTestException("Quedó sin vender el hueco " + i + ": " + mailbox.getStack(i));
			}
		}
		if (!mailbox.getStack(3).isOf(Items.DIRT) || mailbox.getStack(3).getCount() != 10) {
			throw new GameTestException("Lo que no tiene precio tiene que quedarse: " + mailbox.getStack(3));
		}
		BlockPos pos = mailbox.getPos();
		if (!ctx.getWorld().getBlockState(pos).get(SalesMailboxBlock.FLAG)) {
			throw new GameTestException("La banderita no se levanta con monedas dentro");
		}
		// al sacar las monedas, se baja
		mailbox.removeStack(SalesMailboxBlockEntity.COIN_SLOT);
		if (ctx.getWorld().getBlockState(pos).get(SalesMailboxBlock.FLAG)) {
			throw new GameTestException("La banderita no se baja al sacar las monedas");
		}
		ctx.complete();
	}

	/** Lo barato no se pierde: lo que sobra de una moneda se guarda para la próxima venta. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void keepsFractionsForNextSale(TestContext ctx) {
		SalesMailboxBlockEntity mailbox = place(ctx);
		double unit = 0.25 * TraderType.marketPrice(Items.COBBLESTONE);
		if (unit <= 0.0) {
			throw new GameTestException("El empedrado no tiene precio");
		}
		// lo justo para que una venta dé algo menos de una moneda
		int count = Math.max(1, Math.min(64, (int) Math.floor(0.7 / unit)));
		double each = unit * count;
		int sales = 0;
		long total = 0;
		mailbox.onDay(1);
		for (int day = 2; day < 40 && total < 3; day++) {
			mailbox.setStack(0, new ItemStack(Items.COBBLESTONE, count));
			mailbox.onDay(day);
			sales++;
			ItemStack coins = mailbox.getStack(SalesMailboxBlockEntity.COIN_SLOT);
			total = coins.isEmpty() ? 0 : coins.getCount();
		}
		long expected = (long) Math.floor(each * sales + 1.0E-9);
		LOG.info("[EW-GAMETEST] buzón: {} ventas de {} empedrado ({} cada una) -> {} monedas (esperado {})", sales, count, each, total, expected);
		if (total != expected || total == 0) {
			throw new GameTestException("Con lo que sobra de cada venta: " + total + " monedas, esperaba " + expected);
		}
		ctx.complete();
	}

	/** Las monedas y las bolsas no se venden ni se pueden meter en los huecos de venta. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void coinsAreNotForSale(TestContext ctx) {
		SalesMailboxBlockEntity mailbox = place(ctx);
		if (mailbox.isValid(0, new ItemStack(Market.currency())) || SalesMailboxBlockEntity.sellable(new ItemStack(Market.currency()))) {
			throw new GameTestException("Las Glory Coins se pueden vender en el buzón");
		}
		if (!mailbox.isValid(SalesMailboxBlockEntity.COIN_SLOT, new ItemStack(com.evolvedwarfare.registry.ModItems.GLORY_POUCH))) {
			throw new GameTestException("No se puede dejar una bolsa en el hueco de las monedas");
		}
		ctx.complete();
	}
}
