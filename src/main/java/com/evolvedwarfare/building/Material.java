package com.evolvedwarfare.building;

/**
 * Materiales de obra. Cada bloque de un edificio gasta una unidad de su material, sea cual sea la variante: las
 * escaleras, losas, vallas, puertas o barriles de madera gastan un tablón (de cualquier madera); los troncos, un
 * tronco; los ladrillos y escaleras de piedra, un bloque de piedra; etc. La madera y la piedra que se ven en el
 * edificio las decide la semilla, no lo que pagues.
 */
public enum Material {
	PLANKS,
	LOGS,
	STONE,
	GLASS,
	TORCH,
	WOOL,
	HAY,
	/** No cuesta nada (tierra de los cimientos o de los bancales, la mitad de arriba de una puerta). */
	FREE;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
