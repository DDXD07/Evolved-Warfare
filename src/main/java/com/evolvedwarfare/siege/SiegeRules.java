package com.evolvedwarfare.siege;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Reglas del mundo del asedio (se cambian con /gamerule):
 * <ul>
 * <li>{@code evolvedwarfareSiegeBlockDamage}: los proyectiles y el ariete rompen bloques (por defecto, sí).</li>
 * <li>{@code evolvedwarfareSiegeRaids}: los asaltos a pueblos y castillos pueden traer máquinas de asedio (sí).</li>
 * </ul>
 */
public final class SiegeRules {
	public static final GameRules.Key<GameRules.BooleanRule> BLOCK_DAMAGE = GameRuleRegistry.register(
			"evolvedwarfareSiegeBlockDamage", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));
	public static final GameRules.Key<GameRules.BooleanRule> SIEGE_RAIDS = GameRuleRegistry.register(
			"evolvedwarfareSiegeRaids", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));

	private SiegeRules() {
	}

	public static void init() {
	}

	public static boolean blockDamage(World world) {
		return world.getGameRules().getBoolean(BLOCK_DAMAGE);
	}

	public static boolean siegeRaids(World world) {
		return world.getGameRules().getBoolean(SIEGE_RAIDS);
	}
}
