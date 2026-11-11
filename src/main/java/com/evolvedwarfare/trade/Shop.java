package com.evolvedwarfare.trade;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Quien tiene tienda (los comerciantes de las ciudades y los aldeanos tenderos): lo que le queda hoy de cada línea de su
 * catálogo, gastarlo, y qué hacer después de cada trato.
 */
public interface Shop {
	int stockLeft(int index, Deal deal);

	void consumeStock(int index, int lots);

	void onTrade(PlayerEntity player);

	/** El que atiende (para saber si sigue vivo y cerca, y quién es su cliente). */
	MerchantEntity merchant();
}
