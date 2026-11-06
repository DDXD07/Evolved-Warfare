package com.evolvedwarfare.client.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.smithing.ForgedPart;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

/** La silueta de cada pieza (16x16), sacada de su dibujo: lo que hay que tallar en el molde. */
public final class PartMasks {
	private static final Map<ForgedPart, boolean[][]> CACHE = new EnumMap<>(ForgedPart.class);

	private PartMasks() {
	}

	/** [x][y]: true donde está la pieza. */
	public static boolean[][] of(ForgedPart part) {
		return CACHE.computeIfAbsent(part, p -> {
			boolean[][] mask = new boolean[16][16];
			try (InputStream in = MinecraftClient.getInstance().getResourceManager()
					.open(EvolvedWarfare.id("textures/item/part/" + p.id + ".png")); NativeImage img = NativeImage.read(in)) {
				for (int y = 0; y < 16 && y < img.getHeight(); y++) {
					for (int x = 0; x < 16 && x < img.getWidth(); x++) {
						mask[x][y] = ((img.getColor(x, y) >>> 24) & 255) > 40;
					}
				}
			} catch (Exception e) {
				for (int i = 3; i < 13; i++) {
					mask[i][i] = true;
					mask[i][i - 1] = true;
				}
			}
			return mask;
		});
	}

	public static int count(boolean[][] mask) {
		int n = 0;
		for (boolean[] col : mask) {
			for (boolean b : col) {
				n += b ? 1 : 0;
			}
		}
		return n;
	}

	public static void clear() {
		CACHE.clear();
	}
}
