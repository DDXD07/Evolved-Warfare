package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.CatalogBlock;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.screen.CatalogScreenHandler;
import com.evolvedwarfare.trade.CatalogReaders;
import com.evolvedwarfare.trade.TraderType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El Catálogo del mercader: el libro del mostrador abre el catálogo de su mercader (y dice dónde está el libro, para la
 * animación), hablar con el mercader también lo abre, si duerme la tienda está cerrada y en las tiendas antiguas el
 * libro aparece solo encima del mostrador.
 */
public class CatalogGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new GameTestException(message);
		}
	}

	/** Suelo, el mostrador en z = 3 (de x = 0 a 4) y el mercader detrás, en (2, 1, 2), con su cama al lado. */
	private static TraderEntity shop(TestContext ctx) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		for (int x = 0; x < 5; x++) {
			ctx.setBlockState(x, 1, 3, Blocks.SPRUCE_PLANKS);
		}
		ServerWorld world = ctx.getWorld();
		TraderEntity trader = ModEntities.TRADER.create(world);
		BlockPos counter = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		trader.setTraderType(TraderType.JEWELER);
		trader.setCityHome(counter, counter.west(2), 777L);
		trader.refreshPositionAndAngles(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5, 0.0F, 0.0F);
		trader.initialize(world, world.getLocalDifficulty(counter), SpawnReason.STRUCTURE, null);
		world.spawnEntity(trader);
		return trader;
	}

	private static ServerPlayerEntity customer(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 1, 4));
		player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 30.0F);
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bookOpensTheCatalog(TestContext ctx) {
		TraderEntity trader = shop(ctx);
		BlockPos book = new BlockPos(2, 2, 3);
		ctx.setBlockState(book, ModBlocks.TRADER_CATALOG.getDefaultState().with(CatalogBlock.FACING, Direction.SOUTH)
				.with(CatalogBlock.TRADE, TraderType.JEWELER.ordinal()));
		ServerPlayerEntity player = customer(ctx);
		ctx.useBlock(book, player);
		boolean viaBook = player.currentScreenHandler instanceof CatalogScreenHandler h && ctx.getAbsolutePos(book).equals(h.book())
				&& h.traderType() == TraderType.JEWELER && !h.deals().isEmpty();
		player.closeHandledScreen();
		trader.interact(player, Hand.MAIN_HAND);
		boolean viaTrader = player.currentScreenHandler instanceof CatalogScreenHandler h2 && ctx.getAbsolutePos(book).equals(h2.book());
		player.closeHandledScreen();
		TraderEntity.forceBedtime = true;
		trader.sleep(ctx.getAbsolutePos(new BlockPos(0, 1, 2)));
		ctx.useBlock(book, player);
		boolean closedAtNight = !(player.currentScreenHandler instanceof CatalogScreenHandler);
		TraderEntity.forceBedtime = null;
		trader.wakeUp();
		LOG.info("[EW-GAMETEST] catálogo: por el libro {}, hablando {}, de noche cerrado {}", viaBook, viaTrader, closedAtNight);
		check(viaBook, "El libro no abre el catálogo del joyero (con el sitio del libro)");
		check(viaTrader, "Hablar con el mercader no abre su catálogo");
		check(closedAtNight, "Con el mercader dormido se puede comprar");
		trader.discard();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void oldShopsGetTheirBook(TestContext ctx) {
		TraderEntity trader = shop(ctx);
		BlockPos expected = new BlockPos(2, 2, 3);
		ctx.waitAndRun(230, () -> {
			var state = ctx.getBlockState(expected);
			LOG.info("[EW-GAMETEST] catálogo en una tienda antigua: {}", state);
			check(state.isOf(ModBlocks.TRADER_CATALOG), "No ha aparecido el libro encima del mostrador: " + state);
			check(state.get(CatalogBlock.FACING) == Direction.SOUTH, "El libro no mira a los clientes: " + state.get(CatalogBlock.FACING));
			check(CatalogBlock.trade(state) == TraderType.JEWELER, "El libro no es del joyero");
			trader.discard();
			ctx.complete();
		});
	}

	/** Los demás ven al que lee: el servidor sabe quién tiene un catálogo abierto, con qué libro, y deja de saberlo al cerrarlo. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void othersSeeTheReader(TestContext ctx) {
		TraderEntity trader = shop(ctx);
		BlockPos book = new BlockPos(2, 2, 3);
		ctx.setBlockState(book, ModBlocks.TRADER_CATALOG.getDefaultState().with(CatalogBlock.FACING, Direction.SOUTH)
				.with(CatalogBlock.TRADE, TraderType.TAILOR.ordinal()));
		ServerPlayerEntity player = customer(ctx);
		ctx.useBlock(book, player);
		CatalogReaders.Reader reader = CatalogReaders.of(player);
		boolean reading = reader != null && ctx.getAbsolutePos(book).equals(reader.book());
		boolean cover = reader != null && reader.trade() == TraderType.TAILOR.ordinal();
		CatalogReaders.turn(player, true);
		CatalogReaders.turn(player, false);
		player.closeHandledScreen();
		boolean closed = !CatalogReaders.reading(player);
		// si el menú desaparece sin cerrarse (muerte, cambio de dimensión…), también se entera
		ctx.useBlock(book, player);
		boolean again = CatalogReaders.reading(player);
		player.currentScreenHandler = player.playerScreenHandler;
		ctx.waitAndRun(25, () -> {
			boolean forgotten = !CatalogReaders.reading(player);
			LOG.info("[EW-GAMETEST] lectores: leyendo {}, portada {}, cerrado {}, otra vez {}, olvidado {}", reading, cover, closed, again, forgotten);
			check(reading, "El servidor no sabe que está leyendo el libro del mostrador");
			check(cover, "La portada no es la del libro (sastre)");
			check(closed, "Al cerrar el catálogo sigue leyendo");
			check(again, "Al volver a abrirlo no lee");
			check(forgotten, "Sin el menú del catálogo sigue leyendo");
			trader.discard();
			ctx.complete();
		});
	}
}
