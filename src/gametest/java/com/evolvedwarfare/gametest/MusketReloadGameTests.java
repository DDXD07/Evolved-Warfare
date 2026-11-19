package com.evolvedwarfare.gametest;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.MusketItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La recarga del mosquete (fase 117): 7 segundos, y mientras dura se va a media velocidad y sin correr. */
public class MusketReloadGameTests implements FabricGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ew-gametest");

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void reloadIsSlowAndSlowsYou(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		ItemStack musket = new ItemStack(ModItems.MUSKET);
		player.setStackInHand(Hand.MAIN_HAND, musket);
		player.getInventory().insertStack(new ItemStack(ModItems.MUSKET_BALL, 4));
		int slot = player.getInventory().selectedSlot;
		double base = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		int ticks = MusketItem.reloadTicks(player);
		musket.set(ModComponents.MUSKET_RELOAD, world.getTime());
		player.setSprinting(true);
		musket.getItem().inventoryTick(musket, world, player, slot, true);
		double during = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		boolean sprint = player.isSprinting();
		// a mitad aún no está cargado
		musket.set(ModComponents.MUSKET_RELOAD, world.getTime() - ticks / 2);
		musket.getItem().inventoryTick(musket, world, player, slot, true);
		boolean halfLoaded = MusketItem.isLoaded(musket);
		// al acabar, cargado y a velocidad normal
		musket.set(ModComponents.MUSKET_RELOAD, world.getTime() - ticks);
		musket.getItem().inventoryTick(musket, world, player, slot, true);
		double after = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		boolean loaded = MusketItem.isLoaded(musket);
		// si cambia de objeto a mitad, también vuelve a su velocidad
		musket.remove(ModComponents.MUSKET_LOADED);
		musket.set(ModComponents.MUSKET_RELOAD, world.getTime());
		musket.getItem().inventoryTick(musket, world, player, slot, true);
		musket.getItem().inventoryTick(musket, world, player, slot, false);
		double switched = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		LOG.info("[EW-GAMETEST] mosquete: recarga {} tics, velocidad {} -> {} recargando (corre {}) -> {} al acabar, {} al cambiar; a mitad cargado {}, al final {}",
				ticks, base, during, sprint, after, switched, halfLoaded, loaded);
		if (ticks != MusketItem.RELOAD_TICKS || ticks != 140) {
			throw new GameTestException("La recarga no dura 7 segundos: " + ticks);
		}
		if (Math.abs(during - base * 0.5) > 1.0E-6 || sprint) {
			throw new GameTestException("Recargando no va a media velocidad o sigue corriendo: " + base + " -> " + during);
		}
		if (halfLoaded || !loaded) {
			throw new GameTestException("No carga cuando debe: a mitad " + halfLoaded + ", al final " + loaded);
		}
		if (Math.abs(after - base) > 1.0E-6 || Math.abs(switched - base) > 1.0E-6) {
			throw new GameTestException("Se queda lento: " + after + " / " + switched);
		}
		ctx.complete();
	}
}
