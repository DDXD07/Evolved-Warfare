package com.evolvedwarfare.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Lo que hay en un crisol: de qué metal ({@code ""} si está vacío), cuántos lingotes aún sin fundir, cuánto metal fundido
 * (en lingotes) y, si está fuera del fuego, el tiempo del mundo en que se enfría (0 si no se está enfriando).
 */
public record CrucibleContents(String metal, int solid, float molten, long coolAt) {
	public static final CrucibleContents EMPTY = new CrucibleContents("", 0, 0.0F, 0L);
	public static final Codec<CrucibleContents> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("metal").forGetter(CrucibleContents::metal),
			Codec.INT.fieldOf("solid").forGetter(CrucibleContents::solid),
			Codec.FLOAT.fieldOf("molten").forGetter(CrucibleContents::molten),
			Codec.LONG.optionalFieldOf("cool_at", 0L).forGetter(CrucibleContents::coolAt)).apply(i, CrucibleContents::new));
	public static final PacketCodec<ByteBuf, CrucibleContents> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.STRING, CrucibleContents::metal,
			PacketCodecs.VAR_INT, CrucibleContents::solid, PacketCodecs.FLOAT, CrucibleContents::molten, PacketCodecs.VAR_LONG,
			CrucibleContents::coolAt, CrucibleContents::new);

	public boolean isEmpty() {
		return solid <= 0 && molten < 0.01F;
	}

	public ForgeMaterial material() {
		return ForgeMaterial.byId(metal);
	}
}
