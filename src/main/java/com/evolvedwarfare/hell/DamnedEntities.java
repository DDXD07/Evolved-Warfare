package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.Heightmap;

/**
 * Las criaturas del Nether bíblico (fase 2): langostas del Abismo (y las mayores), serpientes de fuego, cabras del
 * desierto, caídos, nephilim, espíritus de los condenados y estatuas de sal; sus sonidos, huevos y dónde pueden aparecer.
 */
public final class DamnedEntities {
	public static final List<Item> EGGS = new ArrayList<>();
	private static final Map<String, SoundEvent[]> SOUNDS = new HashMap<>();

	public static final EntityType<AbyssLocustEntity> ABYSS_LOCUST = type("abyss_locust",
			EntityType.Builder.create(AbyssLocustEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 0.5F).eyeHeight(0.3F).makeFireImmune()
					.maxTrackingRange(8), 0x34363C, 0xE02814);
	public static final EntityType<AbyssLocustEntity> GREATER_LOCUST = type("greater_locust",
			EntityType.Builder.create(AbyssLocustEntity::new, SpawnGroup.MONSTER).dimensions(1.1F, 0.9F).eyeHeight(0.55F).makeFireImmune()
					.maxTrackingRange(8), 0x202024, 0xFF5020);
	public static final EntityType<FireSerpentEntity> FIRE_SERPENT = type("fire_serpent",
			EntityType.Builder.create(FireSerpentEntity::new, SpawnGroup.MONSTER).dimensions(0.7F, 0.5F).eyeHeight(0.3F).makeFireImmune()
					.maxTrackingRange(8), 0x2C1612, 0xFF7A1E);
	public static final EntityType<DesertGoatEntity> DESERT_GOAT = type("desert_goat",
			EntityType.Builder.create(DesertGoatEntity::new, SpawnGroup.MONSTER).dimensions(0.9F, 1.3F).eyeHeight(1.1F).makeFireImmune()
					.maxTrackingRange(10), 0x96785C, 0xE2C428);
	public static final EntityType<FallenEntity> FALLEN = type("fallen",
			EntityType.Builder.create(FallenEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.1F).eyeHeight(1.85F).makeFireImmune()
					.maxTrackingRange(12), 0x221C20, 0xE6E6FF);
	public static final EntityType<NephilimEntity> NEPHILIM = type("nephilim",
			EntityType.Builder.create(NephilimEntity::new, SpawnGroup.MONSTER).dimensions(1.8F, 3.8F).eyeHeight(3.3F).makeFireImmune()
					.maxTrackingRange(12), 0x54504E, 0x6E281E);
	public static final EntityType<DamnedSpiritEntity> DAMNED_SPIRIT = type("damned_spirit",
			EntityType.Builder.create(DamnedSpiritEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 1.8F).eyeHeight(1.5F).makeFireImmune()
					.maxTrackingRange(8), 0x96BED2, 0x5A5A60);
	public static final EntityType<SaltStatueEntity> SALT_STATUE = type("salt_statue",
			EntityType.Builder.create(SaltStatueEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 1.95F).eyeHeight(1.7F).makeFireImmune()
					.maxTrackingRange(10), 0xE8E4DC, 0xC8C4BA);

	private DamnedEntities() {
	}

	private static <T extends HostileEntity> EntityType<T> type(String id, EntityType.Builder<T> builder, int a, int b) {
		EntityType<T> type = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(id), builder.build(id));
		EGGS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(id + "_spawn_egg"), new SpawnEggItem(type, a, b, new Item.Settings())));
		String snd = id.equals("greater_locust") ? "abyss_locust" : id;
		SOUNDS.computeIfAbsent(snd, k -> new SoundEvent[] {sound("entity." + k + ".ambient"), sound("entity." + k + ".hurt"),
				sound("entity." + k + ".death")});
		return type;
	}

	private static SoundEvent sound(String name) {
		var id = EvolvedWarfare.id(name);
		return Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	/** 0 de ambiente, 1 de daño, 2 de muerte. */
	public static SoundEvent sound(EntityType<?> type, int kind) {
		String id = EntityType.getId(type).getPath();
		SoundEvent[] s = SOUNDS.get(id.equals("greater_locust") ? "abyss_locust" : id);
		return s == null ? null : s[kind];
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(ABYSS_LOCUST, AbyssLocustEntity.createAttributes(false));
		FabricDefaultAttributeRegistry.register(GREATER_LOCUST, AbyssLocustEntity.createAttributes(true));
		FabricDefaultAttributeRegistry.register(FIRE_SERPENT, FireSerpentEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DESERT_GOAT, DesertGoatEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FALLEN, FallenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(NEPHILIM, NephilimEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DAMNED_SPIRIT, DamnedSpiritEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SALT_STATUE, SaltStatueEntity.createAttributes());
		for (EntityType<? extends HostileEntity> t : List.<EntityType<? extends HostileEntity>>of(ABYSS_LOCUST, GREATER_LOCUST, DAMNED_SPIRIT)) {
			register(t, true);
		}
		for (EntityType<? extends HostileEntity> t : List.<EntityType<? extends HostileEntity>>of(FIRE_SERPENT, DESERT_GOAT, FALLEN, NEPHILIM, SALT_STATUE)) {
			register(t, false);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends HostileEntity> void register(EntityType<T> type, boolean air) {
		SpawnRestriction.register(type, air ? SpawnLocationTypes.UNRESTRICTED : SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				HostileEntity::canSpawnIgnoreLightLevel);
	}
}
