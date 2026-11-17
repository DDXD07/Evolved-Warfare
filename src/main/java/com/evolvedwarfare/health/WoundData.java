package com.evolvedwarfare.health;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Las heridas de un jugador (fase 92): la vida de cada parte, qué miembros están rotos, cuáles entablillados (y cuánto les
 * falta para soldar, en segundos) y cuánto le queda de sangrar (segundos). Es inmutable: se cambia con copias.
 */
public record WoundData(List<Float> points, int broken, List<Integer> splint, int bleeding, int sinceHit) {
	public static final Codec<WoundData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.listOf().fieldOf("points").forGetter(WoundData::points),
			Codec.INT.fieldOf("broken").forGetter(WoundData::broken),
			Codec.INT.listOf().fieldOf("splint").forGetter(WoundData::splint),
			Codec.INT.fieldOf("bleeding").forGetter(WoundData::bleeding),
			Codec.INT.optionalFieldOf("since_hit", 0).forGetter(WoundData::sinceHit)).apply(i, WoundData::new));

	public static WoundData healthy() {
		List<Float> p = new ArrayList<>();
		List<Integer> s = new ArrayList<>();
		for (BodyPart part : BodyPart.values()) {
			p.add(part.max);
			s.add(0);
		}
		return new WoundData(p, 0, s, 0, 0);
	}

	public float get(BodyPart part) {
		return part.ordinal() < points.size() ? points.get(part.ordinal()) : part.max;
	}

	public float fraction(BodyPart part) {
		return get(part) / part.max;
	}

	public boolean isBroken(BodyPart part) {
		return (broken & part.bit()) != 0;
	}

	public int splintLeft(BodyPart part) {
		return part.ordinal() < splint.size() ? splint.get(part.ordinal()) : 0;
	}

	public boolean isSplinted(BodyPart part) {
		return splintLeft(part) > 0;
	}

	public boolean isHurt() {
		for (BodyPart part : BodyPart.values()) {
			if (get(part) < part.max - 0.01F) {
				return true;
			}
		}
		return broken != 0 || bleeding > 0;
	}

	/** Una copia que se puede cambiar. */
	public Mutable edit() {
		return new Mutable(this);
	}

	public static final class Mutable {
		final float[] points = new float[BodyPart.values().length];
		int broken;
		final int[] splint = new int[BodyPart.values().length];
		int bleeding;
		int sinceHit;

		Mutable(WoundData d) {
			for (BodyPart part : BodyPart.values()) {
				points[part.ordinal()] = d.get(part);
				splint[part.ordinal()] = d.splintLeft(part);
			}
			broken = d.broken;
			bleeding = d.bleeding;
			sinceHit = d.sinceHit;
		}

		public WoundData done() {
			List<Float> p = new ArrayList<>();
			List<Integer> s = new ArrayList<>();
			for (int k = 0; k < points.length; k++) {
				p.add(points[k]);
				s.add(splint[k]);
			}
			return new WoundData(p, broken, s, bleeding, sinceHit);
		}
	}
}
