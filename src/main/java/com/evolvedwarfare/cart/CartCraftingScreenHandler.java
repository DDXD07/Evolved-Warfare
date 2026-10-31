package com.evolvedwarfare.cart;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.BlockPos;

/** La mesa de crafteo de la carreta: la de vanilla, pero vale mientras estés cerca de la carreta (no de un bloque). */
public class CartCraftingScreenHandler extends CraftingScreenHandler {
	private final CartEntity cart;

	public CartCraftingScreenHandler(int syncId, PlayerInventory playerInventory, CartEntity cart) {
		super(syncId, playerInventory, ScreenHandlerContext.create(cart.getWorld(), BlockPos.ofFloored(cart.getPos())));
		this.cart = cart;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return cart.isAlive() && player.squaredDistanceTo(cart) <= 64.0;
	}
}
