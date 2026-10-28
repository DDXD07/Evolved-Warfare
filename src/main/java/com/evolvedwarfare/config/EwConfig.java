package com.evolvedwarfare.config;

import com.evolvedwarfare.EvolvedWarfare;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Configuración del mod ({@code config/evolvedwarfare.json}). Se lee al arrancar; se puede cambiar en el juego con
 * {@code /evolvedwarfare config} (o, en un solo jugador con Mod Menu, desde su pantalla) y se guarda al momento.
 * Los valores fuera de rango se recortan a lo permitido.
 */
public final class EwConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static EwConfig instance = new EwConfig();

	// ------------------------------------------------------------------ investigación
	/** Hay que investigar las armas, las máquinas y las fortificaciones antes de fabricarlas. */
	public boolean researchRequired = true;
	/** Velocidad de la investigación (1 = normal; 2 = el doble de rápido). */
	public double researchSpeed = 1.0;

	// ------------------------------------------------------------------ facciones
	/** Cada cuánto (minutos reales, al azar entre los dos) pasa una patrulla cerca de un jugador. */
	public int patrolMinMinutes = 10;
	public int patrolMaxMinutes = 20;
	/** Primer asalto posible y tiempo entre asaltos (minutos reales). */
	public int firstRaidMinutes = 40;
	public int raidMinMinutes = 60;
	public int raidMaxMinutes = 100;
	/** Probabilidad de que, llegado el momento, haya asalto si hay un sitio que asaltar. */
	public double raidChance = 0.34;
	/** Probabilidad de que un asalto a un pueblo o castillo traiga máquinas de asedio (fácil, normal, difícil). */
	public double siegeChanceEasy = 0.30;
	public double siegeChanceNormal = 0.45;
	public double siegeChanceHard = 0.60;
	/** Vida y daño de vikingos, piratas y bandidos (1 = normal). */
	public double factionHealth = 1.0;
	public double factionDamage = 1.0;

	// ------------------------------------------------------------------ asentamientos
	/** Vida y daño extra por cada nivel del asentamiento por encima del 1. */
	public double settlementBonusPerLevel = 0.10;
	/** Bloques de distancia al punto de aparición por cada nivel. */
	public int settlementBlocksPerLevel = 750;
	public int settlementMaxLevel = 10;

	// ------------------------------------------------------------------ tus NPCs
	/** Días seguidos sin cobrar antes de que un NPC tuyo deserte. */
	public int maxUnpaidDays = 3;
	/** Vida y daño de tus soldados (1 = normal; se suman las mejoras investigadas). */
	public double soldierHealth = 1.0;
	public double soldierDamage = 1.0;

	// ------------------------------------------------------------------ asedio
	/** Daño de los proyectiles de las máquinas a las criaturas. */
	public double siegeDamage = 1.0;
	/** Fuerza contra los bloques (piedras y ariete): más alto, rompen bloques más duros. */
	public double siegeBlockPower = 1.0;
	/** Vida de las máquinas. */
	public double siegeEngineHealth = 1.0;
	/** Tiempo de recarga de las máquinas (menos = disparan más a menudo). */
	public double siegeReload = 1.0;

	// ------------------------------------------------------------------ armas
	/** Daño de la daga al atacar por la espalda (x). */
	public double daggerBackstab = 1.5;
	/** Daño de la alabarda contra jinetes y monturas (x). */
	public double halberdVsMounted = 1.5;
	/** Parte de la armadura del enemigo que el martillo de guerra convierte en daño extra. */
	public double warhammerArmorPierce = 0.35;
	/** Probabilidad de que el mangual aturda. */
	public double flailStunChance = 0.25;
	/** Fuerza del arco largo (velocidad y daño de la flecha, x). */
	public double longbowPower = 1.25;

	/** Una opción: nombre del campo, categoría y límites (para el comando y la pantalla). */
	public record Option(String key, String category, double min, double max, double step) {
		public Field field() {
			try {
				return EwConfig.class.getField(key);
			} catch (NoSuchFieldException e) {
				throw new IllegalStateException(key, e);
			}
		}

		public boolean isBoolean() {
			return field().getType() == boolean.class;
		}

		public boolean isInteger() {
			return field().getType() == int.class;
		}

		public String translationKey() {
			return "config.evolvedwarfare." + key;
		}
	}

	public static final List<Option> OPTIONS = List.of(
			new Option("researchRequired", "research", 0, 1, 1),
			new Option("researchSpeed", "research", 0.1, 20.0, 0.25),
			new Option("patrolMinMinutes", "factions", 1, 240, 1),
			new Option("patrolMaxMinutes", "factions", 1, 240, 1),
			new Option("firstRaidMinutes", "factions", 1, 600, 5),
			new Option("raidMinMinutes", "factions", 5, 1440, 5),
			new Option("raidMaxMinutes", "factions", 5, 1440, 5),
			new Option("raidChance", "factions", 0.0, 1.0, 0.05),
			new Option("siegeChanceEasy", "factions", 0.0, 1.0, 0.05),
			new Option("siegeChanceNormal", "factions", 0.0, 1.0, 0.05),
			new Option("siegeChanceHard", "factions", 0.0, 1.0, 0.05),
			new Option("factionHealth", "factions", 0.1, 10.0, 0.1),
			new Option("factionDamage", "factions", 0.1, 10.0, 0.1),
			new Option("settlementBonusPerLevel", "settlements", 0.0, 1.0, 0.01),
			new Option("settlementBlocksPerLevel", "settlements", 100, 100000, 50),
			new Option("settlementMaxLevel", "settlements", 1, 10, 1),
			new Option("maxUnpaidDays", "npcs", 1, 30, 1),
			new Option("soldierHealth", "npcs", 0.1, 10.0, 0.1),
			new Option("soldierDamage", "npcs", 0.1, 10.0, 0.1),
			new Option("siegeDamage", "siege", 0.0, 10.0, 0.1),
			new Option("siegeBlockPower", "siege", 0.0, 10.0, 0.1),
			new Option("siegeEngineHealth", "siege", 0.1, 10.0, 0.1),
			new Option("siegeReload", "siege", 0.1, 10.0, 0.1),
			new Option("daggerBackstab", "weapons", 1.0, 5.0, 0.1),
			new Option("halberdVsMounted", "weapons", 1.0, 5.0, 0.1),
			new Option("warhammerArmorPierce", "weapons", 0.0, 2.0, 0.05),
			new Option("flailStunChance", "weapons", 0.0, 1.0, 0.05),
			new Option("longbowPower", "weapons", 0.5, 3.0, 0.05));

	public static EwConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("evolvedwarfare.json");
	}

	@Nullable
	public static Option option(String key) {
		for (Option option : OPTIONS) {
			if (option.key().equalsIgnoreCase(key)) {
				return option;
			}
		}
		return null;
	}

	/** Lee el archivo (si no existe, lo crea con los valores de fábrica). */
	public static synchronized void load() {
		Path path = file();
		EwConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, EwConfig.class);
			} catch (IOException | JsonParseException e) {
				EvolvedWarfare.LOGGER.error("Configuración ilegible ({}); se usan los valores de fábrica", path, e);
			}
		}
		instance = loaded != null ? loaded : new EwConfig();
		instance.clampAll();
		save();
	}

	public static synchronized void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			EvolvedWarfare.LOGGER.error("No se pudo guardar la configuración en {}", path, e);
		}
	}

	/** Vuelve a los valores de fábrica. */
	public static synchronized void reset() {
		instance = new EwConfig();
		save();
	}

	private void clampAll() {
		for (Option option : OPTIONS) {
			set(option, value(option));
		}
		if (patrolMaxMinutes < patrolMinMinutes) {
			patrolMaxMinutes = patrolMinMinutes;
		}
		if (raidMaxMinutes < raidMinMinutes) {
			raidMaxMinutes = raidMinMinutes;
		}
	}

	/** El valor actual de una opción, como número (los booleanos: 0 o 1). */
	public double value(Option option) {
		try {
			Field field = option.field();
			if (field.getType() == boolean.class) {
				return field.getBoolean(this) ? 1.0 : 0.0;
			}
			if (field.getType() == int.class) {
				return field.getInt(this);
			}
			return field.getDouble(this);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Cambia una opción (recortada a sus límites). */
	public void set(Option option, double value) {
		double clamped = Math.max(option.min(), Math.min(option.max(), value));
		try {
			Field field = option.field();
			if (field.getType() == boolean.class) {
				field.setBoolean(this, clamped >= 0.5);
			} else if (field.getType() == int.class) {
				field.setInt(this, (int) Math.round(clamped));
			} else {
				field.setDouble(this, Math.round(clamped * 1000.0) / 1000.0);
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** El valor para mostrar ("sí"/"no" lo traduce quien lo muestra). */
	public String display(Option option) {
		double value = value(option);
		if (option.isBoolean()) {
			return value >= 0.5 ? "true" : "false";
		}
		if (option.isInteger()) {
			return Integer.toString((int) value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Interpreta lo que escribe alguien en el comando: true/false/sí/no o un número. */
	@Nullable
	public static Double parse(Option option, String text) {
		String t = text.trim().toLowerCase(Locale.ROOT);
		if (option.isBoolean()) {
			if (t.equals("true") || t.equals("si") || t.equals("sí") || t.equals("on") || t.equals("1")) {
				return 1.0;
			}
			if (t.equals("false") || t.equals("no") || t.equals("off") || t.equals("0")) {
				return 0.0;
			}
			return null;
		}
		try {
			return Double.parseDouble(t.replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static List<String> categories() {
		List<String> out = new ArrayList<>();
		for (Option option : OPTIONS) {
			if (!out.contains(option.category())) {
				out.add(option.category());
			}
		}
		return out;
	}
}
