package com.evolvedwarfare.client.atlas;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.atlas.Atlas;
import com.evolvedwarfare.atlas.AtlasState;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

/**
 * Lo que sabe el cliente del Atlas: cómo se ven los chunks que ha explorado (pintados en teselas de 32 x 32 chunks, en
 * tinta sobre papel), las estructuras vistas, los territorios y dónde están los jugadores.
 */
public final class AtlasClient {
	public static final int TILE_CHUNKS = 32, TILE = TILE_CHUNKS * 16;
	static final int INK_R = 42, INK_G = 35, INK_B = 28, PAPER_R = 238, PAPER_G = 229, PAPER_B = 205;
	static final Long2ObjectMap<byte[]> CHUNKS = new Long2ObjectOpenHashMap<>();
	static final Long2ObjectMap<AtlasState.Marker> MARKERS = new Long2ObjectOpenHashMap<>();
	static List<Atlas.TerritoryView> territories = List.of();
	static final Long2ObjectMap<Atlas.TerritoryView> OWNER = new Long2ObjectOpenHashMap<>();
	static List<Atlas.PlayerDot> players = List.of();
	private static final Long2ObjectMap<Tile> TILES = new Long2ObjectOpenHashMap<>();
	private static int tileIds;

	static final class Tile {
		final NativeImage image = new NativeImage(NativeImage.Format.RGBA, TILE, TILE, true);
		final NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
		final Identifier id = EvolvedWarfare.id("atlas_tile_" + tileIds++);
		boolean dirty = true;

		Tile() {
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
		}
	}

	private AtlasClient() {
	}

	public static void register() {
		Atlas.openScreen = () -> MinecraftClient.getInstance().setScreen(new AtlasScreen());
		ClientPlayNetworking.registerGlobalReceiver(Atlas.ChunksPayload.ID, (p, ctx) -> {
			for (int i = 0; i < p.keys().length; i++) {
				CHUNKS.put(p.keys()[i], p.images()[i]);
				paint(p.keys()[i], p.images()[i]);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Atlas.MarkersPayload.ID, (p, ctx) -> {
			if (p.replace()) {
				MARKERS.clear();
			}
			for (AtlasState.Marker m : p.markers()) {
				MARKERS.put(m.key(), m);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Atlas.TerritoriesPayload.ID, (p, ctx) -> {
			territories = new ArrayList<>(p.territories());
			OWNER.clear();
			for (Atlas.TerritoryView t : territories) {
				for (long k : t.chunks()) {
					OWNER.put(k, t);
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Atlas.PlayersPayload.ID, (p, ctx) -> players = p.players());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AtlasClient::clear));
	}

	private static void clear() {
		CHUNKS.clear();
		MARKERS.clear();
		OWNER.clear();
		territories = List.of();
		players = List.of();
		for (Tile t : TILES.values()) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(t.id);
		}
		TILES.clear();
	}

	/** El tono de tinta (0 tinta, 255 papel) en color ABGR. */
	static int color(int shade) {
		int r = INK_R + (PAPER_R - INK_R) * shade / 255, g = INK_G + (PAPER_G - INK_G) * shade / 255, b = INK_B + (PAPER_B - INK_B) * shade / 255;
		return 0xFF000000 | b << 16 | g << 8 | r;
	}

	private static void paint(long key, byte[] img) {
		int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
		long tk = ChunkPos.toLong(Math.floorDiv(cx, TILE_CHUNKS), Math.floorDiv(cz, TILE_CHUNKS));
		Tile t = TILES.get(tk);
		if (t == null) {
			t = new Tile();
			TILES.put(tk, t);
		}
		int ox = Math.floorMod(cx, TILE_CHUNKS) * 16, oz = Math.floorMod(cz, TILE_CHUNKS) * 16;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				t.image.setColor(ox + x, oz + z, color(img[z * 16 + x] & 255));
			}
		}
		t.dirty = true;
	}

	/** La tesela (subida a la tarjeta si ha cambiado), o null si no hay nada explorado en ella. */
	static Identifier tile(int tx, int tz) {
		Tile t = TILES.get(ChunkPos.toLong(tx, tz));
		if (t == null) {
			return null;
		}
		if (t.dirty) {
			t.texture.upload();
			t.dirty = false;
		}
		return t.id;
	}

	static boolean explored(long chunk) {
		return CHUNKS.containsKey(chunk);
	}
}
