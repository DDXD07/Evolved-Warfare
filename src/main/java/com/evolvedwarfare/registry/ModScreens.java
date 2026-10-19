package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.screen.NpcScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreens {
	/** Menú de un NPC. Al abrirse se envía al cliente el id de la entidad. */
	public static final ScreenHandlerType<NpcScreenHandler> NPC = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("npc"),
			new ExtendedScreenHandlerType<>(NpcScreenHandler::new, PacketCodecs.VAR_INT));

	private ModScreens() {
	}

	public static void init() {
	}
}
