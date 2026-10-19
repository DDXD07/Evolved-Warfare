package com.evolvedwarfare.entity;

/** Órdenes básicas de un NPC. */
public enum NpcOrder {
	/** Va detrás de su dueño (y se teletransporta si se queda muy lejos). */
	FOLLOW,
	/** Se queda quieto donde está. */
	STAY,
	/** Vuelve a su casa y se queda por los alrededores. */
	HOME;

	public String translationKey() {
		return "order.evolvedwarfare." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static NpcOrder byName(String name) {
		for (NpcOrder order : values()) {
			if (order.name().equals(name)) {
				return order;
			}
		}
		return FOLLOW;
	}
}
