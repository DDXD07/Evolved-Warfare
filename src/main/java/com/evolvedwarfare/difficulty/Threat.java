package com.evolvedwarfare.difficulty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Lo que se le ha hecho a un monstruo al aparecer (fase 95): su nivel de peligro y, si es élite, sus rasgos. */
public record Threat(int level, List<String> affixes) {
	public static final Codec<Threat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("level").forGetter(Threat::level),
			Codec.STRING.listOf().optionalFieldOf("affixes", List.of()).forGetter(Threat::affixes))
			.apply(i, Threat::new));

	public boolean elite() {
		return !affixes.isEmpty();
	}

	public boolean has(Elites.Affix affix) {
		return affixes.contains(affix.id());
	}
}
