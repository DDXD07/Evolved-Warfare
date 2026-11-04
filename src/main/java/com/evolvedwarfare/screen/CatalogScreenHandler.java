package com.evolvedwarfare.screen;

import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.registry.ModScreens;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * El catálogo de un mercader: la misma compraventa que su tienda ({@link TraderScreenHandler}) con otra pantalla (el
 * libro). Lleva también dónde está el libro del mostrador, para la animación de acercarse y abrirlo.
 */
public class CatalogScreenHandler extends TraderScreenHandler {
	/** Lo que se manda al abrir: la tienda y el libro (si lo hay). */
	public record Opening(Data data, BlockPos book, boolean hasBook) {
		public static final PacketCodec<RegistryByteBuf, Opening> PACKET_CODEC = PacketCodec.tuple(
				Data.PACKET_CODEC, Opening::data,
				BlockPos.PACKET_CODEC, Opening::book,
				PacketCodecs.BOOL, Opening::hasBook,
				Opening::new);
	}

	private final Opening opening;

	/** Cliente. */
	public CatalogScreenHandler(int syncId, PlayerInventory playerInventory, Opening opening) {
		super(ModScreens.CATALOG, syncId, playerInventory, null, opening.data(), new ArrayPropertyDelegate(opening.data().deals().size()));
		this.opening = opening;
	}

	/** Servidor. */
	public CatalogScreenHandler(int syncId, PlayerInventory playerInventory, TraderEntity trader, Opening opening) {
		super(ModScreens.CATALOG, syncId, playerInventory, trader, opening.data(), stockOf(trader, opening.data()));
		this.opening = opening;
	}

	@Override
	public void onClosed(net.minecraft.entity.player.PlayerEntity player) {
		super.onClosed(player);
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			com.evolvedwarfare.trade.CatalogReaders.close(serverPlayer);
		}
	}

	/** El libro del mostrador, o null si se abrió hablando con el mercader en otro sitio. */
	@Nullable
	public BlockPos book() {
		return opening.hasBook() ? opening.book() : null;
	}
}
