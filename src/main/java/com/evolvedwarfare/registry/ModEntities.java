package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.PirateEntity;
import com.evolvedwarfare.entity.RecruitEntity;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.entity.VikingEntity;
import com.evolvedwarfare.entity.WorkerEntity;
import com.evolvedwarfare.siege.SiegeEngineEntity;
import com.evolvedwarfare.siege.SiegeKind;
import com.evolvedwarfare.siege.SiegeShotEntity;
import com.evolvedwarfare.weapon.JavelinEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	/** El recluta: NPC básico del que salen todas las demás clases (soldados, trabajadores...). */
	public static final EntityType<RecruitEntity> RECRUIT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("recruit"),
			EntityType.Builder.create(RecruitEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("recruit"));

	/** Soldado del jugador (guerrero, bárbaro, arquero, ballestero o médico). */
	public static final EntityType<SoldierEntity> SOLDIER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("soldier"),
			EntityType.Builder.create(SoldierEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("soldier"));

	/** Trabajador del jugador (granjero, leñador, minero...): un recluta con oficio y puesto. */
	public static final EntityType<WorkerEntity> WORKER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("worker"),
			EntityType.Builder.create(WorkerEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("worker"));

	/** Comerciante de ciudad (fase 9): comercia con Glory Coins. */
	public static final EntityType<TraderEntity> TRADER = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("trader"),
			EntityType.Builder.create(TraderEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("trader"));

	/** Bandido: enemigo de los jugadores. */
	public static final EntityType<BanditEntity> BANDIT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("bandit"),
			EntityType.Builder.create(BanditEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("bandit"));

	/** Vikingo: guerrero del norte (fase 5). */
	public static final EntityType<VikingEntity> VIKING = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("viking"),
			EntityType.Builder.create(VikingEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("viking"));

	/** Pirata: saqueador de las costas (fase 5). */
	public static final EntityType<PirateEntity> PIRATE = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("pirate"),
			EntityType.Builder.create(PirateEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(10)
					.build("pirate"));

	/** Máquinas de asedio (fase 7). */
	public static final EntityType<SiegeEngineEntity> CATAPULT = engine(SiegeKind.CATAPULT, 3.0F, 2.0F);
	public static final EntityType<SiegeEngineEntity> BALLISTA = engine(SiegeKind.BALLISTA, 1.6F, 1.7F);
	public static final EntityType<SiegeEngineEntity> TREBUCHET = engine(SiegeKind.TREBUCHET, 5.0F, 6.3F);
	public static final EntityType<SiegeEngineEntity> RAM = engine(SiegeKind.RAM, 3.0F, 2.9F);

	/** Proyectiles de las máquinas (piedra, pedrusco, vasija incendiaria, virote). */
	public static final EntityType<SiegeShotEntity> SIEGE_SHOT = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("siege_shot"),
			EntityType.Builder.<SiegeShotEntity>create(SiegeShotEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.maxTrackingRange(10)
					.trackingTickInterval(10)
					.build("siege_shot"));

	/** Jabalina lanzada (fase 8). */
	public static final EntityType<JavelinEntity> JAVELIN = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("javelin"),
			EntityType.Builder.<JavelinEntity>create(JavelinEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.eyeHeight(0.13F)
					.maxTrackingRange(4)
					.trackingTickInterval(20)
					.build("javelin"));

	/** Bala de mosquete en vuelo (fase 10). */
	public static final EntityType<com.evolvedwarfare.weapon.MusketBallEntity> MUSKET_BALL = Registry.register(Registries.ENTITY_TYPE,
			EvolvedWarfare.id("musket_ball"),
			EntityType.Builder.<com.evolvedwarfare.weapon.MusketBallEntity>create(com.evolvedwarfare.weapon.MusketBallEntity::new,
							SpawnGroup.MISC)
					.dimensions(0.15F, 0.15F)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("musket_ball"));

	private static EntityType<SiegeEngineEntity> engine(SiegeKind kind, float width, float height) {
		return Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(kind.id()),
				EntityType.Builder.<SiegeEngineEntity>create((type, world) -> new SiegeEngineEntity(type, world, kind), SpawnGroup.MISC)
						.dimensions(width, height)
						.maxTrackingRange(10)
						.trackingTickInterval(2)
						.build(kind.id()));
	}

	public static EntityType<SiegeEngineEntity> engineType(SiegeKind kind) {
		return switch (kind) {
			case CATAPULT -> CATAPULT;
			case BALLISTA -> BALLISTA;
			case TREBUCHET -> TREBUCHET;
			case RAM -> RAM;
		};
	}

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(RECRUIT, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, AbstractNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(WORKER, WorkerEntity.createWorkerAttributes());
		FabricDefaultAttributeRegistry.register(BANDIT, BanditEntity.createBanditAttributes());
		FabricDefaultAttributeRegistry.register(VIKING, FactionNpcEntity.createFactionAttributes());
		FabricDefaultAttributeRegistry.register(PIRATE, FactionNpcEntity.createFactionAttributes());
		FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createTraderAttributes());
	}
}
