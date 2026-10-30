package com.evolvedwarfare;

import com.evolvedwarfare.skill.Perk;
import java.util.function.Consumer;
import java.util.function.ToIntBiFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/** Ganchos que rellena el cliente (para abrir pantallas sin que el código común cargue clases del cliente). */
public final class ClientBridge {
	/** Abre la pantalla del escáner de planos (en el cliente). */
	public static Consumer<Hand> openScanner = hand -> {
	};
	/** El rango de una ventaja en el cliente (solo se sabe el del jugador propio; los demás, 0). */
	public static ToIntBiFunction<PlayerEntity, Perk> clientPerkRank = (player, perk) -> 0;

	private ClientBridge() {
	}
}
