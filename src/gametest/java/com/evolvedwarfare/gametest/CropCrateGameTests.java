package com.evolvedwarfare.gametest;

import com.evolvedwarfare.block.CropCrateBlock;
import com.evolvedwarfare.block.CropCrateBlockEntity;
import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.screen.TraderScreenHandler;
import com.evolvedwarfare.trade.Deal;
import com.evolvedwarfare.trade.TraderType;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La Caja de cultivos: un solo cultivo y hasta 64; agachado se coge con lo que lleva y al ponerla (o romperla) lo
 * conserva; y los comerciantes compran los cultivos en cajas llenas, no sueltos.
 */
public class CropCrateGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void crateFillsPicksUpAndKeepsContents(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos rel = new BlockPos(2, 1, 2);
		ctx.setBlockState(rel, ModBlocks.CROP_CRATE);
		BlockPos pos = ctx.getAbsolutePos(rel);
		CropCrateBlockEntity crate = crate(world, pos);
		if (crate.isValid(0, new ItemStack(Items.DIRT)) || !crate.isValid(0, new ItemStack(Items.WHEAT))) {
			throw new GameTestException("La caja debe aceptar trigo y no tierra");
		}
		crate.setStack(0, new ItemStack(Items.WHEAT, 70));
		if (crate.getStack(0).getCount() != 64) {
			throw new GameTestException("Caben más de 64: " + crate.getStack(0).getCount());
		}
		if (crate.isValid(0, new ItemStack(Items.CARROT))) {
			throw new GameTestException("Se pueden mezclar cultivos");
		}
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		boolean picked = CropCrateBlock.pickUp(world, pos, player);
		ItemStack held = player.getMainHandStack();
		CropCrateItem.Contents contents = CropCrateItem.contents(held);
		LOG.info("[EW-GAMETEST] caja cogida={}: {} en la mano ({}), bloque {}", picked, held.getName().getString(), contents,
				world.getBlockState(pos));
		if (!picked || !world.getBlockState(pos).isAir() || contents == null || contents.item() != Items.WHEAT || contents.count() != 64
				|| !contents.full()) {
			throw new GameTestException("Al cogerla no lleva sus 64 de trigo: " + contents);
		}
		ItemStack other = CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.WHEAT);
		if (!ItemStack.areItemsAndComponentsEqual(held, other) || held.getMaxCount() < 2) {
			throw new GameTestException("Dos cajas llenas de trigo no se pueden apilar");
		}
		// al ponerla otra vez vuelve con lo suyo, y al romperla suelta la caja llena
		net.minecraft.util.ActionResult placed = ((net.minecraft.item.BlockItem) held.getItem()).place(new net.minecraft.item.ItemPlacementContext(
				world, player, Hand.MAIN_HAND, held, new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos),
						net.minecraft.util.math.Direction.UP, pos, false)));
		CropCrateBlockEntity again = crate(world, pos);
		LOG.info("[EW-GAMETEST] caja puesta otra vez ({}): lleva {}", placed, again.getStack(0));
		if (again.getStack(0).getItem() != Items.WHEAT || again.getStack(0).getCount() != 64) {
			throw new GameTestException("Al ponerla se vacía: " + again.getStack(0));
		}
		BlockState state = world.getBlockState(pos);
		List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, again);
		LOG.info("[EW-GAMETEST] caja rota suelta: {}", drops.stream().map(s -> s.getName().getString() + " " + CropCrateItem.contents(s)).toList());
		if (drops.size() != 1 || CropCrateItem.contents(drops.get(0)) == null || CropCrateItem.contents(drops.get(0)).count() != 64) {
			throw new GameTestException("Rota no suelta la caja con lo suyo: " + drops);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tradersBuyCropsOnlyInFullCrates(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<Deal> grocer = TraderType.GROCER.catalog(1, 1234L, 0L, world.getRegistryManager());
		List<Deal> spices = TraderType.SPICE_MERCHANT.catalog(1, 99L, 0L, world.getRegistryManager());
		Deal wheat = crateDeal(grocer, Items.WHEAT);
		Deal cane = crateDeal(spices, Items.SUGAR_CANE);
		LOG.info("[EW-GAMETEST] cajas: trigo {} monedas ({} al día), caña {} monedas ({} al día)", wheat.price(), wheat.stock(), cane.price(),
				cane.stock());
		for (Deal deal : grocer) {
			if (!deal.sell() && CropCrateItem.isCrop(deal.item())) {
				throw new GameTestException("El tendero aún compra " + deal.item() + " suelto");
			}
		}
		if (grocer.stream().noneMatch(d -> !d.sell() && d.prototype().isOf(Items.EGG))) {
			throw new GameTestException("Los huevos (no son cultivo) tienen que seguir comprándose sueltos");
		}
		if (wheat.count() != 1 || wheat.price() <= 0 || wheat.stock() < 2) {
			throw new GameTestException("Caja de trigo rara: " + wheat);
		}
		ItemStack full = CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.WHEAT);
		ItemStack half = new ItemStack(ModBlocks.CROP_CRATE_ITEM);
		half.set(com.evolvedwarfare.registry.ModComponents.CROP_CRATE, new CropCrateItem.Contents(Items.WHEAT, 32));
		if (!wheat.accepts(full) || wheat.accepts(half) || wheat.accepts(new ItemStack(Items.WHEAT, 64))
				|| wheat.accepts(CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.CARROT))) {
			throw new GameTestException("Acepta lo que no debe (solo cajas llenas de trigo)");
		}
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.getInventory().clear();
		player.getInventory().insertStack(full.copyWithCount(3));
		player.getInventory().insertStack(half);
		player.getInventory().insertStack(new ItemStack(Items.WHEAT, 64));
		int have = TraderScreenHandler.count(player.getInventory(), wheat);
		if (have != 3) {
			throw new GameTestException("Cuenta mal las cajas llenas: " + have);
		}
		ctx.complete();
	}

	/** Con una caja en la mano, la otra mano no sirve; y en la mano izquierda no se puede llevar (pasa al inventario). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void crateNeedsBothHands(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setStackInHand(Hand.MAIN_HAND, CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.CARROT));
		player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.BREAD, 4));
		net.minecraft.util.ActionResult off = net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker()
				.interact(player, world, Hand.OFF_HAND).getResult();
		net.minecraft.util.ActionResult main = net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker()
				.interact(player, world, Hand.MAIN_HAND).getResult();
		if (off != net.minecraft.util.ActionResult.FAIL || main == net.minecraft.util.ActionResult.FAIL) {
			throw new GameTestException("Con la caja, la otra mano se puede usar: " + off + " / " + main);
		}
		// una caja en la mano izquierda: al siguiente tic pasa al inventario
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		player.setStackInHand(Hand.OFF_HAND, CropCrateItem.filled(ModBlocks.CROP_CRATE_ITEM, Items.CARROT));
		ctx.waitAndRun(3, () -> {
			int crates = player.getInventory().count(ModBlocks.CROP_CRATE_ITEM);
			LOG.info("[EW-GAMETEST] dos manos: otra mano {} / mano principal {}; caja en la izquierda -> izquierda={}, cajas en el inventario={}",
					off, main, player.getOffHandStack(), crates);
			if (!player.getOffHandStack().isEmpty() || crates != 1) {
				throw new GameTestException("La caja se queda en la mano izquierda: " + player.getOffHandStack());
			}
			ctx.complete();
		});
	}

	private static Deal crateDeal(List<Deal> deals, Item crop) {
		for (Deal deal : deals) {
			CropCrateItem.Contents contents = CropCrateItem.contents(deal.prototype());
			if (!deal.sell() && contents != null && contents.item() == crop && contents.full()) {
				return deal;
			}
		}
		throw new GameTestException("No compra cajas de " + crop);
	}

	private static CropCrateBlockEntity crate(ServerWorld world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof CropCrateBlockEntity crate) {
			return crate;
		}
		throw new GameTestException("No hay caja en " + pos);
	}
}
