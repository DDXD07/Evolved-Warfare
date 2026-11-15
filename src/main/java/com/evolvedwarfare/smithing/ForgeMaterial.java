package com.evolvedwarfare.smithing;

import org.jetbrains.annotations.Nullable;

/** Los metales que se forjan: hierro, oro (blando, más fácil), hierro con filo de diamante (más difícil) y acero. */
public enum ForgeMaterial {
	IRON("iron", 0xD8D8D8, 1.0F),
	GOLD("gold", 0xF5C542, 0.8F),
	DIAMOND("diamond", 0x5FE3D8, 1.3F),
	/** El acero (fase 77): sale del crisol con carbón y fuelle; da las herramientas y armas de hierro, mejoradas. */
	STEEL("steel", 0x9AAEC4, 1.15F);

	public final String id;
	public final int color;
	/** Lo rápido que va el martillo (más rápido = más difícil). */
	public final float speed;

	ForgeMaterial(String id, int color, float speed) {
		this.id = id;
		this.color = color;
		this.speed = speed;
	}

	@Nullable
	public static ForgeMaterial byId(String id) {
		for (ForgeMaterial m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}
}
