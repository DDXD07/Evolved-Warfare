package com.evolvedwarfare.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Formatting;

/**
 * Lo de un pez pescado con el minijuego: su peso (gramos) y su calidad (1 bronce, 2 plata, 3 oro, 4 platino). Los peces
 * con calidad solo se venden en el Buzón de ventas, y pagan según su peso y su calidad.
 */
public record FishData(int grams, int quality) {
	public static final int BRONZE = 1;
	public static final int SILVER = 2;
	public static final int GOLD = 3;
	public static final int PLATINUM = 4;

	public static final Codec<FishData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("grams").forGetter(FishData::grams),
			Codec.INT.fieldOf("quality").forGetter(FishData::quality)).apply(i, FishData::new));
	public static final PacketCodec<ByteBuf, FishData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, FishData::grams, PacketCodecs.VAR_INT, FishData::quality, FishData::new);

	public double kg() {
		return grams / 1000.0;
	}

	/** Lo que multiplica el precio cada calidad. */
	public double priceFactor() {
		return switch (quality) {
			case SILVER -> 1.25;
			case GOLD -> 1.6;
			case PLATINUM -> 2.2;
			default -> 1.0;
		};
	}

	public static String key(int quality) {
		return switch (quality) {
			case SILVER -> "silver";
			case GOLD -> "gold";
			case PLATINUM -> "platinum";
			default -> "bronze";
		};
	}

	public static int color(int quality) {
		return switch (quality) {
			case SILVER -> 0xD8DCE4;
			case GOLD -> 0xF4C430;
			case PLATINUM -> 0x9AF0F0;
			default -> 0xCD7F32;
		};
	}

	public static Formatting formatting(int quality) {
		return switch (quality) {
			case SILVER -> Formatting.GRAY;
			case GOLD -> Formatting.GOLD;
			case PLATINUM -> Formatting.AQUA;
			default -> Formatting.RED;
		};
	}
}
