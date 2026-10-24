package com.evolvedwarfare.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** Lo que lleva un objeto Plano: qué plano de la biblioteca es y unos datos para mostrar. */
public record BlueprintRef(String id, String name, boolean builtin, int sizeX, int sizeY, int sizeZ, int blocks) {
	public static final Codec<BlueprintRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(BlueprintRef::id),
			Codec.STRING.fieldOf("name").forGetter(BlueprintRef::name),
			Codec.BOOL.optionalFieldOf("builtin", false).forGetter(BlueprintRef::builtin),
			Codec.INT.fieldOf("size_x").forGetter(BlueprintRef::sizeX),
			Codec.INT.fieldOf("size_y").forGetter(BlueprintRef::sizeY),
			Codec.INT.fieldOf("size_z").forGetter(BlueprintRef::sizeZ),
			Codec.INT.fieldOf("blocks").forGetter(BlueprintRef::blocks)
	).apply(instance, BlueprintRef::new));

	public static final PacketCodec<ByteBuf, BlueprintRef> PACKET_CODEC = PacketCodec.ofStatic(
			(buf, ref) -> {
				PacketCodecs.STRING.encode(buf, ref.id());
				PacketCodecs.STRING.encode(buf, ref.name());
				PacketCodecs.BOOL.encode(buf, ref.builtin());
				PacketCodecs.VAR_INT.encode(buf, ref.sizeX());
				PacketCodecs.VAR_INT.encode(buf, ref.sizeY());
				PacketCodecs.VAR_INT.encode(buf, ref.sizeZ());
				PacketCodecs.VAR_INT.encode(buf, ref.blocks());
			},
			buf -> new BlueprintRef(PacketCodecs.STRING.decode(buf), PacketCodecs.STRING.decode(buf), PacketCodecs.BOOL.decode(buf),
					PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf),
					PacketCodecs.VAR_INT.decode(buf)));

	public static BlueprintRef of(StoredBlueprint b) {
		return new BlueprintRef(b.id, b.name, b.builtin, b.sizeX, b.sizeY, b.sizeZ, b.blockCount());
	}
}
