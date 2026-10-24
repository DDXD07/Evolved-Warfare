package com.evolvedwarfare.client.blueprint;

import com.evolvedwarfare.blueprint.BlueprintLibrary;
import com.evolvedwarfare.network.BlueprintPayloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

/** Lo que el cliente sabe de la biblioteca de planos: la lista y los planos que ya ha pedido. */
public final class ClientBlueprints {
	public static List<BlueprintLibrary.Summary> entries = List.of();
	public static BlockPos table = BlockPos.ORIGIN;
	public static String player = "";
	public static boolean admin;
	public static int version;
	private static final Map<String, BlueprintPayloads.Data> DATA = new HashMap<>();

	private ClientBlueprints() {
	}

	public static void library(BlueprintPayloads.Library payload) {
		entries = payload.entries();
		table = payload.table();
		player = payload.player();
		admin = payload.admin();
		version++;
	}

	public static void data(BlueprintPayloads.Data payload) {
		DATA.put(payload.id(), payload);
		version++;
	}

	public static BlueprintPayloads.Data get(String id) {
		return DATA.get(id);
	}

	public static void forget(String id) {
		DATA.remove(id);
	}
}
