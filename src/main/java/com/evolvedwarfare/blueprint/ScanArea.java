package com.evolvedwarfare.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Las dos esquinas que ha marcado el escáner (la siguiente que se marca es la que toque). */
public record ScanArea(Optional<BlockPos> first, Optional<BlockPos> second) {
	public static final ScanArea EMPTY = new ScanArea(Optional.empty(), Optional.empty());
	public static final Codec<ScanArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.optionalFieldOf("first").forGetter(ScanArea::first),
			BlockPos.CODEC.optionalFieldOf("second").forGetter(ScanArea::second)
	).apply(instance, ScanArea::new));
	public static final PacketCodec<ByteBuf, ScanArea> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.optional(BlockPos.PACKET_CODEC), ScanArea::first,
			PacketCodecs.optional(BlockPos.PACKET_CODEC), ScanArea::second,
			ScanArea::new);

	public boolean complete() {
		return first.isPresent() && second.isPresent();
	}

	@Nullable
	public BlockBox box() {
		return complete() ? BlockBox.create(first.get(), second.get()) : null;
	}

	/** Marca una esquina: la primera si no hay ninguna o ya estaban las dos; si no, la segunda. */
	public ScanArea mark(BlockPos pos) {
		if (first.isEmpty() || complete()) {
			return new ScanArea(Optional.of(pos.toImmutable()), Optional.empty());
		}
		return new ScanArea(first, Optional.of(pos.toImmutable()));
	}
}
