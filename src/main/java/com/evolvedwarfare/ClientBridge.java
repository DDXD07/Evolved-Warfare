package com.evolvedwarfare;

import java.util.function.Consumer;
import net.minecraft.util.Hand;

/** Ganchos que rellena el cliente (para abrir pantallas sin que el código común cargue clases del cliente). */
public final class ClientBridge {
	/** Abre la pantalla del escáner de planos (en el cliente). */
	public static Consumer<Hand> openScanner = hand -> {
	};

	private ClientBridge() {
	}
}
