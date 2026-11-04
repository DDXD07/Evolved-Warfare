package com.evolvedwarfare.wildlife;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.Heightmap;

/** Los tipos de entidad de las 20 especies, sus atributos, dónde y cómo aparecen. */
public final class Wildlife {
	private static final Map<WildSpecies, EntityType<WildAnimalEntity>> TYPES = new EnumMap<>(WildSpecies.class);
	private static final Map<EntityType<?>, WildSpecies> SPECIES = new IdentityHashMap<>();

	static {
		for (WildSpecies s : WildSpecies.values()) {
			EntityType<WildAnimalEntity> type = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(s.id()),
					EntityType.Builder.create(WildAnimalEntity::new, SpawnGroup.CREATURE)
							.dimensions(s.width, s.height)
							.eyeHeight(s.height * 0.85F)
							.maxTrackingRange(10)
							.build(s.id()));
			TYPES.put(s, type);
			SPECIES.put(type, s);
		}
	}

	private Wildlife() {
	}

	public static EntityType<WildAnimalEntity> type(WildSpecies s) {
		return TYPES.get(s);
	}

	public static WildSpecies of(EntityType<?> type) {
		WildSpecies s = SPECIES.get(type);
		return s != null ? s : WildSpecies.RED_DEER;
	}

	public static boolean isWild(EntityType<?> type) {
		return SPECIES.containsKey(type);
	}

	/** Los tipos y sus atributos (desde ModEntities.init). */
	public static void registerTypes() {
		for (WildSpecies s : WildSpecies.values()) {
			FabricDefaultAttributeRegistry.register(TYPES.get(s), attributes(s));
		}
	}

	private static DefaultAttributeContainer.Builder attributes(WildSpecies s) {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, s.health)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, s.speed)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Math.max(1.0, s.damage))
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, s.health >= 40 ? 0.6 : 0.0);
	}

	/** Dónde aparecen (al generarse el mundo y, poco a poco, después), sobre hierba, tierra, arena, nieve o piedra con luz. */
	public static void registerSpawns() {
		for (WildSpecies s : WildSpecies.values()) {
			EntityType<WildAnimalEntity> type = TYPES.get(s);
			SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
					(t, world, reason, pos, random) -> {
						BlockState below = world.getBlockState(pos.down());
						boolean ground = below.isIn(BlockTags.ANIMALS_SPAWNABLE_ON) || below.isIn(BlockTags.SAND) || below.isIn(BlockTags.DIRT)
								|| below.isIn(BlockTags.SNOW) || below.isIn(BlockTags.BASE_STONE_OVERWORLD) || below.isIn(BlockTags.TERRACOTTA);
						return ground && world.getBaseLightLevel(pos, 0) > 8;
					});
			if (!s.biomes.isEmpty()) {
				BiomeModifications.addSpawn(BiomeSelectors.includeByKey(s.biomes), SpawnGroup.CREATURE, type, s.weight, s.minGroup, s.maxGroup);
			}
		}
	}
}
