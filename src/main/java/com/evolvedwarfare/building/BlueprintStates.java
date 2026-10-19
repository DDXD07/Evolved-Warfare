package com.evolvedwarfare.building;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Convierte los bloques de un plano (texto) en estados de bloque de Minecraft y los gira según el puesto. */
public final class BlueprintStates {
	private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();

	private BlueprintStates() {
	}

	/** "minecraft:oak_stairs[facing=east,half=bottom]" → estado (las propiedades desconocidas se ignoran). */
	public static BlockState parse(String text) {
		return CACHE.computeIfAbsent(text, BlueprintStates::doParse);
	}

	private static BlockState doParse(String text) {
		int bracket = text.indexOf('[');
		String name = bracket < 0 ? text : text.substring(0, bracket);
		Block block = Registries.BLOCK.get(Identifier.of(name));
		BlockState state = block.getDefaultState();
		if (bracket >= 0 && text.endsWith("]")) {
			for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
				int eq = pair.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				Property<?> property = block.getStateManager().getProperty(pair.substring(0, eq));
				if (property != null) {
					state = with(state, property, pair.substring(eq + 1));
				}
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(v -> state.with(property, v)).orElse(state);
	}

	/** Los planos están hechos con el puesto mirando al norte; esto es el giro para que miren a donde mira el puesto. */
	public static BlockRotation rotationFor(Direction facing) {
		return switch (facing) {
			case EAST -> BlockRotation.CLOCKWISE_90;
			case SOUTH -> BlockRotation.CLOCKWISE_180;
			case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
			default -> BlockRotation.NONE;
		};
	}
}
