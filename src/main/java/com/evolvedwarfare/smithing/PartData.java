package com.evolvedwarfare.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** Una pieza forjada: qué pieza es, de qué metal y con qué calidad (1 tosca, 2 común, 3 buena, 4 obra maestra). */
public record PartData(String part, String material, int quality) {
	public static final Codec<PartData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("part").forGetter(PartData::part),
			Codec.STRING.fieldOf("material").forGetter(PartData::material),
			Codec.INT.fieldOf("quality").forGetter(PartData::quality)).apply(i, PartData::new));
	public static final PacketCodec<ByteBuf, PartData> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.STRING, PartData::part,
			PacketCodecs.STRING, PartData::material, PacketCodecs.VAR_INT, PartData::quality, PartData::new);
}
